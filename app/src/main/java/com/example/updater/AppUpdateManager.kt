package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
  val versionTag: String,
  val normalizedVersion: String,
  val releaseName: String,
  val changelog: String,
  val apkDownloadUrl: String?,
  val apkSizeBytes: Long,
  val releasePageUrl: String
)

sealed class UpdateDownloadState {
  data object Idle : UpdateDownloadState()
  data class Downloading(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
  data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
  data class Error(val message: String) : UpdateDownloadState()
}

/**
 * Checks GitHub Releases API (`https://api.github.com/repos/{owner}/{repo}/releases/latest`)
 * for newer APK releases, downloads the APK cleanly with progress, and launches Android's
 * system package installer via FileProvider.
 */
object AppUpdateManager {

  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  /**
   * Normalizes a GitHub repo input — accepts either `owner/repo` or `https://github.com/owner/repo`.
   */
  fun normalizeRepoSlug(raw: String): String {
    val cleaned = raw.trim()
      .removePrefix("https://github.com/")
      .removePrefix("http://github.com/")
      .removePrefix("github.com/")
      .removeSuffix(".git")
      .trim('/')
    val parts = cleaned.split("/").filter { it.isNotBlank() }
    return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ""
  }

  /**
   * Semantic version comparator (e.g. `isNewerVersion("v1.8.0", "1.7.0") == true`).
   */
  fun isNewerVersion(remoteTag: String, currentVersion: String = BuildConfig.VERSION_NAME): Boolean {
    val rParts = parseVersionParts(remoteTag)
    val cParts = parseVersionParts(currentVersion)
    val len = maxOf(rParts.size, cParts.size)
    for (i in 0 until len) {
      val r = rParts.getOrElse(i) { 0 }
      val c = cParts.getOrElse(i) { 0 }
      if (r != c) return r > c
    }
    return false
  }

  private fun parseVersionParts(version: String): List<Int> {
    val numeric = version.trim().removePrefix("v").removePrefix("V")
      .substringBefore("-")
      .substringBefore("+")
    return numeric.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
  }

  suspend fun checkLatestRelease(repoSlugInput: String): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
    val slug = normalizeRepoSlug(repoSlugInput)
    if (slug.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Set your GitHub repo (owner/repo) in Settings → App Updates"))
    }
    runCatching {
      val apiUrl = "https://api.github.com/repos/$slug/releases/latest"
      val req = Request.Builder()
        .url(apiUrl)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "ZenithFoxBrowser/${BuildConfig.VERSION_NAME}")
        .build()

      client.newCall(req).execute().use { resp ->
        if (resp.code == 404) return@use null
        if (!resp.isSuccessful) {
          throw IllegalStateException("GitHub API returned HTTP ${resp.code}")
        }
        val body = resp.body?.string().orEmpty()
        if (body.isBlank()) return@use null
        val json = JSONObject(body)
        if (json.optBoolean("draft", false)) return@use null

        val tag = json.optString("tag_name", "").trim()
        if (tag.isBlank()) return@use null
        val normalized = tag.removePrefix("v").removePrefix("V")
        val releaseName = json.optString("name", "").ifBlank { "ZenithFox Browser $tag" }
        val changelog = json.optString("body", "").ifBlank { "Bug fixes and performance improvements." }
        val htmlUrl = json.optString("html_url", "https://github.com/$slug/releases/latest")

        var apkUrl: String? = null
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
          for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
              apkUrl = asset.optString("browser_download_url", "").takeIf { it.startsWith("http") }
              apkSize = asset.optLong("size", 0L)
              break
            }
          }
        }

        AppReleaseInfo(
          versionTag = tag,
          normalizedVersion = normalized,
          releaseName = releaseName,
          changelog = changelog,
          apkDownloadUrl = apkUrl,
          apkSizeBytes = apkSize,
          releasePageUrl = htmlUrl
        )
      }
    }
  }

  suspend fun downloadApk(
    context: Context,
    release: AppReleaseInfo,
    onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
  ): Result<File> = withContext(Dispatchers.IO) {
    val downloadUrl = release.apkDownloadUrl
      ?: return@withContext Result.failure(IllegalStateException("No APK asset attached to release ${release.versionTag}"))

    runCatching {
      val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
      // Remove stale cached APKs before downloading
      updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

      val safeVersion = release.normalizedVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val outFile = File(updatesDir, "ZenithFoxBrowser-$safeVersion.apk")

      val req = Request.Builder()
        .url(downloadUrl)
        .header("User-Agent", "ZenithFoxBrowser/${BuildConfig.VERSION_NAME}")
        .build()

      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
          throw IllegalStateException("Download failed (HTTP ${resp.code})")
        }
        val body = resp.body ?: throw IllegalStateException("Empty APK response")
        val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
        body.byteStream().use { input ->
          FileOutputStream(outFile).use { output ->
            val buf = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastReportedPct = -1
            while (true) {
              val read = input.read(buf)
              if (read <= 0) break
              output.write(buf, 0, read)
              downloaded += read
              val pct = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
              if (pct != lastReportedPct) {
                lastReportedPct = pct
                onProgress(pct, downloaded, total)
              }
            }
            output.flush()
          }
        }
      }
      onProgress(100, outFile.length(), outFile.length())
      outFile
    }
  }

  /**
   * Launches Android's package installer for the downloaded APK.
   * On Android 8+, directs user to allow "Install unknown apps" for ZenithFox Browser if not yet enabled.
   */
  fun launchApkInstaller(context: Context, apkFile: File): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
      val permIntent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { context.startActivity(permIntent) }
      return false
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(installIntent)
      true
    }.getOrDefault(false)
  }
}

package com.example.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.example.data.db.DownloadDao
import com.example.data.db.DownloadEntity
import com.example.data.model.HlsVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class BrowserDownloadManager(
  private val context: Context,
  private val downloadDao: DownloadDao,
  private val scope: CoroutineScope
) {
  private val systemDownloadManager =
    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

  private val hlsDownloader = HlsVideoDownloader(context, downloadDao, scope)

  val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeAll()

  init {
    startProgressPoller()
  }

  suspend fun inspectHlsQualities(
    playlistUrl: String,
    headers: Map<String, String>,
    fallbackDurationSec: Double = 0.0
  ): Result<List<HlsVariant>> = hlsDownloader.inspectVariants(playlistUrl, headers, fallbackDurationSec)

  suspend fun probeDirectVideoSize(
    url: String,
    headers: Map<String, String>
  ): Long = hlsDownloader.probeDirectVideoSize(url, headers)

  /**
   * Starts a real download:
   * - HLS (.m3u8) playlists are handled by our native Kotlin HlsVideoDownloader (downloads & merges segments)
   * - Direct files (.mp4, .webm, .vtt, .srt, general files) use Android DownloadManager with captured CDN headers
   * - Honestly rejects blob: URLs and DRM-encrypted streams
   */
  suspend fun enqueueDownload(
    url: String,
    suggestedFileName: String? = null,
    mimeType: String? = null,
    subfolder: String = "AnimeBrowser",
    headers: Map<String, String> = emptyMap()
  ): Result<Long> {
    val lower = url.lowercase()
    if (lower.startsWith("blob:") || lower.contains(".mpd")) {
      return Result.failure(
        IllegalArgumentException("Blob/DASH DRM streams cannot be directly downloaded.")
      )
    }

    if (lower.contains(".m3u8") ||
      mimeType?.contains("mpegurl", ignoreCase = true) == true
    ) {
      val baseTitle = suggestedFileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "Anime_Episode_${System.currentTimeMillis() / 1000}"
      return hlsDownloader.startHlsDownload(
        playlistUrl = url,
        baseFileName = baseTitle,
        headers = headers,
        subfolder = subfolder
      )
    }

    return runCatching {
      val fileName = (suggestedFileName?.takeIf { it.isNotBlank() }
        ?: URLUtil.guessFileName(url, null, mimeType))
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
      val safeSubfolder = subfolder.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "ZenithFox" }

      val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle(fileName)
        setDescription("Downloading via ZenithFox Browser")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setAllowedOverMetered(true)
        setAllowedOverRoaming(false)
        headers.forEach { (key, value) ->
          if (value.isNotBlank()) {
            addRequestHeader(key, value)
          }
        }
        if (resolvedMime != "*/*") {
          setMimeType(resolvedMime)
        }
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safeSubfolder/$fileName")
      }

      val systemId = systemDownloadManager.enqueue(request)
      val entity = DownloadEntity(
        systemDownloadId = systemId,
        fileName = fileName,
        url = url,
        mimeType = resolvedMime,
        status = "RUNNING",
        destinationFolder = "Downloads/$safeSubfolder"
      )
      downloadDao.insert(entity)
    }
  }

  suspend fun cancelDownload(download: DownloadEntity) {
    if (download.systemDownloadId > 0) {
      runCatching { systemDownloadManager.remove(download.systemDownloadId) }
    } else {
      hlsDownloader.cancelJob(download.id)
    }
    downloadDao.updateProgress(
      id = download.id,
      status = "CANCELLED",
      downloadedBytes = download.downloadedBytes,
      totalBytes = download.totalBytes,
      localUri = download.localUri
    )
  }

  suspend fun retryDownload(download: DownloadEntity, subfolder: String): Result<Long> {
    downloadDao.deleteById(download.id)
    return enqueueDownload(
      url = download.url,
      suggestedFileName = download.fileName,
      mimeType = download.mimeType,
      subfolder = subfolder
    )
  }

  suspend fun deleteDownload(download: DownloadEntity) {
    if (download.systemDownloadId > 0) {
      runCatching { systemDownloadManager.remove(download.systemDownloadId) }
    } else {
      hlsDownloader.cancelJob(download.id)
    }
    if (download.localUri.startsWith("file://")) {
      runCatching {
        val file = File(Uri.parse(download.localUri).path.orEmpty())
        if (file.exists()) file.delete()
      }
    } else if (download.localUri.startsWith("content://")) {
      runCatching {
        context.contentResolver.delete(Uri.parse(download.localUri), null, null)
      }
    }
    downloadDao.deleteById(download.id)
  }

  fun openDownloadedFile(download: DownloadEntity): Boolean {
    return runCatching {
      val uri: Uri = when {
        download.systemDownloadId > 0 ->
          systemDownloadManager.getUriForDownloadedFile(download.systemDownloadId)
        download.localUri.startsWith("file://") -> {
          val file = File(Uri.parse(download.localUri).path.orEmpty())
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        download.localUri.isNotBlank() -> Uri.parse(download.localUri)
        else -> return false
      } ?: return false

      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, download.mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  private fun startProgressPoller() {
    scope.launch(Dispatchers.IO) {
      while (isActive) {
        runCatching {
          val active = downloadDao.getActiveDownloads()
          for (item in active) {
            if (item.systemDownloadId <= 0) continue
            val query = DownloadManager.Query().setFilterById(item.systemDownloadId)
            val cursor: Cursor? = systemDownloadManager.query(query)
            cursor?.use { c ->
              if (c.moveToFirst()) {
                val statusInt = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = if (localUriIdx >= 0) c.getString(localUriIdx).orEmpty() else item.localUri

                val statusStr = when (statusInt) {
                  DownloadManager.STATUS_PENDING -> "PENDING"
                  DownloadManager.STATUS_RUNNING -> "RUNNING"
                  DownloadManager.STATUS_PAUSED -> "PAUSED"
                  DownloadManager.STATUS_SUCCESSFUL -> "COMPLETED"
                  DownloadManager.STATUS_FAILED -> "FAILED"
                  else -> item.status
                }
                downloadDao.updateProgress(
                  id = item.id,
                  status = statusStr,
                  downloadedBytes = downloaded.coerceAtLeast(0L),
                  totalBytes = total.coerceAtLeast(0L),
                  localUri = localUri
                )
              }
            }
          }
        }
        delay(1200L)
      }
    }
  }
}

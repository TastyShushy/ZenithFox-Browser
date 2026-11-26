package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.data.model.SearchEngine
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object UrlUtils {
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .build()

  fun normalizeInputToUrl(rawInput: String, searchEngine: SearchEngine): String {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed == "about:home" || trimmed == "about:blank") return trimmed

    val lower = trimmed.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return trimmed
    }

    val looksLikeDomain = !trimmed.contains(" ") &&
      trimmed.contains(".") &&
      !trimmed.startsWith(".") &&
      !trimmed.endsWith(".")

    return if (looksLikeDomain) {
      "https://$trimmed"
    } else {
      val encoded = URLEncoder.encode(trimmed, "UTF-8")
      String.format(searchEngine.searchUrlTemplate, encoded)
    }
  }

  fun extractHost(url: String): String {
    if (url.isBlank()) return ""
    return runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
  }

  fun displayHostOrUrl(url: String): String {
    if (url.isBlank() || url == "about:home") return ""
    val host = extractHost(url)
    return host.removePrefix("www.").ifBlank { url }
  }

  fun buildTranslateUrl(originalUrl: String): String {
    val encoded = URLEncoder.encode(originalUrl, "UTF-8")
    return "https://translate.google.com/translate?sl=auto&tl=en&u=$encoded"
  }

  suspend fun fetchSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
    val clean = query.trim()
    if (clean.length < 2 || clean.startsWith("http://") || clean.startsWith("https://")) {
      return@withContext emptyList()
    }
    runCatching {
      val encoded = URLEncoder.encode(clean, "UTF-8")
      val req = Request.Builder()
        .url("https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded")
        .header("User-Agent", "Mozilla/5.0 (Android 16; Mobile) Gecko/140.0 Firefox/140.0")
        .build()
      httpClient.newCall(req).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        val root = JSONArray(body)
        val suggestionsArray = root.optJSONArray(1) ?: return@withContext emptyList()
        buildList {
          for (i in 0 until minOf(suggestionsArray.length(), 6)) {
            val item = suggestionsArray.optString(i)
            if (item.isNotBlank()) add(item)
          }
        }
      }
    }.getOrDefault(emptyList())
  }

  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return runCatching {
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
      val pixels = IntArray(sizePx * sizePx)
      for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
        }
      }
      Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
      }
    }.getOrNull()
  }

  suspend fun savePageHtmlLocally(context: Context, title: String, html: String): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val dir = File(context.cacheDir, "saved_pages").apply { mkdirs() }
        val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40).ifBlank { "webpage" }
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.html")
        file.writeText(html, Charsets.UTF_8)
        file
      }
    }

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
      gb >= 1.0 -> String.format("%.2f GB", gb)
      mb >= 1.0 -> String.format("%.1f MB", mb)
      kb >= 1.0 -> String.format("%.0f KB", kb)
      else -> "$bytes B"
    }
  }

  fun formatTimeSec(sec: Double): String {
    if (!sec.isFinite() || sec < 0) return "0:00"
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
  }
}

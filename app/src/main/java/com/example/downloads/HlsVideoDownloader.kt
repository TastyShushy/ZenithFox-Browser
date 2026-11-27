package com.example.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.db.DownloadDao
import com.example.data.db.DownloadEntity
import com.example.data.model.HlsVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin HLS (.m3u8) playlist inspector & parallel segment downloader/merger.
 * Uses OkHttp with exact Referer/Origin/User-Agent/Cookie headers captured by the built-in
 * Anime Video Downloader extension so anime streaming CDNs serve segments cleanly.
 * Supports multi-quality master playlists, standard AES-128 segments, fMP4 init maps,
 * and MediaStore Downloads saving (Android 10–16+). Honestly rejects DRM-encrypted streams.
 */
class HlsVideoDownloader(
  private val context: Context,
  private val downloadDao: DownloadDao,
  private val scope: CoroutineScope
) {
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val activeJobs = ConcurrentHashMap<Long, Job>()

  /**
   * Parses an HLS master playlist into selectable quality variants (e.g. 1080p, 720p, 480p)
   * and calculates accurate estimated file size (MB) from bitrate * video duration.
   */
  suspend fun inspectVariants(
    playlistUrl: String,
    headers: Map<String, String>,
    fallbackDurationSec: Double = 0.0
  ): Result<List<HlsVariant>> = withContext(Dispatchers.IO) {
    runCatching {
      val text = fetchText(playlistUrl, headers)
      if (isDrmManifest(text)) {
        throw IllegalStateException("This HLS stream uses DRM encryption (Widevine/FairPlay) and cannot be downloaded.")
      }
      val rawVariants = parseMasterVariants(playlistUrl, text)
      if (rawVariants.isNotEmpty()) {
        // Get true episode duration from video player telemetry or by peeking one media sub-playlist
        var duration = fallbackDurationSec
        if (duration <= 0.0) {
          val sampleUrl = rawVariants.first().url
          duration = runCatching { parseDurationSeconds(fetchText(sampleUrl, headers)) }.getOrDefault(0.0)
        }
        rawVariants
          .sortedByDescending { it.bandwidthKbps }
          .map { v ->
            val estBytes = if (duration > 0.0 && v.bandwidthKbps > 0) {
              ((v.bandwidthKbps.toDouble() * 1000.0 / 8.0) * duration).toLong()
            } else 0L
            v.copy(estimatedBytes = estBytes, durationSec = duration)
          }
      } else {
        val duration = parseDurationSeconds(text).takeIf { it > 0.0 } ?: fallbackDurationSec
        // Estimate average bitrate (~2.2 Mbps for typical 720p/1080p anime) if single media playlist lacks BANDWIDTH tag
        val parsed = parseMediaPlaylist(playlistUrl, text)
        val estBytes = if (duration > 0.0) {
          ((2_200_000.0 / 8.0) * duration).toLong()
        } else (parsed.segments.size * 650_000L)
        listOf(
          HlsVariant(
            label = "Source Quality",
            resolution = "Auto",
            bandwidthKbps = 0,
            url = playlistUrl,
            estimatedBytes = estBytes,
            durationSec = duration
          )
        )
      }
    }
  }

  /**
   * Probes Content-Length for direct MP4/WebM URLs via lightweight HEAD request if size wasn't reported.
   */
  suspend fun probeDirectVideoSize(url: String, headers: Map<String, String>): Long = withContext(Dispatchers.IO) {
    runCatching {
      val builder = Request.Builder().url(url).head()
      headers.forEach { (k, v) -> if (v.isNotBlank()) builder.header(k, v) }
      if (!headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 15; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36")
      }
      client.newCall(builder.build()).execute().use { resp ->
        resp.header("Content-Length")?.toLongOrNull() ?: 0L
      }
    }.getOrDefault(0L)
  }

  /**
   * Starts a background HLS download, updating Room progress in real time and merging into
   * Downloads/<subfolder>/<fileName>.ts (or .mp4 for fMP4 streams).
   */
  suspend fun startHlsDownload(
    playlistUrl: String,
    baseFileName: String,
    headers: Map<String, String>,
    subfolder: String = "AnimeBrowser"
  ): Result<Long> {
    val safeSubfolder = subfolder.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "AnimeBrowser" }
    val cleanBase = baseFileName
      .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
      .trim()
      .ifBlank { "anime_episode_${System.currentTimeMillis() / 1000}" }
      .take(90)

    val entity = DownloadEntity(
      systemDownloadId = -1L, // Managed by HlsVideoDownloader instead of Android DownloadManager
      fileName = "$cleanBase.ts",
      url = playlistUrl,
      mimeType = "video/mp2t",
      status = "RUNNING",
      destinationFolder = "Downloads/$safeSubfolder"
    )
    val rowId = downloadDao.insert(entity)

    val job = scope.launch(Dispatchers.IO) {
      val tempDir = File(context.cacheDir, "hls_dl_$rowId").apply { mkdirs() }
      try {
        // 1. Fetch playlist; if it's a master playlist, pick highest bandwidth variant automatically
        var mediaPlaylistUrl = playlistUrl
        var playlistText = fetchText(mediaPlaylistUrl, headers)
        if (isDrmManifest(playlistText)) {
          throw IllegalStateException("DRM-protected HLS stream")
        }
        val masterVariants = parseMasterVariants(mediaPlaylistUrl, playlistText)
        if (masterVariants.isNotEmpty()) {
          val best = masterVariants.maxByOrNull { it.bandwidthKbps } ?: masterVariants.first()
          mediaPlaylistUrl = best.url
          playlistText = fetchText(mediaPlaylistUrl, headers)
          if (isDrmManifest(playlistText)) {
            throw IllegalStateException("DRM-protected HLS variant")
          }
        }

        // 2. Parse segments, optional init map (fMP4), and optional AES-128 key
        val parsed = parseMediaPlaylist(mediaPlaylistUrl, playlistText)
        if (parsed.segments.isEmpty()) {
          throw IllegalStateException("No downloadable video segments found in playlist")
        }

        val isFmp4 = parsed.initMapUrl != null || parsed.segments.first().url.contains(".m4s", ignoreCase = true)
        val finalFileName = if (isFmp4) "$cleanBase.mp4" else "$cleanBase.ts"
        val finalMime = if (isFmp4) "video/mp4" else "video/mp2t"

        // Fetch AES-128 keys (cached per key URI)
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        parsed.segments.mapNotNull { it.keyUri }.distinct().forEach { keyUrl ->
          keyCache[keyUrl] = fetchBytes(keyUrl, headers)
        }

        // Optional fMP4 init segment
        val initBytes = parsed.initMapUrl?.let { fetchBytes(it, headers) }

        val totalSegments = parsed.segments.size
        val completedSegments = AtomicInteger(0)
        val downloadedBytes = AtomicLong(initBytes?.size?.toLong() ?: 0L)
        val semaphore = Semaphore(4) // 4 concurrent segment downloads

        // 3. Download all segments in parallel with retry
        coroutineScope {
          parsed.segments.mapIndexed { idx, seg ->
            async(Dispatchers.IO) {
              semaphore.withPermit {
                var raw = fetchBytesWithRetry(seg.url, headers, maxRetries = 2)
                if (seg.keyUri != null) {
                  val key = keyCache[seg.keyUri]
                  if (key != null) {
                    raw = decryptAes128(raw, key, seg.ivBytes ?: mediaSequenceToIv(parsed.mediaSequence + idx))
                  }
                }
                val segFile = File(tempDir, String.format("seg_%06d.bin", idx))
                segFile.writeBytes(raw)

                val done = completedSegments.incrementAndGet()
                val bytesNow = downloadedBytes.addAndGet(raw.size.toLong())
                val estTotal = if (done > 0) (bytesNow * totalSegments) / done else bytesNow
                if (done == 1 || done % 3 == 0 || done == totalSegments) {
                  downloadDao.updateProgress(
                    id = rowId,
                    status = "RUNNING",
                    downloadedBytes = bytesNow,
                    totalBytes = estTotal.coerceAtLeast(bytesNow),
                    localUri = ""
                  )
                }
              }
            }
          }.awaitAll()
        }

        // 4. Merge segments into final playable file in Downloads/AnimeBrowser/
        val savedUri = writeMergedFileToDownloads(
          fileName = finalFileName,
          mimeType = finalMime,
          subfolder = safeSubfolder,
          initBytes = initBytes,
          tempDir = tempDir,
          segmentCount = totalSegments
        )

        val finalBytes = downloadedBytes.get()
        downloadDao.updateProgress(
          id = rowId,
          status = "COMPLETED",
          downloadedBytes = finalBytes,
          totalBytes = finalBytes,
          localUri = savedUri
        )
      } catch (ce: CancellationException) {
        downloadDao.updateProgress(rowId, "CANCELLED", 0L, 0L, "")
        throw ce
      } catch (e: Exception) {
        downloadDao.updateProgress(rowId, "FAILED", 0L, 0L, "")
      } finally {
        activeJobs.remove(rowId)
        runCatching { tempDir.deleteRecursively() }
      }
    }

    activeJobs[rowId] = job
    return Result.success(rowId)
  }

  fun cancelJob(downloadId: Long) {
    activeJobs.remove(downloadId)?.cancel()
  }

  private fun writeMergedFileToDownloads(
    fileName: String,
    mimeType: String,
    subfolder: String,
    initBytes: ByteArray?,
    tempDir: File,
    segmentCount: Int
  ): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val resolver = context.contentResolver
      val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subfolder")
        put(MediaStore.Downloads.IS_PENDING, 1)
      }
      val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Could not create MediaStore file in Downloads")
      resolver.openOutputStream(uri)?.use { out ->
        streamSegmentsToOutput(out, initBytes, tempDir, segmentCount)
      } ?: throw IllegalStateException("Could not open MediaStore output stream")

      values.clear()
      values.put(MediaStore.Downloads.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
      return uri.toString()
    } else {
      val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        subfolder
      ).apply { mkdirs() }
      val outFile = File(dir, fileName)
      FileOutputStream(outFile).use { out ->
        streamSegmentsToOutput(out, initBytes, tempDir, segmentCount)
      }
      return Uri.fromFile(outFile).toString()
    }
  }

  private fun streamSegmentsToOutput(
    rawOut: OutputStream,
    initBytes: ByteArray?,
    tempDir: File,
    segmentCount: Int
  ) {
    BufferedOutputStream(rawOut, 64 * 1024).use { bos ->
      if (initBytes != null) bos.write(initBytes)
      val buffer = ByteArray(32 * 1024)
      for (i in 0 until segmentCount) {
        val segFile = File(tempDir, String.format("seg_%06d.bin", i))
        if (!segFile.exists()) continue
        segFile.inputStream().use { input ->
          var read: Int
          while (input.read(buffer).also { read = it } != -1) {
            bos.write(buffer, 0, read)
          }
        }
      }
      bos.flush()
    }
  }

  private fun fetchText(url: String, headers: Map<String, String>): String {
    val req = buildRequest(url, headers)
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} fetching playlist")
      return resp.body?.string().orEmpty()
    }
  }

  private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray {
    val req = buildRequest(url, headers)
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} fetching segment")
      return resp.body?.bytes() ?: ByteArray(0)
    }
  }

  private fun fetchBytesWithRetry(url: String, headers: Map<String, String>, maxRetries: Int): ByteArray {
    var lastError: Throwable? = null
    for (attempt in 0..maxRetries) {
      try {
        return fetchBytes(url, headers)
      } catch (t: Throwable) {
        lastError = t
      }
    }
    throw lastError ?: IllegalStateException("Segment download failed")
  }

  private fun buildRequest(url: String, headers: Map<String, String>): Request {
    val builder = Request.Builder().url(url)
    headers.forEach { (k, v) ->
      if (v.isNotBlank()) builder.header(k, v)
    }
    if (!headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
      builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 15; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36")
    }
    return builder.build()
  }

  private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(data)
  }

  private fun mediaSequenceToIv(seq: Long): ByteArray {
    val buf = ByteBuffer.allocate(16)
    buf.putLong(0L)
    buf.putLong(seq)
    return buf.array()
  }

  companion object {
    private val BANDWIDTH_RE = Regex("""BANDWIDTH=(\d+)""")
    private val RESOLUTION_RE = Regex("""RESOLUTION=(\d+x\d+)""")
    private val URI_ATTR_RE = Regex("""URI="([^"]+)"""")
    private val METHOD_ATTR_RE = Regex("""METHOD=([A-Z0-9-]+)""")
    private val IV_ATTR_RE = Regex("""IV=0x([0-9a-fA-F]+)""")
    private val EXTINF_RE = Regex("""#EXTINF:([\d.]+)""")

    fun parseDurationSeconds(mediaPlaylistText: String): Double {
      var total = 0.0
      for (match in EXTINF_RE.findAll(mediaPlaylistText)) {
        total += match.groupValues[1].toDoubleOrNull() ?: 0.0
      }
      return total
    }

    data class HlsSegment(
      val url: String,
      val keyUri: String?,
      val ivBytes: ByteArray?
    )

    data class ParsedMediaPlaylist(
      val mediaSequence: Long,
      val initMapUrl: String?,
      val segments: List<HlsSegment>
    )

    fun isDrmManifest(manifestText: String): Boolean {
      val upper = manifestText.uppercase()
      return upper.contains("METHOD=SAMPLE-AES") ||
        upper.contains("COM.APPLE.STREAMINGKEYDELIVERY") ||
        upper.contains("URN:UUID:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED") // Widevine UUID
    }

    fun resolveUrl(baseUrl: String, relative: String): String {
      val trimmed = relative.trim()
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
      return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    fun parseMasterVariants(masterUrl: String, text: String): List<HlsVariant> {
      val lines = text.lines().map { it.trim() }
      val result = mutableListOf<HlsVariant>()
      var i = 0
      while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("#EXT-X-STREAM-INF:")) {
          val bw = BANDWIDTH_RE.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
          val res = RESOLUTION_RE.find(line)?.groupValues?.get(1).orEmpty()
          var j = i + 1
          while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
          if (j < lines.size) {
            val variantUrl = resolveUrl(masterUrl, lines[j])
            val heightLabel = res.substringAfter("x", "").takeIf { it.isNotBlank() }?.let { "${it}p" }
              ?: if (bw > 0) "${bw / 1000} kbps" else "Stream ${result.size + 1}"
            result.add(
              HlsVariant(
                label = heightLabel,
                resolution = res.ifBlank { "Auto" },
                bandwidthKbps = bw / 1000,
                url = variantUrl
              )
            )
            i = j
          }
        }
        i++
      }
      return result
    }

    fun parseMediaPlaylist(playlistUrl: String, text: String): ParsedMediaPlaylist {
      val lines = text.lines().map { it.trim() }
      var mediaSeq = 0L
      var initMapUrl: String? = null
      var currentKeyUri: String? = null
      var currentIv: ByteArray? = null
      val segments = mutableListOf<HlsSegment>()

      for (line in lines) {
        when {
          line.isBlank() -> continue
          line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
            mediaSeq = line.substringAfter(":").trim().toLongOrNull() ?: 0L
          }
          line.startsWith("#EXT-X-MAP:") -> {
            val mapUri = URI_ATTR_RE.find(line)?.groupValues?.get(1)
            if (!mapUri.isNullOrBlank()) {
              initMapUrl = resolveUrl(playlistUrl, mapUri)
            }
          }
          line.startsWith("#EXT-X-KEY:") -> {
            val method = METHOD_ATTR_RE.find(line)?.groupValues?.get(1).orEmpty()
            if (method == "NONE") {
              currentKeyUri = null
              currentIv = null
            } else if (method == "AES-128") {
              val kUri = URI_ATTR_RE.find(line)?.groupValues?.get(1)
              currentKeyUri = kUri?.let { resolveUrl(playlistUrl, it) }
              val ivHex = IV_ATTR_RE.find(line)?.groupValues?.get(1)
              currentIv = ivHex?.let { hexToBytes16(it) }
            }
          }
          line.startsWith("#") -> continue
          else -> {
            segments.add(
              HlsSegment(
                url = resolveUrl(playlistUrl, line),
                keyUri = currentKeyUri,
                ivBytes = currentIv
              )
            )
          }
        }
      }
      return ParsedMediaPlaylist(mediaSeq, initMapUrl, segments)
    }

    private fun hexToBytes16(hex: String): ByteArray {
      val padded = hex.padStart(32, '0').takeLast(32)
      return ByteArray(16) { idx ->
        padded.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
      }
    }
  }
}

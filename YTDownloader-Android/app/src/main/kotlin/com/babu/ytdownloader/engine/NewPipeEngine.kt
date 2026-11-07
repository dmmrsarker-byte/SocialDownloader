package com.babu.ytdownloader.engine

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet6Address
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * 100% pure-Kotlin/JVM YouTube extractor and downloader powered by:
 * 1. Direct YouTube Innertube API (ANDROID_VR & IOS clients) for fast, pre-signed MP4/M4A streams
 * 2. NewPipeExtractor fallback for playlists & web streams
 * 3. Byte-level HTTP Range Pause/Resume + 5x exponential backoff retry for slow 3G/flaky networks
 * 4. Automatic signed-URL refresh on HTTP 403/410 expiration during long pauses
 */
object NewPipeEngine {

  @Volatile private var appContext: Context? = null
  private val initialized = AtomicBoolean(false)
  private val canceledIds = ConcurrentHashMap.newKeySet<String>()
  private val videoCache = ConcurrentHashMap<String, YtExtractedData>()
  private val json = Json { ignoreUnknownKeys = true }

  private const val CACHE_TTL_MS = 10 * 60 * 1000L
  private const val MAX_CHUNK_RETRIES = 5

  internal data class YtStream(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val height: Int,
    val bitrate: Int,
    val contentLength: Long,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isMp4: Boolean,
    val isM4a: Boolean,
    val userAgent: String,
  )

  private data class YtExtractedData(
    val videoId: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val streams: List<YtStream>,
    val timestampMs: Long = System.currentTimeMillis(),
  )

  private val ipv6FirstDns = object : Dns {
    override fun lookup(hostname: String) =
      Dns.SYSTEM.lookup(hostname).sortedByDescending { it is Inet6Address }
  }

  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .dns(ipv6FirstDns)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  fun init(context: Context) {
    appContext = context.applicationContext
    ensureInit()
  }

  fun ensureInit() {
    if (initialized.compareAndSet(false, true)) {
      NewPipe.init(OkHttpNpDownloader(httpClient), Localization.DEFAULT)
    }
  }

  fun cancel(processId: String): Boolean = canceledIds.add(processId)

  /** Removes any partial files for a canceled or deleted task. */
  fun cleanupTaskFiles(processId: String) {
    val dir = appContext?.cacheDir?.let { File(it, "yt_work") } ?: return
    if (!dir.exists()) return
    dir.listFiles()?.forEach { f ->
      if (processId in f.name) {
        runCatching { f.delete() }
      }
    }
  }

  fun fetchInfo(rawUrl: String): MediaInfo {
    ensureInit()
    val ytMusic = isYtMusicUrl(rawUrl)
    val url = normalizeUrl(rawUrl)
    val yt = ServiceList.YouTube

    if (isPlaylistOnlyUrl(url)) {
      val pl = PlaylistInfo.getInfo(yt, url)
      val entries = pl.relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
        PlaylistEntry(
          id = runCatching { yt.streamLHFactory.getId(item.url) }.getOrNull(),
          title = item.name ?: "Video",
          url = item.url,
          durationSec = item.duration.takeIf { it > 0 },
          thumbnail = item.thumbnails.maxByOrNull { it.height }?.url,
          uploader = item.uploaderName?.removeSuffix(" - Topic"),
        )
      }
      return MediaInfo(
        url = pl.url ?: url,
        title = pl.name ?: if (ytMusic) "YT Music Playlist" else "Playlist",
        uploader = pl.uploaderName?.removeSuffix(" - Topic"),
        thumbnail = pl.thumbnails.maxByOrNull { it.height }?.url ?: entries.firstOrNull()?.thumbnail,
        durationSec = null,
        isPlaylist = true,
        entries = entries,
        isAudioCentric = ytMusic,
      )
    }

    val extracted = extractSingleVideo(url, forceRefresh = false)
    val heights = extracted.streams
      .filter { it.isVideo && it.height >= 144 }
      .map { snapHeight(it.height) }
      .distinct()
      .sortedDescending()

    return MediaInfo(
      url = if (ytMusic) rawUrl.trim() else extracted.url,
      title = extracted.title,
      uploader = extracted.uploader?.removeSuffix(" - Topic"),
      thumbnail = extracted.thumbnail,
      durationSec = extracted.durationSec,
      isPlaylist = false,
      videoHeights = heights.ifEmpty { listOf(1080, 720, 480, 360) },
      isAudioCentric = ytMusic,
    )
  }

  fun download(
    processId: String,
    rawUrl: String,
    options: DownloadOptions,
    outDir: File,
    onProgress: (Float, Long, String) -> Unit,
  ): List<File> {
    ensureInit()
    canceledIds.remove(processId)
    outDir.mkdirs()

    val url = normalizeUrl(rawUrl)
    onProgress(-1f, 0, "Preparing stream…")

    var data = extractSingleVideo(url, forceRefresh = false)
    checkCanceled(processId)

    val desiredTitle = options.customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: data.title
    val safeTitle = sanitizeFileName(desiredTitle).take(100)
    val idSuffix = if (options.customTitle.isNullOrBlank()) {
      data.videoId.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
    } else {
      ""
    }
    val baseName = "$safeTitle$idSuffix"

    return try {
      when (options.type) {
        MediaType.AUDIO -> downloadAudio(processId, data, options.audioFormat, outDir, baseName, onProgress)
        MediaType.VIDEO -> downloadVideo(processId, data, options.videoHeight, outDir, baseName, onProgress)
      }
    } catch (e: ExpiredStreamUrlException) {
      // Signed URL expired after a long pause; refresh once and resume from current byte offset!
      checkCanceled(processId)
      data = extractSingleVideo(url, forceRefresh = true)
      when (options.type) {
        MediaType.AUDIO -> downloadAudio(processId, data, options.audioFormat, outDir, baseName, onProgress)
        MediaType.VIDEO -> downloadVideo(processId, data, options.videoHeight, outDir, baseName, onProgress)
      }
    }
  }

  private class ExpiredStreamUrlException(message: String) : IOException(message)

  private fun extractSingleVideo(url: String, forceRefresh: Boolean): YtExtractedData {
    var resolvedUrl = url
    var videoId = extractVideoId(resolvedUrl)
    if (videoId == null) {
      runCatching {
        val req = Request.Builder().url(resolvedUrl).header("User-Agent", DESKTOP_USER_AGENT).build()
        httpClient.newCall(req).execute().use { resp ->
          resolvedUrl = resp.request.url.toString()
        }
      }
      videoId = extractVideoId(resolvedUrl)
    }
    if (!forceRefresh && videoId != null) {
      videoCache[videoId]?.takeIf { System.currentTimeMillis() - it.timestampMs < CACHE_TTL_MS }?.let {
        return it
      }
    }

    if (videoId != null) {
      // 1. Primary: YouTube Innertube ANDROID_VR client
      runCatching {
        fetchViaInnertube(
          videoId = videoId,
          watchUrl = url,
          clientName = "ANDROID_VR",
          clientVersion = "1.60.19",
          deviceMake = "Oculus",
          deviceModel = "Quest 3",
          osName = "Android",
          osVersion = "12L",
          androidSdkVersion = 32,
          userAgent = VR_USER_AGENT,
        )
      }.getOrNull()?.let {
        videoCache[videoId] = it
        return it
      }

      // 2. Secondary: YouTube Innertube IOS client
      runCatching {
        fetchViaInnertube(
          videoId = videoId,
          watchUrl = url,
          clientName = "IOS",
          clientVersion = "20.03.02",
          deviceMake = "Apple",
          deviceModel = "iPhone16,2",
          osName = "iPhone",
          osVersion = "18.2.1.22C161",
          androidSdkVersion = null,
          userAgent = IOS_USER_AGENT,
        )
      }.getOrNull()?.let {
        videoCache[videoId] = it
        return it
      }
    }

    // 3. Fallback: NewPipeExtractor
    val info = StreamInfo.getInfo(ServiceList.YouTube, url)
    val streams = mutableListOf<YtStream>()
    for (v in info.videoStreams) {
      if (!v.isUrl) continue
      val h = v.height.takeIf { it > 0 } ?: 360
      streams.add(
        YtStream(
          itag = v.itag,
          url = v.content,
          mimeType = v.format?.mimeType ?: "video/mp4",
          height = h,
          bitrate = v.bitrate,
          contentLength = -1L,
          isVideo = true,
          isAudio = true,
          isMp4 = v.format == MediaFormat.MPEG_4,
          isM4a = false,
          userAgent = DESKTOP_USER_AGENT,
        )
      )
    }
    for (v in info.videoOnlyStreams) {
      if (!v.isUrl) continue
      val h = v.height.takeIf { it > 0 } ?: 360
      streams.add(
        YtStream(
          itag = v.itag,
          url = v.content,
          mimeType = v.format?.mimeType ?: "video/mp4",
          height = h,
          bitrate = v.bitrate,
          contentLength = -1L,
          isVideo = true,
          isAudio = false,
          isMp4 = v.format == MediaFormat.MPEG_4,
          isM4a = false,
          userAgent = DESKTOP_USER_AGENT,
        )
      )
    }
    for (a in info.audioStreams) {
      if (!a.isUrl) continue
      streams.add(
        YtStream(
          itag = a.itag,
          url = a.content,
          mimeType = a.format?.mimeType ?: "audio/mp4",
          height = 0,
          bitrate = a.averageBitrate * 1000,
          contentLength = -1L,
          isVideo = false,
          isAudio = true,
          isMp4 = false,
          isM4a = a.format == MediaFormat.M4A,
          userAgent = DESKTOP_USER_AGENT,
        )
      )
    }

    if (streams.isEmpty()) {
      throw IllegalStateException("No downloadable YouTube streams found.")
    }

    val result = YtExtractedData(
      videoId = info.id ?: (videoId ?: ""),
      url = info.url ?: url,
      title = info.name ?: "YouTube Video",
      uploader = info.uploaderName,
      thumbnail = info.thumbnails.maxByOrNull { it.height }?.url,
      durationSec = info.duration.takeIf { it > 0 },
      streams = streams,
    )
    if (videoId != null) videoCache[videoId] = result
    return result
  }

  private fun fetchViaInnertube(
    videoId: String,
    watchUrl: String,
    clientName: String,
    clientVersion: String,
    deviceMake: String,
    deviceModel: String,
    osName: String,
    osVersion: String,
    androidSdkVersion: Int?,
    userAgent: String,
  ): YtExtractedData {
    val sdkField = if (androidSdkVersion != null) """"androidSdkVersion": $androidSdkVersion,""" else ""
    val payload = """
      {
        "videoId": "$videoId",
        "contentCheckOk": true,
        "racyCheckOk": true,
        "context": {
          "client": {
            "clientName": "$clientName",
            "clientVersion": "$clientVersion",
            "deviceMake": "$deviceMake",
            "deviceModel": "$deviceModel",
            "osName": "$osName",
            "osVersion": "$osVersion",
            $sdkField
            "hl": "en",
            "gl": "US"
          }
        }
      }
    """.trimIndent()

    val req = Request.Builder()
      .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
      .header("User-Agent", userAgent)
      .header("Content-Type", "application/json")
      .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
      .build()

    val respJson = httpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) throw IOException("Innertube HTTP ${resp.code}")
      resp.body?.string() ?: throw IOException("Empty Innertube body")
    }

    val root = json.parseToJsonElement(respJson).jsonObject
    val statusObj = root["playabilityStatus"] as? JsonObject
    val status = statusObj?.str("status")
    if (status != "OK") {
      val reason = statusObj?.str("reason") ?: "Status: $status"
      throw IllegalStateException(reason)
    }

    val videoDetails = root["videoDetails"] as? JsonObject
    val title = videoDetails?.str("title") ?: "YouTube Video"
    val author = videoDetails?.str("author")
    val lengthSec = videoDetails?.str("lengthSeconds")?.toLongOrNull()?.takeIf { it > 0 }
    val thumb = ((videoDetails?.get("thumbnail") as? JsonObject)?.get("thumbnails") as? JsonArray)
      ?.mapNotNull { it as? JsonObject }
      ?.maxByOrNull { it.int("height") ?: 0 }
      ?.str("url")
      ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    val streamingData = root["streamingData"] as? JsonObject
      ?: throw IllegalStateException("Missing streamingData")

    val allFormats = mutableListOf<JsonObject>()
    (streamingData["formats"] as? JsonArray)?.mapNotNullTo(allFormats) { it as? JsonObject }
    (streamingData["adaptiveFormats"] as? JsonArray)?.mapNotNullTo(allFormats) { it as? JsonObject }

    val streams = mutableListOf<YtStream>()
    for (fmt in allFormats) {
      val streamUrl = fmt.str("url") ?: continue
      val mime = fmt.str("mimeType") ?: ""
      val itag = fmt.int("itag") ?: 0
      val rawW = fmt.int("width") ?: 0
      val rawH = fmt.int("height") ?: 0
      val height = if (rawW > 0 && rawH > 0) minOf(rawW, rawH) else rawH
      val bitrate = fmt.int("averageBitrate") ?: fmt.int("bitrate") ?: 0
      val clen = fmt.str("contentLength")?.toLongOrNull() ?: -1L

      val isVideo = mime.startsWith("video/")
      val isAudio = mime.startsWith("audio/") || (isVideo && "mp4a" in mime)
      val isMp4 = mime.startsWith("video/mp4")
      val isM4a = mime.startsWith("audio/mp4")

      streams.add(
        YtStream(
          itag = itag,
          url = streamUrl,
          mimeType = mime,
          height = height,
          bitrate = bitrate,
          contentLength = clen,
          isVideo = isVideo,
          isAudio = isAudio,
          isMp4 = isMp4,
          isM4a = isM4a,
          userAgent = userAgent,
        )
      )
    }

    if (streams.isEmpty()) {
      throw IllegalStateException("No direct URLs in Innertube response")
    }

    return YtExtractedData(
      videoId = videoId,
      url = watchUrl,
      title = title,
      uploader = author,
      thumbnail = thumb,
      durationSec = lengthSec,
      streams = streams,
    )
  }

  private fun downloadAudio(
    processId: String,
    data: YtExtractedData,
    format: AudioFormat,
    outDir: File,
    baseName: String,
    onProgress: (Float, Long, String) -> Unit,
  ): List<File> {
    val audio = pickBestAudio(data.streams, format)
      ?: throw IllegalStateException("No audio stream found for this video")

    val ext = format.ext
    val mimeType = when (format) {
      AudioFormat.MP3 -> "audio/mpeg"
      AudioFormat.M4A -> "audio/mp4"
      AudioFormat.OPUS -> "audio/ogg"
    }
    val workDir = getWorkDir(outDir)
    val tempOut = File(workDir, "yt_audio_$processId.$ext")

    if (audio.isVideo) {
      val tempMuxed = File(workDir, "yt_muxed_audio_$processId.mp4")
      downloadStreamToFile(processId, audio.url, audio.userAgent, audio.contentLength, tempMuxed, 0f, 92f, onProgress)
      onProgress(95f, 0, "Extracting pure audio track…")
      if (!extractAudioTrackFromMp4(tempMuxed, tempOut)) {
        tempMuxed.copyTo(tempOut, overwrite = true)
      }
      tempMuxed.delete()
    } else {
      downloadStreamToFile(processId, audio.url, audio.userAgent, audio.contentLength, tempOut, 0f, 98f, onProgress)
    }

    val finalFile = publishToPublicDir(tempOut, outDir, "$baseName.$ext", mimeType)
    tempOut.delete()
    onProgress(100f, 0, "Completed")
    return listOf(finalFile)
  }

  private fun downloadVideo(
    processId: String,
    data: YtExtractedData,
    targetHeight: Int?,
    outDir: File,
    baseName: String,
    onProgress: (Float, Long, String) -> Unit,
  ): List<File> {
    val maxH = targetHeight ?: Int.MAX_VALUE
    val muxedMp4 = data.streams.filter { it.isVideo && it.isAudio && it.isMp4 }
    val videoOnlyMp4 = data.streams.filter { it.isVideo && !it.isAudio && it.isMp4 && "av01" !in it.mimeType }

    val bestMuxed = muxedMp4.filter { snapHeight(it.height) <= maxH }
      .maxWithOrNull(compareBy<YtStream> { snapHeight(it.height) }.thenBy { it.bitrate })
    val bestVideoOnly = videoOnlyMp4.filter { snapHeight(it.height) <= maxH }
      .maxWithOrNull(compareBy<YtStream> { snapHeight(it.height) }.thenBy { it.bitrate })
    val bestAudio = pickBestM4aAudio(data.streams)

    val workDir = getWorkDir(outDir)
    val tempFinal = File(workDir, "yt_final_$processId.mp4")

    if (bestMuxed != null && (bestVideoOnly == null || bestAudio == null || snapHeight(bestMuxed.height) >= snapHeight(bestVideoOnly.height))) {
      downloadStreamToFile(processId, bestMuxed.url, bestMuxed.userAgent, bestMuxed.contentLength, tempFinal, 0f, 98f, onProgress)
      val finalFile = publishToPublicDir(tempFinal, outDir, "$baseName.mp4", "video/mp4")
      tempFinal.delete()
      onProgress(100f, 0, "Completed")
      return listOf(finalFile)
    }

    val vStream = bestVideoOnly ?: bestMuxed
      ?: data.streams.firstOrNull { it.isVideo }
      ?: throw IllegalStateException("No video stream found")

    if (bestAudio == null || vStream.isAudio) {
      downloadStreamToFile(processId, vStream.url, vStream.userAgent, vStream.contentLength, tempFinal, 0f, 98f, onProgress)
      val finalFile = publishToPublicDir(tempFinal, outDir, "$baseName.mp4", "video/mp4")
      tempFinal.delete()
      onProgress(100f, 0, "Completed")
      return listOf(finalFile)
    }

    val tmpVideo = File(workDir, "tmp_v_$processId.mp4")
    val tmpAudio = File(workDir, "tmp_a_$processId.m4a")

    downloadStreamToFile(processId, vStream.url, vStream.userAgent, vStream.contentLength, tmpVideo, 0f, 75f, onProgress)
    checkCanceled(processId)
    downloadStreamToFile(processId, bestAudio.url, bestAudio.userAgent, bestAudio.contentLength, tmpAudio, 75f, 92f, onProgress)
    checkCanceled(processId)

    onProgress(95f, 0, "Merging video & audio…")
    val muxedOk = runCatching {
      muxVideoAndAudio(tmpVideo, tmpAudio, tempFinal)
      tempFinal.exists() && tempFinal.length() > 0
    }.getOrDefault(false)

    if (!muxedOk) {
      tempFinal.delete()
      if (bestMuxed != null) {
        downloadStreamToFile(processId, bestMuxed.url, bestMuxed.userAgent, bestMuxed.contentLength, tempFinal, 92f, 98f, onProgress)
      } else {
        tmpVideo.copyTo(tempFinal, overwrite = true)
      }
    }
    val finalFile = publishToPublicDir(tempFinal, outDir, "$baseName.mp4", "video/mp4")
    tmpVideo.delete()
    tmpAudio.delete()
    tempFinal.delete()
    onProgress(100f, 0, "Completed")
    return listOf(finalFile)
  }

  private fun getWorkDir(fallback: File): File {
    val dir = appContext?.cacheDir?.let { File(it, "yt_work") } ?: fallback
    dir.mkdirs()
    return dir
  }

  /**
   * Copies the completed file from internal cacheDir into public Download/SocialDownloader/...
   * Synchronized to prevent filename collisions when multiple concurrent downloads finish at the same millisecond.
   */
  @Synchronized
  fun publishToPublicDir(tempFile: File, outDir: File, fileName: String, mimeType: String): File {
    outDir.mkdirs()
    val directTarget = resolveAvailableFile(outDir, fileName)
    val directSuccess = runCatching {
      FileInputStream(tempFile).use { input ->
        FileOutputStream(directTarget).use { output ->
          input.copyTo(output, 64 * 1024)
        }
      }
      directTarget.exists() && directTarget.length() > 0
    }.getOrDefault(false)

    if (directSuccess) return directTarget

    val ctx = appContext
    if (ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
      val subPath = outDir.absolutePath.removePrefix(downloadsRoot).trimStart('/', '\\').replace('\\', '/')
      val relPath = if (subPath.isNotEmpty()) "${Environment.DIRECTORY_DOWNLOADS}/$subPath" else Environment.DIRECTORY_DOWNLOADS

      val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, directTarget.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }
      val resolver = ctx.contentResolver
      val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
      if (uri != null) {
        resolver.openOutputStream(uri)?.use { out ->
          FileInputStream(tempFile).use { input ->
            input.copyTo(out, 64 * 1024)
          }
        }
        val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, doneValues, null, null)
        return directTarget
      }
    }

    throw IOException("Could not save file to Downloads folder.")
  }

  private fun resolveAvailableFile(dir: File, fileName: String): File {
    var candidate = File(dir, fileName)
    if (!candidate.exists()) return candidate
    if (runCatching { candidate.delete() }.getOrDefault(false) && !candidate.exists()) {
      return candidate
    }
    val base = fileName.substringBeforeLast('.')
    val ext = fileName.substringAfterLast('.', "")
    for (i in 1..999) {
      val nextName = if (ext.isNotEmpty()) "$base ($i).$ext" else "$base ($i)"
      candidate = File(dir, nextName)
      if (!candidate.exists()) return candidate
    }
    return candidate
  }

  /**
   * Downloads a YouTube stream in 4 MB HTTP Range chunks with:
   * - Byte-level Pause/Resume (resumes from dest.length() if partial file exists)
   * - 5x Exponential Backoff retry per chunk for slow 3G / packet drops
   * - HTTP 401/403/410 detection to trigger automatic signed-URL refresh
   */
  private fun downloadStreamToFile(
    processId: String,
    streamUrl: String,
    userAgent: String,
    knownContentLength: Long,
    dest: File,
    startPct: Float,
    endPct: Float,
    onProgress: (Float, Long, String) -> Unit,
  ) {
    dest.parentFile?.mkdirs()

    val totalBytes = if (knownContentLength > 0) knownContentLength else probeContentLength(streamUrl, userAgent)
    val chunkSize = 4L * 1024L * 1024L
    var downloaded = if (dest.exists()) dest.length() else 0L

    if (totalBytes > 0 && downloaded >= totalBytes) {
      reportProgress(totalBytes, totalBytes, System.currentTimeMillis(), startPct, endPct, onProgress)
      return
    }
    if (totalBytes in 1 until downloaded) {
      dest.delete()
      downloaded = 0L
    }

    val startTime = System.currentTimeMillis()
    val startOffset = downloaded

    if (totalBytes > 0) {
      FileOutputStream(dest, downloaded > 0).use { out ->
        while (downloaded < totalBytes) {
          checkCanceled(processId)
          val endByte = minOf(downloaded + chunkSize - 1, totalBytes - 1)
          var attempt = 0
          while (true) {
            checkCanceled(processId)
            try {
              val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=$downloaded-$endByte")
                .build()
              httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403 || resp.code == 410) {
                  throw ExpiredStreamUrlException("HTTP ${resp.code}")
                }
                if (!resp.isSuccessful && resp.code != 206) {
                  throw IOException("HTTP Error ${resp.code}")
                }
                val body = resp.body ?: throw IOException("Empty response body")
                val buf = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                  var read: Int
                  while (input.read(buf).also { read = it } != -1) {
                    checkCanceled(processId)
                    out.write(buf, 0, read)
                    downloaded += read
                    reportProgress(downloaded, totalBytes, startTime, startPct, endPct, onProgress, startOffset)
                  }
                }
              }
              break
            } catch (e: ExpiredStreamUrlException) {
              throw e
            } catch (e: InterruptedException) {
              throw e
            } catch (e: IOException) {
              attempt++
              if (attempt >= MAX_CHUNK_RETRIES) throw e
              sleepWithCancelCheck(processId, attempt * 600L)
            }
          }
        }
      }
    } else {
      var attempt = 0
      while (true) {
        checkCanceled(processId)
        try {
          val reqBuilder = Request.Builder().url(streamUrl).header("User-Agent", userAgent)
          if (downloaded > 0) {
            reqBuilder.header("Range", "bytes=$downloaded-")
          }
          httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403 || resp.code == 410) {
              throw ExpiredStreamUrlException("HTTP ${resp.code}")
            }
            if (!resp.isSuccessful && resp.code != 206) throw IOException("HTTP Error ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response body")
            val isPartial = resp.code == 206 && downloaded > 0
            if (!isPartial) downloaded = 0L
            val len = body.contentLength().let { if (it > 0 && isPartial) it + downloaded else it }

            FileOutputStream(dest, isPartial).use { out ->
              val buf = ByteArray(64 * 1024)
              body.byteStream().use { input ->
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                  checkCanceled(processId)
                  out.write(buf, 0, read)
                  downloaded += read
                  reportProgress(downloaded, len, startTime, startPct, endPct, onProgress, startOffset)
                }
              }
            }
          }
          break
        } catch (e: ExpiredStreamUrlException) {
          throw e
        } catch (e: InterruptedException) {
          throw e
        } catch (e: IOException) {
          attempt++
          if (attempt >= MAX_CHUNK_RETRIES) throw e
          sleepWithCancelCheck(processId, attempt * 600L)
        }
      }
    }
  }

  private fun sleepWithCancelCheck(processId: String, delayMs: Long) {
    val step = 100L
    var waited = 0L
    while (waited < delayMs) {
      checkCanceled(processId)
      Thread.sleep(minOf(step, delayMs - waited))
      waited += step
    }
  }

  private fun probeContentLength(url: String, userAgent: String): Long {
    Regex("""[?&]clen=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()?.let {
      if (it > 0) return it
    }
    return runCatching {
      val req = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Range", "bytes=0-0")
        .build()
      httpClient.newCall(req).execute().use { resp ->
        resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
      }
    }.getOrDefault(-1L)
  }

  private fun reportProgress(
    downloaded: Long,
    total: Long,
    startTime: Long,
    startPct: Float,
    endPct: Float,
    onProgress: (Float, Long, String) -> Unit,
    startOffset: Long = 0L,
  ) {
    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.2)
    val bytesThisSession = (downloaded - startOffset).coerceAtLeast(0L)
    val speedBps = (bytesThisSession / elapsedSec).toLong()
    val pct = if (total > 0) {
      startPct + (downloaded.toFloat() / total.toFloat()) * (endPct - startPct)
    } else startPct
    val eta = if (total > downloaded && speedBps > 0) (total - downloaded) / speedBps else 0L
    val detail = buildString {
      if (total > 0) append("%.1f MB".format(total / 1048576.0))
      else append("%.1f MB".format(downloaded / 1048576.0))
      if (speedBps > 0) append(" • ").append("%.1f MB/s".format(speedBps / 1048576.0))
    }
    onProgress(pct.coerceIn(0f, 100f), eta, detail)
  }

  /** Hardware-free pure Android MP4 video + M4A audio muxer using android.media.MediaMuxer. */
  @SuppressLint("WrongConstant")
  private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
    outputFile.delete()
    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val vExtractor = MediaExtractor()
    val aExtractor = MediaExtractor()
    try {
      vExtractor.setDataSource(videoFile.absolutePath)
      aExtractor.setDataSource(audioFile.absolutePath)

      var vTrackIndex = -1
      for (i in 0 until vExtractor.trackCount) {
        val fmt = vExtractor.getTrackFormat(i)
        if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
          vExtractor.selectTrack(i)
          vTrackIndex = muxer.addTrack(fmt)
          break
        }
      }

      var aTrackIndex = -1
      for (i in 0 until aExtractor.trackCount) {
        val fmt = aExtractor.getTrackFormat(i)
        if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
          aExtractor.selectTrack(i)
          aTrackIndex = muxer.addTrack(fmt)
          break
        }
      }

      require(vTrackIndex >= 0 && aTrackIndex >= 0) { "Invalid tracks for MediaMuxer" }
      muxer.start()

      val buffer = ByteBuffer.allocate(1024 * 1024)
      val info = MediaCodec.BufferInfo()

      while (true) {
        val size = vExtractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = vExtractor.sampleTime
        info.flags = vExtractor.sampleFlags
        muxer.writeSampleData(vTrackIndex, buffer, info)
        vExtractor.advance()
      }

      while (true) {
        val size = aExtractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = aExtractor.sampleTime
        info.flags = aExtractor.sampleFlags
        muxer.writeSampleData(aTrackIndex, buffer, info)
        aExtractor.advance()
      }

      muxer.stop()
    } finally {
      runCatching { vExtractor.release() }
      runCatching { aExtractor.release() }
      runCatching { muxer.release() }
    }
  }

  /** Extracts the pure hardware AAC/audio track from a muxed MP4 file without re-encoding. */
  @SuppressLint("WrongConstant")
  internal fun extractAudioTrackFromMp4(videoFile: File, outputAudioFile: File): Boolean {
    if (!videoFile.exists() || videoFile.length() == 0L) return false
    outputAudioFile.delete()
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    return try {
      extractor.setDataSource(videoFile.absolutePath)
      var audioTrackIndex = -1
      var muxerTrackIndex = -1
      for (i in 0 until extractor.trackCount) {
        val fmt = extractor.getTrackFormat(i)
        val mime = fmt.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("audio/")) {
          extractor.selectTrack(i)
          audioTrackIndex = i
          muxer = MediaMuxer(outputAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
          muxerTrackIndex = muxer.addTrack(fmt)
          break
        }
      }
      if (audioTrackIndex < 0 || muxer == null || muxerTrackIndex < 0) {
        return false
      }
      muxer.start()
      val buffer = ByteBuffer.allocate(512 * 1024)
      val info = MediaCodec.BufferInfo()
      while (true) {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = extractor.sampleFlags
        muxer.writeSampleData(muxerTrackIndex, buffer, info)
        extractor.advance()
      }
      muxer.stop()
      outputAudioFile.exists() && outputAudioFile.length() > 0L
    } catch (_: Throwable) {
      outputAudioFile.delete()
      false
    } finally {
      runCatching { extractor.release() }
      runCatching { muxer?.release() }
    }
  }

  /**
   * Selects the highest-bitrate audio stream from YouTube / YouTube Music:
   * - For M4A: prefers highest-bitrate AAC/M4A (itag 141 256kbps / itag 140 128kbps)
   * - For OPUS: prefers highest-bitrate Opus/WebM (itag 774 256kbps / itag 251 160kbps)
   * - For MP3 (Best Audio): picks the highest bitrate audio-only stream across all formats
   */
  internal fun pickBestAudio(streams: List<YtStream>, format: AudioFormat = AudioFormat.MP3): YtStream? {
    val audioOnly = streams.filter { it.isAudio && !it.isVideo }
    val chosen = when (format) {
      AudioFormat.M4A ->
        audioOnly.filter { it.isM4a }.maxByOrNull { it.bitrate }
          ?: audioOnly.maxByOrNull { it.bitrate }
      AudioFormat.OPUS ->
        audioOnly.filter { "opus" in it.mimeType.lowercase() || "webm" in it.mimeType.lowercase() }
          .maxByOrNull { it.bitrate }
          ?: audioOnly.maxByOrNull { it.bitrate }
      AudioFormat.MP3 ->
        audioOnly.maxByOrNull { it.bitrate }
    }
    return chosen ?: streams.filter { it.isAudio }.maxByOrNull { it.bitrate }
  }

  private fun pickBestM4aAudio(streams: List<YtStream>): YtStream? {
    val audioOnly = streams.filter { it.isAudio && !it.isVideo }
    return audioOnly.filter { it.isM4a }.maxByOrNull { it.bitrate }
      ?: audioOnly.maxByOrNull { it.bitrate }
      ?: streams.filter { it.isAudio }.maxByOrNull { it.bitrate }
  }

  private val standardHeights = listOf(4320, 2160, 1440, 1080, 720, 480, 360, 240, 144)
  internal fun snapHeight(h: Int): Int = standardHeights.firstOrNull { h >= it * 0.9 } ?: h

  internal fun isYtMusicUrl(rawUrl: String): Boolean =
    "music.youtube.com" in rawUrl.lowercase()

  internal fun extractVideoId(url: String): String? =
    Regex("""(?:v=|youtu\.be/|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)

  internal fun normalizeUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    val videoId = extractVideoId(trimmed)
    val listMatch = Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(trimmed)
    val browseListMatch = Regex("""/browse/VL([A-Za-z0-9_-]+)""").find(trimmed)
    val listId = listMatch?.groupValues?.get(1) ?: browseListMatch?.groupValues?.get(1)

    if (videoId != null && (listId == null || listId.startsWith("RD"))) {
      return "https://www.youtube.com/watch?v=$videoId"
    }
    if (listId != null && ("/playlist" in trimmed || "/browse/" in trimmed || listId.startsWith("PL") || listId.startsWith("OL"))) {
      return "https://www.youtube.com/playlist?list=$listId"
    }
    return trimmed
  }

  private fun isPlaylistOnlyUrl(url: String): Boolean =
    "/playlist?" in url.lowercase() && "list=" in url.lowercase()

  internal fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "video" }

  private fun checkCanceled(processId: String) {
    if (canceledIds.remove(processId)) {
      throw InterruptedException("Canceled")
    }
  }

  private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

  private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

  private const val VR_USER_AGENT =
    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
  private const val IOS_USER_AGENT =
    "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
  private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

  private class OkHttpNpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
      val method = request.httpMethod()
      val data = request.dataToSend()
      val body = if (data != null) data.toRequestBody(null)
      else if (method == "POST") ByteArray(0).toRequestBody(null)
      else null

      val builder = Request.Builder()
        .url(request.url())
        .method(method, body)
        .header("User-Agent", DESKTOP_USER_AGENT)

      for ((k, values) in request.headers()) {
        builder.removeHeader(k)
        for (v in values) builder.addHeader(k, v)
      }

      client.newCall(builder.build()).execute().use { resp ->
        if (resp.code == 429) {
          throw ReCaptchaException("reCAPTCHA Challenge requested", request.url())
        }
        val responseBody = resp.body?.string() ?: ""
        return NpResponse(
          resp.code,
          resp.message,
          resp.headers.toMultimap(),
          responseBody,
          resp.request.url.toString(),
        )
      }
    }
  }
}

package com.babu.ytdownloader.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.Inet6Address
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Pure Kotlin/JVM extractor and downloader for:
 * - Pornhub (pornhub.com / pornhub.org / pornhubpremium.com — 1080p, 720p, 480p, 240p MP4 & HLS)
 * - Facebook (Video, Watch, Reels, Share)
 * - Instagram (Reels, Post, IGTV, Share)
 * - Threads (threads.net / threads.com)
 * - LinkedIn (linkedin.com / lnkd.in posts & native videos)
 * - X / Twitter (x.com / twitter.com / t.co status videos & GIFs)
 * - TikTok (tiktok.com / vm.tiktok.com / vt.tiktok.com HD watermark-free)
 * - Reddit, Pinterest & generic OpenGraph/Schema.org video pages
 *
 * Includes:
 * - Byte-level HTTP Range Pause/Resume & HLS segment-level Pause/Resume
 * - 5x Exponential Backoff retry for slow 3G / packet drops
 * - Automatic signed-URL cache eviction & refresh on HTTP 401/403/410
 */
object SocialMediaEngine {

  @Volatile private var appContext: Context? = null
  private val canceledIds = ConcurrentHashMap.newKeySet<String>()
  private val streamCache = ConcurrentHashMap<String, ExtractedSocialMedia>()
  private val json = Json { ignoreUnknownKeys = true }

  private const val CACHE_TTL_MS = 10 * 60 * 1000L
  private const val MAX_RETRIES = 5

  internal data class ExtractedSocialMedia(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Long? = null,
    val hdVideoUrl: String?,
    val sdVideoUrl: String?,
    val audioUrl: String?,
    val qualityMap: Map<Int, String> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis(),
  )

  private class ExpiredStreamException(message: String) : IOException(message)

  private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

  private fun cookieDomainKey(host: String): String {
    val h = host.lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
    val parts = h.split('.')
    return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
  }

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
    .cookieJar(object : CookieJar {
      override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domain = cookieDomainKey(url.host)
        val list = cookieStore.getOrPut(domain) { mutableListOf() }
        synchronized(list) {
          list.removeAll { existing -> cookies.any { it.name == existing.name } }
          list.addAll(cookies.filter { it.value.isNotEmpty() && it.value != "deleted" })
        }
      }

      override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val domain = cookieDomainKey(url.host)
        val list = cookieStore[domain] ?: return emptyList()
        synchronized(list) {
          return list.toList()
        }
      }
    })
    .build()

  private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
  private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  fun isSocialUrl(url: String): Boolean {
    val u = url.lowercase()
    return !("youtube.com" in u || "youtu.be" in u)
  }

  fun cancel(processId: String): Boolean = canceledIds.add(processId)

  /** Removes any partial files for a canceled or deleted task. */
  fun cleanupTaskFiles(processId: String) {
    val dir = appContext?.cacheDir?.let { File(it, "social_work") } ?: return
    if (!dir.exists()) return
    dir.listFiles()?.forEach { f ->
      if (processId in f.name) {
        runCatching { f.delete() }
      }
    }
  }

  internal fun isAudioPlatformOrFileUrl(rawUrl: String): Boolean {
    val u = rawUrl.trim().lowercase()
    val clean = u.substringBefore("?").substringBefore("#")
    return "music.youtube.com" in u ||
      "soundcloud.com" in u || "on.soundcloud.com" in u ||
      "bandcamp.com" in u ||
      "audiomack.com" in u || "mixcloud.com" in u || "open.spotify.com" in u ||
      clean.endsWith(".mp3") || clean.endsWith(".m4a") || clean.endsWith(".flac") ||
      clean.endsWith(".wav") || clean.endsWith(".opus") || clean.endsWith(".ogg") || clean.endsWith(".aac")
  }

  fun fetchInfo(rawUrl: String): MediaInfo {
    val extracted = extractWithCache(rawUrl, forceRefresh = false)
    val heights = mutableListOf<Int>()
    if (extracted.qualityMap.isNotEmpty()) {
      heights.addAll(extracted.qualityMap.keys.sortedDescending())
    } else {
      if (!extracted.hdVideoUrl.isNullOrBlank()) heights.add(1080)
      if (!extracted.sdVideoUrl.isNullOrBlank() && extracted.sdVideoUrl != extracted.hdVideoUrl) {
        heights.add(720)
      }
      if (heights.isEmpty()) heights.addAll(listOf(1080, 720, 480))
    }

    val audioCentric = isAudioPlatformOrFileUrl(rawUrl) ||
      (!extracted.audioUrl.isNullOrBlank() && extracted.qualityMap.isEmpty())

    return MediaInfo(
      url = extracted.url,
      title = extracted.title,
      uploader = extracted.uploader,
      thumbnail = extracted.thumbnail,
      durationSec = extracted.durationSec,
      isPlaylist = false,
      videoHeights = heights,
      isAudioCentric = audioCentric,
    )
  }

  fun download(
    processId: String,
    rawUrl: String,
    options: DownloadOptions,
    outDir: File,
    onProgress: (Float, Long, String) -> Unit,
  ): List<File> {
    canceledIds.remove(processId)
    outDir.mkdirs()

    onProgress(-1f, 0, "Preparing stream…")
    var extracted = extractWithCache(rawUrl, forceRefresh = false)

    val ext = if (options.type == MediaType.AUDIO) options.audioFormat.ext else "mp4"
    val desiredTitle = options.customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: extracted.title
    val safeTitle = sanitizeFileName(desiredTitle).take(90)
    val fileName = "$safeTitle.$ext"
    val mimeType = if (options.type == MediaType.AUDIO) {
      when (options.audioFormat) {
        AudioFormat.MP3 -> "audio/mpeg"
        AudioFormat.M4A -> "audio/mp4"
        AudioFormat.OPUS -> "audio/ogg"
      }
    } else {
      "video/mp4"
    }

    val workDir = appContext?.cacheDir?.let { File(it, "social_work").apply { mkdirs() } } ?: outDir
    val tempFile = File(workDir, "dl_$processId.$ext")
    val segIndexFile = File(workDir, "dl_$processId.seg")

    fun resolveStreamUrl(data: ExtractedSocialMedia): String =
      when (options.type) {
        MediaType.AUDIO -> data.audioUrl ?: selectVideoUrlForHeight(data, 480)
        MediaType.VIDEO -> selectVideoUrlForHeight(data, options.videoHeight)
      } ?: throw IllegalStateException("No downloadable media stream found for this link.")

    var streamUrl = resolveStreamUrl(extracted)
    val needsAudioDemux = options.type == MediaType.AUDIO && extracted.audioUrl.isNullOrBlank()
    val rawDownloadTarget = if (needsAudioDemux) File(workDir, "dl_muxed_$processId.mp4") else tempFile

    try {
      if (".m3u8" in streamUrl.lowercase()) {
        downloadHlsM3u8ToFile(processId, streamUrl, extracted.url, rawDownloadTarget, segIndexFile, onProgress)
      } else {
        downloadFileWithProgress(processId, streamUrl, extracted.url, rawDownloadTarget, onProgress)
      }
    } catch (e: ExpiredStreamException) {
      // Signed URL expired after a long pause; re-extract fresh URL and resume!
      checkCanceled(processId)
      extracted = extractWithCache(rawUrl, forceRefresh = true)
      streamUrl = resolveStreamUrl(extracted)
      if (".m3u8" in streamUrl.lowercase()) {
        downloadHlsM3u8ToFile(processId, streamUrl, extracted.url, rawDownloadTarget, segIndexFile, onProgress)
      } else {
        downloadFileWithProgress(processId, streamUrl, extracted.url, rawDownloadTarget, onProgress)
      }
    }

    if (needsAudioDemux) {
      onProgress(96f, 0, "Extracting pure audio track…")
      if (!NewPipeEngine.extractAudioTrackFromMp4(rawDownloadTarget, tempFile)) {
        rawDownloadTarget.copyTo(tempFile, overwrite = true)
      }
      rawDownloadTarget.delete()
    }

    val finalFile = NewPipeEngine.publishToPublicDir(tempFile, outDir, fileName, mimeType)
    tempFile.delete()
    segIndexFile.delete()
    onProgress(100f, 0, "Completed")
    return listOf(finalFile)
  }

  private fun extractWithCache(rawUrl: String, forceRefresh: Boolean): ExtractedSocialMedia {
    if (!forceRefresh) {
      streamCache[rawUrl]?.takeIf { System.currentTimeMillis() - it.timestampMs < CACHE_TTL_MS }?.let {
        return it
      }
    }
    val extracted = extract(rawUrl)
    streamCache[rawUrl] = extracted
    streamCache[extracted.url] = extracted
    return extracted
  }

  internal fun selectVideoUrlForHeight(extracted: ExtractedSocialMedia, targetHeight: Int?): String? {
    if (extracted.qualityMap.isNotEmpty()) {
      val sorted = extracted.qualityMap.entries.sortedByDescending { it.key }
      if (targetHeight == null) return sorted.first().value
      return sorted.firstOrNull { it.key <= targetHeight }?.value ?: sorted.last().value
    }
    val wantHd = targetHeight == null || targetHeight >= 720
    return if (wantHd) (extracted.hdVideoUrl ?: extracted.sdVideoUrl) else (extracted.sdVideoUrl ?: extracted.hdVideoUrl)
  }

  private fun seedCookiesInJar(pageUrl: String, pairs: Map<String, String>) {
    val httpUrl = pageUrl.toHttpUrlOrNull() ?: return
    val domain = cookieDomainKey(httpUrl.host)
    val cookies = pairs.mapNotNull { (k, v) ->
      Cookie.Builder()
        .name(k)
        .value(v)
        .domain(domain)
        .path("/")
        .build()
    }
    httpClient.cookieJar.saveFromResponse(httpUrl, cookies)
  }

  private fun getCookieValue(pageUrl: String, cookieName: String): String? {
    val httpUrl = pageUrl.toHttpUrlOrNull() ?: return null
    return httpClient.cookieJar.loadForRequest(httpUrl).firstOrNull { it.name == cookieName }?.value
  }

  internal fun solvePornhubLeastFactorChallenge(html: String): Pair<String, String>? {
    if ("leastFactor" !in html) return null
    val clean = html.replace(Regex("""/\*[\s\S]*?\*/"""), "")
    val psMatch = Regex("""var\s+p\s*=\s*(\d+)\s*;\s*var\s+s\s*=\s*(\d+)\s*;""").find(clean) ?: return null
    var p = psMatch.groupValues[1].toLongOrNull() ?: return null
    val s = psMatch.groupValues[2].toLongOrNull() ?: return null
    val s32 = s.toInt()

    val bodyStart = psMatch.range.last + 1
    val bodyEnd = clean.indexOf("leastFactor(p)", bodyStart).takeIf { it > bodyStart } ?: return null
    val stmts = clean.substring(bodyStart, bodyEnd)

    val stmtRegex = Regex(
      """if\s*\(\(s\s*>>\s*(\d+)\)\s*&\s*1\)\s*p\s*([+-])=\s*(\d+)(?:\s*\*\s*(\d+))?\s*;\s*else\s*p\s*([+-])=\s*(\d+)(?:\s*\*\s*(\d+))?\s*;|p\s*([+-])=\s*(\d+)(?:\s*\*\s*(\d+))?\s*;"""
    )
    for (m in stmtRegex.findAll(stmts)) {
      if (m.groupValues[1].isNotEmpty()) {
        val shift = m.groupValues[1].toInt()
        if (((s32 shr shift) and 1) == 1) {
          val op = m.groupValues[2]
          val v = m.groupValues[3].toLong() * (m.groupValues[4].toLongOrNull() ?: 1L)
          if (op == "+") p += v else p -= v
        } else {
          val op = m.groupValues[5]
          val v = m.groupValues[6].toLong() * (m.groupValues[7].toLongOrNull() ?: 1L)
          if (op == "+") p += v else p -= v
        }
      } else {
        val op = m.groupValues[8]
        val v = m.groupValues[9].toLong() * (m.groupValues[10].toLongOrNull() ?: 1L)
        if (op == "+") p += v else p -= v
      }
    }

    var n = p
    when {
      p == 0L -> n = 0L
      p % 2L == 0L -> n = 2L
      p % 3L == 0L -> n = 3L
      p % 5L == 0L -> n = 5L
      else -> {
        val limit = floor(sqrt(p.toDouble())).toLong()
        var i = 7L
        while (i <= limit) {
          if (p % i == 0L) { n = i; break }
          if (p % (i + 4L) == 0L) { n = i + 4L; break }
          if (p % (i + 6L) == 0L) { n = i + 6L; break }
          if (p % (i + 10L) == 0L) { n = i + 10L; break }
          if (p % (i + 12L) == 0L) { n = i + 12L; break }
          if (p % (i + 16L) == 0L) { n = i + 16L; break }
          if (p % (i + 22L) == 0L) { n = i + 22L; break }
          if (p % (i + 24L) == 0L) { n = i + 24L; break }
          i += 30L
        }
      }
    }
    if (n == 0L) return null
    val ckMatch = Regex("""document\.cookie\s*=\s*"KEY="\s*\+\s*n\s*\+\s*"\*"\s*\+\s*p/n\s*\+\s*":"\s*\+\s*s\s*\+\s*":([^";]+)""").find(clean)
      ?: return null
    val suffix = ckMatch.groupValues[1]
    val q = p / n
    return "KEY" to "$n*$q:$s:$suffix"
  }

  private fun extract(rawUrl: String): ExtractedSocialMedia {
    val cleanUrl = rawUrl.trim()
    val u = cleanUrl.lowercase()
    return when {
      "pornhub.com" in u || "pornhub.org" in u || "pornhubpremium.com" in u ||
        "redtube.com" in u || "youporn.com" in u || "tube8.com" in u || "thumbzilla.com" in u ->
        extractPornhub(cleanUrl)
      "xvideos.com" in u || "xvideos.es" in u || "xvideos2.com" in u || "xvideos3.com" in u || "xv-ru.com" in u ||
        "xnxx.com" in u || "xnxx.tv" in u || "xnxx.es" in u || "xnxx2.com" in u ->
        extractXvideosOrXnxx(cleanUrl)
      "xhamster.com" in u || "xhamster.desi" in u || "xhamster2.com" in u || "xhamster3.com" in u ||
        "xhwebsite.com" in u || "xhopen.com" in u ->
        extractXHamster(cleanUrl)
      "spankbang.com" in u || "spankbang.party" in u || "spankbang.obi" in u ->
        extractSpankBang(cleanUrl)
      "facebook.com" in u || "fb.watch" in u || "fb.gg" in u || "fb.com" in u ->
        runCatching { extractFacebook(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "Facebook") }
      "instagram.com" in u || "instagr.am" in u ->
        extractInstagram(cleanUrl)
      "threads.net" in u || "threads.com" in u ->
        runCatching { extractThreads(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "Threads") }
      "linkedin.com" in u || "lnkd.in" in u ->
        runCatching { extractLinkedIn(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "LinkedIn") }
      "x.com" in u || "twitter.com" in u || "t.co/" in u ->
        runCatching { extractTwitterX(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "X (Twitter)") }
      "tiktok.com" in u -> extractTikTok(cleanUrl)
      "reddit.com" in u || "redd.it" in u -> extractReddit(cleanUrl)
      "pinterest.com" in u || "pin.it" in u ->
        runCatching { extractPinterest(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "Pinterest") }
      "soundcloud.com" in u || "on.soundcloud.com" in u ->
        runCatching { extractSoundCloud(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "SoundCloud") }
      "bandcamp.com" in u ->
        runCatching { extractBandcamp(cleanUrl) }.getOrElse { extractGenericHtml(cleanUrl, "Bandcamp") }
      else -> extractGenericHtml(cleanUrl, hostDisplayName(cleanUrl))
    }
  }

  private fun hostDisplayName(url: String): String = runCatching {
    val host = URI(url).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return "Web Video"
    val base = host.substringBefore('.')
    if (base.isNotBlank()) base.replaceFirstChar { it.uppercase() } else host
  }.getOrDefault("Web Video")

  // ================================================================ Pornhub / RedTube / YouPorn / Tube8
  private fun extractPornhub(rawUrl: String): ExtractedSocialMedia {
    val viewKeyMatch = Regex("""(?:viewkey=|/embed/)([a-zA-Z0-9]+)""").find(rawUrl)
    val pageUrl = if ("/embed/" in rawUrl && "pornhub" in rawUrl.lowercase() && viewKeyMatch != null) {
      "https://www.pornhub.com/view_video.php?viewkey=${viewKeyMatch.groupValues[1]}"
    } else {
      rawUrl
    }

    var html = fetchHtmlWithHeaders(
      pageUrl,
      userAgent = DESKTOP_UA,
      extraHeaders = emptyMap(),
    )

    // If Pornhub returns its leastFactor(p) JS cookie challenge, solve it and re-fetch with CookieJar!
    val challengeCookie = solvePornhubLeastFactorChallenge(html)
    if (challengeCookie != null) {
      seedCookiesInJar(
        pageUrl,
        mapOf(
          challengeCookie.first to challengeCookie.second,
          "age_verified" to "1",
          "accessAgeDisclaimerPH" to "1",
          "accessAgeDisclaimerUK" to "1",
          "platform" to "pc",
        ),
      )
      html = fetchHtmlWithHeaders(pageUrl, userAgent = DESKTOP_UA, extraHeaders = emptyMap())
    } else {
      seedCookiesInJar(
        pageUrl,
        mapOf(
          "age_verified" to "1",
          "accessAgeDisclaimerPH" to "1",
          "accessAgeDisclaimerUK" to "1",
          "platform" to "pc",
        ),
      )
    }

    return runCatching {
      parsePornhubHtml(pageUrl, html) { mediaEndpointUrl ->
        val resolvedEndpoint = if (mediaEndpointUrl.startsWith("http")) {
          mediaEndpointUrl
        } else {
          URI(pageUrl).resolve(mediaEndpointUrl).toString()
        }
        fetchHtmlWithHeaders(
          resolvedEndpoint,
          userAgent = DESKTOP_UA,
          extraHeaders = mapOf(
            "Referer" to pageUrl,
            "X-Requested-With" to "XMLHttpRequest",
          ),
        )
      }
    }.getOrElse {
      parseUniversalWebHtml(pageUrl, html, hostDisplayName(pageUrl))
    }
  }

  // ================================================================ XVideos & XNXX
  private fun extractXvideosOrXnxx(rawUrl: String): ExtractedSocialMedia {
    val html = fetchHtmlWithHeaders(
      rawUrl,
      userAgent = DESKTOP_UA,
      extraHeaders = emptyMap(),
    )
    return parseXvideosOrXnxxHtml(rawUrl, html) { hlsMasterUrl ->
      runCatching {
        fetchHtmlWithHeaders(hlsMasterUrl, userAgent = DESKTOP_UA, extraHeaders = mapOf("Referer" to rawUrl))
      }.getOrNull()
    }
  }

  internal fun parseXvideosOrXnxxHtml(
    pageUrl: String,
    html: String,
    fetchHlsMaster: (String) -> String? = { null },
  ): ExtractedSocialMedia {
    val highMp4 = Regex("""setVideoUrlHigh\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)?.let(::unescapeJsonUrl)
    val lowMp4 = Regex("""setVideoUrlLow\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)?.let(::unescapeJsonUrl)
    val hlsUrl = Regex("""setVideoHLS\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)?.let(::unescapeJsonUrl)
    val titleRaw = Regex("""setVideoTitle\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)
      ?: findMeta(html, "og:title")
      ?: hostDisplayName(pageUrl)
    val thumbRaw = Regex("""setThumbUrl169\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)
      ?: Regex("""setThumbUrl\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)
      ?: findMeta(html, "og:image")
    val durationSec = findMeta(html, "og:duration")?.toLongOrNull()

    val qMap = mutableMapOf<Int, String>()
    if (!hlsUrl.isNullOrBlank() && hlsUrl.startsWith("http")) {
      val masterText = fetchHlsMaster(hlsUrl)
      if (!masterText.isNullOrBlank()) {
        qMap.putAll(parseHlsMasterQualities(hlsUrl, masterText))
      }
      if (qMap.isEmpty()) {
        qMap[1080] = hlsUrl
      }
    }
    if (!highMp4.isNullOrBlank() && highMp4.startsWith("http")) {
      qMap[720] = highMp4
      if (1080 !in qMap) qMap[1080] = highMp4
    }
    if (!lowMp4.isNullOrBlank() && lowMp4.startsWith("http")) {
      qMap[360] = lowMp4
    }

    if (qMap.isEmpty()) {
      return parseUniversalWebHtml(pageUrl, html, hostDisplayName(pageUrl))
    }

    val sorted = qMap.keys.sortedDescending()
    val hd = highMp4 ?: qMap[sorted.first()]
    val sd = lowMp4 ?: qMap[sorted.last()]

    return ExtractedSocialMedia(
      url = pageUrl,
      title = decodeHtmlEntities(titleRaw),
      uploader = hostDisplayName(pageUrl),
      thumbnail = thumbRaw?.let(::unescapeJsonUrl),
      durationSec = durationSec,
      hdVideoUrl = hd,
      sdVideoUrl = sd,
      audioUrl = null,
      qualityMap = qMap,
    )
  }

  // ================================================================ xHamster
  private fun extractXHamster(rawUrl: String): ExtractedSocialMedia {
    val html = fetchHtmlWithHeaders(
      rawUrl,
      userAgent = DESKTOP_UA,
      extraHeaders = emptyMap(),
    )
    return parseXHamsterHtml(rawUrl, html)
  }

  internal fun parseXHamsterHtml(pageUrl: String, html: String): ExtractedSocialMedia {
    val qMap = mutableMapOf<Int, String>()

    // Extract quality keys like "1080p":"https://...", "720p":"https://...", "480p":"https://...", "240p":"https://..."
    val qMatches = Regex(""""(\d{3,4})p"\s*:\s*"(https?:[^"]+\.mp4[^"]*)"""").findAll(html)
    for (m in qMatches) {
      val h = m.groupValues[1].toIntOrNull() ?: continue
      val u = unescapeJsonUrl(m.groupValues[2])
      if (h in 144..4320 && u.startsWith("http")) {
        qMap[h] = u
      }
    }

    // Also check {"quality":"720p","url":"https://..."} or {"url":"https://...","quality":"1080p"}
    val objMatches1 = Regex(""""quality"\s*:\s*"(\d{3,4})p?"[^}]*"url"\s*:\s*"(https?:[^"]+)"""").findAll(html)
    for (m in objMatches1) {
      val h = m.groupValues[1].toIntOrNull() ?: continue
      val u = unescapeJsonUrl(m.groupValues[2])
      if (h in 144..4320 && u.startsWith("http") && h !in qMap) qMap[h] = u
    }
    val objMatches2 = Regex(""""url"\s*:\s*"(https?:[^"]+)"[^}]*"quality"\s*:\s*"(\d{3,4})p?"""").findAll(html)
    for (m in objMatches2) {
      val u = unescapeJsonUrl(m.groupValues[1])
      val h = m.groupValues[2].toIntOrNull() ?: continue
      if (h in 144..4320 && u.startsWith("http") && h !in qMap) qMap[h] = u
    }

    if (qMap.isEmpty()) {
      return parseUniversalWebHtml(pageUrl, html, "xHamster")
    }

    val sorted = qMap.keys.sortedDescending()
    val hd = qMap[sorted.first()]
    val sd = qMap[sorted.firstOrNull { it <= 720 } ?: sorted.last()]
    val title = findMeta(html, "og:title") ?: "xHamster Video"
    val thumb = findMeta(html, "og:image")

    return ExtractedSocialMedia(
      url = pageUrl,
      title = decodeHtmlEntities(title),
      uploader = "xHamster",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = hd,
      sdVideoUrl = sd,
      audioUrl = null,
      qualityMap = qMap,
    )
  }

  // ================================================================ SpankBang
  private fun extractSpankBang(rawUrl: String): ExtractedSocialMedia {
    val html = fetchHtmlWithHeaders(
      rawUrl,
      userAgent = DESKTOP_UA,
      extraHeaders = emptyMap(),
    )
    return parseSpankBangHtml(rawUrl, html)
  }

  internal fun parseSpankBangHtml(pageUrl: String, html: String): ExtractedSocialMedia {
    val qMap = mutableMapOf<Int, String>()
    val qualities = mapOf(
      "4k" to 2160,
      "2160p" to 2160,
      "1080p" to 1080,
      "720p" to 720,
      "480p" to 480,
      "320p" to 320,
      "240p" to 240,
    )
    for ((key, height) in qualities) {
      val m = Regex("""['"]$key['"]\s*:\s*\[\s*['"](https?:[^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)
      if (m != null) {
        val u = unescapeJsonUrl(m.groupValues[1])
        if (u.startsWith("http")) qMap[height] = u
      }
    }
    val m3u8Match = Regex("""['"]m3u8['"]\s*:\s*\[\s*['"](https?:[^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)
    if (qMap.isEmpty() && m3u8Match != null) {
      val u = unescapeJsonUrl(m3u8Match.groupValues[1])
      if (u.startsWith("http")) qMap[1080] = u
    }

    if (qMap.isEmpty()) {
      return parseUniversalWebHtml(pageUrl, html, "SpankBang")
    }

    val sorted = qMap.keys.sortedDescending()
    val hd = qMap[sorted.first()]
    val sd = qMap[sorted.firstOrNull { it <= 720 } ?: sorted.last()]
    val title = findMeta(html, "og:title") ?: "SpankBang Video"
    val thumb = findMeta(html, "og:image")

    return ExtractedSocialMedia(
      url = pageUrl,
      title = decodeHtmlEntities(title),
      uploader = "SpankBang",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = hd,
      sdVideoUrl = sd,
      audioUrl = null,
      qualityMap = qMap,
    )
  }

  internal fun parsePornhubHtml(
    pageUrl: String,
    html: String,
    fetchMediaJson: (String) -> String,
  ): ExtractedSocialMedia {
    val fvMatch = Regex("""var\s+flashvars_\d+\s*=\s*(\{.+?\});\s*\r?\n""").find(html)
      ?: Regex("""flashvars_\d+\s*=\s*(\{.+?\});""").find(html)

    val mp4ByHeight = mutableMapOf<Int, String>()
    val hlsByHeight = mutableMapOf<Int, String>()
    var title: String? = null
    var thumb: String? = null
    var durationSec: Long? = null

    fun processMediaDefinitions(mediaDefs: List<JsonObject>) {
      for (md in mediaDefs) {
        val fmt = md.str("format")?.lowercase() ?: continue
        val vUrl = md.str("videoUrl")?.takeIf { it.startsWith("http") || it.startsWith("/") } ?: continue
        val qInt = parseQualityHeight(md["quality"])

        if (fmt == "mp4") {
          if ("/media/mp4" in vUrl || "get_media" in vUrl || !vUrl.contains(".mp4")) {
            runCatching {
              val mediaJson = fetchMediaJson(vUrl)
              val arr = json.parseToJsonElement(mediaJson).jsonArray
              for (item in arr.mapNotNull { it as? JsonObject }) {
                val directUrl = item.str("videoUrl")?.takeIf { it.startsWith("http") } ?: continue
                val h = item.str("height")?.toIntOrNull()
                  ?: parseQualityHeight(item["quality"])
                  ?: 720
                mp4ByHeight[h] = directUrl
              }
            }
          } else if (qInt != null && vUrl.startsWith("http")) {
            mp4ByHeight[qInt] = vUrl
          }
        } else if (fmt == "hls" && vUrl.startsWith("http")) {
          if (qInt != null) {
            hlsByHeight[qInt] = vUrl
          } else if ("/media/hls" in vUrl || "get_media" in vUrl) {
            runCatching {
              val mediaJson = fetchMediaJson(vUrl)
              val arr = json.parseToJsonElement(mediaJson).jsonArray
              for (item in arr.mapNotNull { it as? JsonObject }) {
                val directUrl = item.str("videoUrl")?.takeIf { it.startsWith("http") } ?: continue
                val h = item.str("height")?.toIntOrNull()
                  ?: parseQualityHeight(item["quality"])
                  ?: 720
                hlsByHeight[h] = directUrl
              }
            }
          }
        }
      }
    }

    if (fvMatch != null) {
      runCatching {
        val fv = json.parseToJsonElement(fvMatch.groupValues[1]).jsonObject
        title = fv.str("video_title")
        thumb = fv.str("image_url")
        durationSec = fv.str("video_duration")?.toLongOrNull()

        val mediaDefs = (fv["mediaDefinitions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        processMediaDefinitions(mediaDefs)
      }
    }

    // Fallback for RedTube / YouPorn / Tube8 / Thumbzilla or if flashvars regex had a semicolon inside string
    if (mp4ByHeight.isEmpty() && hlsByHeight.isEmpty()) {
      val mdMatch = Regex(""""mediaDefinitions"\s*:\s*(\[\s*\{[\s\S]*?\}\s*\])""").find(html)
      if (mdMatch != null) {
        runCatching {
          val arr = json.parseToJsonElement(mdMatch.groupValues[1]).jsonArray
          processMediaDefinitions(arr.mapNotNull { it as? JsonObject })
        }
      }
    }

    val mergedMap = mutableMapOf<Int, String>()
    for ((h, u) in hlsByHeight) mergedMap[h] = u
    for ((h, u) in mp4ByHeight) mergedMap[h] = u

    if (mergedMap.isEmpty()) {
      val fallbackMp4 = findFirstJsonUrl(
        html,
        listOf(
          """"videoUrl"\s*:\s*"(https:[^"]+\.mp4[^"]*)"""",
          """"contentUrl"\s*:\s*"(https:[^"]+\.mp4[^"]*)"""",
        ),
      )
      if (fallbackMp4 != null) {
        mergedMap[720] = fallbackMp4
      }
    }

    if (mergedMap.isEmpty()) {
      throw IllegalStateException("Could not extract Pornhub video stream.")
    }

    val sortedHeights = mergedMap.keys.sortedDescending()
    val hdUrl = mergedMap[sortedHeights.first()]
    val sdUrl = mergedMap[sortedHeights.firstOrNull { it <= 720 } ?: sortedHeights.last()]

    val finalTitle = decodeHtmlEntities(
      title ?: findMeta(html, "og:title") ?: "Pornhub Video"
    ).removeSuffix(" - Pornhub.com").trim()
    val finalThumb = (thumb ?: findMeta(html, "og:image"))?.let(::decodeHtmlEntities)

    return ExtractedSocialMedia(
      url = pageUrl,
      title = finalTitle,
      uploader = hostDisplayName(pageUrl),
      thumbnail = finalThumb,
      durationSec = durationSec,
      hdVideoUrl = hdUrl,
      sdVideoUrl = sdUrl,
      audioUrl = null,
      qualityMap = mergedMap,
    )
  }

  private fun parseQualityHeight(element: JsonElement?): Int? {
    if (element == null) return null
    if (element is JsonPrimitive) {
      return element.content.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 144..4320 }
    }
    return null
  }

  // ================================================================ Facebook
  private fun extractFacebook(url: String): ExtractedSocialMedia {
    val resolvedUrl = if ("/share/" in url || "fb.watch" in url || "fb.com" in url) resolveFinalUrl(url) else url
    val desktopTargetUrl = resolvedUrl
      .replace("://m.facebook.com/", "://www.facebook.com/")
      .replace("://web.facebook.com/", "://www.facebook.com/")
    val html = fetchHtml(desktopTargetUrl, userAgent = DESKTOP_UA)

    val hdPatterns = listOf(
      """"browser_native_hd_url"\s*:\s*"([^"]+)"""",
      """"playable_url_quality_hd"\s*:\s*"([^"]+)"""",
      """"hd_src_no_ratelimit"\s*:\s*"([^"]+)"""",
      """"hd_src"\s*:\s*"([^"]+)"""",
    )
    val sdPatterns = listOf(
      """"browser_native_sd_url"\s*:\s*"([^"]+)"""",
      """"playable_url"\s*:\s*"([^"]+)"""",
      """"sd_src_no_ratelimit"\s*:\s*"([^"]+)"""",
      """"sd_src"\s*:\s*"([^"]+)"""",
      """"base_url"\s*:\s*"(https:[^"]+\.mp4[^"]*)"""",
      """property="og:video"\s+content="([^"]+)"""",
      """property="og:video:secure_url"\s+content="([^"]+)"""",
    )

    var hd = findFirstJsonUrl(html, hdPatterns)
    var sd = findFirstJsonUrl(html, sdPatterns)

    if (hd == null && sd == null) {
      val mHtml = fetchHtml(resolvedUrl, userAgent = MOBILE_UA)
      hd = findFirstJsonUrl(mHtml, hdPatterns)
      sd = findFirstJsonUrl(mHtml, sdPatterns)
    }

    val title = findMeta(html, "og:title")
      ?: findMeta(html, "twitter:title")
      ?: "Facebook Video"
    val thumb = findMeta(html, "og:image")

    if (hd == null && sd == null) {
      throw IllegalStateException("Could not extract Facebook video stream (Ensure the video is public).")
    }

    val qMap = mutableMapOf<Int, String>()
    if (hd != null) qMap[1080] = hd
    if (sd != null) qMap[720] = sd
    if (hd != null && 480 !in qMap) qMap[480] = sd ?: hd

    return ExtractedSocialMedia(
      url = resolvedUrl,
      title = decodeHtmlEntities(title),
      uploader = "Facebook",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = hd ?: sd,
      sdVideoUrl = sd ?: hd,
      audioUrl = null,
      qualityMap = qMap,
    )
  }

  private fun instagramShortcodeToMediaId(shortcode: String): String? {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    var id = BigInteger.ZERO
    val base = BigInteger.valueOf(64L)
    for (c in shortcode) {
      val idx = alphabet.indexOf(c)
      if (idx < 0) return null
      id = id.multiply(base).add(BigInteger.valueOf(idx.toLong()))
    }
    return id.toString()
  }

  // ================================================================ Instagram
  private fun extractInstagram(rawUrl: String): ExtractedSocialMedia {
    val resolved = if ("/share/" in rawUrl) resolveFinalUrl(rawUrl) else rawUrl
    val normUrl = resolved.replace("instagram.com/reels/", "instagram.com/reel/")
    val shortcodeMatch = Regex("""instagram\.com/(?:[^/]+/)?(?:reel|p|tv)/([A-Za-z0-9_-]+)""").find(normUrl)
    val shortcode = shortcodeMatch?.groupValues?.get(1)

    var hd: String? = null
    var sd: String? = null
    var title: String? = null
    var thumb: String? = null
    val qMap = mutableMapOf<Int, String>()

    var gqlDebug = ""
    // 1. Primary: Instagram PolarisLoggedOutDesktopWWWPostRootContentQuery GraphQL API (works without login!)
    if (shortcode != null) {
      runCatching {
        val mediaId = instagramShortcodeToMediaId(shortcode)
        val igCookies = mutableMapOf<String, String>()
        fun captureSetCookies(headers: okhttp3.Headers) {
          for (sc in headers.values("Set-Cookie")) {
            val pair = sc.substringBefore(';').trim()
            val eq = pair.indexOf('=')
            if (eq > 0) {
              val k = pair.substring(0, eq).trim()
              val v = pair.substring(eq + 1).trim()
              if (v.isNotEmpty() && v != "deleted") igCookies[k] = v
            }
          }
        }

        // Hit root https://www.instagram.com/ first so Instagram sets mid and csrftoken cookies
        val homeReq = Request.Builder()
          .url("https://www.instagram.com/")
          .header("User-Agent", DESKTOP_UA)
          .build()
        val homeHtml = httpClient.newCall(homeReq).execute().use { resp ->
          captureSetCookies(resp.headers)
          resp.body?.string().orEmpty()
        }

        if (mediaId != null) {
          val lsd = Regex(""""LSD",\s*\[\],\s*\{"token"\s*:\s*"([^"]+)"""").find(homeHtml)?.groupValues?.get(1)
            ?: "AVqbxe3J_YA"

          // Pre-flight ruling check sets ig_nrcb=1, ig_did, and updates csrftoken cookie
          runCatching {
            val rulingReq = Request.Builder()
              .url("https://www.instagram.com/api/v1/web/get_ruling_for_content/?content_type=MEDIA&target_id=$mediaId")
              .header("User-Agent", DESKTOP_UA)
              .header("Accept", "*/*")
              .header("Accept-Language", "en-US,en;q=0.9")
              .header("X-IG-App-ID", "936619743392459")
              .header("X-ASBD-ID", "359341")
              .header("X-IG-WWW-Claim", "0")
              .header("Origin", "https://www.instagram.com")
              .apply {
                if (igCookies.isNotEmpty()) {
                  header("Cookie", igCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
              }
              .build()
            httpClient.newCall(rulingReq).execute().use { resp ->
              captureSetCookies(resp.headers)
              resp.body?.string()
            }
          }
          igCookies.putIfAbsent("ig_nrcb", "1")

          val csrf = igCookies["csrftoken"] ?: getCookieValue("https://www.instagram.com/", "csrftoken") ?: "missing"
          val cookieHeader = igCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

          val varsJson = """{"media_id":"$mediaId"}"""
          val formBody = buildString {
            append("lsd=").append(URLEncoder.encode(lsd, "UTF-8"))
            append("&fb_api_caller_class=RelayModern")
            append("&fb_api_req_friendly_name=PolarisLoggedOutDesktopWWWPostRootContentQuery")
            append("&server_timestamps=true")
            append("&variables=").append(URLEncoder.encode(varsJson, "UTF-8"))
            append("&doc_id=27130156389949648")
          }

          val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("sec-ch-ua", """"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"""")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", """"Windows"""")
            .header("sec-fetch-site", "none")
            .header("sec-fetch-mode", "navigate")
            .header("sec-fetch-user", "?1")
            .header("sec-fetch-dest", "document")
            .header("upgrade-insecure-requests", "1")
            .header("X-IG-App-ID", "936619743392459")
            .header("X-FB-LSD", lsd)
            .header("X-CSRFToken", csrf)
            .header("X-ASBD-ID", "359341")
            .header("X-IG-WWW-Claim", "0")
            .header("X-FB-Friendly-Name", "PolarisLoggedOutDesktopWWWPostRootContentQuery")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://www.instagram.com")
            .header("Referer", "https://www.instagram.com/reel/$shortcode")
            .apply {
              if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader)
            }
            .post(formBody.toByteArray(Charsets.UTF_8).toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

          val gqlResp = httpClient.newCall(req).execute().use { resp ->
            gqlDebug = "code=${resp.code} cookies=${igCookies.keys}"
            resp.body?.string().orEmpty()
          }
          gqlDebug += " len=${gqlResp.length} hasVV=${"video_versions" in gqlResp}"
          hd = findFirstJsonUrl(
            gqlResp,
            listOf(
              """"video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""",
              """"video_url"\s*:\s*"([^"]+)"""",
            ),
          )
          if (thumb == null) {
            thumb = findFirstJsonUrl(
              gqlResp,
              listOf(
                """"image_versions2"\s*:\s*\{\s*"candidates"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""",
                """"display_url"\s*:\s*"([^"]+)"""",
              ),
            )
          }
          if (title == null) {
            val capMatch = Regex(""""caption"\s*:\s*\{[^}]*"text"\s*:\s*"([^"]+)"""").find(gqlResp)
            val userMatch = Regex(""""user"\s*:\s*\{[^}]*"username"\s*:\s*"([^"]+)"""").find(gqlResp)
            title = capMatch?.groupValues?.get(1)?.let(::unescapeJsonUrl)?.lineSequence()?.firstOrNull()?.take(90)
              ?: userMatch?.groupValues?.get(1)?.let { "Video by @$it ($shortcode)" }
          }
        }
      }.onFailure { e ->
        gqlDebug += " err=${e.javaClass.simpleName}:${e.message}"
      }
    }

    // 2. Fallback: Captioned embed page
    if (hd == null && shortcode != null) {
      val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
      runCatching {
        val embedHtml = fetchHtml(embedUrl, userAgent = DESKTOP_UA)
        hd = findFirstJsonUrl(
          embedHtml,
          listOf(
            """"video_url"\s*:\s*"([^"]+)"""",
            """\\\"video_url\\\"\s*:\s*\\\"([^"\\]+)\\\""""",
            """class="EmbeddedMediaVideo"[^>]*src="([^"]+)"""",
          ),
        )
        if (thumb == null) {
          thumb = findFirstJsonUrl(
            embedHtml,
            listOf(
              """"display_url"\s*:\s*"([^"]+)"""",
              """class="EmbeddedMediaImage"[^>]*src="([^"]+)"""",
            ),
          )
        }
        if (title == null) title = "Instagram Video ($shortcode)"
      }
    }

    // 3. Fallback: Mobile page scrape
    if (hd == null) {
      val pageHtml = fetchHtml(normUrl, userAgent = MOBILE_UA)
      hd = findFirstJsonUrl(
        pageHtml,
        listOf(
          """"video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""",
          """"video_url"\s*:\s*"([^"]+)"""",
          """"contentUrl"\s*:\s*"([^"]+)"""",
          """property="og:video"\s+content="([^"]+)"""",
          """property="og:video:secure_url"\s+content="([^"]+)"""",
        ),
      )
      if (title == null) title = findMeta(pageHtml, "og:title") ?: "Instagram Video"
      if (thumb == null) thumb = findMeta(pageHtml, "og:image")
    }

    val validHd = hd ?: throw IllegalStateException("Could not extract Instagram video stream ($gqlDebug).")
    if (1080 !in qMap) qMap[1080] = validHd
    if (720 !in qMap) qMap[720] = sd ?: validHd

    return ExtractedSocialMedia(
      url = normUrl,
      title = decodeHtmlEntities(title ?: "Instagram Video"),
      uploader = "Instagram",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = validHd,
      sdVideoUrl = sd ?: validHd,
      audioUrl = null,
      qualityMap = qMap,
    )
  }

  // ================================================================ Threads
  private fun extractThreads(rawUrl: String): ExtractedSocialMedia {
    val cleanUrl = rawUrl.substringBefore("?")
    var hd: String? = null
    var title: String? = null
    var thumb: String? = null

    runCatching {
      val html = fetchHtml(rawUrl, userAgent = DESKTOP_UA)
      hd = findFirstJsonUrl(
        html,
        listOf(
          """"video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""",
          """"video_url"\s*:\s*"([^"]+)"""",
          """"contentUrl"\s*:\s*"([^"]+)"""",
          """property="og:video"\s+content="([^"]+)"""",
          """property="og:video:secure_url"\s+content="([^"]+)"""",
          """<video[^>]+src="([^"]+)"""",
        ),
      )
      title = findMeta(html, "og:title") ?: findMeta(html, "og:description")
      thumb = findMeta(html, "og:image")
    }

    if (hd == null) {
      runCatching {
        val embedUrl = cleanUrl.trimEnd('/') + "/embed"
        val embedHtml = fetchHtml(embedUrl, userAgent = DESKTOP_UA)
        hd = findFirstJsonUrl(
          embedHtml,
          listOf(
            """<video[^>]+src="([^"]+)"""",
            """"video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""",
            """"video_url"\s*:\s*"([^"]+)"""",
          ),
        )
        if (thumb == null) {
          thumb = findFirstJsonUrl(embedHtml, listOf("""<img[^>]+src="([^"]+)""""))
        }
      }
    }

    val validHd = hd ?: throw IllegalStateException("Could not extract Threads video stream (Ensure the Threads post contains a public video).")

    return ExtractedSocialMedia(
      url = rawUrl,
      title = decodeHtmlEntities(title ?: "Threads Video"),
      uploader = "Threads",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = validHd,
      sdVideoUrl = validHd,
      audioUrl = null,
      qualityMap = mapOf(1080 to validHd, 720 to validHd),
    )
  }

  // ================================================================ LinkedIn
  private fun extractLinkedIn(rawUrl: String): ExtractedSocialMedia {
    val html = fetchHtml(rawUrl, userAgent = DESKTOP_UA)
    val unescapedHtml = decodeHtmlEntities(html)

    val hd = findFirstJsonUrl(
      unescapedHtml,
      listOf(
        """"src"\s*:\s*"(https://dms\.licdn\.com/[^"]+)"""",
        """"url"\s*:\s*"(https://dms\.licdn\.com/[^"]+)"""",
        """"contentUrl"\s*:\s*"(https://[^"]+\.mp4[^"]*)"""",
        """"contentUrl"\s*:\s*"(https://dms\.licdn\.com/[^"]+)"""",
        """property="og:video"\s+content="([^"]+)"""",
        """property="og:video:secure_url"\s+content="([^"]+)"""",
        """<video[^>]+src="([^"]+)"""",
      ),
    ) ?: throw IllegalStateException("Could not extract LinkedIn video stream (Ensure the LinkedIn post is public).")

    val title = findMeta(html, "og:title")
      ?: findMeta(html, "twitter:title")
      ?: "LinkedIn Video"
    val thumb = findMeta(html, "og:image")

    return ExtractedSocialMedia(
      url = rawUrl,
      title = decodeHtmlEntities(title),
      uploader = "LinkedIn",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = hd,
      sdVideoUrl = hd,
      audioUrl = null,
      qualityMap = mapOf(1080 to hd, 720 to hd),
    )
  }

  // ================================================================ X / Twitter
  private fun extractTwitterX(rawUrl: String): ExtractedSocialMedia {
    val resolvedUrl = if ("t.co/" in rawUrl.lowercase()) resolveFinalUrl(rawUrl) else rawUrl
    val statusId = Regex("""(?:status|statuses)/(\d+)""").find(resolvedUrl)?.groupValues?.get(1)
      ?: throw IllegalStateException("Could not find X / Twitter status ID in URL.")

    runCatching {
      val apiJson = fetchHtml("https://api.vxtwitter.com/Twitter/status/$statusId", userAgent = DESKTOP_UA)
      val root = json.parseToJsonElement(apiJson).jsonObject
      val text = root.str("text")?.lineSequence()?.firstOrNull()?.take(100) ?: "X Video ($statusId)"
      val uploader = root.str("user_name") ?: root.str("user_screen_name") ?: "X (Twitter)"
      val mediaList = root["media_extended"] as? JsonArray
      val videoObj = mediaList
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.str("type") == "video" || it.str("type") == "gif" || it.str("url")?.contains(".mp4") == true }
      val videoUrl = videoObj?.str("url")
      val thumb = videoObj?.str("thumbnail_url")
      if (!videoUrl.isNullOrBlank()) {
        return ExtractedSocialMedia(
          url = resolvedUrl,
          title = text,
          uploader = uploader,
          thumbnail = thumb,
          hdVideoUrl = videoUrl,
          sdVideoUrl = videoUrl,
          audioUrl = null,
          qualityMap = mapOf(1080 to videoUrl, 720 to videoUrl),
        )
      }
    }

    runCatching {
      val synJson = fetchHtml("https://cdn.syndication.twimg.com/tweet-result?id=$statusId&token=a", userAgent = DESKTOP_UA)
      val mp4Urls = Regex(""""url"\s*:\s*"(https:[^"]+\.mp4[^"]*)"""").findAll(synJson)
        .map { unescapeJsonUrl(it.groupValues[1]) }
        .filter { "video.twimg.com" in it }
        .toList()
      if (mp4Urls.isNotEmpty()) {
        val hd = mp4Urls.last()
        val sd = mp4Urls.first()
        val thumb = findFirstJsonUrl(synJson, listOf(""""poster"\s*:\s*"([^"]+)"""", """"media_url_https"\s*:\s*"([^"]+)""""))
        return ExtractedSocialMedia(
          url = resolvedUrl,
          title = "X / Twitter Video ($statusId)",
          uploader = "X (Twitter)",
          thumbnail = thumb,
          hdVideoUrl = hd,
          sdVideoUrl = sd,
          audioUrl = null,
          qualityMap = mapOf(1080 to hd, 720 to sd),
        )
      }
    }

    throw IllegalStateException("Could not extract X / Twitter video stream (Ensure the post contains a public video).")
  }

  // ================================================================ TikTok
  private fun extractTikTok(rawUrl: String): ExtractedSocialMedia {
    runCatching {
      val encoded = URLEncoder.encode(rawUrl, "UTF-8")
      val respText = fetchHtml("https://www.tikwm.com/api/?url=$encoded&hd=1", userAgent = DESKTOP_UA)
      val root = json.parseToJsonElement(respText).jsonObject
      val data = root["data"] as? JsonObject
      if (data != null) {
        val hd = data.str("hdplay")?.let { resolveTikwmUrl(it) }
        val sd = (data.str("play") ?: data.str("wmplay"))?.let { resolveTikwmUrl(it) }
        val music = data.str("music")?.let { resolveTikwmUrl(it) }
        val title = data.str("title")?.ifBlank { null } ?: "TikTok Video"
        val cover = (data.str("origin_cover") ?: data.str("cover"))?.let { resolveTikwmUrl(it) }
        val author = (data["author"] as? JsonObject)?.str("nickname") ?: "TikTok"
        val duration = data.str("duration")?.toLongOrNull()
        if (!hd.isNullOrBlank() || !sd.isNullOrBlank()) {
          val qMap = mutableMapOf<Int, String>()
          if (!hd.isNullOrBlank()) qMap[1080] = hd
          if (!sd.isNullOrBlank()) qMap[720] = sd
          return ExtractedSocialMedia(
            url = rawUrl,
            title = title,
            uploader = author,
            thumbnail = cover,
            durationSec = duration,
            hdVideoUrl = hd ?: sd,
            sdVideoUrl = sd ?: hd,
            audioUrl = music,
            qualityMap = qMap,
          )
        }
      }
    }

    return extractGenericHtml(rawUrl, "TikTok")
  }

  private fun resolveTikwmUrl(u: String): String =
    if (u.startsWith("http")) u else "https://www.tikwm.com$u"

  // ================================================================ Reddit
  private fun extractReddit(rawUrl: String): ExtractedSocialMedia {
    val resolvedUrl = resolveFinalUrl(rawUrl)
    val base = resolvedUrl.substringBefore("?").trimEnd('/')
    runCatching {
      val jsonText = fetchHtml("$base.json", userAgent = DESKTOP_UA)
      val fallbackUrl = findFirstJsonUrl(
        jsonText,
        listOf(
          """"fallback_url"\s*:\s*"([^"]+)"""",
          """"scrubber_media_url"\s*:\s*"([^"]+)"""",
        ),
      )
      if (fallbackUrl != null) {
        val title = Regex(""""title"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1) ?: "Reddit Video"
        val thumb = findFirstJsonUrl(jsonText, listOf(""""thumbnail"\s*:\s*"(https://[^"]+)""""))
        return ExtractedSocialMedia(
          url = resolvedUrl,
          title = unescapeJsonUrl(title),
          uploader = "Reddit",
          thumbnail = thumb,
          hdVideoUrl = fallbackUrl,
          sdVideoUrl = fallbackUrl,
          audioUrl = null,
          qualityMap = mapOf(1080 to fallbackUrl, 720 to fallbackUrl),
        )
      }
    }
    return extractGenericHtml(resolvedUrl, "Reddit")
  }

  // ================================================================ Pinterest
  private fun extractPinterest(rawUrl: String): ExtractedSocialMedia {
    val resolvedUrl = resolveFinalUrl(rawUrl)
    val html = fetchHtml(resolvedUrl, userAgent = DESKTOP_UA)
    val videoUrl = findFirstJsonUrl(
      html,
      listOf(
        """"url"\s*:\s*"(https://v\d*\.pinimg\.com/videos/[^"]+\.mp4[^"]*)"""",
        """"contentUrl"\s*:\s*"(https://[^"]+\.mp4[^"]*)"""",
        """property="og:video"\s+content="([^"]+)"""",
        """property="og:video:secure_url"\s+content="([^"]+)"""",
      ),
    ) ?: throw IllegalStateException("Could not extract Pinterest video stream.")

    val title = findMeta(html, "og:title") ?: "Pinterest Video"
    val thumb = findMeta(html, "og:image")
    return ExtractedSocialMedia(
      url = resolvedUrl,
      title = decodeHtmlEntities(title),
      uploader = "Pinterest",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = videoUrl,
      sdVideoUrl = videoUrl,
      audioUrl = null,
      qualityMap = mapOf(1080 to videoUrl, 720 to videoUrl),
    )
  }

  // ================================================================ SoundCloud & Bandcamp (Pure Audio Platforms)
  private fun extractSoundCloud(rawUrl: String): ExtractedSocialMedia {
    val resolvedUrl = if ("on.soundcloud.com" in rawUrl.lowercase()) resolveFinalUrl(rawUrl) else rawUrl
    val html = fetchHtmlWithHeaders(resolvedUrl, userAgent = DESKTOP_UA, extraHeaders = emptyMap())

    // Discover SoundCloud client_id from inline script or script bundle if needed
    var clientId = Regex("""client_id\s*[:=]\s*["']([a-zA-Z0-9]{32})["']""").find(html)?.groupValues?.get(1)
    if (clientId == null) {
      val scriptUrls = Regex("""<script[^>]+src=["'](https://a-v2\.sndcdn\.com/assets/[^"']+\.js)["']""")
        .findAll(html)
        .map { it.groupValues[1] }
        .toList()
        .takeLast(3)
      for (sUrl in scriptUrls.asReversed()) {
        val js = runCatching { fetchHtmlWithHeaders(sUrl, userAgent = DESKTOP_UA, extraHeaders = emptyMap()) }.getOrNull() ?: continue
        val m = Regex("""client_id\s*:\s*["']([a-zA-Z0-9]{32})["']""").find(js)
        if (m != null) {
          clientId = m.groupValues[1]
          break
        }
      }
    }

    return parseSoundCloudHtml(resolvedUrl, html) { transcodingApiUrl ->
      val cid = clientId ?: return@parseSoundCloudHtml null
      val sep = if ("?" in transcodingApiUrl) "&" else "?"
      val reqUrl = "${transcodingApiUrl}${sep}client_id=$cid"
      val respJson = runCatching {
        fetchHtmlWithHeaders(reqUrl, userAgent = DESKTOP_UA, extraHeaders = mapOf("Referer" to resolvedUrl))
      }.getOrNull() ?: return@parseSoundCloudHtml null
      findFirstJsonUrl(respJson, listOf(""""url"\s*:\s*"(https?:[^"]+)""""))
    }
  }

  internal fun parseSoundCloudHtml(
    pageUrl: String,
    html: String,
    resolveTranscoding: (String) -> String? = { null },
  ): ExtractedSocialMedia {
    val progressiveApi = findFirstJsonUrl(
      html,
      listOf(
        """"url"\s*:\s*"(https://api-v2\.soundcloud\.com/media/soundcloud:tracks:[^"]+/stream/progressive[^"]*)"""",
        """"url"\s*:\s*"(https://api\.soundcloud\.com/tracks/[^"]+/stream[^"]*)"""",
      ),
    )
    val hlsApi = findFirstJsonUrl(
      html,
      listOf(
        """"url"\s*:\s*"(https://api-v2\.soundcloud\.com/media/soundcloud:tracks:[^"]+/stream/hls[^"]*)"""",
      ),
    )

    val directAudio = (progressiveApi?.let(resolveTranscoding))
      ?: (hlsApi?.let(resolveTranscoding))
      ?: findFirstJsonUrl(
        html,
        listOf(
          """"streamUrl"\s*:\s*"(https?:[^"]+)"""",
          """"audioUrl"\s*:\s*"(https?:[^"]+)"""",
          """(https://cf-media\.sndcdn\.com/[^\s"'<>\\]+)""",
        ),
      )
      ?: progressiveApi
      ?: hlsApi
      ?: throw IllegalStateException("Could not extract SoundCloud audio stream.")

    val title = findMeta(html, "og:title")
      ?: findMeta(html, "twitter:title")
      ?: findPageTitleTag(html)
      ?: "SoundCloud Track"
    val thumb = findMeta(html, "og:image") ?: findMeta(html, "twitter:image")
    val durMs = Regex(""""full_duration"\s*:\s*(\d+)""").find(html)?.groupValues?.get(1)?.toLongOrNull()
      ?: Regex(""""duration"\s*:\s*(\d+)""").find(html)?.groupValues?.get(1)?.toLongOrNull()

    return ExtractedSocialMedia(
      url = pageUrl,
      title = decodeHtmlEntities(title),
      uploader = "SoundCloud",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      durationSec = durMs?.let { if (it > 1000L) it / 1000L else it },
      hdVideoUrl = directAudio,
      sdVideoUrl = directAudio,
      audioUrl = directAudio,
      qualityMap = emptyMap(),
    )
  }

  private fun extractBandcamp(rawUrl: String): ExtractedSocialMedia {
    val html = fetchHtmlWithHeaders(rawUrl, userAgent = DESKTOP_UA, extraHeaders = emptyMap())
    return parseBandcampHtml(rawUrl, html)
  }

  internal fun parseBandcampHtml(pageUrl: String, html: String): ExtractedSocialMedia {
    val unescaped = decodeHtmlEntities(html)
    val mp3Url = findFirstJsonUrl(
      unescaped,
      listOf(
        """"mp3-128"\s*:\s*"(https?:[^"]+)"""",
        """"mp3-v0"\s*:\s*"(https?:[^"]+)"""",
        """"file"\s*:\s*\{\s*"[^"]+"\s*:\s*"(https?:[^"]+)"""",
        """(https://t4\.bcbits\.com/stream/[^\s"'<>\\]+)""",
      ),
    ) ?: throw IllegalStateException("Could not extract Bandcamp audio stream.")

    val title = findMeta(html, "og:title")
      ?: findMeta(html, "twitter:title")
      ?: findPageTitleTag(html)
      ?: "Bandcamp Track"
    val thumb = findMeta(html, "og:image") ?: findMeta(html, "twitter:image")
    val durationSec = Regex(""""duration"\s*:\s*(\d+(?:\.\d+)?)""").find(unescaped)
      ?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()

    return ExtractedSocialMedia(
      url = pageUrl,
      title = decodeHtmlEntities(title),
      uploader = "Bandcamp",
      thumbnail = thumb?.let(::decodeHtmlEntities),
      durationSec = durationSec,
      hdVideoUrl = mp3Url,
      sdVideoUrl = mp3Url,
      audioUrl = mp3Url,
      qualityMap = emptyMap(),
    )
  }

  // ================================================================ Universal Any-Site / Adult / HTML5 Video & Audio Extractor
  private fun extractGenericHtml(url: String, defaultPlatform: String): ExtractedSocialMedia {
    val cleanBase = url.substringBefore("?").substringBefore("#").lowercase()
    if (cleanBase.endsWith(".mp4") || cleanBase.endsWith(".webm") || cleanBase.endsWith(".mov") || cleanBase.endsWith(".mkv")) {
      return ExtractedSocialMedia(
        url = url,
        title = url.substringAfterLast('/').substringBefore('?').ifBlank { defaultPlatform },
        uploader = defaultPlatform,
        thumbnail = null,
        hdVideoUrl = url,
        sdVideoUrl = url,
        audioUrl = null,
        qualityMap = mapOf(1080 to url, 720 to url),
      )
    }
    if (
      cleanBase.endsWith(".mp3") || cleanBase.endsWith(".m4a") || cleanBase.endsWith(".flac") ||
      cleanBase.endsWith(".wav") || cleanBase.endsWith(".opus") || cleanBase.endsWith(".ogg") || cleanBase.endsWith(".aac")
    ) {
      return ExtractedSocialMedia(
        url = url,
        title = url.substringAfterLast('/').substringBefore('?').ifBlank { defaultPlatform },
        uploader = defaultPlatform,
        thumbnail = null,
        hdVideoUrl = url,
        sdVideoUrl = url,
        audioUrl = url,
        qualityMap = emptyMap(),
      )
    }
    if (cleanBase.endsWith(".m3u8")) {
      val masterText = runCatching { fetchHtmlWithHeaders(url, userAgent = DESKTOP_UA, extraHeaders = emptyMap()) }.getOrNull()
      val hlsMap = if (!masterText.isNullOrBlank()) parseHlsMasterQualities(url, masterText) else emptyMap()
      val qMap = hlsMap.ifEmpty { mapOf(1080 to url, 720 to url) }
      val sorted = qMap.keys.sortedDescending()
      return ExtractedSocialMedia(
        url = url,
        title = url.substringAfterLast('/').substringBefore('?').ifBlank { defaultPlatform },
        uploader = defaultPlatform,
        thumbnail = null,
        hdVideoUrl = qMap[sorted.first()],
        sdVideoUrl = qMap[sorted.last()],
        audioUrl = null,
        qualityMap = qMap,
      )
    }

    val html = fetchHtmlWithHeaders(
      url,
      userAgent = DESKTOP_UA,
      extraHeaders = emptyMap(),
    )

    return parseUniversalWebHtml(
      pageUrl = url,
      html = html,
      defaultPlatform = defaultPlatform,
      fetchSubPage = { subUrl ->
        runCatching {
          fetchHtmlWithHeaders(
            subUrl,
            userAgent = DESKTOP_UA,
            extraHeaders = mapOf("Referer" to url),
          )
        }.getOrNull()
      },
    )
  }

  internal fun parseUniversalWebHtml(
    pageUrl: String,
    html: String,
    defaultPlatform: String = "Web Video",
    fetchSubPage: (String) -> String? = { null },
    allowIframeRecurse: Boolean = true,
  ): ExtractedSocialMedia {
    val qMap = mutableMapOf<Int, String>()
    var extractedAudioUrl: String? = null

    // 1. Check HTML5 <source src="..." label/res/data-res="720p"> tags (both video and audio)
    val sourceTagMatches = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)
    for (tagMatch in sourceTagMatches) {
      val tag = tagMatch.value
      val srcRaw = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: continue
      val resolvedSrc = resolveRelativeUrl(pageUrl, unescapeJsonUrl(srcRaw)) ?: continue
      if (isLikelyAudioUrl(resolvedSrc) || tag.contains("audio/", ignoreCase = true)) {
        if (extractedAudioUrl == null) extractedAudioUrl = resolvedSrc
        continue
      }
      if (!isLikelyVideoOrStreamUrl(resolvedSrc) && !tag.contains("video/", ignoreCase = true) && !tag.contains("mpegurl", ignoreCase = true)) continue
      val resInt = Regex("""\b(?:res|data-res|label|title|size)\s*=\s*["']?(\d{3,4})p?["']?""", RegexOption.IGNORE_CASE)
        .find(tag)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 144..4320 }
        ?: guessHeightFromUrl(resolvedSrc)
        ?: 720
      if (resInt !in qMap) qMap[resInt] = resolvedSrc
    }

    // 1b. Check HTML5 <audio src="..."> tags & OpenGraph/JSON audio properties
    val audioTagMatch = Regex("""<audio\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
    if (audioTagMatch != null && extractedAudioUrl == null) {
      extractedAudioUrl = resolveRelativeUrl(pageUrl, unescapeJsonUrl(audioTagMatch.groupValues[1]))
    }
    if (extractedAudioUrl == null) {
      extractedAudioUrl = findFirstJsonUrl(
        decodeHtmlEntities(html),
        listOf(
          """property=["']og:audio:secure_url["']\s+content=["'](https?:[^"']+)["']""",
          """property=["']og:audio:url["']\s+content=["'](https?:[^"']+)["']""",
          """property=["']og:audio["']\s+content=["'](https?:[^"']+)["']""",
          """"audioUrl"\s*:\s*"(https?:[^"]+)"""",
          """"audio_url"\s*:\s*"(https?:[^"]+)"""",
          """"mp3-128"\s*:\s*"(https?:[^"]+)"""",
          """"mp3Url"\s*:\s*"(https?:[^"]+)"""",
          """"stream_url"\s*:\s*"(https?:[^"]+\.(?:mp3|m4a|opus|ogg|aac|flac|wav)[^"]*)"""",
          """"contentUrl"\s*:\s*"(https?:[^"]+\.(?:mp3|m4a|opus|ogg|aac|flac|wav)[^"]*)"""",
        ),
      )
    }
    if (extractedAudioUrl == null) {
      extractedAudioUrl = Regex("""https?:(?:\\?/\\?/)[^\s"'<>\\]+\.(?:mp3|m4a|flac|wav|opus|ogg|aac)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
        .find(html)?.value?.let(::unescapeJsonUrl)
    }

    // 2. Check HTML5 <video src="..."> tag
    val videoTagMatches = Regex("""<video\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html)
    for (vm in videoTagMatches) {
      val resolvedSrc = resolveRelativeUrl(pageUrl, unescapeJsonUrl(vm.groupValues[1])) ?: continue
      val h = guessHeightFromUrl(resolvedSrc) ?: 1080
      if (h !in qMap) qMap[h] = resolvedSrc
    }

    // 3. Check JS player quality objects: {"file":"https://...","label":"1080p"} or {"src":"https://...","res":720}
    val jsQualityPatterns = listOf(
      Regex("""["'](?:file|src|url|videoUrl|video_url|mp4)["']\s*:\s*["'](https?:[^"']+)["'][^}]{0,120}?["'](?:label|quality|res|height)["']\s*:\s*["']?(\d{3,4})p?["']?""", RegexOption.IGNORE_CASE),
      Regex("""["'](?:label|quality|res|height)["']\s*:\s*["']?(\d{3,4})p?["']?[^}]{0,120}?["'](?:file|src|url|videoUrl|video_url|mp4)["']\s*:\s*["'](https?:[^"']+)["']""", RegexOption.IGNORE_CASE),
    )
    for (m in jsQualityPatterns[0].findAll(html)) {
      val u = unescapeJsonUrl(m.groupValues[1])
      val h = m.groupValues[2].toIntOrNull()?.takeIf { it in 144..4320 } ?: 720
      if (u.startsWith("http") && isLikelyVideoOrStreamUrl(u) && h !in qMap) qMap[h] = u
    }
    for (m in jsQualityPatterns[1].findAll(html)) {
      val h = m.groupValues[1].toIntOrNull()?.takeIf { it in 144..4320 } ?: 720
      val u = unescapeJsonUrl(m.groupValues[2])
      if (u.startsWith("http") && isLikelyVideoOrStreamUrl(u) && h !in qMap) qMap[h] = u
    }

    // 4. Check common JSON / JS player keys & OpenGraph / Schema.org VideoObject
    val primaryUrl = findFirstJsonUrl(
      html,
      listOf(
        """"playAddr"\s*:\s*"([^"]+)"""",
        """"downloadAddr"\s*:\s*"([^"]+)"""",
        """__fileurl\s*=\s*['"](https?:[^'"]+)['"]""",
        """\bvideo_alt_url2\s*:\s*['"](https?:[^'"]+)['"]""",
        """\bvideo_alt_url\s*:\s*['"](https?:[^'"]+)['"]""",
        """\bvideo_url\s*:\s*['"](https?:[^'"]+)['"]""",
        """"videoUrl"\s*:\s*"(https?:[^"]+\.(?:mp4|m3u8)[^"]*)"""",
        """"video_url"\s*:\s*"(https?:[^"]+)"""",
        """"contentUrl"\s*:\s*"(https?:[^"]+\.(?:mp4|m3u8|webm)[^"]*)"""",
        """"contentUrl"\s*:\s*"(https?:[^"]+)"""",
        """"hlsUrl"\s*:\s*"(https?:[^"]+\.m3u8[^"]*)"""",
        """"mp4Url"\s*:\s*"(https?:[^"]+\.mp4[^"]*)"""",
        """["']?file["']?\s*:\s*["'](https?:[^"']+\.(?:mp4|m3u8)[^"']*)["']""",
        """property=["']og:video:secure_url["']\s+content=["'](https?:[^"']+)["']""",
        """property=["']og:video:url["']\s+content=["'](https?:[^"']+)["']""",
        """property=["']og:video["']\s+content=["'](https?:[^"']+)["']""",
        """name=["']twitter:player:stream["']\s+content=["'](https?:[^"']+)["']""",
        """href=["'](https?:[^"']+/dload/[^"']+\.mp4[^"']*)["']""",
      ),
    )
    if (!primaryUrl.isNullOrBlank() && isLikelyVideoOrStreamUrl(primaryUrl)) {
      val h = guessHeightFromUrl(primaryUrl) ?: 1080
      if (h !in qMap) qMap[h] = primaryUrl
    }

    // 5. Scan raw script/HTML for direct https://...mp4 or https://...m3u8 links (filtering out preview thumbnails/ads)
    if (qMap.isEmpty()) {
      val rawStreamMatches = Regex("""https?:(?:\\?/\\?/)[^\s"'<>\\]+\.(?:mp4|m3u8)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
        .findAll(html)
        .map { unescapeJsonUrl(it.value) }
        .filter { candidate ->
          val low = candidate.lowercase()
          !low.contains("preview") && !low.contains("thumb") && !low.contains("sprite") && !low.contains("banner")
        }
        .toList()

      for (candidate in rawStreamMatches) {
        val h = guessHeightFromUrl(candidate) ?: if (".m3u8" in candidate.lowercase()) 1080 else 720
        if (h !in qMap) qMap[h] = candidate
      }
    }

    // 6. If any .m3u8 master playlist is in qMap, expand its resolution variants
    val m3u8Entry = qMap.entries.firstOrNull { ".m3u8" in it.value.lowercase() }
    if (m3u8Entry != null) {
      val masterText = fetchSubPage(m3u8Entry.value)
      if (!masterText.isNullOrBlank()) {
        val variants = parseHlsMasterQualities(m3u8Entry.value, masterText)
        for ((vh, vu) in variants) {
          if (vh !in qMap) qMap[vh] = vu
        }
      }
    }

    // 7. Embedded <iframe src="..."> player fallback (1 level deep)
    if (qMap.isEmpty() && extractedAudioUrl == null && allowIframeRecurse) {
      val iframeUrls = Regex("""<iframe\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .findAll(html)
        .mapNotNull { resolveRelativeUrl(pageUrl, unescapeJsonUrl(it.groupValues[1])) }
        .filter { u ->
          val low = u.lowercase()
          !low.contains("googletagmanager") && !low.contains("doubleclick") && !low.contains("facebook.com/plugins") && !low.contains("ads")
        }
        .take(3)
        .toList()

      for (iframeUrl in iframeUrls) {
        val iframeHtml = fetchSubPage(iframeUrl) ?: continue
        val subResult = runCatching {
          parseUniversalWebHtml(
            pageUrl = iframeUrl,
            html = iframeHtml,
            defaultPlatform = defaultPlatform,
            fetchSubPage = fetchSubPage,
            allowIframeRecurse = false,
          )
        }.getOrNull()
        if (subResult != null) {
          val pageTitle = findMeta(html, "og:title") ?: findPageTitleTag(html) ?: subResult.title
          val pageThumb = findMeta(html, "og:image") ?: subResult.thumbnail
          return subResult.copy(
            url = pageUrl,
            title = decodeHtmlEntities(pageTitle),
            thumbnail = pageThumb?.let(::decodeHtmlEntities),
          )
        }
      }
    }

    if (qMap.isEmpty() && extractedAudioUrl == null) {
      throw IllegalStateException("Could not extract downloadable video or audio stream from this page.")
    }

    val sortedHeights = qMap.keys.sortedDescending()
    val hdUrl = if (sortedHeights.isNotEmpty()) qMap[sortedHeights.first()] else extractedAudioUrl
    val sdUrl = if (sortedHeights.isNotEmpty()) qMap[sortedHeights.firstOrNull { it <= 720 } ?: sortedHeights.last()] else extractedAudioUrl

    val title = findMeta(html, "og:title")
      ?: findMeta(html, "twitter:title")
      ?: findPageTitleTag(html)
      ?: defaultPlatform
    val thumb = findMeta(html, "og:image") ?: findMeta(html, "twitter:image")

    return ExtractedSocialMedia(
      url = pageUrl,
      title = decodeHtmlEntities(title),
      uploader = defaultPlatform,
      thumbnail = thumb?.let(::decodeHtmlEntities),
      hdVideoUrl = hdUrl,
      sdVideoUrl = sdUrl,
      audioUrl = extractedAudioUrl,
      qualityMap = qMap,
    )
  }

  internal fun parseHlsMasterQualities(masterUrl: String, playlistText: String): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    var pendingHeight: Int? = null
    val baseUri = runCatching { URI(masterUrl) }.getOrNull()

    for (line in lines) {
      if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
        val resMatch = Regex("""RESOLUTION\s*=\s*(\d+)x(\d+)""", RegexOption.IGNORE_CASE).find(line)
        val nameMatch = Regex("""NAME\s*=\s*"(\d{3,4})p?"""", RegexOption.IGNORE_CASE).find(line)
        pendingHeight = if (resMatch != null) {
          val w = resMatch.groupValues[1].toIntOrNull() ?: 0
          val h = resMatch.groupValues[2].toIntOrNull() ?: 0
          if (w > 0 && h > 0) minOf(w, h) else h
        } else {
          nameMatch?.groupValues?.get(1)?.toIntOrNull()
        }
      } else if (!line.startsWith("#")) {
        val resolved = if (line.startsWith("http")) {
          line
        } else {
          baseUri?.resolve(line)?.toString() ?: line
        }
        val h = pendingHeight ?: guessHeightFromUrl(resolved) ?: 720
        if (h in 144..4320 && h !in result) {
          result[h] = resolved
        }
        pendingHeight = null
      }
    }
    return result
  }

  private fun resolveRelativeUrl(baseUrl: String, candidate: String): String? {
    val c = candidate.trim()
    if (c.isEmpty() || c.startsWith("blob:") || c.startsWith("data:") || c.startsWith("javascript:")) return null
    if (c.startsWith("http://") || c.startsWith("https://")) return c
    return runCatching {
      if (c.startsWith("//")) {
        val scheme = URI(baseUrl).scheme ?: "https"
        "$scheme:$c"
      } else {
        URI(baseUrl).resolve(c).toString()
      }
    }.getOrNull()
  }

  private fun isLikelyAudioUrl(url: String): Boolean {
    val clean = url.lowercase().substringBefore("?").substringBefore("#")
    return clean.endsWith(".mp3") || clean.endsWith(".m4a") || clean.endsWith(".flac") ||
      clean.endsWith(".wav") || clean.endsWith(".opus") || clean.endsWith(".ogg") || clean.endsWith(".aac")
  }

  private fun isLikelyVideoOrStreamUrl(url: String): Boolean {
    val u = url.lowercase()
    if (u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".gif") || u.endsWith(".webp") || u.endsWith(".svg") || u.endsWith(".css") || u.endsWith(".js")) {
      return false
    }
    return ".mp4" in u || ".m3u8" in u || ".webm" in u || ".mov" in u || ".mkv" in u ||
      "video" in u || "stream" in u || "cdn" in u || "media" in u || "play" in u || "/dload/" in u
  }

  private fun guessHeightFromUrl(url: String): Int? {
    val m = Regex("""(?:^|[/_.-])(2160|1440|1080|720|480|360|240)p?(?:[/_?.&-]|$)""", RegexOption.IGNORE_CASE).find(url)
    return m?.groupValues?.get(1)?.toIntOrNull()
  }

  private fun findPageTitleTag(html: String): String? =
    Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
      .find(html)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

  // ================================================================ Helpers
  private fun resolveFinalUrl(url: String): String = runCatching {
    val req = Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()
    httpClient.newCall(req).execute().use { resp -> resp.request.url.toString() }
  }.getOrDefault(url)

  private fun fetchHtml(url: String, userAgent: String): String =
    fetchHtmlWithHeaders(url, userAgent, emptyMap())

  private fun fetchHtmlWithHeaders(
    url: String,
    userAgent: String,
    extraHeaders: Map<String, String>,
  ): String {
    val builder = Request.Builder()
      .url(url)
      .header("User-Agent", userAgent)
      .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,image/avif,image/webp,*/*;q=0.8")
      .header("Accept-Language", "en-US,en;q=0.9")
      .header("sec-ch-ua", """"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"""")
      .header("sec-ch-ua-mobile", if ("Mobile" in userAgent) "?1" else "?0")
      .header("sec-ch-ua-platform", if ("Android" in userAgent) """"Android"""" else """"Windows"""")
      .header("sec-fetch-site", "none")
      .header("sec-fetch-mode", "navigate")
      .header("sec-fetch-user", "?1")
      .header("sec-fetch-dest", "document")
      .header("upgrade-insecure-requests", "1")
    for ((k, v) in extraHeaders) {
      builder.header(k, v)
    }
    httpClient.newCall(builder.build()).execute().use { resp ->
      return resp.body?.string().orEmpty()
    }
  }

  internal fun findFirstJsonUrl(html: String, patterns: List<String>): String? {
    for (pat in patterns) {
      val m = Regex(pat, RegexOption.IGNORE_CASE).find(html)
      if (m != null) {
        val raw = m.groupValues[1]
        val clean = unescapeJsonUrl(raw)
        if (clean.startsWith("http")) return clean
      }
    }
    return null
  }

  internal fun findMeta(html: String, prop: String): String? {
    val p1 = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
    if (p1 != null) return p1.groupValues[1]
    val p2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE).find(html)
    return p2?.groupValues?.get(1)
  }

  internal fun unescapeJsonUrl(raw: String): String {
    var s = raw
      .replace("""\\u0025""", "%")
      .replace("""\u0025""", "%")
      .replace("""\\u0026""", "&")
      .replace("""\u0026""", "&")
      .replace("""\\u003C""", "<")
      .replace("""\u003C""", "<")
      .replace("""\\u003E""", ">")
      .replace("""\u003E""", ">")
      .replace("""\\u003D""", "=")
      .replace("""\u003D""", "=")
      .replace("""\\\/""", "/")
      .replace("""\/""", "/")
      .replace("&amp;", "&")
    s = Regex("""\\u([0-9a-fA-F]{4})""").replace(s) { mr ->
      mr.groupValues[1].toInt(16).toChar().toString()
    }
    return s.trim()
  }

  internal fun decodeHtmlEntities(s: String): String =
    s.replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#039;", "'")
      .replace("&#x27;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .trim()

  private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

  private fun checkCanceled(processId: String) {
    if (canceledIds.remove(processId)) {
      throw kotlinx.coroutines.CancellationException("Canceled")
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

  /**
   * Direct MP4/audio downloader with:
   * - Byte-level HTTP Range Pause/Resume (`Range: bytes=$existing-`)
   * - HTTP 200 vs 206 verification (resets file stream if server ignores Range so file is never corrupted)
   * - 5x Exponential Backoff retry on mid-stream network drops (slow 3G resilience)
   */
  internal fun downloadFileWithProgress(
    processId: String,
    streamUrl: String,
    refererUrl: String,
    dest: File,
    onProgress: (Float, Long, String) -> Unit,
    clientOverride: OkHttpClient = httpClient,
  ) {
    val refererHeader = runCatching {
      val uri = URI(refererUrl)
      if (uri.scheme != null && uri.host != null) refererUrl else "https://www.google.com/"
    }.getOrDefault("https://www.google.com/")

    val startTime = System.currentTimeMillis()
    var attempt = 0
    var expectedTotal = -1L

    while (true) {
      checkCanceled(processId)
      var downloaded = if (dest.exists()) dest.length() else 0L
      if (expectedTotal > 0 && downloaded >= expectedTotal) {
        return
      }
      val startOffset = downloaded

      try {
        val reqBuilder = Request.Builder()
          .url(streamUrl)
          .header("User-Agent", DESKTOP_UA)
          .header("Referer", refererHeader)

        if (downloaded > 0) {
          reqBuilder.header("Range", "bytes=$downloaded-")
        }

        clientOverride.newCall(reqBuilder.build()).execute().use { resp ->
          if (resp.code == 401 || resp.code == 403 || resp.code == 410) {
            throw ExpiredStreamException("HTTP ${resp.code}")
          }
          if (resp.code == 416) {
            // Range Not Satisfiable — file is already complete
            return
          }
          if (!resp.isSuccessful && resp.code != 206) {
            throw IOException("HTTP ${resp.code}")
          }

          val body = resp.body ?: throw IOException("Empty response body")
          val isPartial = resp.code == 206 && downloaded > 0
          if (!isPartial) {
            downloaded = 0L
          }

          val contentLen = body.contentLength()
          val total = if (contentLen > 0) {
            if (isPartial) downloaded + contentLen else contentLen
          } else {
            expectedTotal
          }
          if (total > 0) expectedTotal = total

          FileOutputStream(dest, isPartial).use { fos ->
            body.byteStream().use { ins ->
              val buf = ByteArray(64 * 1024)
              while (true) {
                checkCanceled(processId)
                val r = ins.read(buf)
                if (r == -1) break
                fos.write(buf, 0, r)
                downloaded += r

                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.2)
                val sessionBytes = (downloaded - startOffset).coerceAtLeast(0L)
                val speedBps = sessionBytes / elapsedSec
                val pct = if (total > 0) ((downloaded.toDouble() / total.toDouble()) * 100.0).toFloat() else 50f
                val eta = if (total > downloaded && speedBps > 1) ((total - downloaded) / speedBps).toLong() else 0L
                onProgress(
                  pct.coerceIn(1f, 99f),
                  eta,
                  "%.1f MB • %.1f MB/s".format(downloaded / (1024.0 * 1024.0), speedBps / (1024.0 * 1024.0)),
                )
              }
            }
          }
        }
        if (expectedTotal <= 0 || downloaded >= expectedTotal) {
          break
        }
      } catch (e: ExpiredStreamException) {
        throw e
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: IOException) {
        attempt++
        if (attempt >= MAX_RETRIES) throw e
        sleepWithCancelCheck(processId, attempt * 600L)
      }
    }
  }

  /**
   * Pure-Kotlin HLS (.m3u8) playlist & segment downloader with:
   * - Segment-index Pause/Resume (persisted in [segIndexFile])
   * - 5x per-segment Exponential Backoff retry for slow 3G connections
   */
  private fun downloadHlsM3u8ToFile(
    processId: String,
    m3u8Url: String,
    refererUrl: String,
    dest: File,
    segIndexFile: File,
    onProgress: (Float, Long, String) -> Unit,
  ) {
    val playlistText = fetchHtmlWithHeaders(
      m3u8Url,
      userAgent = DESKTOP_UA,
      extraHeaders = mapOf("Referer" to refererUrl),
    )
    val lines = playlistText.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    if (lines.isEmpty()) throw ExpiredStreamException("Empty HLS playlist")

    val subPlaylistLine = lines.lastOrNull { ".m3u8" in it.lowercase() }
    val (activePlaylistUrl, segmentLines) = if (subPlaylistLine != null) {
      val resolvedSubUrl = URI(m3u8Url).resolve(subPlaylistLine).toString()
      val subText = fetchHtmlWithHeaders(
        resolvedSubUrl,
        userAgent = DESKTOP_UA,
        extraHeaders = mapOf("Referer" to refererUrl),
      )
      val segs = subText.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
      resolvedSubUrl to segs
    } else {
      m3u8Url to lines
    }

    if (segmentLines.isEmpty()) throw IOException("No segments found in HLS playlist")

    var completedSegs = if (dest.exists() && segIndexFile.exists()) {
      runCatching { segIndexFile.readText().trim().toInt() }.getOrDefault(0)
    } else {
      0
    }
    if (completedSegs <= 0 || completedSegs >= segmentLines.size) {
      completedSegs = 0
      dest.delete()
      segIndexFile.delete()
    }

    val baseUri = URI(activePlaylistUrl)
    val startTime = System.currentTimeMillis()
    var downloadedBytes = if (dest.exists()) dest.length() else 0L
    val startOffset = downloadedBytes

    FileOutputStream(dest, completedSegs > 0).use { fos ->
      val buf = ByteArray(64 * 1024)
      for (idx in completedSegs until segmentLines.size) {
        checkCanceled(processId)
        val segLine = segmentLines[idx]
        val segUrl = if (segLine.startsWith("http")) segLine else baseUri.resolve(segLine).toString()

        var segAttempt = 0
        val segBytes = ByteArrayOutputStreamWithCap()
        while (true) {
          checkCanceled(processId)
          try {
            segBytes.reset()
            val req = Request.Builder()
              .url(segUrl)
              .header("User-Agent", DESKTOP_UA)
              .header("Referer", refererUrl)
              .build()

            httpClient.newCall(req).execute().use { resp ->
              if (resp.code == 401 || resp.code == 403 || resp.code == 410) {
                throw ExpiredStreamException("Segment HTTP ${resp.code}")
              }
              if (!resp.isSuccessful) throw IOException("Segment HTTP ${resp.code}")
              val body = resp.body ?: throw IOException("Empty segment")
              body.byteStream().use { ins ->
                while (true) {
                  checkCanceled(processId)
                  val r = ins.read(buf)
                  if (r == -1) break
                  segBytes.write(buf, 0, r)
                }
              }
            }
            break
          } catch (e: ExpiredStreamException) {
            throw e
          } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
          } catch (e: IOException) {
            segAttempt++
            if (segAttempt >= MAX_RETRIES) throw e
            sleepWithCancelCheck(processId, segAttempt * 600L)
          }
        }

        val bytes = segBytes.toByteArray()
        fos.write(bytes)
        fos.flush()
        downloadedBytes += bytes.size
        runCatching { segIndexFile.writeText((idx + 1).toString()) }

        val doneCount = idx + 1
        val totalSegs = segmentLines.size
        val pct = (doneCount.toFloat() / totalSegs.toFloat()) * 98f
        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.2)
        val sessionBytes = (downloadedBytes - startOffset).coerceAtLeast(0L)
        val speedBps = sessionBytes / elapsedSec
        val doneThisSession = (doneCount - completedSegs).coerceAtLeast(1)
        val segsPerSec = doneThisSession / elapsedSec
        val eta = if (segsPerSec > 0.05) ((totalSegs - doneCount) / segsPerSec).toLong() else 0L

        onProgress(
          pct.coerceIn(1f, 99f),
          eta,
          "%.1f MB • %.1f MB/s (%d/%d)".format(
            downloadedBytes / (1024.0 * 1024.0),
            speedBps / (1024.0 * 1024.0),
            doneCount,
            totalSegs,
          ),
        )
      }
    }
  }

  private class ByteArrayOutputStreamWithCap : java.io.ByteArrayOutputStream(256 * 1024)

  internal fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
      .replace(Regex("\\s+"), " ")
      .trim()
      .ifBlank { "social_video" }
}

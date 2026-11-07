package com.babu.ytdownloader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(sec: Long?): String {
  if (sec == null || sec <= 0) return ""
  val h = sec / 3600
  val m = (sec % 3600) / 60
  val s = sec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return ""
  val units = listOf("B", "KB", "MB", "GB")
  var value = bytes.toDouble()
  var i = 0
  while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
  return if (i == 0) "$bytes B" else "%.1f %s".format(value, units[i])
}

fun formatDate(millis: Long): String =
  SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))

private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
private val bareUrlRegex = Regex("""(?:^|\s)((?:www\.|m\.)?[a-zA-Z0-9-]+\.[a-zA-Z]{2,}/\S+)""")

/** Pulls the first http(s) link (or bare domain/path link) out of shared / pasted text. */
fun extractUrl(text: String?): String? {
  if (text.isNullOrBlank()) return null
  val trimmed = text.trim()
  val direct = urlRegex.find(trimmed)?.value?.trimEnd('.', ',', ')', ']', '"', '\'', ';', '>')
  if (direct != null) {
    return when {
      direct.startsWith("HTTP://", ignoreCase = true) && !direct.startsWith("http://") ->
        "http://" + direct.substring(7)
      direct.startsWith("HTTPS://", ignoreCase = true) && !direct.startsWith("https://") ->
        "https://" + direct.substring(8)
      else -> direct
    }
  }
  val bare = bareUrlRegex.find(trimmed)?.groupValues?.get(1)?.trimEnd('.', ',', ')', ']', '"', '\'', ';', '>')
  if (bare != null) {
    return "https://$bare"
  }
  return null
}

/** Extracts all valid URLs from a multi-link pasted or shared text block. */
fun extractAllUrls(text: String?): List<String> {
  if (text.isNullOrBlank()) return emptyList()
  val result = mutableListOf<String>()
  for (m in urlRegex.findAll(text)) {
    val raw = m.value.trimEnd('.', ',', ')', ']', '"', '\'', ';', '>')
    val normalized = when {
      raw.startsWith("HTTP://", ignoreCase = true) && !raw.startsWith("http://") ->
        "http://" + raw.substring(7)
      raw.startsWith("HTTPS://", ignoreCase = true) && !raw.startsWith("https://") ->
        "https://" + raw.substring(8)
      else -> raw
    }
    if ("/" in normalized.substringAfter("://") && normalized !in result) {
      result.add(normalized)
    }
  }
  return result
}

private val socialHosts = listOf(
  "youtube.com/", "youtu.be/", "music.youtube.com/",
  "soundcloud.com/", "on.soundcloud.com/", "bandcamp.com/", "audiomack.com/", "mixcloud.com/", "open.spotify.com/",
  "pornhub.com/", "pornhub.org/", "pornhubpremium.com/",
  "xvideos.com/", "xvideos.es/", "xvideos2.com/", "xvideos3.com/", "xv-ru.com/",
  "xnxx.com/", "xnxx.tv/", "xnxx.es/", "xnxx2.com/",
  "xhamster.com/", "xhamster.desi/", "xhamster2.com/", "xhamster3.com/", "xhwebsite.com/", "xhopen.com/",
  "spankbang.com/", "spankbang.party/", "spankbang.obi/",
  "redtube.com/", "youporn.com/", "tube8.com/", "thumbzilla.com/",
  "eporner.com/", "motherless.com/", "beeg.com/", "txxx.com/", "upornia.com/", "hclips.com/",
  "rule34video.com/", "hanime.tv/", "chaturbate.com/", "stripchat.com/",
  "facebook.com/", "fb.watch/", "fb.gg/", "fb.com/",
  "instagram.com/", "instagr.am/",
  "threads.net/", "threads.com/",
  "linkedin.com/", "lnkd.in/",
  "x.com/", "twitter.com/", "t.co/",
  "tiktok.com/",
  "reddit.com/", "redd.it/",
  "pinterest.com/", "pin.it/",
  "vimeo.com/", "dailymotion.com/", "twitch.tv/", "bilibili.com/", "rumble.com/", "odysee.com/", "bitchute.com/", "streamable.com/", "vk.com/", "ok.ru/",
)

fun isAudioCentricUrl(url: String?): Boolean {
  if (url.isNullOrBlank()) return false
  val u = url.trim().lowercase()
  val clean = u.substringBefore("?").substringBefore("#")
  return "music.youtube.com" in u ||
    "soundcloud.com" in u || "on.soundcloud.com" in u ||
    "bandcamp.com" in u ||
    "audiomack.com" in u || "mixcloud.com" in u || "open.spotify.com" in u ||
    clean.endsWith(".mp3") || clean.endsWith(".m4a") || clean.endsWith(".flac") ||
    clean.endsWith(".wav") || clean.endsWith(".opus") || clean.endsWith(".ogg") || clean.endsWith(".aac")
}

fun isSupportedSocialUrl(url: String?): Boolean {
  if (url.isNullOrBlank()) return false
  val u = url.lowercase()
  if (!u.startsWith("http://") && !u.startsWith("https://")) return false
  val clean = u.substringBefore("?").substringBefore("#")
  return socialHosts.any { it in u } ||
    isAudioCentricUrl(u) ||
    clean.endsWith(".mp4") || clean.endsWith(".m3u8") || clean.endsWith(".webm") || clean.endsWith(".mov") ||
    "/video" in u || "/watch" in u || "/reel" in u || "/shorts/" in u || "/embed/" in u || "viewkey=" in u || "/track/" in u
}

private fun File.uri(context: Context) =
  FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

private fun File.mime(): String =
  MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"

fun openFile(context: Context, path: String?) {
  val file = path?.let(::File)
  if (file == null || !file.exists()) {
    Toast.makeText(context, "ফাইলটি পাওয়া যায়নি (মুছে ফেলা হয়েছে?)", Toast.LENGTH_SHORT).show()
    return
  }
  val intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(file.uri(context), file.mime())
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, "এই ফাইল খোলার মতো কোনো অ্যাপ নেই", Toast.LENGTH_SHORT).show()
  }
}

fun shareFile(context: Context, path: String?) {
  val file = path?.let(::File)
  if (file == null || !file.exists()) {
    Toast.makeText(context, "ফাইলটি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    return
  }
  val send = Intent(Intent.ACTION_SEND)
    .setType(file.mime())
    .putExtra(Intent.EXTRA_STREAM, file.uri(context))
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(send, "শেয়ার করুন").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

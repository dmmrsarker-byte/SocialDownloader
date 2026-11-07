package com.babu.ytdownloader.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.babu.ytdownloader.engine.AudioFormat
import com.babu.ytdownloader.engine.MediaType
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Simple SharedPreferences-backed settings exposed as StateFlows. */
object AppSettings {
  private lateinit var prefs: SharedPreferences

  private val _language = MutableStateFlow("en")
  val language: StateFlow<String> = _language.asStateFlow()

  private val _languageChosen = MutableStateFlow(false)
  val languageChosen: StateFlow<Boolean> = _languageChosen.asStateFlow()

  private val _audioFormat = MutableStateFlow(AudioFormat.MP3)
  val audioFormat: StateFlow<AudioFormat> = _audioFormat.asStateFlow()

  private val _concurrency = MutableStateFlow(2)
  val concurrency: StateFlow<Int> = _concurrency.asStateFlow()

  private val _nightly = MutableStateFlow(false)
  val nightly: StateFlow<Boolean> = _nightly.asStateFlow()

  private val _autoDownload = MutableStateFlow(false)
  val autoDownload: StateFlow<Boolean> = _autoDownload.asStateFlow()

  private val _sponsorHeadline = MutableStateFlow("🔥 Visit our website for free tools, updates & pro guides!")
  val sponsorHeadline: StateFlow<String> = _sponsorHeadline.asStateFlow()

  private val _sponsorUrl = MutableStateFlow("")
  val sponsorUrl: StateFlow<String> = _sponsorUrl.asStateFlow()

  fun init(context: Context) {
    prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    _language.value = prefs.getString("language", "en") ?: "en"
    _languageChosen.value = prefs.getBoolean("language_chosen", false)
    _audioFormat.value = runCatching { AudioFormat.valueOf(prefs.getString("audio_format", "MP3")!!) }
      .getOrDefault(AudioFormat.MP3)
    _concurrency.value = prefs.getInt("concurrency", 2).coerceIn(1, 3)
    _nightly.value = prefs.getBoolean("nightly", false)
    _autoDownload.value = prefs.getBoolean("auto_start_download", false)
    _sponsorHeadline.value = prefs.getString("sponsor_headline", _sponsorHeadline.value) ?: _sponsorHeadline.value
    _sponsorUrl.value = prefs.getString("sponsor_url", "") ?: ""
  }

  fun setLanguage(code: String) {
    _language.value = code
    _languageChosen.value = true
    prefs.edit().putString("language", code).putBoolean("language_chosen", true).apply()
  }

  fun setAudioFormat(value: AudioFormat) {
    _audioFormat.value = value
    prefs.edit().putString("audio_format", value.name).apply()
  }

  fun setConcurrency(value: Int) {
    _concurrency.value = value.coerceIn(1, 3)
    prefs.edit().putInt("concurrency", _concurrency.value).apply()
  }

  fun setNightly(value: Boolean) {
    _nightly.value = value
    prefs.edit().putBoolean("nightly", value).apply()
  }

  fun setAutoDownload(value: Boolean) {
    _autoDownload.value = value
    prefs.edit().putBoolean("auto_start_download", value).apply()
  }

  fun setSponsor(headline: String, url: String) {
    _sponsorHeadline.value = headline
    _sponsorUrl.value = url
    prefs.edit().putString("sponsor_headline", headline).putString("sponsor_url", url).apply()
  }

  /** Download/SocialDownloader in the phone's public storage. */
  val rootDir: File
    get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SocialDownloader")

  fun outputDir(type: MediaType, playlist: String?): File {
    val base = File(rootDir, if (type == MediaType.AUDIO) "Audio" else "Video")
    return if (playlist.isNullOrBlank()) base else File(base, sanitize(playlist))
  }

  private fun sanitize(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "Playlist" }
}

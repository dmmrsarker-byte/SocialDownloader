package com.babu.ytdownloader.engine

import android.content.Context
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Pure Kotlin/JVM multi-platform media engine combining NewPipeExtractor (YouTube)
 * and SocialMediaEngine (Facebook, Instagram, Threads, LinkedIn, X/Twitter, TikTok & more).
 * Contains zero native .so executables so the APK passes all Play Protect & OEM security checks.
 */
object YtDlpEngine {

  sealed interface State {
    data object Initializing : State
    data class Ready(val version: String?) : State
    data class Failed(val message: String) : State
  }

  private val ready = CompletableDeferred<Unit>()
  private val _state = MutableStateFlow<State>(State.Initializing)
  val state: StateFlow<State> = _state.asStateFlow()

  private const val ENGINE_VERSION = "SocialEngine v2.5 • NewPipe v0.26.5"

  fun init(context: Context) {
    runCatching { NewPipeEngine.init(context) }
    runCatching { SocialMediaEngine.init(context) }
    ready.complete(Unit)
    _state.value = State.Ready(ENGINE_VERSION)
  }

  suspend fun awaitReady() = ready.await()

  private fun isYouTubeUrl(url: String): Boolean {
    val u = url.lowercase()
    return "youtube.com" in u || "youtu.be" in u
  }

  /** Reads title / thumbnail / formats (or playlist entries) without downloading. */
  suspend fun fetchInfo(url: String, preferPlaylist: Boolean = false): MediaInfo = withContext(Dispatchers.IO) {
    awaitReady()
    if (isYouTubeUrl(url)) {
      NewPipeEngine.fetchInfo(url, preferPlaylist = preferPlaylist)
    } else {
      SocialMediaEngine.fetchInfo(url)
    }
  }

  /**
   * Downloads one item. [onProgress] receives (percent 0..100, eta seconds, status line).
   * Returns the final file(s) written to disk.
   */
  suspend fun download(
    processId: String,
    url: String,
    options: DownloadOptions,
    outDir: File,
    onProgress: (Float, Long, String) -> Unit,
  ): List<File> = withContext(Dispatchers.IO) {
    awaitReady()
    if (isYouTubeUrl(url)) {
      NewPipeEngine.download(processId, url, options, outDir, onProgress)
    } else {
      SocialMediaEngine.download(processId, url, options, outDir, onProgress)
    }
  }

  fun cancel(processId: String): Boolean {
    val a = NewPipeEngine.cancel(processId)
    val b = SocialMediaEngine.cancel(processId)
    return a || b
  }

  fun cleanupTaskFiles(processId: String) {
    runCatching { NewPipeEngine.cleanupTaskFiles(processId) }
    runCatching { SocialMediaEngine.cleanupTaskFiles(processId) }
  }

  fun version(): String = ENGINE_VERSION

  suspend fun update(nightly: Boolean): Boolean = withContext(Dispatchers.IO) {
    awaitReady()
    _state.value = State.Ready(ENGINE_VERSION)
    true
  }
}

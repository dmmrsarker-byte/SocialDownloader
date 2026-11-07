package com.babu.ytdownloader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.download.DownloadManager
import com.babu.ytdownloader.download.DownloadTask
import com.babu.ytdownloader.engine.AudioFormat
import com.babu.ytdownloader.engine.DownloadOptions
import com.babu.ytdownloader.engine.MediaInfo
import com.babu.ytdownloader.engine.MediaType
import com.babu.ytdownloader.engine.YtDlpEngine
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
  val url: String = "",
  val customTitle: String = "",
  val loading: Boolean = false,
  val error: String? = null,
  val info: MediaInfo? = null,
  val type: MediaType = MediaType.VIDEO,
  /** null = Best */
  val height: Int? = null,
  val audioFormat: AudioFormat = AppSettings.audioFormat.value,
  /** Indexes of selected playlist entries. */
  val selected: Set<Int> = emptySet(),
)

class MainViewModel : ViewModel() {

  /** 0 = Home, 1 = Downloads, 2 = History, 3 = Settings */
  var tab by mutableIntStateOf(0)

  private val _home = MutableStateFlow(HomeState())
  val home: StateFlow<HomeState> = _home.asStateFlow()

  private val _autoQueuedEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
  val autoQueuedEvents: SharedFlow<Int> = _autoQueuedEvents.asSharedFlow()

  private var analyzeJob: Job? = null
  private var pasteDebounceJob: Job? = null
  private val analyzeSeq = AtomicLong(0L)
  private var lastShared: String? = null
  private var lastAutoGrabbedUrl: String? = null

  /**
   * Updates the URL field. When a valid link is pasted into the text box,
   * automatically checks the link and displays the Format & Quality Options Card!
   */
  fun onUrlChange(value: String) {
    val prevUrl = _home.value.url
    _home.update { it.copy(url = value, error = null) }

    pasteDebounceJob?.cancel()
    val detected = extractUrl(value)
    if (detected != null && detected != prevUrl && "/" in detected.substringAfter("://")) {
      pasteDebounceJob = viewModelScope.launch {
        delay(300)
        if (_home.value.url == value && _home.value.info?.url != detected) {
          analyze(autoStart = AppSettings.autoDownload.value)
        }
      }
    }
  }

  /** Shared from another app. Automatically grabs & checks the link (and starts download if autoDownload is on). */
  fun onShared(text: String?) {
    if (text == null || text == lastShared) return
    lastShared = text
    val url = extractUrl(text) ?: return
    lastAutoGrabbedUrl = url
    openLink(url, autoStart = AppSettings.autoDownload.value)
  }

  /** Automatically called when a supported social link is detected on the clipboard. */
  fun onClipboardUrl(url: String) {
    if (url == lastAutoGrabbedUrl) return
    if (_home.value.loading || _home.value.url == url) return
    lastAutoGrabbedUrl = url
    openLink(url, autoStart = AppSettings.autoDownload.value)
  }

  /** Called when a link is shared/copied into the app or "Download from Link" is tapped in Downloads/History. */
  fun openLink(text: String?, autoStart: Boolean = false) {
    val url = extractUrl(text) ?: return
    pasteDebounceJob?.cancel()
    tab = 0
    _home.update { it.copy(url = url, error = null) }
    analyze(autoStart = autoStart)
  }

  fun analyze(autoStart: Boolean = false) {
    pasteDebounceJob?.cancel()
    val url = extractUrl(_home.value.url) ?: run {
      _home.update { it.copy(error = S.t("invalid_url")) }
      return
    }
    lastAutoGrabbedUrl = url
    analyzeJob?.cancel()
    val seq = analyzeSeq.incrementAndGet()
    _home.update { it.copy(url = url, loading = true, error = null, info = null) }
    analyzeJob = viewModelScope.launch {
      try {
        val info = YtDlpEngine.fetchInfo(url)
        if (analyzeSeq.get() != seq) return@launch
        val defaultHeight = info.videoHeights.firstOrNull { it == 1080 }
          ?: info.videoHeights.firstOrNull()
        val defaultType = if (info.isAudioCentric || isAudioCentricUrl(url)) MediaType.AUDIO else MediaType.VIDEO
        _home.update {
          it.copy(
            loading = false,
            info = info,
            customTitle = info.title,
            type = defaultType,
            height = defaultHeight,
            audioFormat = AppSettings.audioFormat.value,
            selected = info.entries.indices.toSet(),
          )
        }
        if (autoStart && !info.isPlaylist) {
          val count = startDownload()
          if (count > 0) {
            _autoQueuedEvents.tryEmit(count)
          }
        }
      } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        if (analyzeSeq.get() != seq) return@launch
        val msg = t.message?.lines()?.lastOrNull { "ERROR" in it }?.substringAfter("ERROR:")?.trim()
          ?: t.message ?: S.t("invalid_url")
        // Automatically save the failed link so the user never loses it and can retry anytime!
        DownloadManager.saveFailedLink(url = url, errorMsg = msg)
        _home.update { it.copy(loading = false, error = msg) }
      }
    }
  }

  /** Enqueues a direct download from the currently entered URL (even if initial fetch failed). */
  @Synchronized
  fun directDownloadFromCurrentUrl(): Int {
    val s = _home.value
    val url = extractUrl(s.url) ?: return 0
    val custom = s.customTitle.trim().takeIf { it.isNotEmpty() }
    DownloadManager.enqueueDirectFromLink(url = url, customTitle = custom, type = s.type)
    _home.value = HomeState()
    return 1
  }

  fun clear() {
    pasteDebounceJob?.cancel()
    analyzeSeq.incrementAndGet()
    analyzeJob?.cancel()
    _home.value = HomeState()
  }

  fun setCustomTitle(value: String) = _home.update { it.copy(customTitle = value) }
  fun setType(type: MediaType) = _home.update { it.copy(type = type) }
  fun setHeight(height: Int?) = _home.update { it.copy(height = height) }
  fun setAudioFormat(format: AudioFormat) = _home.update { it.copy(audioFormat = format) }

  fun toggleEntry(index: Int) = _home.update {
    it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
  }

  fun selectAll(all: Boolean) = _home.update {
    it.copy(selected = if (all) it.info?.entries?.indices?.toSet().orEmpty() else emptySet())
  }

  /**
   * Atomically consumes the current HomeState and adds the selection to the download queue.
   * Safe against rapid double-taps on "Start Download".
   */
  @Synchronized
  fun startDownload(): Int {
    val s = _home.getAndUpdate { HomeState() }
    val info = s.info ?: return 0
    val customSingleTitle = if (!info.isPlaylist) {
      s.customTitle.trim().takeIf { it.isNotEmpty() }
    } else {
      null
    }
    val options = DownloadOptions(
      type = s.type,
      videoHeight = s.height,
      audioFormat = s.audioFormat,
      customTitle = customSingleTitle,
    )
    val tasks = if (info.isPlaylist) {
      info.entries.filterIndexed { i, _ -> i in s.selected }.map { e ->
        DownloadTask(
          url = e.url, title = e.title, thumbnail = e.thumbnail,
          uploader = e.uploader ?: info.uploader, playlist = info.title, options = options,
        )
      }
    } else {
      listOf(
        DownloadTask(
          url = info.url,
          title = customSingleTitle ?: info.title,
          thumbnail = info.thumbnail,
          uploader = info.uploader,
          playlist = null,
          options = options,
        )
      )
    }
    return DownloadManager.enqueue(tasks)
  }
}

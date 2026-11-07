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
import com.babu.ytdownloader.engine.NewPipeEngine
import com.babu.ytdownloader.engine.YtDlpEngine
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

data class HomeState(
  val url: String = "",
  val batchUrls: List<String> = emptyList(),
  val customTitle: String = "",
  val loading: Boolean = false,
  val error: String? = null,
  val info: MediaInfo? = null,
  val type: MediaType = MediaType.VIDEO,
  /** null = Best */
  val height: Int? = null,
  val audioFormat: AudioFormat = AppSettings.audioFormat.value,
  /** Embed Music Cover Art & Album Metadata into downloaded audio files. */
  val embedCoverArt: Boolean = true,
  /** Prefer extracting the full playlist/mix when a link has both v= and list=. */
  val preferPlaylist: Boolean = false,
  /** Search/filter query inside a loaded playlist. */
  val playlistQuery: String = "",
  /** Prefix downloaded playlist tracks with "01 - ", "02 - ", etc. */
  val numberPlaylistTracks: Boolean = true,
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
    val allLinks = extractAllUrls(value)
    _home.update { it.copy(url = value, batchUrls = allLinks, error = null) }

    pasteDebounceJob?.cancel()
    val detected = allLinks.firstOrNull() ?: extractUrl(value)
    if (allLinks.size <= 1 && detected != null && detected != prevUrl && "/" in detected.substringAfter("://")) {
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

  fun analyze(autoStart: Boolean = false, preferPlaylist: Boolean = _home.value.preferPlaylist) {
    pasteDebounceJob?.cancel()
    val url = extractUrl(_home.value.url) ?: run {
      _home.update { it.copy(error = S.t("invalid_url")) }
      return
    }
    lastAutoGrabbedUrl = url
    analyzeJob?.cancel()
    val seq = analyzeSeq.incrementAndGet()
    _home.update { it.copy(url = url, loading = true, error = null, info = null, preferPlaylist = preferPlaylist) }
    analyzeJob = viewModelScope.launch {
      try {
        val info = YtDlpEngine.fetchInfo(url, preferPlaylist = preferPlaylist)
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
            preferPlaylist = info.isPlaylist,
            playlistQuery = "",
          )
        }
        if (autoStart && (!info.isPlaylist || info.entries.size in 1..50)) {
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

  fun togglePlaylistMode(loadPlaylist: Boolean) {
    analyze(autoStart = false, preferPlaylist = loadPlaylist)
  }

  fun saveMusicCover(onSaved: (String) -> Unit, onError: (String) -> Unit) {
    val s = _home.value
    val info = s.info ?: return
    val thumbUrl = info.thumbnail ?: info.entries.firstOrNull()?.thumbnail ?: return
    val title = s.customTitle.trim().ifEmpty { info.title }
    val cropSquare = s.type == MediaType.AUDIO || info.isAudioCentric
    viewModelScope.launch {
      try {
        val saved = withContext(Dispatchers.IO) {
          val coversDir = File(AppSettings.rootDir, "Covers")
          NewPipeEngine.downloadCoverArt(thumbUrl, title, coversDir, cropSquare = cropSquare)
        }
        onSaved(saved.absolutePath)
      } catch (t: Throwable) {
        onError(t.message ?: "Failed to save cover image")
      }
    }
  }

  /** Enqueues all links pasted in a multi-link batch. */
  @Synchronized
  fun enqueueBatchUrls(): Int {
    val s = _home.value
    val urls = s.batchUrls.ifEmpty { listOfNotNull(extractUrl(s.url)) }
    if (urls.isEmpty()) return 0
    for (u in urls) {
      val itemType = if (isAudioCentricUrl(u)) MediaType.AUDIO else s.type
      DownloadManager.enqueueDirectFromLink(url = u, customTitle = null, type = itemType)
    }
    _home.value = HomeState()
    return urls.size
  }

  /** Enqueues a direct download from the currently entered URL (even if initial fetch failed). */
  @Synchronized
  fun directDownloadFromCurrentUrl(): Int {
    val s = _home.value
    if (s.batchUrls.size > 1) return enqueueBatchUrls()
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
  fun setEmbedCoverArt(enabled: Boolean) = _home.update { it.copy(embedCoverArt = enabled) }
  fun setPlaylistQuery(query: String) = _home.update { it.copy(playlistQuery = query) }
  fun setNumberPlaylistTracks(enabled: Boolean) = _home.update { it.copy(numberPlaylistTracks = enabled) }

  fun toggleEntry(index: Int) = _home.update {
    it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
  }

  fun selectAll(all: Boolean) = _home.update {
    it.copy(selected = if (all) it.info?.entries?.indices?.toSet().orEmpty() else emptySet())
  }

  fun invertPlaylistSelection() = _home.update { s ->
    val allIndices = s.info?.entries?.indices?.toSet().orEmpty()
    s.copy(selected = allIndices - s.selected)
  }

  private fun formatNumberedTrackTitle(index: Int, rawTitle: String, enabled: Boolean): String {
    if (!enabled) return rawTitle
    if (Regex("""^\d{1,3}[\s._-]+""").containsMatchIn(rawTitle)) return rawTitle
    return "%02d - %s".format(index + 1, rawTitle)
  }

  /** Immediately queues a single track from the currently loaded playlist without clearing the playlist view. */
  @Synchronized
  fun downloadSinglePlaylistEntry(index: Int): Int {
    val s = _home.value
    val info = s.info ?: return 0
    val e = info.entries.getOrNull(index) ?: return 0
    val playlistFolderName = s.customTitle.trim().takeIf { it.isNotEmpty() } ?: info.title
    val trackTitle = formatNumberedTrackTitle(index, e.title, s.numberPlaylistTracks)
    val thumb = e.thumbnail ?: info.thumbnail
    val uploader = e.uploader ?: info.uploader
    val task = DownloadTask(
      url = e.url,
      title = trackTitle,
      thumbnail = thumb,
      uploader = uploader,
      playlist = playlistFolderName,
      options = DownloadOptions(
        type = s.type,
        videoHeight = s.height,
        audioFormat = s.audioFormat,
        customTitle = trackTitle,
        embedCoverArt = s.embedCoverArt,
        thumbnailUrl = thumb,
        artistName = uploader,
        albumName = playlistFolderName,
      ),
    )
    return DownloadManager.enqueue(listOf(task))
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
    val playlistFolderName = if (info.isPlaylist) {
      s.customTitle.trim().takeIf { it.isNotEmpty() } ?: info.title
    } else {
      null
    }
    val baseOptions = DownloadOptions(
      type = s.type,
      videoHeight = s.height,
      audioFormat = s.audioFormat,
      customTitle = customSingleTitle,
      embedCoverArt = s.embedCoverArt,
    )
    val tasks = if (info.isPlaylist) {
      info.entries.mapIndexedNotNull { i, e ->
        if (i !in s.selected) return@mapIndexedNotNull null
        val thumb = e.thumbnail ?: info.thumbnail
        val uploader = e.uploader ?: info.uploader
        val trackTitle = formatNumberedTrackTitle(i, e.title, s.numberPlaylistTracks)
        DownloadTask(
          url = e.url,
          title = trackTitle,
          thumbnail = thumb,
          uploader = uploader,
          playlist = playlistFolderName,
          options = baseOptions.copy(
            customTitle = trackTitle,
            thumbnailUrl = thumb,
            artistName = uploader,
            albumName = playlistFolderName,
          ),
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
          options = baseOptions.copy(
            thumbnailUrl = info.thumbnail,
            artistName = info.uploader,
          ),
        )
      )
    }
    return DownloadManager.enqueue(tasks)
  }
}

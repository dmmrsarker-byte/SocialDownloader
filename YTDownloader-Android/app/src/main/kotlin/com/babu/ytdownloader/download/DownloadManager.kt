package com.babu.ytdownloader.download

import android.content.Context
import android.media.MediaScannerConnection
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.data.HistoryItem
import com.babu.ytdownloader.data.HistoryRepository
import com.babu.ytdownloader.engine.DownloadOptions
import com.babu.ytdownloader.engine.MediaType
import com.babu.ytdownloader.engine.NewPipeEngine
import com.babu.ytdownloader.engine.YtDlpEngine
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class TaskStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELED }

@Serializable
data class DownloadTask(
  val id: String = UUID.randomUUID().toString(),
  val url: String,
  val title: String,
  val thumbnail: String?,
  val uploader: String?,
  val playlist: String?,
  val options: DownloadOptions,
  val status: TaskStatus = TaskStatus.QUEUED,
  val progress: Float = 0f,
  val etaSec: Long = 0,
  val detail: String = "",
  val error: String? = null,
  val files: List<String> = emptyList(),
) {
  val isActive get() = status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
}

/**
 * Thread-safe persistent download queue (v2.1) with:
 * - Disk persistence (`tasks_v21.json`) so failed/paused links are never lost when closing the app
 * - Automatic saving of failed links (both download failures & fetch failures) with one-tap Retry or Download from Link
 * - Live Rename support (updates task title, customTitle, physical file on disk, and HistoryRepository)
 * - Duplicate-enqueue protection & per-task generation counter isolation
 */
object DownloadManager {
  @Volatile private var app: Context? = null
  private var tasksFile: File? = null
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
  private val persistLock = Mutex()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val canceled = ConcurrentHashMap.newKeySet<String>()
  private val paused = ConcurrentHashMap.newKeySet<String>()
  private val taskGeneration = ConcurrentHashMap<String, AtomicLong>()

  private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
  val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

  fun init(context: Context) {
    val appCtx = context.applicationContext
    app = appCtx
    val f = File(appCtx.filesDir, "tasks_v21.json")
    tasksFile = f
    scope.launch {
      val loaded = runCatching {
        if (f.exists()) json.decodeFromString<List<DownloadTask>>(f.readText()) else emptyList()
      }.getOrDefault(emptyList())
      if (loaded.isNotEmpty()) {
        val normalized = loaded.map { t ->
          if (t.status == TaskStatus.RUNNING || t.status == TaskStatus.QUEUED) {
            t.copy(status = TaskStatus.PAUSED, etaSec = 0, detail = "Paused (Saved)")
          } else {
            t
          }
        }
        _tasks.update { current ->
          val currentIds = current.map { it.id }.toSet()
          current + normalized.filterNot { it.id in currentIds }
        }
      }
    }
  }

  @Synchronized
  fun enqueue(newTasks: List<DownloadTask>): Int {
    if (newTasks.isEmpty()) return 0
    val existing = _tasks.value
    val toAdd = mutableListOf<DownloadTask>()

    for (candidate in newTasks) {
      val duplicate = existing.firstOrNull {
        (it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING || it.status == TaskStatus.PAUSED) &&
          it.url == candidate.url &&
          it.options.type == candidate.options.type &&
          it.options.videoHeight == candidate.options.videoHeight &&
          it.options.audioFormat == candidate.options.audioFormat
      } ?: toAdd.firstOrNull {
        it.url == candidate.url &&
          it.options.type == candidate.options.type &&
          it.options.videoHeight == candidate.options.videoHeight &&
          it.options.audioFormat == candidate.options.audioFormat
      }

      if (duplicate != null) {
        if (duplicate.status == TaskStatus.PAUSED) {
          resume(duplicate.id)
        }
        continue
      }
      toAdd.add(candidate)
    }

    if (toAdd.isEmpty()) return 0
    _tasks.update { toAdd + it }
    persistTasks()
    app?.let { DownloadService.start(it) }
    schedule()
    return toAdd.size
  }

  /**
   * Saves a link that failed during initial fetch/analysis into the persistent Downloads & History
   * lists so the user never loses the link and can tap Retry or Download from Link anytime.
   */
  @Synchronized
  fun saveFailedLink(
    url: String,
    title: String? = null,
    errorMsg: String = "Failed to fetch media info",
    type: MediaType = MediaType.VIDEO,
  ): DownloadTask {
    val cleanUrl = url.trim()
    val fallbackTitle = title?.takeIf { it.isNotBlank() }
      ?: cleanUrl.substringAfter("://").take(65)
    val existing = _tasks.value.firstOrNull { it.url == cleanUrl && it.status == TaskStatus.FAILED }
    if (existing != null) {
      update(existing.id) { it.copy(error = errorMsg, detail = "Saved failed link") }
      return _tasks.value.first { it.id == existing.id }
    }

    val task = DownloadTask(
      url = cleanUrl,
      title = fallbackTitle,
      thumbnail = null,
      uploader = "Saved Link",
      playlist = null,
      options = DownloadOptions(type = type, customTitle = title?.takeIf { it.isNotBlank() }),
      status = TaskStatus.FAILED,
      progress = 0f,
      detail = "Saved failed link",
      error = errorMsg,
    )
    _tasks.update { listOf(task) + it }
    persistTasks()
    HistoryRepository.add(
      HistoryItem(
        id = task.id,
        title = task.title,
        url = task.url,
        thumbnail = null,
        uploader = task.uploader,
        playlist = null,
        type = task.options.type.name,
        quality = task.options.qualityLabel(),
        filePath = null,
        sizeBytes = 0L,
        status = "FAILED",
        error = errorMsg,
      )
    )
    return task
  }

  /**
   * Direct download from a raw link (even if preview fetch failed or user wants immediate retry from link).
   */
  @Synchronized
  fun enqueueDirectFromLink(
    url: String,
    customTitle: String? = null,
    type: MediaType = MediaType.VIDEO,
  ): DownloadTask {
    val cleanUrl = url.trim()
    val existing = _tasks.value.firstOrNull { it.url == cleanUrl && (it.status == TaskStatus.FAILED || it.status == TaskStatus.PAUSED || it.status == TaskStatus.CANCELED) }
    if (existing != null) {
      if (!customTitle.isNullOrBlank()) {
        renameTask(existing.id, customTitle)
      }
      retry(existing.id)
      return _tasks.value.first { it.id == existing.id }
    }

    val displayTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() }
      ?: cleanUrl.substringAfter("://").take(65)
    val task = DownloadTask(
      url = cleanUrl,
      title = displayTitle,
      thumbnail = null,
      uploader = null,
      playlist = null,
      options = DownloadOptions(
        type = type,
        videoHeight = 1080,
        audioFormat = AppSettings.audioFormat.value,
        customTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() },
      ),
      status = TaskStatus.QUEUED,
    )
    _tasks.update { listOf(task) + it }
    persistTasks()
    app?.let { DownloadService.start(it) }
    schedule()
    return task
  }

  /**
   * Renames a task (queued, paused, failed, or completed).
   * If the task has already downloaded a file on disk, renames the file on disk too!
   */
  @Synchronized
  fun renameTask(id: String, newTitle: String): Boolean {
    val cleanTitle = newTitle.trim().takeIf { it.isNotEmpty() } ?: return false
    val task = _tasks.value.firstOrNull { it.id == id } ?: return false
    var updatedFiles = task.files

    val mainPath = task.files.firstOrNull()
    if (!mainPath.isNullOrBlank()) {
      val oldFile = File(mainPath)
      if (oldFile.exists()) {
        val ext = oldFile.extension.ifBlank {
          if (task.options.type == MediaType.AUDIO) task.options.audioFormat.ext else "mp4"
        }
        val safeBase = NewPipeEngine.sanitizeFileName(cleanTitle).take(100)
        val parent = oldFile.parentFile ?: oldFile.absoluteFile.parentFile
        if (parent != null) {
          var target = File(parent, "$safeBase.$ext")
          var idx = 1
          while (target.exists() && target.absolutePath != oldFile.absolutePath && idx < 999) {
            target = File(parent, "$safeBase ($idx).$ext")
            idx++
          }
          val renamedOk = runCatching { oldFile.renameTo(target) }.getOrDefault(false)
          if (renamedOk && target.exists()) {
            updatedFiles = listOf(target.absolutePath) + task.files.drop(1)
            app?.let { ctx ->
              MediaScannerConnection.scanFile(ctx, arrayOf(oldFile.absolutePath, target.absolutePath), null, null)
            }
          }
        }
      }
    }

    update(id) {
      it.copy(
        title = cleanTitle,
        options = it.options.copy(customTitle = cleanTitle),
        files = updatedFiles,
      )
    }
    HistoryRepository.updateTitleAndPath(id, cleanTitle, updatedFiles.firstOrNull())
    return true
  }

  @Synchronized
  fun pause(id: String) {
    val task = _tasks.value.firstOrNull { it.id == id } ?: return
    when (task.status) {
      TaskStatus.QUEUED -> {
        bumpGeneration(id)
        update(id) { it.copy(status = TaskStatus.PAUSED, etaSec = 0, detail = "Paused") }
      }
      TaskStatus.RUNNING -> {
        paused.add(id)
        bumpGeneration(id)
        update(id) { it.copy(status = TaskStatus.PAUSED, etaSec = 0, detail = "Paused") }
        YtDlpEngine.cancel(id)
        schedule()
      }
      else -> Unit
    }
  }

  @Synchronized
  fun resume(id: String) {
    val task = _tasks.value.firstOrNull { it.id == id } ?: return
    if (task.status != TaskStatus.PAUSED && task.status != TaskStatus.FAILED && task.status != TaskStatus.CANCELED) return
    paused.remove(id)
    canceled.remove(id)
    update(id) { it.copy(status = TaskStatus.QUEUED, error = null, detail = "Resuming…") }
    app?.let { DownloadService.start(it) }
    schedule()
  }

  @Synchronized
  fun cancel(id: String) {
    val task = _tasks.value.firstOrNull { it.id == id } ?: return
    paused.remove(id)
    bumpGeneration(id)
    when (task.status) {
      TaskStatus.QUEUED, TaskStatus.PAUSED -> {
        update(id) { it.copy(status = TaskStatus.CANCELED, progress = 0f, etaSec = 0, detail = "Canceled") }
        YtDlpEngine.cleanupTaskFiles(id)
      }
      TaskStatus.RUNNING -> {
        canceled.add(id)
        update(id) { it.copy(status = TaskStatus.CANCELED, etaSec = 0, detail = "Canceled") }
        YtDlpEngine.cancel(id)
        schedule()
      }
      else -> Unit
    }
  }

  fun cancelAll() = _tasks.value.filter { it.isActive || it.status == TaskStatus.PAUSED }.forEach { cancel(it.id) }

  @Synchronized
  fun retry(id: String) {
    paused.remove(id)
    canceled.remove(id)
    update(id) { it.copy(status = TaskStatus.QUEUED, error = null, detail = "Retrying…") }
    app?.let { DownloadService.start(it) }
    schedule()
  }

  @Synchronized
  fun remove(id: String) {
    cancel(id)
    YtDlpEngine.cleanupTaskFiles(id)
    taskGeneration.remove(id)
    _tasks.update { list -> list.filterNot { it.id == id } }
    persistTasks()
  }

  /**
   * Clears completed/canceled tasks while keeping active, paused, and FAILED tasks
   * so failed links stay saved until explicitly retried or removed.
   */
  fun clearFinished() {
    _tasks.update { list ->
      list.filter { it.isActive || it.status == TaskStatus.PAUSED || it.status == TaskStatus.FAILED }
    }
    persistTasks()
  }

  private fun bumpGeneration(id: String): Long =
    taskGeneration.getOrPut(id) { AtomicLong(0L) }.incrementAndGet()

  private fun isCurrentGeneration(id: String, gen: Long): Boolean =
    taskGeneration[id]?.get() == gen

  // ------------------------------------------------------------------------

  @Synchronized
  private fun schedule() {
    val list = _tasks.value
    val free = AppSettings.concurrency.value - list.count { it.status == TaskStatus.RUNNING }
    if (free <= 0) return
    list.filter { it.status == TaskStatus.QUEUED }.take(free).forEach { task ->
      val gen = bumpGeneration(task.id)
      paused.remove(task.id)
      canceled.remove(task.id)
      update(task.id) {
        it.copy(
          status = TaskStatus.RUNNING,
          detail = if (it.progress > 0f) "Resuming…" else "Starting…",
        )
      }
      run(task, gen)
    }
  }

  private fun run(task: DownloadTask, gen: Long) = scope.launch {
    try {
      val latestTask = _tasks.value.firstOrNull { it.id == task.id } ?: task
      val outDir = AppSettings.outputDir(latestTask.options.type, latestTask.playlist)
      val effectiveOptions = latestTask.options.copy(
        thumbnailUrl = latestTask.options.thumbnailUrl ?: latestTask.thumbnail,
        artistName = latestTask.options.artistName ?: latestTask.uploader,
        albumName = latestTask.options.albumName ?: latestTask.playlist,
      )
      val files = YtDlpEngine.download(latestTask.id, latestTask.url, effectiveOptions, outDir) { progress, eta, line ->
        if (!isCurrentGeneration(latestTask.id, gen)) return@download
        update(latestTask.id, persistImmediate = false) {
          if (it.status != TaskStatus.RUNNING) return@update it
          it.copy(
            progress = if (progress >= 0) maxOf(it.progress, progress.coerceIn(0f, 100f)) else it.progress,
            etaSec = eta,
            detail = describe(line) ?: it.detail,
          )
        }
      }

      if (!isCurrentGeneration(latestTask.id, gen)) return@launch
      if (paused.remove(latestTask.id)) {
        update(latestTask.id) { it.copy(status = TaskStatus.PAUSED, etaSec = 0, detail = "Paused") }
        return@launch
      }
      if (canceled.remove(latestTask.id)) {
        YtDlpEngine.cleanupTaskFiles(latestTask.id)
        update(latestTask.id) { it.copy(status = TaskStatus.CANCELED, progress = 0f, etaSec = 0, detail = "Canceled") }
        return@launch
      }

      val paths = files.map { it.absolutePath }
      val finalTask = _tasks.value.firstOrNull { it.id == latestTask.id } ?: latestTask
      val resolvedTitle = files.firstOrNull()?.nameWithoutExtension ?: finalTask.title
      update(latestTask.id) {
        it.copy(
          title = finalTask.options.customTitle?.takeIf { c -> c.isNotBlank() } ?: resolvedTitle,
          status = TaskStatus.DONE,
          progress = 100f,
          etaSec = 0,
          files = paths,
          error = null,
          detail = "Completed",
        )
      }

      val ctx = app
      if (ctx != null && paths.isNotEmpty()) {
        MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
      }
      val main = files.firstOrNull()
      val updatedTask = _tasks.value.firstOrNull { it.id == latestTask.id } ?: finalTask
      HistoryRepository.add(
        HistoryItem(
          id = updatedTask.id,
          title = updatedTask.title,
          url = updatedTask.url,
          thumbnail = updatedTask.thumbnail,
          uploader = updatedTask.uploader,
          playlist = updatedTask.playlist,
          type = updatedTask.options.type.name,
          quality = updatedTask.options.qualityLabel(),
          filePath = main?.absolutePath,
          sizeBytes = files.sumOf { it.length() },
          status = "DONE",
          error = null,
        )
      )
    } catch (t: Throwable) {
      val wasPaused = paused.remove(task.id)
      val wasCanceled = canceled.remove(task.id)
      if (wasCanceled) {
        YtDlpEngine.cleanupTaskFiles(task.id)
      }
      if (!isCurrentGeneration(task.id, gen)) return@launch
      when {
        wasPaused -> update(task.id) { it.copy(status = TaskStatus.PAUSED, etaSec = 0, detail = "Paused") }
        wasCanceled -> update(task.id) { it.copy(status = TaskStatus.CANCELED, progress = 0f, etaSec = 0, detail = "Canceled") }
        else -> {
          val err = cleanError(t)
          update(task.id) { it.copy(status = TaskStatus.FAILED, etaSec = 0, error = err, detail = "Saved for retry") }
          val failedTask = _tasks.value.firstOrNull { it.id == task.id } ?: task
          HistoryRepository.add(
            HistoryItem(
              id = failedTask.id,
              title = failedTask.title,
              url = failedTask.url,
              thumbnail = failedTask.thumbnail,
              uploader = failedTask.uploader,
              playlist = failedTask.playlist,
              type = failedTask.options.type.name,
              quality = failedTask.options.qualityLabel(),
              filePath = null,
              sizeBytes = 0L,
              status = "FAILED",
              error = err,
            )
          )
        }
      }
    } finally {
      schedule()
    }
  }

  private fun update(id: String, persistImmediate: Boolean = true, transform: (DownloadTask) -> DownloadTask) {
    _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    if (persistImmediate) persistTasks()
  }

  private fun persistTasks() {
    val f = tasksFile ?: return
    val snapshot = _tasks.value
    scope.launch {
      persistLock.withLock {
        runCatching {
          val tmp = File(f.parentFile, "tasks_v21.json.tmp")
          tmp.writeText(json.encodeToString(snapshot))
          tmp.renameTo(f) || run { f.writeText(tmp.readText()); tmp.delete() }
        }
      }
    }
  }

  private val sizeRegex = Regex("""of\s+~?\s*([\d.]+\s*\w+)""")
  private val speedRegex = Regex("""at\s+([\d.]+\s*\w+/s)""")

  private fun describe(line: String): String? = when {
    line.startsWith("[download]") && "%" in line -> {
      val size = sizeRegex.find(line)?.groupValues?.get(1)
      val speed = speedRegex.find(line)?.groupValues?.get(1)
      listOfNotNull(size, speed).joinToString(" • ").ifBlank { null }
    }
    line.startsWith("[Merger]") -> "Merging video & audio…"
    line.startsWith("[ExtractAudio]") -> "Converting audio…"
    line.startsWith("[Metadata]") -> "Adding metadata…"
    !line.startsWith("[") -> line.trim().ifBlank { null }
    else -> null
  }

  private fun cleanError(t: Throwable): String {
    val msg = t.message ?: t.toString()
    return msg.lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
      ?: msg.lines().lastOrNull { it.isNotBlank() }?.trim()
      ?: "Download failed"
  }
}

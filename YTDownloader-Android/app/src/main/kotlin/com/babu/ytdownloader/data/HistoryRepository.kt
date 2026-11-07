package com.babu.ytdownloader.data

import android.content.Context
import android.media.MediaScannerConnection
import com.babu.ytdownloader.engine.NewPipeEngine
import java.io.File
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
data class HistoryItem(
  val id: String,
  val title: String,
  val url: String,
  val thumbnail: String? = null,
  val uploader: String? = null,
  val playlist: String? = null,
  /** "VIDEO" or "AUDIO" */
  val type: String,
  /** e.g. "1080p", "MP3" */
  val quality: String,
  val filePath: String? = null,
  val sizeBytes: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  /** "DONE" or "FAILED" (saved failed links so the user can retry or download from link anytime) */
  val status: String = "DONE",
  val error: String? = null,
)

/** Download history & saved failed links stored as a JSON file in the app's private storage. */
object HistoryRepository {
  @Volatile private var appContext: Context? = null
  private lateinit var file: File
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val ioLock = Mutex()

  private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
  /** Newest first. */
  val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

  fun init(context: Context) {
    appContext = context.applicationContext
    file = File(context.filesDir, "history.json")
    scope.launch {
      val loaded = runCatching { json.decodeFromString<List<HistoryItem>>(file.readText()) }.getOrDefault(emptyList())
      _items.value = loaded.sortedByDescending { it.timestamp }
    }
  }

  fun add(item: HistoryItem) {
    _items.update { existing ->
      val filtered = existing.filterNot {
        it.id == item.id || (it.url == item.url && it.status == "FAILED")
      }
      listOf(item) + filtered
    }
    persist()
  }

  /**
   * Renames a history item's title and, if the file exists on disk, renames the physical file too.
   * Returns the updated file path (or null if no physical file existed).
   */
  fun rename(id: String, newTitle: String): String? {
    val cleanTitle = newTitle.trim().takeIf { it.isNotEmpty() } ?: return null
    val current = _items.value.firstOrNull { it.id == id } ?: return null
    var updatedPath = current.filePath

    if (!current.filePath.isNullOrBlank()) {
      val oldFile = File(current.filePath)
      if (oldFile.exists()) {
        val ext = oldFile.extension.ifBlank { if (current.type == "AUDIO") "mp3" else "mp4" }
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
            updatedPath = target.absolutePath
            appContext?.let { ctx ->
              MediaScannerConnection.scanFile(ctx, arrayOf(oldFile.absolutePath, target.absolutePath), null, null)
            }
          }
        }
      }
    }

    _items.update { list ->
      list.map {
        if (it.id == id) it.copy(title = cleanTitle, filePath = updatedPath) else it
      }
    }
    persist()
    return updatedPath
  }

  fun updateTitleAndPath(id: String, newTitle: String, newPath: String?) {
    _items.update { list ->
      list.map {
        if (it.id == id) it.copy(title = newTitle, filePath = newPath ?: it.filePath) else it
      }
    }
    persist()
  }

  fun remove(id: String, deleteFile: Boolean) {
    val item = _items.value.firstOrNull { it.id == id } ?: return
    if (deleteFile) item.filePath?.let { runCatching { File(it).delete() } }
    _items.update { list -> list.filterNot { it.id == id } }
    persist()
  }

  fun clear() {
    _items.value = emptyList()
    persist()
  }

  private fun persist() {
    if (!::file.isInitialized) return
    val snapshot = _items.value
    scope.launch {
      ioLock.withLock {
        runCatching {
          val tmp = File(file.parentFile, "history.json.tmp")
          tmp.writeText(json.encodeToString(snapshot))
          tmp.renameTo(file) || run { file.writeText(tmp.readText()); tmp.delete() }
        }
      }
    }
  }
}

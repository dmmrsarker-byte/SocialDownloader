package com.babu.ytdownloader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.download.DownloadManager
import com.babu.ytdownloader.download.DownloadTask
import com.babu.ytdownloader.download.TaskStatus
import com.babu.ytdownloader.engine.MediaType
import com.babu.ytdownloader.ui.AppIcons
import com.babu.ytdownloader.ui.S
import com.babu.ytdownloader.ui.openFile

@Composable
fun DownloadsScreen(onDownloadFromLink: (String) -> Unit = {}) {
  val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
  val lang by AppSettings.language.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var renamingTask by remember { mutableStateOf<DownloadTask?>(null) }

  if (tasks.isEmpty()) {
    EmptyState(AppIcons.Queue, S.t("no_downloads_title", lang), S.t("no_downloads_sub", lang))
    return
  }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      val active = tasks.count { it.isActive }
      Text(
        if (active > 0) "${S.t("active_count", lang)}: $active / ${S.t("total_count", lang)}: ${tasks.size}"
        else "${S.t("total_count", lang)}: ${tasks.size}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      if (active > 0 || tasks.any { it.status == TaskStatus.PAUSED }) {
        TextButton(onClick = DownloadManager::cancelAll) { Text(S.t("cancel_all", lang)) }
      }
      TextButton(onClick = DownloadManager::clearFinished) { Text(S.t("clear_done", lang)) }
    }
    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(tasks, key = { it.id }) { task ->
        TaskCard(
          task = task,
          lang = lang,
          onOpen = { openFile(context, task.files.firstOrNull()) },
          onRenameClick = { renamingTask = task },
          onCopyName = {
            copyTextToClipboard(context, "Video Name", task.title, S.t("copied_name_toast", lang))
          },
          onCopyLink = {
            copyTextToClipboard(context, "Video Link", task.url, S.t("copied_link_toast", lang))
          },
          onDownloadFromLink = { onDownloadFromLink(task.url) },
        )
      }
    }
  }

  renamingTask?.let { target ->
    RenameItemDialog(
      initialTitle = target.title,
      lang = lang,
      onDismiss = { renamingTask = null },
      onConfirm = { newName ->
        if (DownloadManager.renameTask(target.id, newName)) {
          Toast.makeText(context, S.t("renamed_toast", lang), Toast.LENGTH_SHORT).show()
        }
        renamingTask = null
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
  task: DownloadTask,
  lang: String,
  onOpen: () -> Unit,
  onRenameClick: () -> Unit,
  onCopyName: () -> Unit,
  onCopyLink: () -> Unit,
  onDownloadFromLink: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card {
    Column(Modifier.padding(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Thumb(task.thumbnail, Modifier.width(100.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              if (task.options.type == MediaType.AUDIO) AppIcons.MusicNote else AppIcons.Movie,
              null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
              "${task.options.qualityLabel()} • ${statusText(task, lang)}",
              style = MaterialTheme.typography.labelSmall,
              color = if (task.status == TaskStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED || task.status == TaskStatus.PAUSED) {
            Spacer(Modifier.height(6.dp))
            if (task.status == TaskStatus.QUEUED && task.progress <= 0f) {
              LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
              LinearProgressIndicator(progress = { (task.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          when (task.status) {
            TaskStatus.QUEUED, TaskStatus.RUNNING -> {
              IconButton(onClick = { DownloadManager.pause(task.id) }) { Icon(AppIcons.Pause, "Pause") }
              IconButton(onClick = { DownloadManager.cancel(task.id) }) { Icon(Icons.Default.Close, "Cancel") }
            }
            TaskStatus.PAUSED -> {
              IconButton(onClick = { DownloadManager.resume(task.id) }) { Icon(Icons.Default.PlayArrow, "Resume") }
              IconButton(onClick = { DownloadManager.remove(task.id) }) { Icon(Icons.Default.Delete, "Remove") }
            }
            TaskStatus.DONE -> {
              IconButton(onClick = onOpen) { Icon(Icons.Default.PlayArrow, "Open") }
            }
            TaskStatus.FAILED, TaskStatus.CANCELED -> {
              IconButton(onClick = { DownloadManager.retry(task.id) }) { Icon(Icons.Default.Refresh, "Retry") }
              IconButton(onClick = { DownloadManager.remove(task.id) }) { Icon(Icons.Default.Delete, "Remove") }
            }
          }
          Box {
            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
              if (task.status == TaskStatus.DONE) {
                DropdownMenuItem(text = { Text(S.t("menu_open", lang)) }, onClick = { menuExpanded = false; onOpen() })
              }
              if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED || task.status == TaskStatus.PAUSED) {
                DropdownMenuItem(
                  text = { Text(S.t("menu_retry_now", lang)) },
                  onClick = { menuExpanded = false; DownloadManager.retry(task.id) },
                )
              }
              DropdownMenuItem(
                text = { Text(S.t("menu_redownload", lang)) },
                onClick = { menuExpanded = false; onDownloadFromLink() },
              )
              DropdownMenuItem(
                text = { Text(S.t("menu_rename", lang)) },
                onClick = { menuExpanded = false; onRenameClick() },
              )
              DropdownMenuItem(
                text = { Text(S.t("menu_copy_name", lang)) },
                onClick = { menuExpanded = false; onCopyName() },
              )
              DropdownMenuItem(
                text = { Text(S.t("menu_copy_link", lang)) },
                onClick = { menuExpanded = false; onCopyLink() },
              )
              DropdownMenuItem(
                text = { Text(S.t("menu_remove_hist", lang), color = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; DownloadManager.remove(task.id) },
              )
            }
          }
        }
      }

      // Quick action chips for Rename, Copy Name, Copy Link, and Retry / Download from Link
      Spacer(Modifier.height(6.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED) {
          AssistChip(
            onClick = { DownloadManager.retry(task.id) },
            label = { Text(S.t("retry_btn", lang)) },
            leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(15.dp)) },
          )
          AssistChip(
            onClick = onDownloadFromLink,
            label = { Text(S.t("download_from_link_btn", lang)) },
            leadingIcon = { Icon(AppIcons.Download, null, Modifier.size(15.dp)) },
          )
        }
        AssistChip(
          onClick = onRenameClick,
          label = { Text(S.t("rename_btn", lang)) },
          leadingIcon = { Icon(AppIcons.Edit, null, Modifier.size(15.dp)) },
        )
        AssistChip(
          onClick = onCopyName,
          label = { Text(S.t("copy_name_btn", lang)) },
          leadingIcon = { Icon(AppIcons.Copy, null, Modifier.size(15.dp)) },
        )
        AssistChip(
          onClick = onCopyLink,
          label = { Text(S.t("copy_link_btn", lang)) },
          leadingIcon = { Icon(AppIcons.Link, null, Modifier.size(15.dp)) },
        )
      }
    }
  }
}

@Composable
internal fun RenameItemDialog(
  initialTitle: String,
  lang: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember(initialTitle) { mutableStateOf(initialTitle) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(S.t("rename_dialog_title", lang)) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(S.t("rename_dialog_hint", lang)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      Button(
        onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
        enabled = text.isNotBlank(),
      ) {
        Text(S.t("save_btn", lang))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(S.t("no_cancel", lang)) }
    },
  )
}

private fun statusText(task: DownloadTask, lang: String): String = when (task.status) {
  TaskStatus.QUEUED -> S.t("status_queued", lang)
  TaskStatus.RUNNING -> buildString {
    append("%.0f%%".format(task.progress))
    if (task.detail.isNotBlank()) append(" • ").append(task.detail)
    if (task.etaSec > 0) append(" • ").append(task.etaSec).append("s")
  }
  TaskStatus.PAUSED -> buildString {
    append(S.t("status_paused", lang))
    if (task.progress > 0f) append(" (%.0f%%)".format(task.progress))
  }
  TaskStatus.DONE -> S.t("status_done", lang)
  TaskStatus.FAILED -> "${S.t("status_failed", lang)}: ${task.error ?: ""}"
  TaskStatus.CANCELED -> S.t("status_canceled", lang)
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
  Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

package com.babu.ytdownloader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.data.HistoryItem
import com.babu.ytdownloader.data.HistoryRepository
import com.babu.ytdownloader.download.DownloadManager
import com.babu.ytdownloader.engine.MediaType
import com.babu.ytdownloader.ui.AppIcons
import com.babu.ytdownloader.ui.S
import com.babu.ytdownloader.ui.formatBytes
import com.babu.ytdownloader.ui.formatDate
import com.babu.ytdownloader.ui.openFile
import com.babu.ytdownloader.ui.shareFile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(onRedownload: (String) -> Unit) {
  val items by HistoryRepository.items.collectAsStateWithLifecycle()
  val lang by AppSettings.language.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmClear by remember { mutableStateOf(false) }
  var renamingItem by remember { mutableStateOf<HistoryItem?>(null) }
  val context = LocalContext.current

  if (items.isEmpty()) {
    EmptyState(AppIcons.History, S.t("empty_history_title", lang), S.t("empty_history_sub", lang))
    return
  }

  val visible = items.filter {
    val matchesFilter = when (filter) {
      null -> true
      "FAILED" -> it.status == "FAILED"
      else -> it.type == filter
    }
    matchesFilter &&
      (query.isBlank() ||
        it.title.contains(query, ignoreCase = true) ||
        it.url.contains(query, ignoreCase = true) ||
        it.playlist?.contains(query, true) == true)
  }

  Column(Modifier.fillMaxSize()) {
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      placeholder = { Text(S.t("search_history", lang)) },
      leadingIcon = { Icon(Icons.Default.Search, null) },
      trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) } },
      singleLine = true,
    )
    FlowRow(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("${S.t("filter_all", lang)} (${items.size})") })
      val failedCount = items.count { it.status == "FAILED" }
      if (failedCount > 0) {
        FilterChip(
          selected = filter == "FAILED",
          onClick = { filter = "FAILED" },
          label = { Text("${S.t("filter_failed", lang)} ($failedCount)") },
        )
      }
      FilterChip(selected = filter == "VIDEO", onClick = { filter = "VIDEO" }, label = { Text(S.t("video_tab", lang)) })
      FilterChip(selected = filter == "AUDIO", onClick = { filter = "AUDIO" }, label = { Text(S.t("audio_tab", lang)) })
      TextButton(onClick = { confirmClear = true }) { Text(S.t("clear_all", lang)) }
    }
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(visible, key = { it.id }) { item ->
        HistoryCard(
          item = item,
          lang = lang,
          onOpen = {
            if (item.status == "FAILED" || item.filePath.isNullOrBlank()) {
              onRedownload(item.url)
            } else {
              openFile(context, item.filePath)
            }
          },
          onShare = { shareFile(context, item.filePath) },
          onRedownload = { onRedownload(item.url) },
          onRetryDirect = {
            val mediaType = if (item.type == "AUDIO") MediaType.AUDIO else MediaType.VIDEO
            DownloadManager.enqueueDirectFromLink(item.url, item.title, mediaType)
            Toast.makeText(context, S.t("queued_toast", lang), Toast.LENGTH_SHORT).show()
          },
          onRenameClick = { renamingItem = item },
          onCopyName = {
            copyTextToClipboard(context, "Video Name", item.title, S.t("copied_name_toast", lang))
          },
          onCopyLink = {
            copyTextToClipboard(context, "Video Link", item.url, S.t("copied_link_toast", lang))
          },
          onDelete = { withFile -> HistoryRepository.remove(item.id, withFile) },
        )
      }
    }
  }

  renamingItem?.let { target ->
    RenameItemDialog(
      initialTitle = target.title,
      lang = lang,
      onDismiss = { renamingItem = null },
      onConfirm = { newName ->
        HistoryRepository.rename(target.id, newName)
        DownloadManager.renameTask(target.id, newName)
        Toast.makeText(context, S.t("renamed_toast", lang), Toast.LENGTH_SHORT).show()
        renamingItem = null
      },
    )
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text(S.t("confirm_clear_title", lang)) },
      text = { Text(S.t("confirm_clear_text", lang)) },
      confirmButton = { TextButton(onClick = { HistoryRepository.clear(); confirmClear = false }) { Text(S.t("yes_clear", lang)) } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(S.t("no_cancel", lang)) } },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(
  item: HistoryItem,
  lang: String,
  onOpen: () -> Unit,
  onShare: () -> Unit,
  onRedownload: () -> Unit,
  onRetryDirect: () -> Unit,
  onRenameClick: () -> Unit,
  onCopyName: () -> Unit,
  onCopyLink: () -> Unit,
  onDelete: (Boolean) -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  val isFailed = item.status == "FAILED"

  Card(Modifier.clickable(onClick = onOpen)) {
    Column(Modifier.padding(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Thumb(item.thumbnail, Modifier.width(105.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              if (item.type == "AUDIO") AppIcons.MusicNote else AppIcons.Movie,
              null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            if (isFailed) {
              Text(
                "${S.t("status_failed", lang)}${item.error?.let { ": $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            } else {
              Text(
                listOf(item.quality, formatBytes(item.sizeBytes)).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          item.playlist?.let {
            Text("📁 $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Text(formatDate(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Box {
          IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!isFailed && !item.filePath.isNullOrBlank()) {
              DropdownMenuItem(text = { Text(S.t("menu_open", lang)) }, onClick = { menu = false; onOpen() })
              DropdownMenuItem(text = { Text(S.t("menu_share", lang)) }, onClick = { menu = false; onShare() })
            }
            DropdownMenuItem(text = { Text(S.t("menu_retry_now", lang)) }, onClick = { menu = false; onRetryDirect() })
            DropdownMenuItem(text = { Text(S.t("menu_redownload", lang)) }, onClick = { menu = false; onRedownload() })
            DropdownMenuItem(text = { Text(S.t("menu_rename", lang)) }, onClick = { menu = false; onRenameClick() })
            DropdownMenuItem(text = { Text(S.t("menu_copy_name", lang)) }, onClick = { menu = false; onCopyName() })
            DropdownMenuItem(text = { Text(S.t("menu_copy_link", lang)) }, onClick = { menu = false; onCopyLink() })
            DropdownMenuItem(text = { Text(S.t("menu_remove_hist", lang)) }, onClick = { menu = false; onDelete(false) })
            if (!item.filePath.isNullOrBlank()) {
              DropdownMenuItem(
                text = { Text(S.t("menu_delete_file", lang), color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete(true) },
              )
            }
          }
        }
      }

      Spacer(Modifier.height(6.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (isFailed) {
          AssistChip(
            onClick = onRetryDirect,
            label = { Text(S.t("retry_btn", lang)) },
            leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(15.dp)) },
          )
        }
        AssistChip(
          onClick = onRedownload,
          label = { Text(S.t("download_from_link_btn", lang)) },
          leadingIcon = { Icon(AppIcons.Download, null, Modifier.size(15.dp)) },
        )
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

package com.babu.ytdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.download.DownloadManager
import com.babu.ytdownloader.ui.screens.DownloadsScreen
import com.babu.ytdownloader.ui.screens.HistoryScreen
import com.babu.ytdownloader.ui.screens.HomeScreen
import com.babu.ytdownloader.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(vm: MainViewModel) {
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
  val lang by AppSettings.language.collectAsStateWithLifecycle()
  val langChosen by AppSettings.languageChosen.collectAsStateWithLifecycle()
  val activeCount = tasks.count { it.isActive }

  // Show First-Launch Language Picker Dialog right after APK installation (Default: English)
  if (!langChosen) {
    FirstLaunchLanguageDialog(
      currentCode = lang,
      onConfirm = { chosenCode -> AppSettings.setLanguage(chosenCode) },
    )
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = vm.tab == 0, onClick = { vm.tab = 0 },
          icon = { Icon(AppIcons.Download, null) }, label = { Text(S.t("nav_home", lang)) },
        )
        NavigationBarItem(
          selected = vm.tab == 1, onClick = { vm.tab = 1 },
          icon = {
            BadgedBox(badge = { if (activeCount > 0) Badge { Text("$activeCount") } }) {
              Icon(AppIcons.Queue, null)
            }
          },
          label = { Text(S.t("nav_downloads", lang)) },
        )
        NavigationBarItem(
          selected = vm.tab == 2, onClick = { vm.tab = 2 },
          icon = { Icon(AppIcons.History, null) }, label = { Text(S.t("nav_history", lang)) },
        )
        NavigationBarItem(
          selected = vm.tab == 3, onClick = { vm.tab = 3 },
          icon = { Icon(Icons.Default.Settings, null) }, label = { Text(S.t("nav_settings", lang)) },
        )
      }
    },
  ) { padding ->
    val modifier = Modifier.padding(padding)
    Box(modifier) {
      when (vm.tab) {
        0 -> HomeScreen(vm) { count ->
          scope.launch {
            val result = snackbar.showSnackbar(
              "$count • ${S.t("queued_toast", lang)}",
              actionLabel = S.t("view_btn", lang),
              duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.tab = 1
          }
        }
        1 -> DownloadsScreen(onDownloadFromLink = { url -> vm.openLink(url, autoStart = false) })
        2 -> HistoryScreen(onRedownload = { url -> vm.openLink(url, autoStart = false) })
        else -> SettingsScreen()
      }
    }
  }
}

@Composable
private fun FirstLaunchLanguageDialog(currentCode: String, onConfirm: (String) -> Unit) {
  var selected by remember { mutableStateOf(currentCode.ifBlank { "en" }) }

  AlertDialog(
    onDismissRequest = { onConfirm(selected) },
    title = {
      Text(S.t("choose_lang_title", selected), fontWeight = FontWeight.Bold)
    },
    text = {
      Column {
        Text(
          S.t("choose_lang_sub", selected),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
          modifier = Modifier
            .heightIn(max = 310.dp)
            .verticalScroll(rememberScrollState()),
        ) {
          S.languages.forEach { option ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { selected = option.code }
                .padding(vertical = 4.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = selected == option.code,
                onClick = { selected = option.code },
              )
              Spacer(Modifier.width(8.dp))
              Text(
                option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected == option.code) FontWeight.Bold else FontWeight.Normal,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(selected) }) {
        Text(S.t("continue_btn", selected))
      }
    },
  )
}

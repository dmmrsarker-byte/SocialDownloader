package com.babu.ytdownloader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.engine.AudioFormat
import com.babu.ytdownloader.engine.YtDlpEngine
import com.babu.ytdownloader.ui.AppIcons
import com.babu.ytdownloader.ui.S
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
  val engine by YtDlpEngine.state.collectAsStateWithLifecycle()
  val lang by AppSettings.language.collectAsStateWithLifecycle()
  val audioFormat by AppSettings.audioFormat.collectAsStateWithLifecycle()
  val concurrency by AppSettings.concurrency.collectAsStateWithLifecycle()
  val nightly by AppSettings.nightly.collectAsStateWithLifecycle()
  val autoDownload by AppSettings.autoDownload.collectAsStateWithLifecycle()
  val sponsorHeadline by AppSettings.sponsorHeadline.collectAsStateWithLifecycle()
  val sponsorUrl by AppSettings.sponsorUrl.collectAsStateWithLifecycle()

  var editHeadline by remember(sponsorHeadline) { mutableStateOf(sponsorHeadline) }
  var editUrl by remember(sponsorUrl) { mutableStateOf(sponsorUrl) }
  var updating by remember { mutableStateOf(false) }
  var updateMsg by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(S.t("settings_title", lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    // ---- Language Selection (Default: English + Bengali, Hindi, Urdu, Arabic, Spanish, Portuguese, Russian, Indonesian)
    Section("🌐 ${S.t("settings_lang_section", lang)}") {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        S.languages.forEach { option ->
          FilterChip(
            selected = lang == option.code,
            onClick = { AppSettings.setLanguage(option.code) },
            label = { Text(option.label) },
          )
        }
      }
    }

    // ---- Engine
    Section(S.t("settings_engine_section", lang)) {
      val status = when (val e = engine) {
        YtDlpEngine.State.Initializing -> S.t("engine_init", lang)
        is YtDlpEngine.State.Ready -> "Engine: ${e.version ?: "Ready"}"
        is YtDlpEngine.State.Failed -> "Issue: ${e.message}"
      }
      Text(status, style = MaterialTheme.typography.bodyMedium)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(S.t("auto_download_label", lang), style = MaterialTheme.typography.bodyMedium)
          Text("YouTube, FB, IG, Threads, LinkedIn, X, TikTok & more", style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = autoDownload, onCheckedChange = AppSettings::setAutoDownload)
      }
      Button(
        enabled = !updating && engine is YtDlpEngine.State.Ready,
        onClick = {
          updating = true
          updateMsg = null
          scope.launch {
            updateMsg = try {
              if (YtDlpEngine.update(nightly)) "Updated ✓" else "Already up to date ✓"
            } catch (t: Throwable) {
              "Update failed: ${t.message}"
            }
            updating = false
          }
        },
      ) {
        if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text(if (updating) S.t("settings_updating", lang) else S.t("settings_update_btn", lang))
      }
      updateMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    // ---- Defaults
    Section(S.t("settings_audio_section", lang)) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioFormat.entries.forEach { f ->
          FilterChip(selected = audioFormat == f, onClick = { AppSettings.setAudioFormat(f) }, label = { Text(f.label) })
        }
      }
    }

    Section(S.t("settings_concurrency_section", lang)) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { n ->
          FilterChip(selected = concurrency == n, onClick = { AppSettings.setConcurrency(n) }, label = { Text("$n") })
        }
      }
    }

    // ---- Monetization / Sponsor Banner Setup
    Section("💰 Monetization / Sponsor Banner") {
      OutlinedTextField(
        value = editHeadline,
        onValueChange = { editHeadline = it },
        label = { Text("Banner Headline") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = editUrl,
        onValueChange = { editUrl = it },
        label = { Text("Website / Ad URL (https://…)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Button(onClick = { AppSettings.setSponsor(editHeadline.trim(), editUrl.trim()) }) {
        Text("Save Banner Settings")
      }
    }

    Section(S.t("settings_folder_section", lang)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(AppSettings.rootDir.absolutePath, style = MaterialTheme.typography.bodySmall)
      }
      Text(
        S.t("settings_folder_desc", lang),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
      content()
    }
  }
}

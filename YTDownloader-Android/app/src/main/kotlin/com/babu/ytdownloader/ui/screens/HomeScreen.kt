package com.babu.ytdownloader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.babu.ytdownloader.R
import com.babu.ytdownloader.data.AppSettings
import com.babu.ytdownloader.engine.AudioFormat
import com.babu.ytdownloader.engine.MediaInfo
import com.babu.ytdownloader.engine.MediaType
import com.babu.ytdownloader.engine.YtDlpEngine
import com.babu.ytdownloader.ui.AppIcons
import com.babu.ytdownloader.ui.HomeState
import com.babu.ytdownloader.ui.MainViewModel
import com.babu.ytdownloader.ui.S
import com.babu.ytdownloader.ui.formatDuration

internal fun copyTextToClipboard(context: Context, label: String, text: String, toastMessage: String) {
  runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onQueued: (Int) -> Unit) {
  val state by vm.home.collectAsStateWithLifecycle()
  val engine by YtDlpEngine.state.collectAsStateWithLifecycle()
  val lang by AppSettings.language.collectAsStateWithLifecycle()
  val autoDownload by AppSettings.autoDownload.collectAsStateWithLifecycle()
  val sponsorHeadline by AppSettings.sponsorHeadline.collectAsStateWithLifecycle()
  val sponsorUrl by AppSettings.sponsorUrl.collectAsStateWithLifecycle()
  val context = LocalContext.current
  @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current

  LaunchedEffect(Unit) {
    vm.autoQueuedEvents.collect { count ->
      onQueued(count)
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Image(
          painter = painterResource(R.drawable.app_logo),
          contentDescription = "Social Downloader Logo",
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column {
          Text(S.t("app_title", lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text(
            S.t("app_subtitle", lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (engine is YtDlpEngine.State.Initializing) {
      item { EngineBanner(S.t("engine_init", lang), loading = true) }
    } else if (engine is YtDlpEngine.State.Failed) {
      item { EngineBanner("${S.t("engine_failed", lang)}${(engine as YtDlpEngine.State.Failed).message}", loading = false) }
    }

    item {
      OutlinedTextField(
        value = state.url,
        onValueChange = vm::onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(S.t("url_label_unified", lang)) },
        placeholder = { Text("https://… (YT Music, YouTube, SoundCloud, FB, IG, X, TikTok, Pornhub, XVideos & Any Site)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { vm.analyze(autoStart = false) }),
        trailingIcon = {
          if (state.url.isNotEmpty()) {
            IconButton(onClick = vm::clear) { Icon(Icons.Default.Clear, "Clear") }
          } else {
            IconButton(onClick = {
              clipboard.getText()?.text?.let {
                vm.onUrlChange(it.trim())
                vm.analyze(autoStart = autoDownload)
              }
            }) {
              Icon(AppIcons.Paste, "Paste")
            }
          }
        },
        isError = state.error != null,
        supportingText = state.error?.let { { Text(it) } },
      )
      Spacer(Modifier.height(8.dp))
      Button(
        onClick = { vm.analyze(autoStart = false) },
        enabled = !state.loading && state.url.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (state.loading) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
          Spacer(Modifier.width(8.dp))
          Text(S.t("fetching", lang))
        } else {
          Icon(Icons.Default.Search, null)
          Spacer(Modifier.width(8.dp))
          Text(S.t("search_btn", lang))
        }
      }
      Spacer(Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          S.t("auto_download_label", lang),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        Switch(
          checked = autoDownload,
          onCheckedChange = AppSettings::setAutoDownload,
        )
      }
    }

    // If fetching failed, show Saved Failed Link Recovery Card with Retry, Direct Download from Link & Copy Link
    if (state.error != null && state.url.isNotBlank()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              S.t("saved_failed_notice", lang),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              fontWeight = FontWeight.Medium,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Button(onClick = { vm.analyze(autoStart = false) }) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(S.t("retry_btn", lang))
              }
              OutlinedButton(onClick = {
                val queued = vm.directDownloadFromCurrentUrl()
                if (queued > 0) onQueued(queued)
              }) {
                Icon(AppIcons.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(S.t("direct_download_btn", lang))
              }
              OutlinedButton(onClick = {
                copyTextToClipboard(context, "URL", state.url.trim(), S.t("copied_link_toast", lang))
              }) {
                Icon(AppIcons.Link, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(S.t("copy_link_btn", lang))
              }
            }
          }
        }
      }
    }

    state.info?.let { info ->
      item { InfoCard(info, state, vm, lang, context) }
      item { OptionsCard(state, vm, lang) }
      item {
        val count = if (info.isPlaylist) state.selected.size else 1
        Button(
          onClick = { onQueued(vm.startDownload()) },
          enabled = count > 0,
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          Icon(AppIcons.Download, null)
          Spacer(Modifier.width(8.dp))
          Text(
            if (info.isPlaylist) "${S.t("download_count_btn", lang)} ($count)" else S.t("download_btn", lang),
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
      if (info.isPlaylist) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "${S.t("playlist_selected", lang)} (${state.selected.size}/${info.entries.size})",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.selectAll(true) }) { Text(S.t("select_all", lang)) }
            TextButton(onClick = { vm.selectAll(false) }) { Text(S.t("select_none", lang)) }
          }
        }
        itemsIndexed(info.entries) { index, entry ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { vm.toggleEntry(index) }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = index in state.selected, onCheckedChange = { vm.toggleEntry(index) })
            Thumb(entry.thumbnail ?: info.thumbnail, Modifier.width(88.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text("${index + 1}. ${entry.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
              val subLine = listOfNotNull(
                entry.uploader?.takeIf { it.isNotBlank() },
                formatDuration(entry.durationSec).takeIf { it.isNotBlank() },
              ).joinToString(" • ")
              if (subLine.isNotEmpty()) {
                Text(subLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }

    if (sponsorUrl.isNotBlank()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
          modifier = Modifier.fillMaxWidth().clickable {
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sponsorUrl)))
            }
          },
        ) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              sponsorHeadline,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EngineBanner(text: String, loading: Boolean) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      if (loading) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
      }
      Text(text, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(info: MediaInfo, state: HomeState, vm: MainViewModel, lang: String, context: Context) {
  Card {
    Box {
      Thumb(info.thumbnail, Modifier.fillMaxWidth(), corner = 0)
      if (state.type == MediaType.AUDIO || info.isAudioCentric) {
        Text(
          "🎵 Music Cover Art",
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
      val badge = if (info.isPlaylist) "📑 ${info.entries.size} tracks" else formatDuration(info.durationSec)
      if (badge.isNotEmpty()) {
        Text(
          badge,
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
      info.uploader?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      OutlinedTextField(
        value = state.customTitle,
        onValueChange = vm::setCustomTitle,
        modifier = Modifier.fillMaxWidth(),
        label = {
          Text(
            if (info.isPlaylist) "Playlist / Album Folder Name" else S.t("custom_name_label", lang)
          )
        },
        leadingIcon = { Icon(AppIcons.Edit, null, Modifier.size(18.dp)) },
        trailingIcon = {
          if (state.customTitle.isNotEmpty()) {
            IconButton(onClick = { vm.setCustomTitle("") }) {
              Icon(Icons.Default.Clear, "Reset")
            }
          }
        },
        singleLine = true,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (!info.thumbnail.isNullOrBlank() || info.entries.any { !it.thumbnail.isNullOrBlank() }) {
          OutlinedButton(
            onClick = {
              vm.saveMusicCover(
                onSaved = {
                  Toast.makeText(context, S.t("cover_saved_toast", lang), Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                  Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                },
              )
            },
          ) {
            Icon(AppIcons.MusicNote, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(S.t("save_cover_btn", lang))
          }
        }
        if (!info.playlistId.isNullOrBlank() && "v=" in state.url) {
          OutlinedButton(
            onClick = { vm.togglePlaylistMode(!info.isPlaylist) },
          ) {
            Icon(AppIcons.Download, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (info.isPlaylist) S.t("load_single_btn", lang) else S.t("load_playlist_btn", lang))
          }
        }
        OutlinedButton(
          onClick = {
            val nameToCopy = state.customTitle.trim().ifEmpty { info.title }
            copyTextToClipboard(context, "Video Name", nameToCopy, S.t("copied_name_toast", lang))
          },
        ) {
          Icon(AppIcons.Copy, null, Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(S.t("copy_name_btn", lang))
        }
        OutlinedButton(
          onClick = {
            copyTextToClipboard(context, "Video Link", info.url, S.t("copied_link_toast", lang))
          },
        ) {
          Icon(AppIcons.Link, null, Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(S.t("copy_link_btn", lang))
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsCard(state: HomeState, vm: MainViewModel, lang: String) {
  val info = state.info ?: return
  Card {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = state.type == MediaType.VIDEO,
          onClick = { vm.setType(MediaType.VIDEO) },
          shape = SegmentedButtonDefaults.itemShape(0, 2),
          icon = { Icon(AppIcons.Movie, null, Modifier.size(18.dp)) },
        ) { Text(S.t("video_tab", lang)) }
        SegmentedButton(
          selected = state.type == MediaType.AUDIO,
          onClick = { vm.setType(MediaType.AUDIO) },
          shape = SegmentedButtonDefaults.itemShape(1, 2),
          icon = { Icon(AppIcons.MusicNote, null, Modifier.size(18.dp)) },
        ) { Text(S.t("audio_tab", lang)) }
      }

      if (state.type == MediaType.VIDEO) {
        Text(S.t("quality_label", lang), style = MaterialTheme.typography.labelLarge)
        val heights = info.videoHeights.ifEmpty { listOf(2160, 1440, 1080, 720, 480, 360) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = state.height == null, onClick = { vm.setHeight(null) }, label = { Text("Best MP4") })
          heights.forEach { h ->
            FilterChip(
              selected = state.height == h,
              onClick = { vm.setHeight(h) },
              label = { Text(if (h >= 2160) "${h}p (4K)" else "${h}p") },
            )
          }
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(S.t("audio_format_label", lang), style = MaterialTheme.typography.labelLarge)
          if (info.isAudioCentric) {
            Text(
              "🎵 Best Audio Mode",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AudioFormat.entries.forEach { f ->
            val desc = when (f) {
              AudioFormat.MP3 -> S.t("mp3_desc", lang)
              AudioFormat.M4A -> S.t("m4a_desc", lang)
              AudioFormat.OPUS -> S.t("opus_desc", lang)
            }
            FilterChip(
              selected = state.audioFormat == f,
              onClick = { vm.setAudioFormat(f) },
              label = { Text(desc) },
            )
          }
        }
        FilterChip(
          selected = state.embedCoverArt,
          onClick = { vm.setEmbedCoverArt(!state.embedCoverArt) },
          label = { Text(S.t("embed_cover_label", lang)) },
        )
      }
    }
  }
}

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier, corner: Int = 8) {
  AsyncImage(
    model = url,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = modifier
      .aspectRatio(16f / 9f)
      .clip(RoundedCornerShape(corner.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant),
  )
}

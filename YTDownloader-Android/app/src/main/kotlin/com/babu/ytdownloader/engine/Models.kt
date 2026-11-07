package com.babu.ytdownloader.engine

import kotlinx.serialization.Serializable

/** What the user wants to download. */
@Serializable
enum class MediaType { VIDEO, AUDIO }

@Serializable
enum class AudioFormat(val ext: String, val label: String) {
  MP3("mp3", "MP3 (320kbps)"),
  M4A("m4a", "M4A (AAC Best)"),
  OPUS("opus", "OPUS (Hi-Fi)"),
}

@Serializable
data class DownloadOptions(
  val type: MediaType,
  /** Max video resolution (smallest side). null = best available. Ignored for audio. */
  val videoHeight: Int? = null,
  val audioFormat: AudioFormat = AudioFormat.MP3,
  /** Optional custom title / filename chosen by the user before or after queuing. */
  val customTitle: String? = null,
  /** Embed Music Cover Art & ID3 metadata into downloaded audio files (and save companion cover). */
  val embedCoverArt: Boolean = true,
  /** High-res thumbnail / music cover art URL for embedding. */
  val thumbnailUrl: String? = null,
  /** Artist / Uploader name for audio metadata. */
  val artistName: String? = null,
  /** Album / Playlist name for audio metadata. */
  val albumName: String? = null,
) {
  fun qualityLabel(): String = when (type) {
    MediaType.VIDEO -> videoHeight?.let { "${it}p" } ?: "Best"
    MediaType.AUDIO -> audioFormat.label
  }
}

/** Metadata returned by the engine for a link (single video/audio OR playlist). */
data class MediaInfo(
  val url: String,
  val title: String,
  val uploader: String?,
  val thumbnail: String?,
  val durationSec: Long?,
  val isPlaylist: Boolean,
  val entries: List<PlaylistEntry> = emptyList(),
  /** Available video resolutions (e.g. 2160, 1080, 720...) – single videos only. */
  val videoHeights: List<Int> = emptyList(),
  /** True when the link is from YouTube Music, SoundCloud, Bandcamp, or a direct audio link. */
  val isAudioCentric: Boolean = false,
  /** Non-null if the URL contains a YouTube / YT Music playlist or mix ID (`list=...`). */
  val playlistId: String? = null,
)

data class PlaylistEntry(
  val id: String?,
  val title: String,
  val url: String,
  val durationSec: Long?,
  val thumbnail: String?,
  val uploader: String?,
)

package com.babu.ytdownloader

import com.babu.ytdownloader.engine.NewPipeEngine
import com.babu.ytdownloader.engine.SocialMediaEngine
import com.babu.ytdownloader.ui.extractAllUrls
import com.babu.ytdownloader.ui.extractUrl
import com.babu.ytdownloader.ui.isSupportedSocialUrl
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QaReliabilityTest {

  @Test
  fun testSlow3gDropAndHttp206ByteRangeResume() {
    val payload = ByteArray(1024) { (it % 251).toByte() }
    val callCount = AtomicInteger(0)

    val flaky3gInterceptor = Interceptor { chain ->
      val req = chain.request()
      val attempt = callCount.incrementAndGet()
      val rangeHeader = req.header("Range")

      if (attempt == 1) {
        // Simulate a 3G drop exactly halfway through (at 512 bytes)
        val failingSource = object : Source {
          var sent = 0
          override fun read(sink: Buffer, byteCount: Long): Long {
            if (sent >= 512) {
              throw SocketTimeoutException("Simulated 3G connection drop at 512 bytes")
            }
            val toWrite = minOf(byteCount.toInt(), 512 - sent)
            sink.write(payload, sent, toWrite)
            sent += toWrite
            return toWrite.toLong()
          }
          override fun timeout() = okio.Timeout.NONE
          override fun close() {}
        }
        val body = object : okhttp3.ResponseBody() {
          override fun contentType() = "video/mp4".toMediaType()
          override fun contentLength() = payload.size.toLong()
          override fun source(): BufferedSource = failingSource.buffer()
        }
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(body)
          .build()
      } else {
        // Attempt 2: Verify client sent Range: bytes=512- and return 206 Partial Content
        assertEquals("bytes=512-", rangeHeader)
        val remaining = payload.copyOfRange(512, payload.size)
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(206)
          .message("Partial Content")
          .header("Content-Range", "bytes 512-${payload.size - 1}/${payload.size}")
          .body(remaining.toResponseBody("video/mp4".toMediaType()))
          .build()
      }
    }

    val client = OkHttpClient.Builder().addInterceptor(flaky3gInterceptor).build()
    val tmpFile = File.createTempFile("qa_resume_206_", ".mp4")
    tmpFile.delete()
    try {
      SocialMediaEngine.downloadFileWithProgress(
        processId = "test-3g-206",
        streamUrl = "https://cdn.example.com/video.mp4",
        refererUrl = "https://www.pornhub.com/view_video.php?viewkey=66293eedd8944",
        dest = tmpFile,
        onProgress = { _, _, _ -> },
        clientOverride = client,
      )
      assertEquals(2, callCount.get())
      assertArrayEquals(payload, tmpFile.readBytes())
    } finally {
      tmpFile.delete()
    }
  }

  @Test
  fun testHttp200FallbackWhenServerIgnoresRangeDoesNotCorruptFile() {
    val payload = "VALID_MP4_HEADER_AND_BODY_CONTENT_1234567890".toByteArray()
    val tmpFile = File.createTempFile("qa_resume_200_", ".mp4")
    // Pre-populate partial bytes as if paused earlier
    tmpFile.writeBytes("PARTIAL_OLD_BYTES".toByteArray())

    val nonRangeServer = Interceptor { chain ->
      val req = chain.request()
      // Server ignores Range header and returns 200 OK with full file from byte 0
      Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(payload.toResponseBody("video/mp4".toMediaType()))
        .build()
    }

    val client = OkHttpClient.Builder().addInterceptor(nonRangeServer).build()
    try {
      SocialMediaEngine.downloadFileWithProgress(
        processId = "test-200-fallback",
        streamUrl = "https://cdn.example.com/non_range.mp4",
        refererUrl = "https://www.facebook.com/",
        dest = tmpFile,
        onProgress = { _, _, _ -> },
        clientOverride = client,
      )
      // Must overwrite from byte 0, NOT append onto "PARTIAL_OLD_BYTES"!
      assertArrayEquals(payload, tmpFile.readBytes())
    } finally {
      tmpFile.delete()
    }
  }

  @Test
  fun testPornhubFlashvarsAndGetMediaQualityExtraction() {
    val sampleHtml = """
      <html><head><meta property="og:title" content="Sample Title - Pornhub.com" /></head>
      <body>
      <script>
      var flashvars_451531011 = {"video_title":"Sample PH Video &amp; Clip","image_url":"https:\/\/ei.phncdn.com\/thumb.jpg","video_duration":"224","mediaDefinitions":[{"defaultQuality":false,"format":"hls","videoUrl":"https:\/\/hv-h.phncdn.com\/hls\/1080P.m3u8","quality":1080},{"defaultQuality":true,"format":"mp4","videoUrl":"https:\/\/www.pornhub.com\/media\/mp4?s=abc","quality":[240,480,720,1080]}]};
      </script>
      </body></html>
    """.trimIndent()

    val sampleMediaJson = """
      [
        {"height":240,"format":"mp4","videoUrl":"https:\/\/ev.phncdn.com\/240p.mp4","quality":"240"},
        {"height":480,"format":"mp4","videoUrl":"https:\/\/ev.phncdn.com\/480p.mp4","quality":"480"},
        {"height":720,"format":"mp4","videoUrl":"https:\/\/ev.phncdn.com\/720p.mp4","quality":"720"},
        {"height":1080,"format":"mp4","videoUrl":"https:\/\/ev.phncdn.com\/1080p.mp4","quality":"1080"}
      ]
    """.trimIndent()

    val extracted = SocialMediaEngine.parsePornhubHtml(
      pageUrl = "https://www.pornhub.com/view_video.php?viewkey=66293eedd8944",
      html = sampleHtml,
      fetchMediaJson = { sampleMediaJson },
    )

    assertEquals("Sample PH Video & Clip", extracted.title)
    assertEquals(224L, extracted.durationSec)
    assertEquals("https://ev.phncdn.com/1080p.mp4", SocialMediaEngine.selectVideoUrlForHeight(extracted, 1080))
    assertEquals("https://ev.phncdn.com/720p.mp4", SocialMediaEngine.selectVideoUrlForHeight(extracted, 720))
    assertEquals("https://ev.phncdn.com/480p.mp4", SocialMediaEngine.selectVideoUrlForHeight(extracted, 480))
    assertEquals("https://ev.phncdn.com/240p.mp4", SocialMediaEngine.selectVideoUrlForHeight(extracted, 240))
  }

  @Test
  fun testYouTubeUrlNormalizationAndSocialDetection() {
    // Radio mix (list=RD...) should normalize to single video watch URL
    assertEquals(
      "https://www.youtube.com/watch?v=0swQYKi98sU",
      NewPipeEngine.normalizeUrl("https://www.youtube.com/watch?v=0swQYKi98sU&list=RD0swQYKi98sU&start_radio=1"),
    )
    // Real playlist (list=PL...) should normalize to playlist URL
    assertEquals(
      "https://www.youtube.com/playlist?list=PL1234567890",
      NewPipeEngine.normalizeUrl("https://www.youtube.com/watch?v=0swQYKi98sU&list=PL1234567890"),
    )

    // Supported URLs including Pornhub, Threads, LinkedIn, X, Facebook, Instagram, TikTok
    assertTrue(isSupportedSocialUrl("https://www.pornhub.com/view_video.php?viewkey=66293eedd8944"))
    assertTrue(isSupportedSocialUrl("https://www.threads.net/@user/post/C12345"))
    assertTrue(isSupportedSocialUrl("https://www.linkedin.com/posts/activity-12345"))
    assertTrue(isSupportedSocialUrl("https://x.com/user/status/123456789"))
    assertFalse(isSupportedSocialUrl("https://example.com/not-a-video-host"))

    // Extract URL from messy shared text
    assertEquals(
      "https://www.pornhub.com/view_video.php?viewkey=66293eedd8944",
      extractUrl("Watch this video: https://www.pornhub.com/view_video.php?viewkey=66293eedd8944)."),
    )
  }

  @Test
  fun testV21SaveFailedLinkAndRenameTask() {
    val failedTask = com.babu.ytdownloader.download.DownloadManager.saveFailedLink(
      url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      title = "Initial Failed Video",
      errorMsg = "Simulated network timeout",
    )
    assertEquals(com.babu.ytdownloader.download.TaskStatus.FAILED, failedTask.status)
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", failedTask.url)

    val renamed = com.babu.ytdownloader.download.DownloadManager.renameTask(failedTask.id, "My Custom Video Name v2.1")
    assertTrue(renamed)
    val updated = com.babu.ytdownloader.download.DownloadManager.tasks.value.first { it.id == failedTask.id }
    assertEquals("My Custom Video Name v2.1", updated.title)
    assertEquals("My Custom Video Name v2.1", updated.options.customTitle)
    com.babu.ytdownloader.download.DownloadManager.remove(failedTask.id)
  }

  @Test
  fun testUniversalAnySiteAndAdultSitesExtraction() {
    // 1. XVideos / XNXX html5player + HLS Master qualities
    val xvHtml = """
      <script>
        html5player.setVideoTitle('Sample XVideos HD Clip');
        html5player.setVideoUrlLow('https://cdn.xvideos.com/low_360p.mp4');
        html5player.setVideoUrlHigh('https://cdn.xvideos.com/high_720p.mp4');
        html5player.setVideoHLS('https://cdn.xvideos.com/hls/master.m3u8');
        html5player.setThumbUrl169('https://cdn.xvideos.com/thumb169.jpg');
      </script>
    """.trimIndent()
    val xvMasterM3u8 = """
      #EXTM3U
      #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
      hls-1080p.m3u8
      #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
      hls-480p.m3u8
    """.trimIndent()
    val xvExtracted = SocialMediaEngine.parseXvideosOrXnxxHtml(
      pageUrl = "https://www.xvideos.com/video12345/sample_clip",
      html = xvHtml,
      fetchHlsMaster = { xvMasterM3u8 },
    )
    assertEquals("Sample XVideos HD Clip", xvExtracted.title)
    assertEquals("https://cdn.xvideos.com/hls/hls-1080p.m3u8", SocialMediaEngine.selectVideoUrlForHeight(xvExtracted, 1080))
    assertEquals("https://cdn.xvideos.com/high_720p.mp4", SocialMediaEngine.selectVideoUrlForHeight(xvExtracted, 720))
    assertEquals("https://cdn.xvideos.com/hls/hls-480p.m3u8", SocialMediaEngine.selectVideoUrlForHeight(xvExtracted, 480))
    assertEquals("https://cdn.xvideos.com/low_360p.mp4", SocialMediaEngine.selectVideoUrlForHeight(xvExtracted, 360))

    // 2. xHamster multi-quality JSON
    val xhHtml = """
      <meta property="og:title" content="Sample xHamster Video" />
      <script>
        window.initials = {"sources":{"mp4":{"1080p":"https:\/\/cdn.xhamster.com\/1080p.mp4","480p":"https:\/\/cdn.xhamster.com\/480p.mp4"}}};
      </script>
    """.trimIndent()
    val xhExtracted = SocialMediaEngine.parseXHamsterHtml("https://xhamster.com/videos/sample-123", xhHtml)
    assertEquals("Sample xHamster Video", xhExtracted.title)
    assertEquals("https://cdn.xhamster.com/1080p.mp4", SocialMediaEngine.selectVideoUrlForHeight(xhExtracted, 1080))
    assertEquals("https://cdn.xhamster.com/480p.mp4", SocialMediaEngine.selectVideoUrlForHeight(xhExtracted, 480))

    // 3. SpankBang stream_data
    val sbHtml = """
      <meta property="og:title" content="Sample SpankBang 4K" />
      <script>
        var stream_data = {'1080p': ['https://v.spankbang.com/1080p.mp4'], '720p': ['https://v.spankbang.com/720p.mp4']};
      </script>
    """.trimIndent()
    val sbExtracted = SocialMediaEngine.parseSpankBangHtml("https://spankbang.com/abc/video/sample", sbHtml)
    assertEquals("Sample SpankBang 4K", sbExtracted.title)
    assertEquals("https://v.spankbang.com/1080p.mp4", SocialMediaEngine.selectVideoUrlForHeight(sbExtracted, 1080))
    assertEquals("https://v.spankbang.com/720p.mp4", SocialMediaEngine.selectVideoUrlForHeight(sbExtracted, 720))

    // 4. Universal Any-Website HTML5 <video>/<source> + relative URL resolution
    val genericSiteHtml = """
      <html>
        <head><title>Custom News &amp; Media Portal</title></head>
        <body>
          <video controls poster="/img/poster.jpg">
            <source src="/media/clip_1080p.mp4" res="1080" type="video/mp4" />
            <source src="/media/clip_480p.mp4" res="480" type="video/mp4" />
          </video>
        </body>
      </html>
    """.trimIndent()
    val genericExtracted = SocialMediaEngine.parseUniversalWebHtml(
      pageUrl = "https://portal.example.org/articles/featured-story",
      html = genericSiteHtml,
      defaultPlatform = "Example",
    )
    assertEquals("Custom News & Media Portal", genericExtracted.title)
    assertEquals("https://portal.example.org/media/clip_1080p.mp4", SocialMediaEngine.selectVideoUrlForHeight(genericExtracted, 1080))
    assertEquals("https://portal.example.org/media/clip_480p.mp4", SocialMediaEngine.selectVideoUrlForHeight(genericExtracted, 480))
  }

  @Test
  fun testLiveNetworkExtractionAcrossPlatforms() {
    // 1. Live YouTube fetchInfo
    val ytInfo = NewPipeEngine.fetchInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    assertNotNull(ytInfo)
    assertTrue(ytInfo.title.isNotBlank())
    assertTrue(ytInfo.videoHeights.isNotEmpty())

    // 2. Live Facebook fetchInfo
    val fbInfo = SocialMediaEngine.fetchInfo("https://m.facebook.com/watch/?v=2342362179944157&_rdr")
    assertNotNull(fbInfo)
    assertTrue(fbInfo.videoHeights.isNotEmpty())

    // 3. Live Instagram Reel fetchInfo
    val igInfo = SocialMediaEngine.fetchInfo("https://www.instagram.com/reel/DfHWdvjxDg4/?exln=Z3dlMWVicWg4NGZr")
    assertNotNull(igInfo)
    assertTrue(igInfo.videoHeights.isNotEmpty())

    // 4. Live Pornhub fetchInfo (tolerates ISP-level TCP blocks via ConnectException while verifying parser when reachable)
    runCatching {
      val phInfo = SocialMediaEngine.fetchInfo("https://www.pornhub.com/view_video.php?viewkey=6aeca4909ef3b")
      assertNotNull(phInfo)
      assertTrue(phInfo.title.isNotBlank())
      assertTrue(phInfo.videoHeights.contains(1080) || phInfo.videoHeights.contains(720))
    }.onFailure { e ->
      if (e !is java.net.ConnectException && e !is java.net.SocketTimeoutException && e.cause !is java.net.ConnectException) {
        throw e
      }
    }

    // 5. Bare domain & case-insensitive URL extraction
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", extractUrl("HTTPS://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals("https://www.pornhub.com/view_video.php?viewkey=6aeca4909ef3b", extractUrl("www.pornhub.com/view_video.php?viewkey=6aeca4909ef3b"))
  }

  @Test
  fun testV23YouTubeMusicAndBestAudioExtraction() {
    // 1. YouTube Music URL detection & normalization
    val ytmUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVMdQw4w9WgXcQ"
    assertTrue(NewPipeEngine.isYtMusicUrl(ytmUrl))
    assertTrue(com.babu.ytdownloader.ui.isAudioCentricUrl(ytmUrl))
    assertTrue(isSupportedSocialUrl(ytmUrl))
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", NewPipeEngine.normalizeUrl(ytmUrl))

    // Live YouTube Music fetchInfo sets isAudioCentric = true
    val ytmInfo = NewPipeEngine.fetchInfo(ytmUrl)
    assertTrue(ytmInfo.isAudioCentric)
    assertTrue(ytmInfo.title.isNotBlank())

    // 2. Highest-bitrate audio selection across codecs (MP3/Best vs M4A vs OPUS)
    val sampleStreams = listOf(
      NewPipeEngine.YtStream(
        itag = 139, url = "https://yt.example/139.m4a", mimeType = "audio/mp4; codecs=\"mp4a.40.5\"",
        height = 0, bitrate = 48_000, contentLength = 1000L, isVideo = false, isAudio = true, isMp4 = false, isM4a = true, userAgent = "UA",
      ),
      NewPipeEngine.YtStream(
        itag = 140, url = "https://yt.example/140.m4a", mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
        height = 0, bitrate = 128_000, contentLength = 2000L, isVideo = false, isAudio = true, isMp4 = false, isM4a = true, userAgent = "UA",
      ),
      NewPipeEngine.YtStream(
        itag = 251, url = "https://yt.example/251.webm", mimeType = "audio/webm; codecs=\"opus\"",
        height = 0, bitrate = 160_000, contentLength = 2500L, isVideo = false, isAudio = true, isMp4 = false, isM4a = false, userAgent = "UA",
      ),
    )
    // MP3 (Best Audio) and OPUS should pick itag 251 (160kbps Opus) over 128kbps M4A
    assertEquals(251, NewPipeEngine.pickBestAudio(sampleStreams, com.babu.ytdownloader.engine.AudioFormat.MP3)?.itag)
    assertEquals(251, NewPipeEngine.pickBestAudio(sampleStreams, com.babu.ytdownloader.engine.AudioFormat.OPUS)?.itag)
    // M4A should pick itag 140 (128kbps AAC/M4A)
    assertEquals(140, NewPipeEngine.pickBestAudio(sampleStreams, com.babu.ytdownloader.engine.AudioFormat.M4A)?.itag)

    // 3. SoundCloud & Bandcamp & Universal HTML5 <audio> extraction
    val scHtml = """
      <meta property="og:title" content="Synthwave Midnight Drive" />
      <meta property="og:image" content="https://i1.sndcdn.com/artworks-synth.jpg" />
      <script>
        window.__sc_hydration = [{"hydratable":"sound","data":{"duration":195000,"media":{"transcodings":[{"url":"https://api-v2.soundcloud.com/media/soundcloud:tracks:999/stream/progressive"}]}}}];
      </script>
    """.trimIndent()
    val scExtracted = SocialMediaEngine.parseSoundCloudHtml(
      pageUrl = "https://soundcloud.com/artist/synthwave-midnight-drive",
      html = scHtml,
      resolveTranscoding = { "https://cf-media.sndcdn.com/synthwave_320.mp3" },
    )
    assertEquals("Synthwave Midnight Drive", scExtracted.title)
    assertEquals("https://cf-media.sndcdn.com/synthwave_320.mp3", scExtracted.audioUrl)
    assertEquals(195L, scExtracted.durationSec)

    val bcHtml = """
      <meta property="og:title" content="Lo-Fi Rain, by Chill Artist" />
      <script>
        var TralbumData = {"trackinfo":[{"title":"Lo-Fi Rain","duration":142.5,"file":{"mp3-128":"https://t4.bcbits.com/stream/abc/mp3-128/12345"}}]};
      </script>
    """.trimIndent()
    val bcExtracted = SocialMediaEngine.parseBandcampHtml("https://chill.bandcamp.com/track/lo-fi-rain", bcHtml)
    assertEquals("Lo-Fi Rain, by Chill Artist", bcExtracted.title)
    assertEquals("https://t4.bcbits.com/stream/abc/mp3-128/12345", bcExtracted.audioUrl)
    assertEquals(142L, bcExtracted.durationSec)

    val html5AudioPage = """
      <html>
        <head><title>Podcast Episode 42</title></head>
        <body>
          <audio controls src="/episodes/ep42_master.mp3"></audio>
        </body>
      </html>
    """.trimIndent()
    val audioPageExtracted = SocialMediaEngine.parseUniversalWebHtml(
      pageUrl = "https://podcast.example.com/listen/42",
      html = html5AudioPage,
      defaultPlatform = "Podcast",
    )
    assertEquals("Podcast Episode 42", audioPageExtracted.title)
    assertEquals("https://podcast.example.com/episodes/ep42_master.mp3", audioPageExtracted.audioUrl)
  }

  @Test
  fun testV24PlaylistCapabilitiesAndMusicCoverEmbedding() {
    // 1. Playlist ID extraction & preferPlaylist normalization
    val mixWatchUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVMdQw4w9WgXcQ"
    assertEquals("RDAMVMdQw4w9WgXcQ", NewPipeEngine.extractPlaylistId(mixWatchUrl))
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", NewPipeEngine.normalizeUrl(mixWatchUrl, preferPlaylist = false))
    assertEquals("https://www.youtube.com/playlist?list=RDAMVMdQw4w9WgXcQ", NewPipeEngine.normalizeUrl(mixWatchUrl, preferPlaylist = true))

    val albumBrowseUrl = "https://music.youtube.com/browse/MPREb_1234567890"
    assertEquals("MPREb_1234567890", NewPipeEngine.extractPlaylistId(albumBrowseUrl))
    assertEquals("https://music.youtube.com/browse/MPREb_1234567890", NewPipeEngine.normalizeUrl(albumBrowseUrl))

    // 2. Innertube Playlist / Mix JSON parsing
    val sampleInnertubeJson = """
      {
        "metadata": {
          "playlistMetadataRenderer": {
            "title": "Synthwave Classics Mix"
          }
        },
        "contents": {
          "twoColumnWatchNextResults": {
            "playlist": {
              "playlist": {
                "playlistPanelRenderer": {
                  "title": "Synthwave Classics Mix",
                  "contents": [
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": { "simpleText": "Track One - Nightcall" },
                        "shortBylineText": { "runs": [{ "text": "Kavinsky - Topic" }] },
                        "lengthText": { "simpleText": "4:18" }
                      }
                    },
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "0swQYKi98sU",
                        "title": { "runs": [{ "text": "Track Two - Sunset" }] },
                        "shortBylineText": { "simpleText": "The Midnight" },
                        "lengthText": { "simpleText": "5:02" }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
      }
    """.trimIndent()
    val (meta, entries) = NewPipeEngine.parseInnertubePlaylistJson(sampleInnertubeJson, ytMusic = true)
    assertEquals("Synthwave Classics Mix", meta.first)
    assertEquals(2, entries.size)
    assertEquals("Track One - Nightcall", entries[0].title)
    assertEquals("Kavinsky", entries[0].uploader)
    assertEquals(258L, entries[0].durationSec)
    assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", entries[0].url)
    assertEquals("Track Two - Sunset", entries[1].title)
    assertEquals(302L, entries[1].durationSec)

    // 3. Bandcamp multi-track Album playlist parsing
    val bcAlbumHtml = """
      <meta property="og:title" content="Midnight Odyssey, by Neon Horizon" />
      <meta property="og:image" content="https://f4.bcbits.com/img/a12345_10.jpg" />
      <script>
        var TralbumData = {
          "trackinfo": [
            {"title":"01. Starlight","duration":185.0,"title_link":"/track/starlight","file":{"mp3-128":"https://t4.bcbits.com/stream/t1.mp3"}},
            {"title":"02. Hyperdrive","duration":210.4,"title_link":"/track/hyperdrive","file":{"mp3-128":"https://t4.bcbits.com/stream/t2.mp3"}}
          ]
        };
      </script>
    """.trimIndent()
    val bcPlaylist = SocialMediaEngine.parseBandcampPlaylistHtml("https://neonhorizon.bandcamp.com/album/midnight-odyssey", bcAlbumHtml)
    assertNotNull(bcPlaylist)
    assertTrue(bcPlaylist!!.isPlaylist)
    assertEquals(2, bcPlaylist.entries.size)
    assertEquals("01. Starlight", bcPlaylist.entries[0].title)
    assertEquals("https://neonhorizon.bandcamp.com/track/starlight", bcPlaylist.entries[0].url)
    assertEquals(185L, bcPlaylist.entries[0].durationSec)
    assertEquals("02. Hyperdrive", bcPlaylist.entries[1].title)

    // 4. ID3v2.3 Music Cover Art (`APIC`) & Metadata (`TIT2`, `TPE1`, `TALB`) embedding + re-tagging idempotency
    val dummyMp3Payload = "FAKE_MPEG_AUDIO_FRAMES_DATA_0123456789".toByteArray()
    val fakeJpegCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())
    val tmpMp3 = File.createTempFile("qa_id3_cover_", ".mp3")
    try {
      tmpMp3.writeBytes(dummyMp3Payload)
      val ok1 = NewPipeEngine.embedId3v2CoverAndMetadata(
        audioFile = tmpMp3,
        title = "Nightcall",
        artist = "Kavinsky",
        album = "OutRun",
        coverBytes = fakeJpegCover,
        coverMime = "image/jpeg",
      )
      assertTrue(ok1)
      val taggedBytes1 = tmpMp3.readBytes()
      // Verify "ID3" magic header and v2.3 version byte (0x03)
      assertEquals('I'.code.toByte(), taggedBytes1[0])
      assertEquals('D'.code.toByte(), taggedBytes1[1])
      assertEquals('3'.code.toByte(), taggedBytes1[2])
      assertEquals(0x03.toByte(), taggedBytes1[3])
      val asciiView = String(taggedBytes1, Charsets.ISO_8859_1)
      assertTrue(asciiView.contains("TIT2"))
      assertTrue(asciiView.contains("TPE1"))
      assertTrue(asciiView.contains("TALB"))
      assertTrue(asciiView.contains("APIC"))
      assertTrue(asciiView.contains("image/jpeg"))
      // Verify original audio payload is intact at the end of the file
      val tail1 = taggedBytes1.copyOfRange(taggedBytes1.size - dummyMp3Payload.size, taggedBytes1.size)
      assertArrayEquals(dummyMp3Payload, tail1)

      // Re-tagging the same file should replace the old ID3 header, not duplicate it!
      val sizeAfterFirstTag = taggedBytes1.size
      val ok2 = NewPipeEngine.embedId3v2CoverAndMetadata(
        audioFile = tmpMp3,
        title = "Nightcall",
        artist = "Kavinsky",
        album = "OutRun",
        coverBytes = fakeJpegCover,
        coverMime = "image/jpeg",
      )
      assertTrue(ok2)
      assertEquals(sizeAfterFirstTag, tmpMp3.readBytes().size)
    } finally {
      tmpMp3.delete()
    }
  }

  @Test
  fun testV25SpotifyTrackBatchUrlsAndContainerSafeId3() {
    // 1. Multi-link Batch URL extraction
    val multiPaste = """
      Check out these tracks:
      1) https://music.youtube.com/watch?v=dQw4w9WgXcQ
      2) https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT
      3) https://soundcloud.com/artist/synthwave-midnight-drive,
      Duplicate ignored: https://music.youtube.com/watch?v=dQw4w9WgXcQ
    """.trimIndent()
    val batch = extractAllUrls(multiPaste)
    assertEquals(3, batch.size)
    assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", batch[0])
    assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", batch[1])
    assertEquals("https://soundcloud.com/artist/synthwave-midnight-drive", batch[2])

    // 2. Innertube Search VideoId JSON parsing + Spotify Track HTML + Official 1:1 Cover Art extraction
    val sampleSearchJson = """
      {
        "contents": {
          "twoColumnSearchResultsRenderer": {
            "primaryContents": {
              "sectionListRenderer": {
                "contents": [
                  {
                    "itemSectionRenderer": {
                      "contents": [
                        {
                          "videoRenderer": {
                            "videoId": "dQw4w9WgXcQ",
                            "title": { "runs": [{ "text": "Rick Astley - Never Gonna Give You Up (Official Audio)" }] }
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        }
      }
    """.trimIndent()
    assertEquals("dQw4w9WgXcQ", NewPipeEngine.parseInnertubeSearchVideoId(sampleSearchJson))

    val spotifyHtml = """
      <html>
        <head>
          <meta property="og:title" content="Blinding Lights" />
          <meta property="og:description" content="The Weeknd · Song · 2020" />
          <meta property="og:image" content="https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36" />
          <meta name="music:duration" content="200" />
        </head>
      </html>
    """.trimIndent()
    val spExtracted = SocialMediaEngine.parseSpotifyTrackHtml(
      pageUrl = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
      html = spotifyHtml,
      resolveYouTubeId = { query ->
        assertEquals("The Weeknd - Blinding Lights audio", query)
        "4NRXx6U8ABQ"
      },
    )
    assertEquals("The Weeknd - Blinding Lights", spExtracted.title)
    assertEquals("The Weeknd", spExtracted.uploader)
    assertEquals("https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36", spExtracted.thumbnail)
    assertEquals(200L, spExtracted.durationSec)
    assertEquals("https://www.youtube.com/watch?v=4NRXx6U8ABQ", spExtracted.audioUrl)

    // 3. Container-safe ID3v2 guard: never shift byte-0 offsets of raw WebM (0x1A45DFA3) or un-demuxed MP4 (ftyp),
    // while tagging ADTS AAC (0xFF 0xF1) streams cleanly.
    val webmBytes = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 1, 2, 3, 4, 5, 6)
    val tmpWebm = File.createTempFile("qa_webm_guard_", ".webm")
    val adtsBytes = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x02, 0x1F, 0xFC.toByte(), 0xAA.toByte(), 0xBB.toByte())
    val tmpAdts = File.createTempFile("qa_adts_tag_", ".mp3")
    try {
      tmpWebm.writeBytes(webmBytes)
      assertTrue(NewPipeEngine.isWebmOrOggContainer(tmpWebm))
      assertFalse(
        NewPipeEngine.embedId3v2CoverAndMetadata(
          audioFile = tmpWebm,
          title = "WebM Track",
          artist = "Artist",
          album = "Album",
          coverBytes = byteArrayOf(1, 2, 3),
        )
      )
      // WebM byte 0 must remain untouched
      assertArrayEquals(webmBytes, tmpWebm.readBytes())

      tmpAdts.writeBytes(adtsBytes)
      assertFalse(NewPipeEngine.isMp4Container(tmpAdts))
      assertFalse(NewPipeEngine.isWebmOrOggContainer(tmpAdts))
      assertTrue(
        NewPipeEngine.embedId3v2CoverAndMetadata(
          audioFile = tmpAdts,
          title = "ADTS Track",
          artist = "Artist",
          album = "Album",
          coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
        )
      )
      val taggedAdts = tmpAdts.readBytes()
      assertEquals('I'.code.toByte(), taggedAdts[0])
      assertEquals('D'.code.toByte(), taggedAdts[1])
      assertEquals('3'.code.toByte(), taggedAdts[2])
    } finally {
      tmpWebm.delete()
      tmpAdts.delete()
    }
  }
}

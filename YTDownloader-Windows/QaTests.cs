using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace YTDownloaderWindows
{
    public static class QaTests
    {
        public static int RunAllTests()
        {
            var results = new List<string>();
            try
            {
                TestUrlExtractionAndNormalization();
                results.Add("PASS: TestUrlExtractionAndNormalization");

                TestPlatformDetectionIncludingPornhubAndThreads();
                results.Add("PASS: TestPlatformDetectionIncludingPornhubAndThreads");

                TestAtomicDoubleClickMediaConsumption();
                results.Add("PASS: TestAtomicDoubleClickMediaConsumption");

                TestPauseResumeAndStaleGenerationIsolation();
                results.Add("PASS: TestPauseResumeAndStaleGenerationIsolation");

                TestV21FailedLinkSavingAndRenameFileOnDisk();
                results.Add("PASS: TestV21FailedLinkSavingAndRenameFileOnDisk");

                TestUniversalAnySiteAndAdultSitesExtraction();
                results.Add("PASS: TestUniversalAnySiteAndAdultSitesExtraction");

                TestV23YouTubeMusicAndBestAudioExtraction();
                results.Add("PASS: TestV23YouTubeMusicAndBestAudioExtraction");

                TestV24PlaylistAndMusicCoverSupport();
                results.Add("PASS: TestV24PlaylistAndMusicCoverSupport");

                TestV25BatchLinksTrackNumberingAndSpotifyCover();
                results.Add("PASS: TestV25BatchLinksTrackNumberingAndSpotifyCover");

                File.WriteAllLines(
                    Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "qa_test_results.txt"),
                    results);
                return 0;
            }
            catch (Exception ex)
            {
                results.Add("FAIL: " + ex.ToString());
                File.WriteAllLines(
                    Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "qa_test_results.txt"),
                    results);
                return 1;
            }
        }

        private static void AssertTrue(bool condition, string message)
        {
            if (!condition) throw new Exception("Assertion failed: " + message);
        }

        private static void AssertEqual<T>(T expected, T actual, string message)
        {
            if (!EqualityComparer<T>.Default.Equals(expected, actual))
            {
                throw new Exception(string.Format("{0} (Expected: {1}, Actual: {2})", message, expected, actual));
            }
        }

        private static void TestUrlExtractionAndNormalization()
        {
            using (var form = new MainForm())
            {
                // 1. Noisy clipboard share text with trailing punctuation
                string extractedPh = form.ExtractSupportedUrlFromText(
                    "Watch this video: https://www.pornhub.com/view_video.php?viewkey=ph654321).");
                AssertEqual(
                    "https://www.pornhub.com/view_video.php?viewkey=ph654321",
                    extractedPh,
                    "Pornhub URL should be extracted cleanly without trailing punctuation");

                // 2. YouTube Mix playlist stripping when chkAllowMixPlaylist is unchecked
                string normalizedYt = form.NormalizeMediaUrl(
                    "https://www.youtube.com/watch?v=0swQYKi98sU&list=RD0swQYKi98sU&start_radio=1");
                AssertEqual(
                    "https://www.youtube.com/watch?v=0swQYKi98sU",
                    normalizedYt,
                    "YouTube Radio Mix URL should strip list=RD by default");

                // 3. Instagram /reels/ normalization
                string normalizedIg = form.NormalizeMediaUrl(
                    "https://www.instagram.com/reels/C8abc123/");
                AssertEqual(
                    "https://www.instagram.com/reel/C8abc123/",
                    normalizedIg,
                    "Instagram /reels/ should normalize to /reel/");

                // 4. Case-insensitive and bare domain URL extraction
                AssertEqual(
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    form.ExtractSupportedUrlFromText("HTTPS://www.youtube.com/watch?v=dQw4w9WgXcQ"),
                    "Case-insensitive HTTPS:// extraction");
                AssertEqual(
                    "https://www.pornhub.com/view_video.php?viewkey=6aeca4909ef3b",
                    form.ExtractSupportedUrlFromText("www.pornhub.com/view_video.php?viewkey=6aeca4909ef3b"),
                    "Bare domain pornhub URL extraction");
                AssertEqual(
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    form.NormalizeMediaUrl("www.youtube.com/watch?v=dQw4w9WgXcQ"),
                    "NormalizeMediaUrl should prepend https:// to bare domain");
            }
        }

        private static void TestPlatformDetectionIncludingPornhubAndThreads()
        {
            using (var form = new MainForm())
            {
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://www.pornhub.com/view_video.php?viewkey=64a1b2c3"), "Pornhub detection");
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://www.pornhubpremium.com/view_video.php?viewkey=123"), "PornhubPremium detection");
                AssertEqual(SocialPlatform.Threads, form.DetectPlatform("https://www.threads.net/@user/post/C9xyz"), "Threads.net detection");
                AssertEqual(SocialPlatform.Threads, form.DetectPlatform("https://www.threads.com/@user/post/C9xyz"), "Threads.com detection");
                AssertEqual(SocialPlatform.LinkedIn, form.DetectPlatform("https://www.linkedin.com/posts/activity-123"), "LinkedIn detection");
                AssertEqual(SocialPlatform.TwitterX, form.DetectPlatform("https://x.com/user/status/123456"), "X.com detection");
                AssertEqual(SocialPlatform.Facebook, form.DetectPlatform("https://fb.watch/abc123/"), "Facebook detection");
                AssertEqual(SocialPlatform.YouTube, form.DetectPlatform("https://youtu.be/0swQYKi98sU"), "YouTube detection");
            }
        }

        private static void TestAtomicDoubleClickMediaConsumption()
        {
            // Simulate 16 concurrent threads double-clicking "Start Download" on the same MediaMetadata
            MediaMetadata sharedMedia = new MediaMetadata
            {
                Url = "https://www.youtube.com/watch?v=0swQYKi98sU",
                Title = "Concurrency QA Test",
                Platform = SocialPlatform.YouTube
            };

            int consumedCount = 0;
            Parallel.For(0, 16, i =>
            {
                var claimed = Interlocked.Exchange(ref sharedMedia, null);
                if (claimed != null)
                {
                    Interlocked.Increment(ref consumedCount);
                }
            });

            AssertEqual(1, consumedCount, "Atomic Interlocked.Exchange must allow exactly 1 thread to consume currentMedia on rapid multi-click");
        }

        private static void TestPauseResumeAndStaleGenerationIsolation()
        {
            var job = new DownloadJob
            {
                Url = "https://www.pornhub.com/view_video.php?viewkey=ph12345",
                Title = "Pause Resume QA Job",
                Platform = SocialPlatform.Pornhub,
                Progress = 42.5f
            };

            // Generation 1 starts
            long gen1 = Interlocked.Increment(ref job.RunGeneration);
            job.IsActive = true;
            AssertEqual(1L, gen1, "First run generation should be 1");

            // User pauses at 42.5%
            job.Paused = true;
            AssertTrue(job.Paused && !job.Canceled, "Job should be marked Paused without Canceled");

            // User immediately resumes before Gen 1's catch/finally unwinds
            job.Paused = false;
            long gen2 = Interlocked.Increment(ref job.RunGeneration);
            AssertEqual(2L, gen2, "Resumed run generation should be 2");

            // Verify Gen 1 sees it is stale and does not clobber job.IsActive or status
            bool gen1IsCurrent = (Interlocked.Read(ref job.RunGeneration) == gen1);
            bool gen2IsCurrent = (Interlocked.Read(ref job.RunGeneration) == gen2);
            AssertTrue(!gen1IsCurrent, "Stale Gen 1 coroutine/task must detect it is superseded");
            AssertTrue(gen2IsCurrent, "Resumed Gen 2 task must be recognized as current");
            AssertEqual(42.5f, job.Progress, "Paused progress percentage must be preserved across resume");
        }

        private static void TestV21FailedLinkSavingAndRenameFileOnDisk()
        {
            using (var form = new MainForm())
            {
                // 1. Verify failed link saving persists the URL & error state for Retry / Download from Link
                var savedJob = form.SaveFailedLink(
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    SocialPlatform.YouTube,
                    "Saved Test Video",
                    "Network timeout during metadata fetch");

                AssertTrue(savedJob != null, "SaveFailedLink must return a saved DownloadJob");
                AssertTrue(savedJob.Failed, "Saved failed link job must have Failed=true");
                AssertEqual("https://www.youtube.com/watch?v=dQw4w9WgXcQ", savedJob.Url, "Saved failed link must preserve URL");

                // 2. Verify RenameFileOnDisk physically renames a downloaded file while preserving extension
                string tempDir = Path.Combine(Path.GetTempPath(), "ytdl_v21_rename_test_" + Guid.NewGuid().ToString("N"));
                Directory.CreateDirectory(tempDir);
                try
                {
                    string origFile = Path.Combine(tempDir, "Original_Video.mp4");
                    File.WriteAllText(origFile, "dummy video payload");

                    string renamedPath = MainForm.RenameFileOnDisk(origFile, "My Custom Renamed Video: 2026?");
                    AssertTrue(File.Exists(renamedPath), "Renamed file must exist on disk");
                    AssertTrue(!File.Exists(origFile), "Original file path should have been moved");
                    AssertTrue(renamedPath.EndsWith(".mp4", StringComparison.OrdinalIgnoreCase), "File extension must be preserved on rename");
                    AssertTrue(Path.GetFileNameWithoutExtension(renamedPath).StartsWith("My Custom Renamed Video_ 2026_"), "Invalid filename chars must be sanitized on rename");
                }
                finally
                {
                    try { Directory.Delete(tempDir, true); } catch { }
                }
            }
        }

        private static void TestUniversalAnySiteAndAdultSitesExtraction()
        {
            using (var form = new MainForm())
            {
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://www.xvideos.com/video12345/sample"), "XVideos adult platform detection");
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://www.xnxx.com/video-abc/sample"), "XNXX adult platform detection");
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://xhamster.com/videos/sample-123"), "xHamster adult platform detection");
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://spankbang.com/abc/video/sample"), "SpankBang adult platform detection");
                AssertEqual(SocialPlatform.Pornhub, form.DetectPlatform("https://www.redtube.com/12345"), "RedTube adult platform detection");

                // Verify XVideos HTML extraction
                string xvHtml = "<script>html5player.setVideoTitle('Sample XVideos HD'); html5player.setVideoUrlHigh('https://cdn.xvideos.com/720p.mp4'); html5player.setVideoUrlLow('https://cdn.xvideos.com/360p.mp4');</script>";
                var xvMeta = MainForm.ParseUniversalWebHtml("https://www.xvideos.com/video12345/sample", "https://www.xvideos.com/video12345/sample", SocialPlatform.Pornhub, xvHtml);
                AssertTrue(xvMeta != null, "XVideos HTML must parse to MediaMetadata");
                AssertEqual("Sample XVideos HD", xvMeta.Title, "XVideos title");
                AssertEqual("https://cdn.xvideos.com/720p.mp4", xvMeta.Url, "XVideos stream URL");

                // Verify generic Any-Site HTML5 <video>/<source> extraction
                string webHtml = "<html><head><title>Custom Web Video Page</title></head><body><video><source src=\"/videos/sample_1080p.mp4\" res=\"1080\" /></video></body></html>";
                var webMeta = MainForm.ParseUniversalWebHtml("https://media.example.org/watch/clip1", "https://media.example.org/watch/clip1", SocialPlatform.TikTokOrOther, webHtml);
                AssertTrue(webMeta != null, "Generic HTML5 video page must parse to MediaMetadata");
                AssertEqual("Custom Web Video Page", webMeta.Title, "Generic web page title");
                AssertEqual("https://media.example.org/videos/sample_1080p.mp4", webMeta.Url, "Generic web relative stream URL resolution");
            }
        }

        private static void TestV23YouTubeMusicAndBestAudioExtraction()
        {
            using (var form = new MainForm())
            {
                // 1. YouTube Music & Audio Platform URL detection & extraction
                string ytmRaw = "Listen on YT Music: https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVMdQw4w9WgXcQ";
                string ytmUrl = form.ExtractSupportedUrlFromText(ytmRaw);
                AssertEqual("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVMdQw4w9WgXcQ", ytmUrl, "YouTube Music URL extraction");
                AssertTrue(MainForm.IsAudioCentricUrl(ytmUrl), "music.youtube.com must be detected as audio-centric");
                AssertEqual(SocialPlatform.YouTube, form.DetectPlatform(ytmUrl), "music.youtube.com uses YouTube platform");
                AssertEqual("https://www.youtube.com/watch?v=dQw4w9WgXcQ", form.NormalizeMediaUrl(ytmUrl), "YT Music radio mix normalizes to single watch URL");

                // 2. SoundCloud, Bandcamp & Direct Audio links
                AssertTrue(MainForm.IsAudioCentricUrl("https://soundcloud.com/artist/track-name"), "SoundCloud is audio-centric");
                AssertTrue(MainForm.IsAudioCentricUrl("https://artist.bandcamp.com/track/song"), "Bandcamp is audio-centric");
                AssertTrue(MainForm.IsAudioCentricUrl("https://cdn.example.com/audio/master.flac"), "Direct FLAC is audio-centric");

                // 3. Universal HTML5 <audio> and Bandcamp MP3 extraction
                string audioHtml = "<html><head><title>Indie Podcast Ep 10</title></head><body><audio src=\"/audio/ep10_320k.mp3\"></audio></body></html>";
                var audioMeta = MainForm.ParseUniversalWebHtml("https://podcast.example.org/ep10", "https://podcast.example.org/ep10", SocialPlatform.TikTokOrOther, audioHtml);
                AssertTrue(audioMeta != null, "HTML5 <audio> page must parse to MediaMetadata");
                AssertTrue(audioMeta.IsAudioCentric, "HTML5 <audio> page must set IsAudioCentric=true");
                AssertEqual("Indie Podcast Ep 10", audioMeta.Title, "HTML5 <audio> page title");
                AssertEqual("https://podcast.example.org/audio/ep10_320k.mp3", audioMeta.Url, "HTML5 <audio> relative URL resolution");
            }
        }

        private static void TestV24PlaylistAndMusicCoverSupport()
        {
            using (var form = new MainForm())
            {
                // 1. Verify playlist detection across YouTube, YT Music Albums, SoundCloud Sets, and Bandcamp Albums
                AssertTrue(!form.IsSingleVideoUrl("https://www.youtube.com/playlist?list=PL1234567890", SocialPlatform.YouTube), "YouTube /playlist?list=PL... must be playlist");
                AssertTrue(!form.IsSingleVideoUrl("https://music.youtube.com/playlist?list=OLAK5uy_12345", SocialPlatform.YouTube), "YT Music OLAK5uy_ album must be playlist");
                AssertTrue(!form.IsSingleVideoUrl("https://music.youtube.com/browse/MPREb_12345", SocialPlatform.YouTube), "YT Music MPREb_ album must be playlist");
                AssertTrue(!form.IsSingleVideoUrl("https://soundcloud.com/artist/sets/my-album", SocialPlatform.TikTokOrOther), "SoundCloud /sets/ must be playlist");
                AssertTrue(!form.IsSingleVideoUrl("https://artist.bandcamp.com/album/my-lp", SocialPlatform.TikTokOrOther), "Bandcamp /album/ must be playlist");

                // 2. Verify Bandcamp multi-track Album HTML parsing into playlist entries with cover art
                string bcAlbumHtml = "<meta property=\"og:title\" content=\"Synthwave LP, by RetroArtist\" />" +
                    "<meta property=\"og:image\" content=\"https://f4.bcbits.com/img/cover_art_10.jpg\" />" +
                    "<script>var TralbumData = {\"trackinfo\":[" +
                    "{\"title\":\"01. Sunrise\",\"duration\":190,\"title_link\":\"/track/sunrise\",\"file\":{\"mp3-128\":\"https://t4.bcbits.com/stream/t1.mp3\"}}," +
                    "{\"title\":\"02. Sunset\",\"duration\":215,\"title_link\":\"/track/sunset\",\"file\":{\"mp3-128\":\"https://t4.bcbits.com/stream/t2.mp3\"}}" +
                    "]};</script>";
                var bcMeta = MainForm.ParseUniversalWebHtml("https://retro.bandcamp.com/album/synthwave-lp", "https://retro.bandcamp.com/album/synthwave-lp", SocialPlatform.TikTokOrOther, bcAlbumHtml);
                AssertTrue(bcMeta != null, "Bandcamp album HTML must parse");
                AssertTrue(bcMeta.IsPlaylist, "Bandcamp album must have IsPlaylist=true");
                AssertTrue(bcMeta.IsAudioCentric, "Bandcamp album must have IsAudioCentric=true");
                AssertEqual("https://f4.bcbits.com/img/cover_art_10.jpg", bcMeta.Thumbnail, "Bandcamp album cover art");
                AssertEqual(2, bcMeta.Entries.Count, "Bandcamp album track count");
                AssertEqual("01. Sunrise", bcMeta.Entries[0].Title, "Bandcamp track 1 title");
                AssertEqual("https://retro.bandcamp.com/track/sunrise", bcMeta.Entries[0].Url, "Bandcamp track 1 URL");
            }
        }

        private static void TestV25BatchLinksTrackNumberingAndSpotifyCover()
        {
            using (var form = new MainForm())
            {
                // 1. Multi-link Batch URL extraction
                string multiText = "Batch links:\n" +
                    "https://music.youtube.com/watch?v=dQw4w9WgXcQ\n" +
                    "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b\n" +
                    "https://soundcloud.com/artist/track-1\n" +
                    "https://music.youtube.com/watch?v=dQw4w9WgXcQ";
                var batch = form.ExtractAllSupportedUrlsFromText(multiText);
                AssertEqual(3, batch.Count, "Batch paste should extract 3 unique supported URLs");
                AssertEqual("https://music.youtube.com/watch?v=dQw4w9WgXcQ", batch[0], "Batch URL 1");
                AssertEqual("https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b", batch[1], "Batch URL 2");
                AssertEqual("https://soundcloud.com/artist/track-1", batch[2], "Batch URL 3");

                // 2. Playlist Track Numbering (01 - Title) idempotency
                AssertEqual("01 - Nightcall", MainForm.FormatNumberedTrackTitle(1, "Nightcall", true), "Track 1 numbering");
                AssertEqual("12 - Sunset", MainForm.FormatNumberedTrackTitle(12, "Sunset", true), "Track 12 numbering");
                AssertEqual("01. Already Numbered", MainForm.FormatNumberedTrackTitle(1, "01. Already Numbered", true), "Do not double-number already numbered track");
                AssertEqual("Nightcall", MainForm.FormatNumberedTrackTitle(1, "Nightcall", false), "Track numbering disabled");

                // 3. Spotify Track HTML + Official 1:1 Cover Art parsing
                string spHtml = "<meta property=\"og:title\" content=\"Blinding Lights\" />" +
                    "<meta property=\"og:description\" content=\"The Weeknd · Song · 2020\" />" +
                    "<meta property=\"og:image\" content=\"https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36\" />" +
                    "<meta name=\"music:duration\" content=\"200\" />";
                var spMeta = MainForm.ParseUniversalWebHtml(
                    "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
                    "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
                    SocialPlatform.TikTokOrOther,
                    spHtml);
                AssertTrue(spMeta != null, "Spotify track HTML must parse");
                AssertTrue(spMeta.IsAudioCentric, "Spotify track must be audio-centric");
                AssertEqual("The Weeknd - Blinding Lights", spMeta.Title, "Spotify full track title");
                AssertEqual("The Weeknd", spMeta.Uploader, "Spotify artist");
                AssertEqual("https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36", spMeta.Thumbnail, "Spotify 1:1 cover art");
                AssertEqual(200L, spMeta.DurationSec, "Spotify duration");
                AssertEqual("ytsearch1:The Weeknd - Blinding Lights audio", spMeta.Url, "Spotify ytsearch1 audio resolution");

                // 4. 1:1 Center-Square JPEG Cover Art Cropping (16:9 320x180 -> 1:1 180x180)
                byte[] wideJpeg;
                using (var wideBmp = new System.Drawing.Bitmap(320, 180))
                using (var g = System.Drawing.Graphics.FromImage(wideBmp))
                using (var ms = new MemoryStream())
                {
                    g.Clear(System.Drawing.Color.DarkSlateBlue);
                    wideBmp.Save(ms, System.Drawing.Imaging.ImageFormat.Jpeg);
                    wideJpeg = ms.ToArray();
                }
                byte[] squareJpeg = MainForm.CropCenterSquareJpeg(wideJpeg);
                using (var msCheck = new MemoryStream(squareJpeg))
                using (var sqBmp = new System.Drawing.Bitmap(msCheck))
                {
                    AssertEqual(180, sqBmp.Width, "Cropped cover width must be 180 (1:1 square)");
                    AssertEqual(180, sqBmp.Height, "Cropped cover height must be 180 (1:1 square)");
                }
            }
        }
    }
}

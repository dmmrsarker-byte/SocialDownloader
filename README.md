# Social Downloader v2.3 (YouTube Music • Best Audio • YouTube • FB • IG • X • TikTok • Adult & Any Site)

All-in-One **Social Downloader v2.3** for **Android** and **Windows Desktop**, featuring **YouTube Music (`music.youtube.com`) Best Audio Quality**, **Universal Audio & Video Extraction (SoundCloud, Bandcamp, HTML5 `<audio>`/`<video>`, HLS/MP4)**, **Auto-Grab on Copy/Share**, and a single unified link input supporting **YouTube Music, YouTube, SoundCloud, Bandcamp, Facebook, Instagram, Threads, LinkedIn, X (Twitter), TikTok, Pornhub, XVideos, XNXX**, and any website.

---

## 📦 Direct Downloads (v2.3.0)

| Platform | File | Size | Description |
| :--- | :--- | :--- | :--- |
| **Android APK (Signed Release)** | [`releases/Social-Downloader-v2.3.0.apk`](releases/Social-Downloader-v2.3.0.apk) | ~18 MB | Pure-JVM Universal APK v2.3.0 signed with production RSA certificate (No Play Protect / security block) |
| **Windows Installer (`.exe`)** | [`releases/SocialDownloader-Setup.exe`](releases/SocialDownloader-Setup.exe) | ~106 MB | Single-file Setup Wizard v2.3.0 (bundles App + `yt-dlp` + `FFmpeg` + Language Selector) |
| **Windows Portable (`.exe`)** | [`releases/SocialDownloader.exe`](releases/SocialDownloader.exe) | ~160 KB | Standalone Windows Desktop executable v2.3.0 |

*(All release packages are also available under [GitHub Releases](../../releases).)*

---

## ✨ What's New in v2.3.0

1. **YouTube Music (`music.youtube.com`) & Best Audio Quality Extraction**:
   - Full support for `music.youtube.com/watch?v=...`, `music.youtube.com/playlist?list=...`, and YT Music radio/album links.
   - Automatically detects **YouTube Music**, **SoundCloud**, **Bandcamp**, **Audiomack**, **Mixcloud**, **Spotify**, and direct audio links (`.mp3`, `.m4a`, `.flac`, `.wav`, `.opus`, `.ogg`, `.aac`) and switches to **Audio Only (Best Quality)** mode automatically.
   - Selects the highest-bitrate audio stream available (`itag 251` 160kbps Opus / `itag 141` 256kbps AAC / `itag 140` 128kbps M4A) with support for **MP3 (320kbps Best)**, **M4A (Original AAC)**, **OPUS (Hi-Fi Best)**, and on Windows **FLAC (Lossless)** & **WAV (Studio)** with embedded track metadata.
2. **More Powerful Universal Audio & Video Downloading (Any Site)**:
   - Native **SoundCloud** (`soundcloud.com`, `on.soundcloud.com`) and **Bandcamp** (`bandcamp.com`) stream extractors.
   - **Hardware Audio Track Demuxing on Android**: When downloading **Audio Only** from any social or web video (Facebook, Instagram, TikTok, X, Pornhub, XVideos, or any website) that only provides a muxed `.mp4` video stream, `MediaExtractor` + `MediaMuxer` strips the video track to save a pure, lightweight audio file.
   - Universal HTML5 `<audio>`, `<video>`, `<source>`, OpenGraph `og:audio`/`og:video`, JSON-LD, HLS `.m3u8`, and embedded `<iframe>` extraction.
3. **Saved Failed Links, Custom Rename & Resilient Pause/Resume**:
   - Automatically saves any failed link to Downloads & History with one-click **Retry**, **Direct Download from Link**, **Rename File/Title**, **Copy Name**, and **Copy Link**.
   - Byte-level HTTP `Range` and HLS segment-level Pause/Resume with 5x exponential backoff and IPv6-first DNS preference to avoid CGNAT rate-limits.

---

## 📁 Repository Structure

```text
Social-Downloader/
├── YTDownloader-Windows/                # Windows Desktop App & Single-File Setup Installer (C# / .NET)
│   ├── YTDownloader.cs                  # Main Windows application source code
│   ├── Installer.cs                     # Single-file Windows Setup Wizard source code
│   ├── build.bat                        # One-click build script for App + Installer
│   ├── SocialDownloader.exe             # Compiled Windows application
│   └── SocialDownloader-Setup.exe       # Compiled single-file Windows Setup Installer
├── YTDownloader-Android/                # Android Native App (Kotlin + Jetpack Compose + Material 3)
│   ├── app/src/main/kotlin/             # Android source code (NewPipeEngine, SocialMediaEngine, YtDlpEngine, UI)
│   ├── app/build.gradle.kts             # Android app Gradle configuration
│   └── gradlew.bat                      # Gradle wrapper
└── releases/                            # Prebuilt Signed Android APK (Social-Downloader-v2.0.0.apk)
```

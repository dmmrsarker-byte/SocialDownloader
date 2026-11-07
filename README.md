# Social Downloader v2.4 (Playlists • Music Cover Art • YT Music • Best Audio • YouTube • FB • IG • X • TikTok & Any Site)

All-in-One **Social Downloader v2.4** for **Android** and **Windows Desktop**, featuring **Full Playlist & Album Downloading (YouTube, YouTube Music, Radio Mixes, SoundCloud Sets & Bandcamp Albums)**, **Embedded Music Cover Art (`APIC` ID3v2.3 & High-Res Cover Downloader)**, **YouTube Music (`music.youtube.com`) Best Audio Quality**, **Universal Audio & Video Extraction**, **Auto-Grab on Copy/Share**, and a single unified link input supporting **YouTube Music, YouTube, SoundCloud, Bandcamp, Facebook, Instagram, Threads, LinkedIn, X (Twitter), TikTok, Pornhub, XVideos, XNXX**, and any website.

---

## 📦 Direct Downloads (v2.4.0)

| Platform | File | Size | Description |
| :--- | :--- | :--- | :--- |
| **Android APK (Signed Release)** | [`releases/Social-Downloader-v2.4.0.apk`](releases/Social-Downloader-v2.4.0.apk) | ~18 MB | Pure-JVM Universal APK v2.4.0 signed with production RSA certificate (No Play Protect / security block) |
| **Windows Installer (`.exe`)** | [`releases/SocialDownloader-Setup.exe`](releases/SocialDownloader-Setup.exe) | ~315 KB | Single-file Setup Wizard v2.4.0 (bundles App + Auto-Engine Setup + Language Selector) |
| **Windows Portable (`.exe`)** | [`releases/SocialDownloader.exe`](releases/SocialDownloader.exe) | ~168 KB | Standalone Windows Desktop executable v2.4.0 |

*(All release packages are also available under [GitHub Releases](../../releases).)*

---

## ✨ What's New in v2.4.0

1. **Full Playlist, Album & Radio Mix Download Capabilities**:
   - Supports **YouTube Playlists (`PL...`)**, **YouTube Music Albums (`OLAK5uy_...`, `MPREb_...`)**, **YouTube / YT Music Radio Mixes (`RD...`, `RDAMVM...`)**, **SoundCloud Sets (`/sets/...`)**, and **Bandcamp Multi-Track Albums (`/album/...`)**.
   - Multi-page YouTube playlist pagination (`PlaylistInfo.getMoreItems`) + direct **Innertube `/next` & `/browse` playlist/album fallback** on Android.
   - One-tap **"📑 Load Full Playlist / Mix"** toggle when a link contains both `v=...` and `list=...`, **Select All / Select None**, customizable **Playlist / Album Folder Name**, and automatic organization into `Download/SocialDownloader/Audio/<PlaylistName>/` or `Video/<PlaylistName>/`.
2. **Music Cover Art (Embedded Cover Art + Standalone High-Res Cover Downloader)**:
   - **Android**: Automatically downloads high-resolution track/album artwork (`maxresdefault.jpg` / track cover) and embeds a standards-compliant **ID3v2.3 header (`TIT2` Title, `TPE1` Artist, `TALB` Album/Playlist, and `APIC` Front Cover Image)** into `.mp3` files plus saves companion cover art (`cover.jpg` / `<Track> [Cover].jpg`).
   - **Windows**: Automatically embeds high-resolution **Music Cover Art** and metadata (`--embed-thumbnail --convert-thumbnails jpg --embed-metadata`) into downloaded audio files (`MP3`, `M4A`, `OPUS`, `FLAC`).
   - **One-Click "🖼 Save Music Cover" Button**: Save the standalone high-res Music Cover / Thumbnail directly to `Covers/` on both Android and Windows.
3. **YouTube Music (`music.youtube.com`) & Best Audio Quality Extraction**:
   - Automatically detects **YouTube Music**, **SoundCloud**, **Bandcamp**, **Audiomack**, **Mixcloud**, **Spotify**, and direct audio links and switches to **Audio + Music Cover** mode automatically.
   - Selects the highest-bitrate audio stream available (`itag 251` 160kbps Opus / `itag 141` 256kbps AAC / `itag 140` 128kbps M4A) and supports hardware audio demuxing on Android.

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

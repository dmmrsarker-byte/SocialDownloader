# Social Downloader v2.5 (Spotify • Container-Safe 1:1 Cover Art • Batch Multi-Link • Playlist Power Controls • YT Music • Any Site)

All-in-One **Social Downloader v2.5** for **Android** and **Windows Desktop**, featuring **Container-Safe ADTS AAC + 1:1 Square Album Cover Art (`APIC` ID3v2.3)**, **Spotify Track (`open.spotify.com/track/...`) Official Cover Art + Audio Resolution**, **Playlist Power Controls (Search/Filter, `01 - Title` Track Numbering, Invert Selection, 1-Tap Per-Track Download)**, **Multi-Link Batch Paste & Queue**, **Full Playlist & Album Downloading (YouTube, YouTube Music, Radio Mixes, SoundCloud Sets & Bandcamp Albums)**, **YouTube Music (`music.youtube.com`) Best Audio Quality**, and universal downloading from **YouTube, Facebook, Instagram, Threads, LinkedIn, X (Twitter), TikTok, Pornhub, XVideos, XNXX**, and any website.

---

## 📦 Direct Downloads (v2.5.0)

| Platform | File | Size | Description |
| :--- | :--- | :--- | :--- |
| **Android APK (Signed Release)** | [`releases/Social-Downloader-v2.5.0.apk`](releases/Social-Downloader-v2.5.0.apk) | ~18 MB | Pure-JVM Universal APK v2.5.0 signed with production RSA certificate (No Play Protect / security block) |
| **Windows Installer (`.exe`)** | [`releases/SocialDownloader-Setup.exe`](releases/SocialDownloader-Setup.exe) | ~505 KB | Single-file Setup Wizard v2.5.0 (bundles App + Auto-Engine Setup + Language Selector) |
| **Windows Portable (`.exe`)** | [`releases/SocialDownloader.exe`](releases/SocialDownloader.exe) | ~205 KB | Standalone Windows Desktop executable v2.5.0 |

*(All release packages are also available under [GitHub Releases](../../releases).)*

---

## ✨ What's New in v2.5.0

1. **Container-Safe Audio + ADTS AAC Conversion + 1:1 Square Album Cover Art**:
   - **Android**: Added `extractAdtsAudioFromMp4` (`MediaExtractor` raw AAC to 7-byte ADTS frame stream `0xFF 0xF1`) and container guards (`isMp4Container`, `isWebmOrOggContainer`) so `.mp3` downloads are pure frame streams compatible with prepended **ID3v2.3 (`TIT2`, `TPE1`, `TALB`, `APIC`)** headers without corrupting MP4/WebM byte-0 offsets.
   - **1:1 Square Album Art Cropping (`cropCenterSquareJpeg` / `CropCenterSquareJpeg`)**: Automatically center-crops 16:9 widescreen YouTube thumbnails into crisp **1:1 square album covers** on both Android and Windows (`--ppa "ThumbnailsConvertor:-vf crop=ih:ih"`).
2. **Spotify Track (`open.spotify.com/track/...`) Official 1:1 Cover Art + Audio Stream Resolution**:
   - Extracts official Spotify track metadata (`Title`, `Artist`, `Duration`, and official 1:1 Spotify `i.scdn.co` cover art) and resolves the highest-quality audio stream via YouTube Innertube search on Android and `ytsearch1:` on Windows.
3. **Playlist Power Controls (Search/Filter, Track Numbering `01 - Title`, Invert Selection, 1-Tap Track Download)**:
   - Filter large playlists instantly by track title or artist (`txtPlaylistFilter` / `playlistQuery`), toggle automatic track numbering (`01 - Track`, `02 - Track`), invert selection in one click, or download any individual track directly from the playlist card.
4. **Multi-Link Batch Paste & Queue**:
   - Paste multiple URLs at once (separated by spaces or newlines) and queue all of them in a single tap (`⚡ Batch Download All Links (N)`).
5. **Full Playlist, Album, Radio Mix & YouTube Music Best Audio**:
   - Supports **YouTube Playlists (`PL...`)**, **YouTube Music Albums (`OLAK5uy_...`, `MPREb_...`)**, **Radio Mixes (`RD...`, `RDAMVM...`)**, **SoundCloud Sets (`/sets/...`)**, and **Bandcamp Multi-Track Albums (`/album/...`)**.

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

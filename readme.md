# Cubic Music Desktop · Version 3

An independent, open-source desktop music player built with Kotlin Multiplatform and Compose for Desktop.

Cubic Music Desktop brings a focused AirBeats-inspired listening workspace to Windows: live search, rich discovery shelves, radio-style Up Next, synchronized lyrics, downloads you can manage locally, and a compact player that stays out of the way.

The desktop stream and recovery layer is called **G-Stream**—my own Cubic Music work. It uses the open InnerTube protocol surface, performs signature and n-parameter handling through the existing extractor stack, refreshes expiring media URLs, and keeps playback moving when a source needs to recover.

## Download

Download the latest portable build from the [desktop releases page](https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC/releases/latest):

**`CubicMusic-Portable.exe`**

The portable build is one file. Copy it anywhere and double-click it; no installer or companion folder is required. The first launch may take a few seconds while the bundled desktop runtime is prepared.

Version 3 adds buffered multi-range playback, automatic source recovery, richer discovery and search, persistent listening taste, keyboard and touchpad navigation, and a smaller glass-style mini-player.

## Antivirus and Windows warnings

The portable launcher is currently unsigned and extracts its private desktop runtime to `%LOCALAPPDATA%\\CubicMusic\\PortableCache`. That self-extracting behavior can trigger broad heuristic detections such as `Trojan:Win32/Wacatac.B!ml`, even when the source is available for inspection. This notice is not a promise that every security alert is harmless: treat an unexplained detection as unsafe until it has been verified.

If your antivirus flags the portable file:

1. Keep the file quarantined and do not disable protection or add an exclusion just to launch it.
2. Download it only from the official [Cubic Music Desktop repository](https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC) and verify the SHA-256 value published with that release.
3. Submit the file to [Microsoft Security Intelligence](https://www.microsoft.com/en-us/wdsi/filesubmission) as a possible false positive if the detection remains after updating definitions.
4. For maximum confidence, build the desktop app from this source with JDK 21 and inspect the portable launcher before sharing it.

## What is in this repository

This repository contains the desktop application and the shared code required to build it:

- Compose Desktop UI and the Cubic Music player shell
- Kotlin desktop playback, buffering, stream recovery, search, discovery and lyrics
- Local playlists, favorites, profile settings and real file-backed Downloads management
- The InnerTube, LRCLIB, Piped, Invidious and Brotli support modules used by the desktop build
- The portable Windows launcher used for the single-file release

The Android source set, Android tests, Android resources, APK assets and Android-only projects are intentionally not included here. The complete multiplatform project remains in the separate [backupcubic repository](https://github.com/cybruGhost/backupcubic).

## Desktop highlights

- Search suggestions and song/video filters while typing
- Rich Browse and Songs pages with charts, trending tracks, artists and releases
- Radio-style Up Next that keeps discovering related music
- Stream recovery with signed URL refresh and buffered chunk playback
- Synchronized and plain lyrics, with a live lyric line in the side player
- Downloads page with play, show-in-Explorer and remove actions
- Favorites, playlists, profile statistics and appearance preferences
- Keyboard navigation, touchpad support and mouse-drag scrolling
- Hover-expanding navigation that collapses automatically
- Compact glass-style mini-player when the window is minimized
- Cubic Music artwork used consistently in the UI, window and portable EXE

## Requirements

For the portable release:

- Windows 10 or Windows 11, 64-bit
- Internet access for search, streaming, artwork and online lyrics
- About 600 MB of free space for the executable and its private runtime cache

The launcher keeps its temporary runtime cache under `%LOCALAPPDATA%\\CubicMusic\\PortableCache`. It can be removed after Cubic Music is closed.

Downloaded songs are stored at `%USERPROFILE%\\Music\\Cubic Music\\Downloads` and can be managed from **MY MUSIC → Downloads** inside the app.

## Build from source

Install JDK 21, then run the desktop distribution task from the repository root:

```powershell
.\\gradlew.bat :composeApp:createDistributable --console=plain
```

The unpacked desktop application is written to:

```text
composeApp/build/compose/binaries/main/app/CubicMusic/
```

The single-file portable launcher is produced for the published release from that distribution and the launcher source in `composeApp/portable/`.

## Open source and reuse

Cubic Music Desktop is released under the [GNU General Public License v3.0](./LICENSE). You are welcome to study, modify, share and reuse the code under those terms. Please keep copyright notices, license text and source availability with redistributed or modified versions.

Permission to reuse Cubic Music code is expressly acknowledged for [N-Zik Group / N-Zik](https://github.com/N-Zik-Group/N-Zik) and [NEVARLeVrai](https://github.com/NEVARLeVrai), subject to GPL-3.0 and the notices of any third-party components they reuse. This acknowledgement is non-exclusive: anyone may use, modify and redistribute the code under the same GPL-3.0 terms.

This desktop repository is maintained separately from the Android project. Improvements that are desktop-specific belong here; Android changes belong in [backupcubic](https://github.com/cybruGhost/backupcubic).

## Attribution

Cubic Music builds on open-source libraries and community projects. Their own licenses and notices remain applicable. In particular, the desktop build uses Kotlin, Compose Multiplatform, OkHttp, NewPipe Extractor, FFmpeg/JAVE, Room, LRCLIB-related code and InnerTube-related modules.

## License

Copyright © 2026 cybruGhost and contributors.

See [LICENSE](./LICENSE) for the complete GPL-3.0 terms.

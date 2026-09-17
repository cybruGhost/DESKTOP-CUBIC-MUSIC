# Cubic Music Desktop · Version 2

An independent, open-source desktop music player built with Kotlin Multiplatform and Compose for Desktop.

Cubic Music Desktop brings a focused AirBeats-inspired listening workspace to Windows: live search, rich discovery shelves, radio-style Up Next, synchronized lyrics, downloads you can manage locally, and a compact player that stays out of the way.

The desktop stream and recovery layer is called **G-Stream**—my own Cubic Music work. It uses the open InnerTube protocol surface, refreshes expiring media URLs, and keeps playback moving when a source needs to recover.

## Download

Download the latest portable build from the [Version 2 release](https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC/releases/tag/v2.0.0):

**`CubicMusic-Portable.exe`**

The portable build is one file. Copy it anywhere and double-click it; no installer or companion folder is required. The first launch may take a few seconds while the bundled desktop runtime is prepared.

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

Permission to reuse Cubic Music code is expressly acknowledged for [N-Zik Group / N-Zik](https://github.com/N-Zik-Group/N-Zik) and [NEVARLeVrai](https://github.com/NEVARLeVrai), subject to GPL-3.0 and the notices of any third-party components they reuse.

This desktop repository is maintained separately from the Android project. Improvements that are desktop-specific belong here; Android changes belong in [backupcubic](https://github.com/cybruGhost/backupcubic).

## Attribution

Cubic Music builds on open-source libraries and community projects. Their own licenses and notices remain applicable. In particular, the desktop build uses Kotlin, Compose Multiplatform, OkHttp, NewPipe Extractor, FFmpeg/JAVE, Room, LRCLIB-related code and InnerTube-related modules.

## License

Copyright © 2026 cybruGhost and contributors.

See [LICENSE](./LICENSE) for the complete GPL-3.0 terms.

# NoBoundRift

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

Personal Android manga and webtoon reader. Content is sourced by scraping third-party websites — no official APIs involved.

> [!CAUTION]
> **Personal use only. This app scrapes third-party websites without their consent (Not asked). Do not distribute, publish, or use commercially.**

## Features

- Browse and search manga/webtoon from multiple sources
- Manga detail page with chapter list — sortable oldest-first or newest-first
- Two reading modes: webtoon scroll and page-by-page flip
- Seamless auto-load of the next chapter when reaching the end
- **Continue Reading** — resumes the exact chapter you were on when you last exited
- Chapter marked as read automatically at 80%+ progress
- Local library — bookmark titles and track reading progress
- **Drag-to-reorder library** — long-press a cover then drag to reposition it
- Lock icon on premium/paywalled chapters
- User-friendly error screen with Retry instead of a crash
- Image refresh button in the reader top bar to retry failed page loads
- **Chapter downloads** — download chapters for offline reading in the background via WorkManager
- **Download All** — queues every chapter sequentially (first to last) so they download one at a time
- **Downloads tab** in the library and detail screens to manage downloaded chapters
- **Offline fallback** — Detail screen builds from local downloads when the network is unavailable
- Friendly offline error with WifiOff icon in Browse instead of a raw exception message
- Refresh button in the Browse search bar to re-run the current search

## Sources

| Source | Site | Method |
|--------|------|--------|
| MangaRead | mangaread.org | Jsoup scraper |
| Manhwaz | manhwaz.com | Jsoup scraper |
| AsuraScans | asurascans.com | Jsoup + Astro SSR JSON props |
| MangaDex | mangadex.org | Official REST API + CDN |

## Stack

- **Kotlin** + **Jetpack Compose**
- **OkHttp** + **Jsoup** for HTTP/scraping
- **Coil** for image loading
- **Room** for local persistence
- **WorkManager** for background chapter downloads
- **Hilt** for dependency injection
- **Coroutines + Flow** for async

## Build

Requires Android Studio or a JDK with the Android SDK configured.

```bash
./gradlew assembleDebug      # build debug APK
./gradlew assembleRelease    # build release APK
./gradlew installDebug       # install on connected device/emulator
```

minSdk 26 · targetSdk 35

## Releases

Pre-built APKs are available on the [Releases](https://github.com/Lycoris69/NoBoundRift/releases) page. Enable "Install from unknown sources" on your device before installing.

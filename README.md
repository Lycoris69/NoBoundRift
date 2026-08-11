# NoBoundRift

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

Personal Android manga and webtoon reader. Content is sourced by scraping third-party websites — no official APIs involved.

> [!CAUTION]
> **Personal use only. This app scrapes third-party websites without their consent (Not asked). Do not distribute, publish, or use commercially.**

## Features

### Reading
- Two reading modes: webtoon scroll and page-by-page flip
- Seamless auto-load of the next chapter when reaching the end
- **Chapter picker** — tap the chapter title in the reader top bar to open a scrollable dropdown of all chapters; current chapter is highlighted; tap any entry to jump directly to it
- **Chapter progress %** — top bar shows percentage through the current chapter (e.g. `42%`); resets to 0% when crossing into the next chapter during seamless load
- **Continue Reading** — resumes the exact chapter and page you were on when you last exited
- Chapter marked as read automatically at 80%+ progress
- Lock icon on premium/paywalled chapters
- Image refresh button in the reader top bar to retry failed page loads

### Library
- Local library — bookmark titles and track reading progress
- **Drag-to-reorder** — long-press a cover then drag to reposition (grid and list modes)
- **NEW badge + auto-sort** — manga with a chapter released in the last 7 days float to the top automatically
- **Library layout setting** — switch between grid and list view in Settings

### Downloads
- **Chapter downloads** — download chapters for offline reading via WorkManager
- **Download All / Cancel All** — queues every chapter; button switches to Cancel All while active
- **Read from Downloads** — tap any completed chapter in Library → Downloads to read it offline without a network connection
- **Downloads tab** in the library and detail screens to manage downloaded chapters
- **Inline download controls** — expand a manga group in the Library Downloads tab to cancel, retry, or delete individual chapters
- **Parallel downloads** — configurable concurrency (1–20 chapters at a time) in Settings
- **Offline fallback** — Detail screen builds from local downloads when the network is unavailable

### Browse & Discovery
- Browse and search manga/webtoon across 8 sources
- Refresh button in the Browse search bar to re-run the current search
- Friendly offline error with WifiOff icon instead of a raw exception message
- **Discover tab** — AniList-powered recommendations with cross-source search; toggle visibility in Settings
- **Source label** on the manga detail page

## Sources

| Source | Site | Method | Notes |
|--------|------|--------|-------|
| MangaRead | mangaread.org | Jsoup scraper | Madara/WordPress |
| Manhwaz | manhwaz.com | Jsoup scraper | Custom Madara-like |
| AsuraScans | asurascans.com | Jsoup + Astro SSR props | Data in `<astro-island>` JSON |
| MangaDex | mangadex.org | Official REST API + CDN | |
| ManhuaTop | manhuatop.org | Jsoup scraper | Madara; AJAX chapter list |
| Mgeko | mgeko.cc | Jsoup + JSON API | Custom site; chapters via /all-chapters/ |
| MangaKatana | mangakatana.com | Jsoup scraper | Custom site; static HTML |
| ManhwaTop | manhwatop.com | Jsoup scraper | Madara; 3-tier AJAX chapter fetch; Cloudflare |

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

# NoBoundRift

Personal Android manga and webtoon reader. Content is sourced by scraping third-party websites — no official APIs involved.

> **Personal use only. Not intended for distribution.**

## Features

- Browse and search manga/webtoon from multiple sources
- Manga detail page with chapter list — sortable oldest-first or newest-first
- Two reading modes: webtoon scroll and page-by-page flip
- Auto-load next chapter when reaching the end of a chapter
- Local library — bookmark titles and track reading progress
- Chapter marked as read automatically at 80%+ progress

## Sources

| Source | Site | Method |
|--------|------|--------|
| MangaRead | mangaread.org | Jsoup scraper |
| Manhwaz | manhwaz.com | Jsoup scraper |

## Stack

- **Kotlin** + **Jetpack Compose**
- **OkHttp** + **Jsoup** for HTTP/scraping
- **Coil** for image loading
- **Room** for local persistence
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

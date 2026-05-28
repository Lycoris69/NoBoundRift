# NoBoundRift

Personal Android manga and webtoon reader. Content is sourced by scraping third-party websites — no official APIs involved.

> **Personal use only. Not intended for distribution.**

## Features

- Browse and search manga/webtoon across multiple sources
- Two reading modes: webtoon scroll and page-by-page flip
- Local library — save titles and track reading progress
- Read status synced per chapter via local Room database

## Sources

| Source | Type | Method |
|--------|------|--------|
| MangaDex | Manga | REST API (`api.mangadex.org`) |
| MangaRead | Manga/Manhwa | Jsoup scraper |
| Manhwaz | Manhwa | Jsoup scraper |

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
./gradlew assembleDebug      # build APK
./gradlew installDebug       # install on connected device/emulator
```

minSdk 26 · targetSdk 35

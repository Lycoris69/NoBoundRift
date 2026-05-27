# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NoBoundRift** is a personal-use Android manga and webtoon reader app written in Kotlin. Content is sourced by scraping third-party websites — no official APIs. Not intended for distribution.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture (data / domain / presentation layers)
- **Scraping**: Jsoup for HTML parsing, OkHttp for HTTP
- **Image loading**: Coil (handles remote manga page images efficiently)
- **Local storage**: Room (reading progress, library, chapter cache)
- **Async**: Kotlin Coroutines + Flow
- **DI**: Hilt

## Build & Run Commands

Once the Android project is scaffolded:

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # install on connected device/emulator
./gradlew test                   # run unit tests (JVM)
./gradlew connectedAndroidTest   # run instrumented tests (requires device/emulator)
./gradlew lint                   # run Android lint
./gradlew :app:testDebugUnitTest --tests "com.example.FooTest"  # single test class
```

## Architecture

### Source System

Each supported website is a `Source` implementation. A source exposes:
- `fetchMangaList()` — browse/search results
- `fetchMangaDetails(url)` — title metadata (cover, synopsis, genres)
- `fetchChapterList(mangaUrl)` — chapter index
- `fetchPageList(chapterUrl)` — image URLs for a chapter

Sources are registered in a `SourceManager` and selected by the user per-title. This is the primary extension point of the app — adding a new site means adding a new `Source` class.

### Data Flow

```
Source (scraper) → Repository → ViewModel → Compose UI
                        ↕
                    Room DB (library, progress, cache)
```

The domain layer (use cases) sits between Repository and ViewModel. Repositories coordinate between the live scraper and the local Room database so the UI always has something to show while a network fetch is in progress.

### Reader

The reader screen receives a list of image URLs from `fetchPageList()` and displays them via `LazyColumn` (webtoon/scroll mode) or `HorizontalPager` (manga/page mode). Images are loaded lazily with Coil.

## Scraping Guidelines

- All HTTP goes through a shared `OkHttpClient` with a realistic User-Agent header and cookie jar so sessions persist across requests.
- Rate-limit requests per source to avoid bans (use a per-source semaphore or delay).
- Jsoup selectors are fragile — isolate them inside the `Source` class so breakage is contained. Add a comment with the date when a selector was last verified.
- Prefer relative URL resolution (`absUrl()`) over string concatenation when extracting links.

## Key Conventions

- Scraper selectors live entirely inside each `Source` class — never leak raw HTML or CSS selectors into domain/UI layers.
- Chapter URLs are stored as the canonical identifier in Room; re-fetching progress by URL is safe.
- Image loading should never block the main thread; all `Source` functions are `suspend` functions called from `Dispatchers.IO`.

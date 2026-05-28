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

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK (unsigned)
./gradlew installDebug           # install on connected device/emulator
./gradlew test                   # run unit tests (JVM)
./gradlew connectedAndroidTest   # run instrumented tests (requires device/emulator)
./gradlew lint                   # run Android lint
./gradlew :app:testDebugUnitTest --tests "com.lycoris.noboundrift.FooTest"  # single test class
```

## Architecture

### Source System

Each supported website is a `Source` implementation (`data/remote/source/`). A source exposes:
- `fetchMangaList(page, query)` — browse or search results depending on whether query is blank
- `fetchMangaDetail(url)` — title metadata (cover, synopsis, genres)
- `fetchChapterList(mangaUrl)` — chapter index, returned sorted ascending by chapter number
- `fetchPageList(chapterUrl)` — image URLs for a chapter

Registered sources (add new ones to `SourceManager`):

| Class | ID | Site | Notes |
|---|---|---|---|
| `MangaReadSource` | 2 | mangaread.org | Madara/WordPress theme |
| `ManhwazSource` | 3 | manhwaz.com | Custom site, similar CSS classes to Madara |

To add a new source: create a class implementing `Source`, give it a unique `id`, add it as a Hilt `@Inject constructor`, and register it in `SourceManager`.

**Chapter loading (both sources):** Chapters are embedded directly in the manga page HTML — `li.wp-manga-chapter a`. No AJAX step needed despite the Madara-like markup. Do not add AJAX logic.

**Search endpoints:**
- MangaRead: `/?s={query}&post_type=wp-manga`, selector `.c-tabs-item__content .post-title h3 a`
- Manhwaz: `/?s={query}&post_type=wp-manga`, selector `.page-item-detail .post-title h3 a`

### Data Flow

```
Source (scraper) → Repository → UseCase → ViewModel → Compose UI
                        ↕
                    Room DB (library, progress)
```

The domain layer (use cases) sits between Repository and ViewModel. Repositories coordinate between the live scraper and the local Room database.

### Reader

The reader receives image URLs from `fetchPageList()` and displays them via `LazyColumn` (webtoon/scroll mode) or `HorizontalPager` (manga/page mode). When the user nears the last page, the ViewModel fetches the next chapter's pages and appends them seamlessly. The full chapter list is fetched once on reader init and stored in `sortedChapters` to resolve `nextChapterUrl` without further network calls.

### Navigation

Routes use URL-encoded nav args — both `mangaUrl` and `chapterUrl` are full URLs passed through `encodeForNav()` / `decodeFromNav()`. The `ARG_MANGA_ID` arg on the Reader route holds the full manga page URL (not just the slug).

## Scraping Guidelines

- All HTTP goes through a shared `OkHttpClient` with a realistic User-Agent header and a HashMap-based `CookieJar`.
- Jsoup selectors are fragile — isolate them inside the `Source` class. Add a comment with the date when a selector was last verified.
- Prefer `absUrl()` over string concatenation when extracting links. Handle `//`-prefixed URLs by prepending `https:`.
- All `Source` functions are `suspend` functions calling `withContext(Dispatchers.IO)`.

## Key Conventions

- Scraper selectors live entirely inside each `Source` class — never leak them into domain/UI layers.
- Chapter URLs are the canonical identifier for reading progress in Room.
- `runCatching` wraps all scraper calls in the repository; `CancellationException` must be checked in `.onFailure` handlers and not treated as an error.
- Browse list deduplicates by `id` (`distinctBy { it.id }`) before updating state to prevent duplicate-key crashes in `LazyVerticalGrid`.
- `loadNextPage()` guards against firing during an initial page-1 load by checking `isLoading`.

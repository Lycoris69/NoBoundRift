package com.lycoris.noboundrift.presentation.navigation

/**
 * Sealed class defining all navigation destinations.
 *
 * Route strings are kept as constants to avoid typos. Dynamic segments use
 * the `{param}` placeholder syntax required by the Compose Navigation library.
 *
 * Navigation argument types:
 * - [Detail]: sourceId (Long), url (String, URI-encoded)
 * - [Reader]: sourceId (Long), chapterUrl (String, URI-encoded), mangaId (String)
 */
sealed class Screen(val route: String) {

    /** Bottom-nav root destinations ─────────────────────────────────────── */

    data object Library : Screen("library")

    data object Browse : Screen("browse/{sourceId}") {
        const val ARG_SOURCE_ID = "sourceId"
        fun createRoute(sourceId: Long) = "browse/$sourceId"
    }

    /** Full-screen destinations ─────────────────────────────────────────── */

    data object Detail : Screen("detail/{sourceId}/{url}") {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_URL = "url"
        fun createRoute(sourceId: Long, url: String): String =
            "detail/$sourceId/${url.encodeForNav()}"
    }

    data object Reader : Screen("reader/{sourceId}/{mangaId}/{chapterUrl}") {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_MANGA_ID = "mangaId"
        const val ARG_CHAPTER_URL = "chapterUrl"
        fun createRoute(sourceId: Long, mangaId: String, chapterUrl: String): String =
            "reader/$sourceId/$mangaId/${chapterUrl.encodeForNav()}"
    }
}

/**
 * URL-encodes a string for safe embedding in a navigation route.
 * The Compose Navigation library splits routes on `/` — any slashes in the
 * value must be percent-encoded.
 */
fun String.encodeForNav(): String =
    java.net.URLEncoder.encode(this, "UTF-8")

fun String.decodeFromNav(): String =
    java.net.URLDecoder.decode(this, "UTF-8")

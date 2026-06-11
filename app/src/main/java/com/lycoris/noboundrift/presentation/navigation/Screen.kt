package com.lycoris.noboundrift.presentation.navigation

sealed class Screen(val route: String) {

    data object Library : Screen("library")

    data object Browse : Screen("browse?query={query}") {
        const val ARG_QUERY = "query"
        fun createRoute(query: String = "") =
            if (query.isBlank()) "browse" else "browse?query=${query.encodeForNav()}"
    }

    data object Detail : Screen("detail/{sourceId}/{url}") {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_URL = "url"
        fun createRoute(sourceId: Long, url: String): String =
            "detail/$sourceId/${url.encodeForNav()}"
    }

    data object Reader : Screen("reader/{sourceId}/{mangaId}/{chapterUrl}/{mangaTitle}") {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_MANGA_ID = "mangaId"
        const val ARG_CHAPTER_URL = "chapterUrl"
        const val ARG_MANGA_TITLE = "mangaTitle"
        fun createRoute(sourceId: Long, mangaId: String, chapterUrl: String, mangaTitle: String): String =
            "reader/$sourceId/${mangaId.encodeForNav()}/${chapterUrl.encodeForNav()}/${mangaTitle.encodeForNav()}"
    }

    data object Recommendation : Screen("recommendation")

    data object Settings : Screen("settings")
}

fun String.encodeForNav(): String = java.net.URLEncoder.encode(this, "UTF-8")
fun String.decodeFromNav(): String = java.net.URLDecoder.decode(this, "UTF-8")

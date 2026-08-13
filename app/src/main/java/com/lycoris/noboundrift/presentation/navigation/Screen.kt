package com.lycoris.noboundrift.presentation.navigation

sealed class Screen(val route: String) {

    data object Library : Screen("library")

    data object Browse : Screen("browse?query={query}&altTitles={altTitles}") {
        const val ARG_QUERY = "query"
        const val ARG_ALT_TITLES = "altTitles"
        fun createRoute(query: String = "", altTitles: List<String> = emptyList()): String =
            "browse?query=${query.encodeForNav()}&altTitles=${altTitles.joinToString("|").encodeForNav()}"
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

    // Settings sub-screens — each is a full nav destination so the bottom bar
    // hides automatically (their routes don't match the four root-tab routes).
    data object SettingsAppearance : Screen("settings/appearance")
    data object SettingsBrowse : Screen("settings/browse")
    data object SettingsReader : Screen("settings/reader")
    data object SettingsLibrary : Screen("settings/library")
    data object SettingsDownloads : Screen("settings/downloads")
    data object SettingsNavigation : Screen("settings/navigation_prefs")
}

fun String.encodeForNav(): String = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
fun String.decodeFromNav(): String = java.net.URLDecoder.decode(this, "UTF-8")

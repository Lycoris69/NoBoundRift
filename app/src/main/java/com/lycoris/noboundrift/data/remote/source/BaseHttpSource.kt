package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.MangaStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Base class for all HTTP-backed [Source] implementations.
 *
 * Centralises:
 * - [getDocument] — HTTP GET with `response.isSuccessful` guard + Jsoup parse
 * - [getRawHtml] — HTTP GET that returns raw HTML string (used by Astro/SSR sites)
 * - [parseStatus] — maps common status strings to [MangaStatus]
 * - [parseDateMillis] — parses an absolute date string using the provided [DateTimeFormatter],
 *   with a fallback to relative ("X days ago") expressions
 *
 * All [DateTimeFormatter] instances should be declared as `companion object` constants in the
 * subclass so they are allocated once and reused — [DateTimeFormatter] is immutable and
 * thread-safe, unlike [java.text.SimpleDateFormat].
 */
abstract class BaseHttpSource(protected val okHttpClient: OkHttpClient) : Source {

    companion object {
        // Matches both full-word ("2 hours ago", "3 days ago") and single-letter abbreviated
        // forms ("2h ago", "3d ago") used by some sources (e.g. ManhuaTop).
        // Group 1 — numeric amount; Group 2 — unit word or letter.
        private val RELATIVE_DATE_REGEX = Regex(
            "(\\d+)\\s*(second|sec|s|minute|min|hour|h|day|d|week|w|month|year|yr)s?(?:\\s+ago)?",
            RegexOption.IGNORE_CASE,
        )
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    /**
     * Executes a GET request to [url] and returns the parsed Jsoup [Document].
     * Must be called from a background thread (IO dispatcher).
     *
     * @throws IllegalStateException on non-2xx response or empty body
     */
    protected fun getDocument(url: String): Document {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from $url")
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response body from $url")
            return Jsoup.parse(body, url)
        }
    }

    /**
     * Executes a GET request to [url] and returns the raw HTML string.
     * Useful for sites where additional regex/JSON extraction is needed before Jsoup parsing.
     *
     * @throws IllegalStateException on non-2xx response or empty body
     */
    protected fun getRawHtml(url: String): String {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from $url")
            return response.body?.string()
                ?: throw IllegalStateException("Empty response body from $url")
        }
    }

    // -------------------------------------------------------------------------
    // Shared parsers
    // -------------------------------------------------------------------------

    /**
     * Maps a free-text status string to the canonical [MangaStatus] enum value.
     */
    protected fun parseStatus(text: String): MangaStatus = when {
        text.contains("ongoing", ignoreCase = true) -> MangaStatus.ONGOING
        text.contains("completed", ignoreCase = true) -> MangaStatus.COMPLETED
        text.contains("hiatus", ignoreCase = true) -> MangaStatus.HIATUS
        text.contains("cancelled", ignoreCase = true) ||
            text.contains("canceled", ignoreCase = true) -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    /**
     * Parses [text] as an upload date in milliseconds (UTC epoch).
     *
     * Tries [formatter] first for absolute dates (e.g. "03.09.2025" or "Dec 31, 2023").
     * Falls back to a relative expression like "3 days ago" if the absolute parse fails.
     * Returns 0 if both attempts fail or [text] is blank.
     *
     * [formatter] must be a [DateTimeFormatter] that produces a full date (year, month, day).
     * Declare it as a `companion object` `val` in the subclass so it is created once.
     */
    protected fun parseDateMillis(text: String, formatter: DateTimeFormatter): Long {
        if (text.isBlank()) return 0L

        // Attempt absolute date parse
        runCatching {
            LocalDate.parse(text.trim(), formatter)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.onSuccess { return it }

        // Fall back to relative expression: "X hours ago", "2h ago", "3d ago", etc.
        val match = RELATIVE_DATE_REGEX.find(text) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val unitMs = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "second", "sec", "s" -> 1_000L
            "minute", "min"      -> 60_000L
            "hour",   "h"        -> 3_600_000L
            "day",    "d"        -> 86_400_000L
            "week",   "w"        -> 7 * 86_400_000L
            "month"              -> 30 * 86_400_000L
            "year",   "yr"       -> 365 * 86_400_000L
            else                 -> return 0L
        }
        return System.currentTimeMillis() - amount * unitMs
    }
}

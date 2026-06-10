package com.lycoris.noboundrift.data.remote.source

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry of all [Source] implementations. The ViewModel and repository
 * look up sources by [id] — adding a new source is as simple as injecting it here
 * and adding it to [sources].
 *
 * Hilt injects each [Source] via constructor injection, so this class doesn't need
 * to know how each source is constructed.
 */
@Singleton
class SourceManager @Inject constructor(
    mangaReadSource: MangaReadSource,
    manhwazSource: ManhwazSource,
    asuraScanSource: AsuraScanSource,
    mangaDexSource: MangaDexSource,
) {
    private val sources: Map<Long, Source> = listOf(
        mangaReadSource,
        manhwazSource,
        asuraScanSource,
        mangaDexSource,
    ).associateBy { it.id }

    /**
     * Returns the [Source] with the given [id], or throws [IllegalArgumentException]
     * if no such source is registered.
     */
    fun getSource(id: Long): Source =
        sources[id] ?: throw IllegalArgumentException(
            "No source registered with id=$id. Available: ${sources.keys}"
        )

    /** Returns all registered sources, suitable for populating a source picker UI. */
    fun getAllSources(): List<Source> = sources.values.toList()
}

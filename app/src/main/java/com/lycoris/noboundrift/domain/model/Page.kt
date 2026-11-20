package com.lycoris.noboundrift.domain.model

/**
 * A single page within a chapter. [index] is 0-based.
 * [imageUrl] is the direct URL passed to Coil for loading.
 *
 * For novel sources a "page" is one paragraph: [text] holds its content, [imageUrl] is empty
 * and [isHeading] marks the chapter title line.
 */
data class Page(
    val index: Int,
    val imageUrl: String,
    val refererUrl: String? = null,
    val text: String? = null,
    val isHeading: Boolean = false,
)

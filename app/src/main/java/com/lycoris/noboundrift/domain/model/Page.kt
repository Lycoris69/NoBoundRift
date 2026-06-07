package com.lycoris.noboundrift.domain.model

/**
 * A single page within a chapter. [index] is 0-based.
 * [imageUrl] is the direct URL passed to Coil for loading.
 */
data class Page(
    val index: Int,
    val imageUrl: String,
    val refererUrl: String? = null,
)

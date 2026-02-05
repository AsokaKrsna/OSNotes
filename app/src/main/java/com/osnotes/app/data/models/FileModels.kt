package com.osnotes.app.data.models

/**
 * Information about a note (PDF file).
 */
data class NoteInfo(
    val name: String,
    val path: String,
    val pageCount: Int = 0,
    val lastModified: Long = 0,
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null
)

/**
 * Information about a folder (collection).
 */
data class FolderInfo(
    val name: String,
    val path: String,
    val noteCount: Int = 0,
    val lastModified: Long = 0
)

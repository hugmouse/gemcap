package mysh.dev.gemcap.domain

import androidx.compose.runtime.Immutable

@Immutable
data class Bookmark(
    val url: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis(),
    val previewPath: String? = null
)

@Immutable
data class HistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

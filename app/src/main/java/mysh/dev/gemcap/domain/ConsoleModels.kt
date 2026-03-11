package mysh.dev.gemcap.domain

import androidx.compose.runtime.Immutable

enum class ConsoleCategory { NETWORK, ERROR, SECURITY, LOGCAT }
enum class ConsoleLevel { INFO, WARNING, ERROR }

@Immutable
data class ConsoleEntry(
    val id: Long,
    val timestamp: Long,
    val category: ConsoleCategory,
    val level: ConsoleLevel,
    val title: String,
    val detail: String? = null
)

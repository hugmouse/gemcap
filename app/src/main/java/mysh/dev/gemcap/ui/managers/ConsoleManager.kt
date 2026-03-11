package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import mysh.dev.gemcap.domain.ConsoleCategory
import mysh.dev.gemcap.domain.ConsoleEntry
import mysh.dev.gemcap.domain.ConsoleLevel
import mysh.dev.gemcap.domain.ConsoleLogger
import mysh.dev.gemcap.ui.model.PanelState

class ConsoleManager(
    private val getPanelState: () -> PanelState,
    private val updatePanelState: (PanelState) -> Unit
) : ConsoleLogger {

    companion object {
        private const val MAX_ENTRIES = 1000
    }

    private val buffer = ArrayDeque<ConsoleEntry>(MAX_ENTRIES)
    private var nextId = 1L

    var entries by mutableStateOf<ImmutableList<ConsoleEntry>>(persistentListOf())
        private set

    var errorCount by mutableIntStateOf(0)
        private set

    override fun log(category: ConsoleCategory, level: ConsoleLevel, title: String, detail: String?) {
        val entry = ConsoleEntry(
            id = nextId++,
            timestamp = System.currentTimeMillis(),
            category = category,
            level = level,
            title = title,
            detail = detail
        )
        synchronized(buffer) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
            entries = buffer.toImmutableList()
            errorCount = buffer.count { it.category == ConsoleCategory.ERROR && it.level == ConsoleLevel.ERROR }
        }
    }

    fun entriesForCategory(category: ConsoleCategory): ImmutableList<ConsoleEntry> {
        return entries.filter { it.category == category }.toImmutableList()
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            nextId = 1L
            entries = persistentListOf()
            errorCount = 0
        }
    }

    fun showConsole() {
        updatePanelState(getPanelState().copy(showConsole = true))
    }

    fun dismissConsole() {
        updatePanelState(getPanelState().copy(showConsole = false))
    }
}

package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import mysh.dev.gemcap.data.BrowserRepository
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.ui.model.AutocompleteState
import mysh.dev.gemcap.ui.model.PanelState

class HistoryManager(
    private val repository: BrowserRepository,
    private val getPanelState: () -> PanelState,
    private val updatePanelState: (PanelState) -> Unit
) {
    var history by mutableStateOf<ImmutableList<HistoryEntry>>(persistentListOf())
        private set

    var autocompleteState by mutableStateOf(AutocompleteState())
        private set

    fun refresh() {
        history = repository.getHistory().toImmutableList()
    }

    fun record(url: String, title: String) {
        if (url.startsWith("about:")) return
        repository.addHistoryEntry(HistoryEntry(url = url, title = title.ifEmpty { url }))
        refresh()
    }

    fun clear() {
        repository.clearHistory()
        refresh()
    }

    fun updateSuggestions(query: String) {
        if (query.length < 2) {
            autocompleteState = AutocompleteState()
            return
        }
        val lowerQuery = query.lowercase()
        val filtered = history.filter { entry ->
            entry.url.lowercase().contains(lowerQuery) ||
                    entry.title.lowercase().contains(lowerQuery)
        }.take(5)
        autocompleteState = AutocompleteState(
            suggestions = filtered.toImmutableList(),
            showSuggestions = filtered.isNotEmpty()
        )
    }

    fun dismissSuggestions() {
        autocompleteState = autocompleteState.copy(showSuggestions = false)
    }

    fun clearAutocomplete() {
        autocompleteState = AutocompleteState()
    }

    fun showScreen() {
        refresh()
        updatePanelState(getPanelState().copy(showHistory = true, showMenu = false))
    }

    fun dismissScreen() {
        updatePanelState(getPanelState().copy(showHistory = false))
    }
}

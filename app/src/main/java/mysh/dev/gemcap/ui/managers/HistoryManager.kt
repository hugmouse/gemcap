package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mysh.dev.gemcap.data.BrowserRepository
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.ui.model.AutocompleteState
import mysh.dev.gemcap.ui.model.PanelState

class HistoryManager(
    private val repository: BrowserRepository,
    private val scope: CoroutineScope,
    private val getPanelState: () -> PanelState,
    private val updatePanelState: (PanelState) -> Unit
) {
    var history by mutableStateOf<ImmutableList<HistoryEntry>>(persistentListOf())
        private set

    var autocompleteState by mutableStateOf(AutocompleteState())
        private set

    private var normalizedCache: List<NormalizedEntry> = emptyList()
    private var debounceJob: Job? = null

    fun refresh() {
        history = repository.getHistory().toImmutableList()
        rebuildCache()
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
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.length < 2) {
            debounceJob?.cancel()
            autocompleteState = AutocompleteState()
            return
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            val results = filterSuggestions(normalizedQuery)
            autocompleteState = AutocompleteState(
                suggestions = results.toImmutableList(),
                showSuggestions = results.isNotEmpty()
            )
        }
    }

    private fun rebuildCache() {
        normalizedCache = history
            .distinctBy { it.url }
            .map { entry ->
                val urlLower = entry.url.lowercase()
                NormalizedEntry(
                    entry = entry,
                    urlLower = urlLower,
                    titleLower = entry.title.lowercase(),
                    urlNoScheme = urlLower.substringAfter("://", urlLower)
                )
            }
    }

    private fun filterSuggestions(normalizedQuery: String): List<HistoryEntry> {
        val queryNoScheme = normalizedQuery.substringAfter("://", normalizedQuery)
        val queryVariants = if (queryNoScheme != normalizedQuery) {
            listOf(normalizedQuery, queryNoScheme)
        } else {
            listOf(normalizedQuery)
        }

        return normalizedCache
            .mapNotNull { cached ->
                val rank = queryVariants.minOfOrNull { candidate ->
                    when {
                        cached.urlLower.startsWith(candidate) || cached.urlNoScheme.startsWith(candidate) -> 0
                        cached.titleLower.startsWith(candidate) -> 1
                        cached.urlLower.contains(candidate) || cached.urlNoScheme.contains(candidate) -> 2
                        cached.titleLower.contains(candidate) -> 3
                        else -> Int.MAX_VALUE
                    }
                } ?: Int.MAX_VALUE

                if (rank == Int.MAX_VALUE) null
                else RankedSuggestion(entry = cached.entry, rank = rank)
            }
            .sortedWith(
                compareBy<RankedSuggestion> { it.rank }
                    .thenByDescending { it.entry.visitedAt }
            )
            .map { it.entry }
            .take(5)
    }

    private data class NormalizedEntry(
        val entry: HistoryEntry,
        val urlLower: String,
        val titleLower: String,
        val urlNoScheme: String
    )

    private data class RankedSuggestion(
        val entry: HistoryEntry,
        val rank: Int
    )

    companion object {
        private const val DEBOUNCE_MS = 150L
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

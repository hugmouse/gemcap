package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.ui.model.SearchState

class SearchManager {
    var searchState by mutableStateOf(SearchState())
        private set

    fun toggleSearch() {
        searchState = if (searchState.isActive) {
            SearchState()
        } else {
            searchState.copy(isActive = true)
        }
    }

    fun search(query: String, content: List<GeminiContent>?) {
        if (content == null) return

        if (query.isBlank()) {
            searchState = searchState.copy(
                query = query,
                results = persistentListOf(),
                currentResultIndex = -1
            )
            return
        }

        val results = content.mapIndexedNotNull { index, item ->
            val text = when (item) {
                is GeminiContent.Text -> item.text
                is GeminiContent.Heading -> item.text
                is GeminiContent.Link -> item.text
                is GeminiContent.ListItem -> item.text
                is GeminiContent.Quote -> item.text
                is GeminiContent.Preformatted -> item.text
                else -> null
            }
            if (text?.contains(query, ignoreCase = true) == true) {
                index
            } else {
                null
            }
        }

        searchState = searchState.copy(
            query = query,
            results = results.toImmutableList(),
            currentResultIndex = if (results.isNotEmpty()) 0 else -1
        )
    }

    fun goToNextResult() {
        if (searchState.results.isEmpty()) return
        searchState = searchState.copy(
            currentResultIndex = (searchState.currentResultIndex + 1) % searchState.results.size
        )
    }

    fun goToPreviousResult() {
        if (searchState.results.isEmpty()) return
        searchState = searchState.copy(
            currentResultIndex = (searchState.currentResultIndex - 1 + searchState.results.size) % searchState.results.size
        )
    }
}

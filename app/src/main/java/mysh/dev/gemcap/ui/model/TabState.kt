package mysh.dev.gemcap.ui.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.domain.GeminiError
import java.util.UUID

/**
 * Cached page data for instant back/forward navigation.
 */
data class PageCache(
    val content: ImmutableList<GeminiContent>,
    val rawBody: String?,
    val title: String
)

data class ScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)
data class PersistedHistoryWindow(
    val entries: List<String>,
    val currentIndex: Int,
    val scrollPositions: Map<String, ScrollPosition>
)

// TODO: move it out of here somewhere, this does not belong here
const val HOME_URL = "about:home"

@Stable
class TabState(
    initialUrl: String = HOME_URL
) {
    val id: String = UUID.randomUUID().toString()

    var url by mutableStateOf(initialUrl)
    var displayedUrl by mutableStateOf(initialUrl)
        private set
    var title by mutableStateOf("New Tab")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<GeminiError?>(null)
    var content by mutableStateOf<ImmutableList<GeminiContent>>(persistentListOf())
    var rawBody by mutableStateOf<String?>(null)

    // Screenshot preview for tab switcher
    var previewBitmap by mutableStateOf<ImageBitmap?>(null)

    // History management - historyIndex is observable for BackHandler to work properly
    private val history = mutableListOf<String>()
    private var historyIndex by mutableIntStateOf(-1)

    // Page cache for back/forward navigation
    private val pageCache = mutableMapOf<String, PageCache>()

    // Scroll position cache for back/forward navigation
    private val scrollPositions = mutableMapOf<String, ScrollPosition>()

    /**
     * Get cached content for a URL, if available.
     */
    fun getCachedPage(pageUrl: String): PageCache? = pageCache[pageUrl]

    /**
     * Store page content in cache.
     */
    fun cachePage(
        pageUrl: String,
        pageContent: ImmutableList<GeminiContent>,
        pageRawBody: String?,
        pageTitle: String
    ) {
        pageCache[pageUrl] = PageCache(pageContent, pageRawBody, pageTitle)
    }

    /**
     * Apply cached page data to current state.
     */
    fun applyCachedPage(pageUrl: String, cached: PageCache) {
        content = cached.content
        rawBody = cached.rawBody
        title = cached.title
        error = null
        displayedUrl = pageUrl
    }

    fun getScrollPosition(pageUrl: String): ScrollPosition =
        scrollPositions[pageUrl] ?: ScrollPosition()

    fun saveScrollPosition(
        pageUrl: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int
    ) {
        scrollPositions[pageUrl] = ScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
        )
    }

    fun updateUrl(newUrl: String) {
        url = newUrl
    }

    fun updateDisplayedUrl(newUrl: String) {
        displayedUrl = newUrl
    }

    fun addToHistory(newUrl: String) {
        // If we are not at the end of history, clear forward history
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(newUrl)
        historyIndex = history.size - 1
        url = newUrl
    }

    fun canGoBack(): Boolean = historyIndex > 0
    fun canGoForward(): Boolean = historyIndex < history.size - 1

    fun goBack(): String? {
        if (canGoBack()) {
            historyIndex--
            url = history[historyIndex]
            return url
        }
        return null
    }

    fun goForward(): String? {
        if (canGoForward()) {
            historyIndex++
            url = history[historyIndex]
            return url
        }
        return null
    }
    fun restoreHistory(historyUrls: List<String>, index: Int) {
        history.clear()
        history.addAll(historyUrls.filter { it.isNotBlank() })
        if (history.isEmpty()) {
            historyIndex = -1
            return
        }
        historyIndex = index.coerceIn(0, history.lastIndex)
        url = history[historyIndex]
    }
    fun restoreScrollPositions(positions: Map<String, ScrollPosition>) {
        scrollPositions.clear()
        scrollPositions.putAll(positions)
    }
    fun buildPersistedHistoryWindow(maxEntries: Int): PersistedHistoryWindow {
        if (history.isEmpty()) {
            return PersistedHistoryWindow(emptyList(), 0, emptyMap())
        }
        val boundedMax = maxEntries.coerceAtLeast(1)
        val current = historyIndex.coerceIn(0, history.lastIndex)
        if (history.size <= boundedMax) {
            val entries = history.toList()
            val persistedScroll = entries.associateWith { key ->
                scrollPositions[key] ?: ScrollPosition()
            }
            return PersistedHistoryWindow(entries, current, persistedScroll)
        }
        val halfWindow = boundedMax / 2
        val start = (current - halfWindow).coerceIn(0, history.size - boundedMax)
        val end = start + boundedMax
        val entries = history.subList(start, end).toList()
        val persistedScroll = entries.associateWith { key ->
            scrollPositions[key] ?: ScrollPosition()
        }
        return PersistedHistoryWindow(entries, current - start, persistedScroll)
    }
}
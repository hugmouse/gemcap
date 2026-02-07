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

// TODO: move it out of here somewhere, this does not belong here
const val HOME_URL = "about:home"

@Stable
class TabState(
    initialUrl: String = HOME_URL
) {
    val id: String = UUID.randomUUID().toString()

    var url by mutableStateOf(initialUrl)
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
    fun applyCachedPage(cached: PageCache) {
        content = cached.content
        rawBody = cached.rawBody
        title = cached.title
        error = null
    }

    fun updateUrl(newUrl: String) {
        url = newUrl
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
}
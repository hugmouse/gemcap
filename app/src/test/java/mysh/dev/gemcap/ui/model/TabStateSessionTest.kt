package mysh.dev.gemcap.ui.model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class TabStateSessionTest {
    @Test
    fun persistedHistoryWindowKeepsAroundCurrentIndex() {
        val tab = TabState()
        repeat(100) { index ->
            tab.addToHistory("gemini://$index")
        }
        repeat(30) {
            tab.goBack()
        }
        val window = tab.buildPersistedHistoryWindow(maxEntries = 50)
        assertEquals(50, window.entries.size)
        assertEquals("gemini://44", window.entries.first())
        assertEquals("gemini://93", window.entries.last())
        assertEquals(25, window.currentIndex)
    }
    @Test
    fun restoreHistoryAndScrollPositionsRestoresNavigationState() {
        val tab = TabState()
        tab.restoreHistory(
            historyUrls = listOf("gemini://a", "gemini://b", "gemini://c"),
            index = 1
        )
        tab.restoreScrollPositions(
            mapOf(
                "gemini://a" to ScrollPosition(1, 2),
                "gemini://b" to ScrollPosition(3, 4)
            )
        )
        assertEquals("gemini://b", tab.url)
        assertTrue(tab.canGoBack())
        assertTrue(tab.canGoForward())
        assertEquals(3, tab.getScrollPosition("gemini://b").firstVisibleItemIndex)
        assertEquals(4, tab.getScrollPosition("gemini://b").firstVisibleItemScrollOffset)
    }
}
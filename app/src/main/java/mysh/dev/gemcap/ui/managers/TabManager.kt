package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mysh.dev.gemcap.data.TabSessionState
import mysh.dev.gemcap.ui.model.ScrollPosition
import mysh.dev.gemcap.ui.model.TabState

class TabManager(
    private val getHomePage: () -> String,
    private val onTabChanged: () -> Unit
) {
    val tabs = mutableStateListOf<TabState>()

    var activeTabId by mutableStateOf<String?>(null)
        private set

    val activeTab: TabState?
        get() = tabs.find { it.id == activeTabId }

    fun initialize(initialTabs: List<TabSessionState> = emptyList(), activeIndex: Int = 0) {
        tabs.clear()
        val restoredTabs = if (initialTabs.isNotEmpty()) {
            initialTabs.mapNotNull { sessionTab ->
                if (sessionTab.entries.isEmpty()) {
                    return@mapNotNull null
                }
                val safeIndex = sessionTab.currentIndex.coerceIn(0, sessionTab.entries.lastIndex)
                val currentEntry = sessionTab.entries[safeIndex]
                val tab = TabState(initialUrl = currentEntry.url)
                tab.restoreHistory(
                    historyUrls = sessionTab.entries.map { it.url },
                    index = safeIndex
                )
                tab.restoreScrollPositions(
                    sessionTab.entries.associate { entry ->
                        entry.url to ScrollPosition(
                            firstVisibleItemIndex = entry.scrollIndex,
                            firstVisibleItemScrollOffset = entry.scrollOffset
                        )
                    }
                )
                tab
            }
        } else {
            listOf(TabState(initialUrl = getHomePage()))
        }
        if (restoredTabs.isEmpty()) {
            tabs.add(TabState(initialUrl = getHomePage()))
        } else {
            tabs.addAll(restoredTabs)
        }
        activeTabId = tabs[activeIndex.coerceIn(0, tabs.lastIndex)].id
        onTabChanged()
    }

    fun addNewTab(): TabState {
        val newTab = TabState(initialUrl = getHomePage())
        tabs.add(newTab)
        activeTabId = newTab.id
        onTabChanged()
        return newTab
    }

    fun closeTab(tabId: String) {
        val tab = tabs.find { it.id == tabId } ?: return
        val index = tabs.indexOf(tab)
        tabs.remove(tab)

        if (activeTabId == tabId) {
            if (tabs.isNotEmpty()) {
                val newIndex = if (index > 0) index - 1 else 0
                activeTabId = tabs[newIndex].id
            } else {
                addNewTab()
            }
        }
        onTabChanged()
    }

    fun selectTab(tabId: String) {
        activeTabId = tabId
        onTabChanged()
    }

    fun openInNewTab(url: String): TabState {
        val newTab = TabState(initialUrl = url)
        tabs.add(newTab)
        onTabChanged()
        return newTab
    }

    fun openImageInNewTab(imageUrl: String): TabState {
        val newTab = TabState(initialUrl = imageUrl)
        tabs.add(newTab)
        activeTabId = newTab.id
        onTabChanged()
        return newTab
    }
}

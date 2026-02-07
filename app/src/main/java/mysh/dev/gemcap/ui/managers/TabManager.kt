package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    fun initialize() {
        val initialTab = TabState(initialUrl = getHomePage())
        tabs.add(initialTab)
        activeTabId = initialTab.id
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
    }

    fun selectTab(tabId: String) {
        activeTabId = tabId
        onTabChanged()
    }

    fun openInNewTab(url: String): TabState {
        val newTab = TabState(initialUrl = url)
        tabs.add(newTab)
        return newTab
    }

    fun openImageInNewTab(imageUrl: String): TabState {
        val newTab = TabState(initialUrl = imageUrl)
        tabs.add(newTab)
        activeTabId = newTab.id
        return newTab
    }
}

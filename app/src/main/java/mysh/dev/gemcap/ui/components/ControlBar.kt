package mysh.dev.gemcap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.ui.callbacks.BrowserCallbacks
import mysh.dev.gemcap.ui.components.controlBarComponents.AddressBar
import mysh.dev.gemcap.ui.components.controlBarComponents.NavigationButtons
import mysh.dev.gemcap.ui.components.controlBarComponents.SearchTextOnPageBar
import mysh.dev.gemcap.ui.components.controlBarComponents.TabCounterButton
import mysh.dev.gemcap.ui.components.controlBarComponents.ToolbarMenu
import mysh.dev.gemcap.ui.model.AddressBarState
import mysh.dev.gemcap.ui.model.ToolbarState

@Composable
fun ControlBar(
    addressBarState: AddressBarState,
    toolbarState: ToolbarState,
    onShowTabSwitcher: () -> Unit,
    callbacks: BrowserCallbacks,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.surfaceVariant
    var searchQuery by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = borderColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (toolbarState.searchActive) {
            SearchTextOnPageBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { callbacks.onSearch(it) },
                onClose = { callbacks.onToggleSearch() },
                resultCount = toolbarState.searchResultCount,
                currentIndex = toolbarState.searchCurrentIndex,
                onPrevious = { callbacks.onGoToPreviousResult() },
                onNext = { callbacks.onGoToNextResult() },
                modifier = Modifier.weight(1f)
            )
        } else {
            NavigationButtons(
                canGoBack = toolbarState.canGoBack,
                onBack = { callbacks.onBack() },
                canGoForward = toolbarState.canGoForward,
                onForward = { callbacks.onForward() },
                onRefresh = { callbacks.onRefresh() },
                onHome = { callbacks.onHome() },
                isCompactMode = toolbarState.isCompactMode
            )

            AddressBar(
                url = addressBarState.url,
                onUrlChange = { callbacks.onUrlChange(it) },
                onGo = { url -> callbacks.onGo(url) },
                hasSecureConnection = addressBarState.hasSecureConnection,
                onConnectionInfoClick = { callbacks.onConnectionInfoClick() },
                suggestions = addressBarState.autocomplete.suggestions,
                showSuggestions = addressBarState.autocomplete.showSuggestions,
                onSuggestionClick = { callbacks.onSuggestionClick(it) },
                onSuggestionsDismiss = { callbacks.onSuggestionsDismiss() },
                modifier = Modifier.weight(1f)
            )

            if (toolbarState.isCompactMode) {
                IconButton(onClick = { callbacks.onNewTab() }) {
                    Icon(Icons.Default.Add, contentDescription = "New Tab")
                }
            }

            if (!toolbarState.isCompactMode) {
                IconButton(onClick = { callbacks.onIdentityClick() }) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Identity",
                        tint = if (toolbarState.hasActiveIdentity)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TabCounterButton(tabCount = toolbarState.tabCount, onClick = onShowTabSwitcher)

            if (!toolbarState.isCompactMode) {
                IconButton(onClick = { callbacks.onToggleSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "Search in page")
                }
            }
        }

        ToolbarMenu(
            showMenu = toolbarState.showMenu,
            onMenuClick = { callbacks.onShowMenu() },
            onMenuDismiss = { callbacks.onDismissMenu() },
            isCompactMode = toolbarState.isCompactMode,
            isBookmarked = toolbarState.isBookmarked,
            hasActiveIdentity = toolbarState.hasActiveIdentity,
            onToggleSearch = { callbacks.onToggleSearch() },
            onIdentityClick = { callbacks.onIdentityClick() },
            onToggleBookmark = { callbacks.onToggleBookmark() },
            onBookmarksClick = { callbacks.onShowBookmarks() },
            onHistoryClick = { callbacks.onShowHistory() },
            onSetAsHomePage = { callbacks.onSetAsHomePage() },
            onSettingsClick = { callbacks.onShowSettings() }
        )
    }
}

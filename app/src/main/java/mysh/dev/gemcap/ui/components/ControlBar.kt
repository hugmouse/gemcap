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
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.ui.components.controlBarComponents.AddressBar
import mysh.dev.gemcap.ui.components.controlBarComponents.NavigationButtons
import mysh.dev.gemcap.ui.components.controlBarComponents.SearchTextOnPageBar
import mysh.dev.gemcap.ui.components.controlBarComponents.TabCounterButton
import mysh.dev.gemcap.ui.components.controlBarComponents.ToolbarMenu

@Composable
fun ControlBar(
    url: String,
    onUrlChange: (String) -> Unit,
    onGo: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canGoForward: Boolean,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit,
    onNewTab: () -> Unit,
    onTabsButtonClick: () -> Unit,
    tabCount: Int,
    isCompactMode: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    showMenu: Boolean,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCertificatesClick: () -> Unit,
    hasSecureConnection: Boolean,
    onConnectionInfoClick: () -> Unit,
    suggestions: ImmutableList<HistoryEntry>,
    showSuggestions: Boolean,
    onSuggestionClick: (HistoryEntry) -> Unit,
    onSuggestionsDismiss: () -> Unit,
    onSetAsHomePage: () -> Unit,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onIdentityClick: () -> Unit = {},
    hasActiveIdentity: Boolean = false,
    onSettingsClick: () -> Unit = {},
    searchResultCount: Int = 0,
    searchCurrentIndex: Int = -1,
    onSearchNext: () -> Unit = {},
    onSearchPrevious: () -> Unit = {}
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
        if (searchActive) {
            SearchTextOnPageBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = onSearch,
                onClose = onToggleSearch,
                resultCount = searchResultCount,
                currentIndex = searchCurrentIndex,
                onPrevious = onSearchPrevious,
                onNext = onSearchNext,
                modifier = Modifier.weight(1f)
            )
        } else {
            NavigationButtons(
                canGoBack = canGoBack,
                onBack = onBack,
                canGoForward = canGoForward,
                onForward = onForward,
                onRefresh = onRefresh,
                onHome = onHome,
                isCompactMode = isCompactMode
            )

            AddressBar(
                url = url,
                onUrlChange = onUrlChange,
                onGo = onGo,
                hasSecureConnection = hasSecureConnection,
                onConnectionInfoClick = onConnectionInfoClick,
                suggestions = suggestions,
                showSuggestions = showSuggestions,
                onSuggestionClick = onSuggestionClick,
                onSuggestionsDismiss = onSuggestionsDismiss,
                modifier = Modifier.weight(1f)
            )

            if (isCompactMode) {
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = "New Tab")
                }
            }

            if (!isCompactMode) {
                IconButton(onClick = onIdentityClick) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Identity",
                        tint = if (hasActiveIdentity)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TabCounterButton(tabCount = tabCount, onClick = onTabsButtonClick)

            if (!isCompactMode) {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search in page")
                }
            }
        }

        ToolbarMenu(
            showMenu = showMenu,
            onMenuClick = onMenuClick,
            onMenuDismiss = onMenuDismiss,
            isCompactMode = isCompactMode,
            isBookmarked = isBookmarked,
            hasActiveIdentity = hasActiveIdentity,
            onToggleSearch = onToggleSearch,
            onIdentityClick = onIdentityClick,
            onToggleBookmark = onToggleBookmark,
            onBookmarksClick = onBookmarksClick,
            onHistoryClick = onHistoryClick,
            onSetAsHomePage = onSetAsHomePage,
            onSettingsClick = onSettingsClick
        )
    }
}

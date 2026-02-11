package mysh.dev.gemcap.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mysh.dev.gemcap.BuildConfig
import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.domain.GeminiError
import mysh.dev.gemcap.ui.callbacks.BrowserCallbacks
import mysh.dev.gemcap.ui.callbacks.BrowserCallbacksImpl
import mysh.dev.gemcap.ui.components.ControlBar
import mysh.dev.gemcap.ui.components.DialogOrchestrator
import mysh.dev.gemcap.ui.components.TopTabStrip
import mysh.dev.gemcap.ui.content.ContentItem
import mysh.dev.gemcap.ui.content.rememberCachedTextStyles
import mysh.dev.gemcap.ui.model.ContentUiState
import mysh.dev.gemcap.ui.model.AddressBarState
import mysh.dev.gemcap.ui.model.ToolbarState
import mysh.dev.gemcap.ui.model.DialogsUiState
import mysh.dev.gemcap.ui.model.HOME_URL
import mysh.dev.gemcap.ui.model.SearchState
import mysh.dev.gemcap.ui.model.TabState
import mysh.dev.gemcap.ui.model.TabsUiState
import mysh.dev.gemcap.util.ScreenshotUtils
import java.net.URLEncoder

private const val TAG = "Recomposition"

private inline fun logRecomposition(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(TAG, message())
    }
}

@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = viewModel(),
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onFontSizeChanged: (FontSize) -> Unit = {}
) {
    logRecomposition { ">>> BrowserScreen (root)" }

    var showTabSwitcher by remember { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current

    val callbacks = remember(viewModel, onThemeModeChanged, onFontSizeChanged, view, density) {
        BrowserCallbacksImpl(
            viewModel = viewModel,
            onThemeModeChanged = onThemeModeChanged,
            onFontSizeChanged = onFontSizeChanged,
            view = view,
            density = density
        )
    }

    val tabsState by remember {
        derivedStateOf {
            TabsUiState(
                tabs = viewModel.tabs.toImmutableList(),
                activeTabId = viewModel.activeTabId,
                activeTab = viewModel.activeTab
            )
        }
    }

    val configuration = LocalConfiguration.current
    // TODO: check if android API provides some screen sizes since this won't fly in long term
    val isCompactMode = configuration.screenWidthDp.dp < 600.dp

    val addressBarState by remember {
        derivedStateOf {
            val tab = viewModel.activeTab
            AddressBarState(
                url = tab?.url ?: "",
                hasSecureConnection = viewModel.certificateState.currentServerCertInfo != null,
                autocomplete = viewModel.autocompleteState
            )
        }
    }

    val toolbarState by remember {
        derivedStateOf {
            val tab = viewModel.activeTab
            ToolbarState(
                canGoBack = tab?.canGoBack() ?: false,
                canGoForward = tab?.canGoForward() ?: false,
                tabCount = viewModel.tabs.size,
                isCompactMode = isCompactMode,
                isBookmarked = viewModel.isCurrentPageBookmarked,
                showMenu = viewModel.panelState.showMenu,
                searchActive = viewModel.searchState.isActive,
                hasActiveIdentity = viewModel.hasActiveIdentityForCurrentUrl,
                searchResultCount = viewModel.searchState.results.size,
                searchCurrentIndex = viewModel.searchState.currentResultIndex
            )
        }
    }

    val dialogsState by remember {
        derivedStateOf {
            DialogsUiState(
                dialogState = viewModel.dialogState,
                panelState = viewModel.panelState,
                settingsState = viewModel.settingsState,
                certificateState = viewModel.certificateState,
                bookmarks = viewModel.bookmarks,
                history = viewModel.history,
                currentHost = viewModel.getCurrentHost(),
                currentPath = viewModel.getCurrentPath()
            )
        }
    }

    val contentState by remember {
        derivedStateOf {
            ContentUiState(
                activeTab = viewModel.activeTab,
                searchState = viewModel.searchState
            )
        }
    }

    val snackbarHostState = viewModel.snackbarHostState

    BrowserScaffold(
        modifier = modifier,
        tabsState = tabsState,
        addressBarState = addressBarState,
        toolbarState = toolbarState,
        dialogsState = dialogsState,
        contentState = contentState,
        showTabSwitcher = showTabSwitcher,
        onShowTabSwitcher = {
            viewModel.activeTab?.previewBitmap =
                ScreenshotUtils.captureAndCropScreenshot(view, density)
            showTabSwitcher = true
        },
        onDismissTabSwitcher = { showTabSwitcher = false },
        snackbarHostState = snackbarHostState,
        callbacks = callbacks
    )
}

@Composable
private fun BrowserScaffold(
    modifier: Modifier,
    tabsState: TabsUiState,
    addressBarState: AddressBarState,
    toolbarState: ToolbarState,
    dialogsState: DialogsUiState,
    contentState: ContentUiState,
    showTabSwitcher: Boolean,
    onShowTabSwitcher: () -> Unit,
    onDismissTabSwitcher: () -> Unit,
    snackbarHostState: SnackbarHostState,
    callbacks: BrowserCallbacks
) {
    logRecomposition { ">>> BrowserScaffold" }

    val activeTab = contentState.activeTab
    val panelState = dialogsState.panelState
    val homePageUrl = normalizeHomeUrl(dialogsState.settingsState.homePage)
    val canGoBack = activeTab?.canGoBack() == true
    val isAtHome = activeTab?.url == homePageUrl
    val shouldGoHome = activeTab != null && !canGoBack && !isAtHome

    BackHandler(
        enabled = (canGoBack || shouldGoHome) &&
                !showTabSwitcher &&
                !panelState.showBookmarks &&
                !panelState.showHistory &&
                !panelState.showSettings
    ) {
        if (canGoBack) {
            callbacks.onBack()
        } else if (shouldGoHome) {
            callbacks.onHome()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = {
                BrowserControlBar(
                    tabsState = tabsState,
                    addressBarState = addressBarState,
                    toolbarState = toolbarState,
                    onShowTabSwitcher = onShowTabSwitcher,
                    callbacks = callbacks
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            BrowserContent(
                activeTab = activeTab,
                contentPadding = innerPadding,
                searchState = contentState.searchState,
                callbacks = callbacks
            )
        }
    }

    if (showTabSwitcher) {
        TabSwitcherOverlay(
            tabs = tabsState.tabs,
            activeTabId = tabsState.activeTabId,
            onDismiss = onDismissTabSwitcher,
            callbacks = callbacks
        )
    }

    DialogOrchestrator(
        dialogState = dialogsState.dialogState,
        panelState = panelState,
        settingsState = dialogsState.settingsState,
        certificateState = dialogsState.certificateState,
        bookmarks = dialogsState.bookmarks,
        history = dialogsState.history,
        currentHost = dialogsState.currentHost,
        currentPath = dialogsState.currentPath,
        currentPageUrl = activeTab?.url ?: "",
        callbacks = callbacks
    )
}

private fun normalizeHomeUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed == HOME_URL || trimmed == "about:gemtext") {
        return trimmed
    }
    if (trimmed.contains("://")) {
        return trimmed
    }
    val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ")
    return if (looksLikeUrl) {
        "gemini://$trimmed"
    } else {
        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        "gemini://gemini-search.mysh.dev/?$encodedQuery"
    }
}


/**
 * Top bar with tabs and controls. Recomposition here means: url/canGoBack/canGoForward changed.
 */
@Composable
private fun BrowserControlBar(
    tabsState: TabsUiState,
    addressBarState: AddressBarState,
    toolbarState: ToolbarState,
    onShowTabSwitcher: () -> Unit,
    callbacks: BrowserCallbacks
) {
    logRecomposition { ">>> BrowserControlBar" }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
        ) {
            if (!toolbarState.isCompactMode) {
                TabStripSection(
                    tabs = tabsState.tabs,
                    activeTabId = tabsState.activeTabId,
                    callbacks = callbacks
                )
            }

            if (addressBarState.url.isNotEmpty() || tabsState.activeTab != null) {
                ControlBarSection(
                    addressBarState = addressBarState,
                    toolbarState = toolbarState,
                    onShowTabSwitcher = onShowTabSwitcher,
                    callbacks = callbacks
                )
            }
        }
    }
}

/**
 * Tab strip section. Recomposition here means: tabs list changed.
 */
@Composable
private fun TabStripSection(
    tabs: ImmutableList<TabState>,
    activeTabId: String?,
    callbacks: BrowserCallbacks
) {
    logRecomposition { ">>> TabStripSection" }

    TopTabStrip(
        tabs = tabs,
        activeTabId = activeTabId,
        onTabSelected = { callbacks.onSelectTab(it) },
        onTabClosed = { callbacks.onCloseTab(it) },
        onNewTab = { callbacks.onNewTab() }
    )
}

@Composable
private fun ControlBarSection(
    addressBarState: AddressBarState,
    toolbarState: ToolbarState,
    onShowTabSwitcher: () -> Unit,
    callbacks: BrowserCallbacks
) {
    logRecomposition { ">>> ControlBarSection (url=${addressBarState.url})" }

    ControlBar(
        addressBarState = addressBarState,
        toolbarState = toolbarState,
        onShowTabSwitcher = onShowTabSwitcher,
        callbacks = callbacks
    )
}

@Composable
private fun BrowserContent(
    activeTab: TabState?,
    contentPadding: PaddingValues,
    searchState: SearchState,
    callbacks: BrowserCallbacks
) {
    logRecomposition {
        ">>> BrowserContent (isLoading=${activeTab?.isLoading}, hasError=${activeTab?.error != null})"
    }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        callbacks.onSuggestionsDismiss()
                    }
                )
            }
    ) {

        when {
            activeTab == null -> {
                Text("No Tabs Open", modifier = Modifier.align(Alignment.Center))
            }

            activeTab.isLoading && activeTab.content.isEmpty() -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            activeTab.error != null -> {
                ErrorDisplay(
                    error = activeTab.error!!,
                    onRetry = { callbacks.onRefresh() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                GeminiContentList(
                    tab = activeTab,
                    content = activeTab.content,
                    isLoading = activeTab.isLoading,
                    searchState = searchState,
                    callbacks = callbacks
                )
            }
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    logRecomposition { ">>> LoadingIndicator" }
    CircularProgressIndicator(modifier = modifier)
}

@Composable
private fun ErrorDisplay(
    error: GeminiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    logRecomposition { ">>> ErrorDisplay" }
    Column(
        modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (error.isTemporary) Icons.Default.Info else Icons.Default.Close,
            contentDescription = null,
            tint = if (error.isTemporary) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (error.statusCode != 0) {
            Text(
                text = "Status ${error.statusCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (error.message.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (error.canRetry) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiContentList(
    tab: TabState,
    content: ImmutableList<GeminiContent>,
    isLoading: Boolean,
    searchState: SearchState,
    callbacks: BrowserCallbacks
) {
    logRecomposition { ">>> GeminiContentList (${content.size} items)" }

    val cachedStyles = rememberCachedTextStyles()
    val currentPageUrl = tab.displayedUrl
    val listState = remember(tab.id, currentPageUrl) {
        val scrollPosition = tab.getScrollPosition(currentPageUrl)
        LazyListState(
            firstVisibleItemIndex = scrollPosition.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = scrollPosition.firstVisibleItemScrollOffset
        )
    }
    val pullToRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(tab.id, currentPageUrl, listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                tab.saveScrollPosition(
                    pageUrl = currentPageUrl,
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = offset
                )
            }
    }

    LaunchedEffect(currentPageUrl, searchState.currentResultIndex) {
        if (searchState.currentResultIndex != -1) {
            listState.animateScrollToItem(searchState.currentResultIndex)
        }
    }

    // For some reason "PullToRefreshBox" is an experimental API, so we have to opt-in
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { callbacks.onRefresh() },
        state = pullToRefreshState,
        modifier = Modifier.pointerInput(pullToRefreshState, isLoading) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.type != PointerEventType.Release) {
                        continue
                    }
                    if (isLoading || pullToRefreshState.distanceFraction <= 0f) {
                        continue
                    }
                    scope.launch {
                        // Allow nested scroll to handle release when it can.
                        yield()
                        if (pullToRefreshState.isAnimating) {
                            return@launch
                        }
                        if (pullToRefreshState.distanceFraction >= 1f) {
                            pullToRefreshState.animateToThreshold()
                            callbacks.onRefresh()
                        } else {
                            pullToRefreshState.animateToHidden()
                        }
                    }
                }
            }
        }
    ) {
        SelectionContainer {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = content, key = { _, item -> item.id }) { index, item ->
                        val highlight =
                            searchState.query.isNotBlank() && index == searchState.currentResultIndex
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(840.dp)
                            ) { // TODO: make configurable
                                ContentItem(
                                    item = item,
                                    styles = cachedStyles,
                                    searchQuery = searchState.query,
                                    highlight = highlight,
                                    onLinkClick = { callbacks.onLinkClick(it) },
                                    onOpenImageInNewTab = { callbacks.onOpenImageInNewTab(it) },
                                    onCopyLink = { callbacks.onCopyLink(it) },
                                    onOpenInNewTab = { callbacks.onOpenInNewTab(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSwitcherOverlay(
    tabs: ImmutableList<TabState>,
    activeTabId: String?,
    onDismiss: () -> Unit,
    callbacks: BrowserCallbacks
) {
    logRecomposition { ">>> TabSwitcherOverlay" }

    TabSwitcherScreen(
        tabs = tabs,
        activeTabId = activeTabId,
        onTabSelected = { callbacks.onSelectTab(it) },
        onTabClosed = { callbacks.onCloseTab(it) },
        onNewTab = { callbacks.onNewTab() },
        onDismiss = onDismiss
    )
}

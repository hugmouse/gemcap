package mysh.dev.gemcap.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import mysh.dev.gemcap.data.BrowserRepository
import mysh.dev.gemcap.ui.managers.ConsoleManager
import mysh.dev.gemcap.ui.managers.LogcatReader
import mysh.dev.gemcap.data.ClientCertRepository
import mysh.dev.gemcap.media.GemcapPlayerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.IdentityImportStoreResult
import mysh.dev.gemcap.data.ImportResult
import mysh.dev.gemcap.data.SearchEngine
import mysh.dev.gemcap.data.SettingsRepository
import mysh.dev.gemcap.data.TabHistoryEntrySession
import mysh.dev.gemcap.data.TabSession
import mysh.dev.gemcap.data.TabSessionState
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.domain.CapsuleIdentityGenerator
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.DownloadPromptState
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.domain.GeminiError
import mysh.dev.gemcap.domain.GeminiParser
import mysh.dev.gemcap.domain.GeminiResponse
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.domain.ServerCertInfo
import mysh.dev.gemcap.domain.StableByteArray
import mysh.dev.gemcap.domain.TofuDomainMismatchState
import mysh.dev.gemcap.domain.TofuWarningState
import mysh.dev.gemcap.network.BackoffManager
import mysh.dev.gemcap.network.DEFAULT_MAX_RESPONSE_BODY_BYTES
import mysh.dev.gemcap.network.CertificateGenerator
import mysh.dev.gemcap.network.GeminiClient
import mysh.dev.gemcap.network.GeminiFetchResult
import mysh.dev.gemcap.network.IdentityParams
import mysh.dev.gemcap.ui.managers.BookmarkManager
import mysh.dev.gemcap.ui.managers.CertificateManager
import mysh.dev.gemcap.ui.managers.DialogManager
import mysh.dev.gemcap.ui.managers.EmbeddedMediaCache
import mysh.dev.gemcap.ui.managers.HistoryManager
import mysh.dev.gemcap.ui.managers.SearchManager
import mysh.dev.gemcap.ui.managers.SettingsManager
import mysh.dev.gemcap.ui.managers.TabManager
import mysh.dev.gemcap.ui.model.AutocompleteState
import mysh.dev.gemcap.ui.model.CertificateState
import mysh.dev.gemcap.ui.model.DialogState
import mysh.dev.gemcap.ui.model.HOME_URL
import mysh.dev.gemcap.ui.model.PanelState
import mysh.dev.gemcap.ui.model.SearchState
import mysh.dev.gemcap.ui.model.SettingsState
import mysh.dev.gemcap.ui.model.TabState
import mysh.dev.gemcap.R
import mysh.dev.gemcap.util.DownloadUtils
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

// Don't you love when one view model literally controls everything?
// TODO: try to refactor it
class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val EMBEDDED_MEDIA_CACHE_MAX_BYTES = 50 * 1024 * 1024
        private const val EMBEDDED_MEDIA_CACHE_TTL_MILLIS = 30L * 60L * 1000L
        private const val IMAGE_IN_MEMORY_MAX_BYTES = 8L * 1024L * 1024L
        private const val TAB_HISTORY_PERSIST_LIMIT = 50
        private const val TAB_SESSION_PERSIST_DEBOUNCE_MILLIS = 400L
    }

    private val certRepository = ClientCertRepository(application)
    private val consoleManager = ConsoleManager(
        getPanelState = { panelState },
        updatePanelState = { panelState = it }
    )
    private val logcatReader = LogcatReader(
        logger = consoleManager,
        scope = viewModelScope
    )
    private val client = GeminiClient(application, certRepository.getIdentityStorage(), consoleLogger = consoleManager)
    private val repository = BrowserRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val certGenerator = CertificateGenerator(certRepository.getIdentityStorage(), certRepository)
    private val backoffManager = BackoffManager()
    private val clipboardManager = application.getSystemService(ClipboardManager::class.java)
    private val embeddedMediaCache = EmbeddedMediaCache(
        maxBytes = EMBEDDED_MEDIA_CACHE_MAX_BYTES,
        ttlMillis = EMBEDDED_MEDIA_CACHE_TTL_MILLIS
    )
    val playerManager = GemcapPlayerManager(application)

    // Track loading jobs per tab for cancellation
    private val loadingJobs = mutableMapOf<String, Job>()
    private val embeddedMediaLoadingJobs = mutableMapOf<String, Job>()
    private var tabSessionPersistJob: Job? = null

    // Panel state (shared across managers)
    var panelState by mutableStateOf(PanelState())
        private set

    val snackbarHostState = SnackbarHostState()


    // Managers
    private val settingsManager = SettingsManager(
        settingsRepository = settingsRepository,
        getPanelState = { panelState },
        updatePanelState = { panelState = it }
    )

    private val tabManager = TabManager(
        getHomePage = { settingsManager.getHomePage() },
        onTabChanged = {
            bookmarkManager.updateBookmarkStatus(activeTab?.url)
            schedulePersistTabSession()
        }
    )

    private val searchManager = SearchManager()

    private val historyManager = HistoryManager(
        repository = repository,
        scope = viewModelScope,
        getPanelState = { panelState },
        updatePanelState = { panelState = it }
    )

    private val bookmarkManager = BookmarkManager(
        application = application,
        repository = repository,
        getPanelState = { panelState },
        updatePanelState = { panelState = it }
    )

    private val dialogManager = DialogManager(
        client = client,
        backoffManager = backoffManager,
        scope = viewModelScope,
        onBackoffExpired = { loadPage(addToHistory = false, forceReload = true) }
    )

    private val certificateManager = CertificateManager(
        certRepository = certRepository,
        certGenerator = certGenerator,
        scope = viewModelScope,
        getDialogState = { dialogManager.getState() },
        updateDialogState = { dialogManager.updateState(it) },
        getPanelState = { panelState },
        updatePanelState = { panelState = it },
        onError = { error -> activeTab?.error = error },
        onCertificateSelected = { url ->
            activeTab?.updateUrl(url)
            loadPage(addToHistory = true)
        }
    )

    private var pendingInputCertAlias: String? = null

    // Expose manager states
    val tabs get() = tabManager.tabs
    val activeTabId: String? get() = tabManager.activeTabId
    val activeTab: TabState? get() = tabManager.activeTab

    val searchState: SearchState get() = searchManager.searchState
    val dialogState: DialogState get() = dialogManager.getState()
    val autocompleteState: AutocompleteState get() = historyManager.autocompleteState
    val settingsState: SettingsState get() = settingsManager.settingsState
    val certificateState: CertificateState get() = certificateManager.certificateState
    val bookmarks: ImmutableList<Bookmark> get() = bookmarkManager.bookmarks
    val history: ImmutableList<HistoryEntry> get() = historyManager.history
    val isCurrentPageBookmarked: Boolean get() = bookmarkManager.isCurrentPageBookmarked
    val consoleEntries get() = consoleManager.entries
    val consoleErrorCount get() = consoleManager.errorCount

    val hasActiveIdentityForCurrentUrl: Boolean
        get() = activeTab?.url?.let { url: String -> certificateManager.hasActiveIdentityForUrl(url) }
            ?: false

    private sealed class FetchResult {
        data class Success(
            val response: GeminiResponse,
            val finalUrl: String,
            val serverCertInfo: ServerCertInfo?
        ) : FetchResult()

        data class InputRequired(
            val promptText: String,
            val targetUrl: String,
            val isSensitive: Boolean
        ) : FetchResult()

        data class TofuWarning(val state: TofuWarningState) : FetchResult()
        data class TofuDomainMismatch(
            val host: String,
            val certDomains: List<String>,
            val pendingUrl: String
        ) : FetchResult()

        data class TofuExpired(val host: String, val expiredAt: Long, val pendingUrl: String) :
            FetchResult()

        data class TofuNotYetValid(val host: String, val notBefore: Long, val pendingUrl: String) :
            FetchResult()

        data class CertificateRequired(val statusCode: Int, val meta: String, val url: String) :
            FetchResult()

        data class SlowDown(val meta: String, val url: String) : FetchResult()
        data class Error(val error: GeminiError) : FetchResult()
    }
    private sealed class EmbeddedMediaFetchResult {
        data class Success(val response: GeminiResponse, val finalUrl: String) :
            EmbeddedMediaFetchResult()
        data class Error(val message: String) : EmbeddedMediaFetchResult()
    }

    private sealed class RedirectLoopResult<out T> {
        data class Complete<T>(val value: T) : RedirectLoopResult<T>()
        data class Redirect(val statusCode: Int, val targetUrl: String) : RedirectLoopResult<Nothing>()
    }
    private val restoredTabSession = settingsRepository.tabSession

    init {
        tabManager.initialize(
            initialTabs = restoredTabSession?.tabs.orEmpty(),
            activeIndex = restoredTabSession?.activeTabIndex ?: 0
        )
        bookmarkManager.refresh()
        historyManager.refresh()
        certificateManager.refresh()
        if (certRepository.consumeBetaMigrationNotice()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.identity_migration_notice))
            }
        }
        if (restoredTabSession == null) {
            loadPage(addToHistory = true)
        } else {
            reloadRestoredTabs()
        }
    }

    // Console
    fun toggleConsole() {
        if (panelState.showConsole) {
            consoleManager.dismissConsole()
            logcatReader.stop()
        } else {
            consoleManager.showConsole()
        }
    }

    fun dismissConsole() {
        consoleManager.dismissConsole()
        logcatReader.stop()
    }

    fun clearConsole() = consoleManager.clear()
    fun startLogcat() = logcatReader.start()
    fun stopLogcat() = logcatReader.stop()

    override fun onCleared() {
        super.onCleared()
        logcatReader.stop()
        playerManager.release()
        // Clean up temp media files
        File(getApplication<Application>().cacheDir, "media").deleteRecursively()
    }

    /** Delete any temp files backing embedded media in the given tab's current content. */
    private fun cleanupTempMediaFiles(tab: TabState) {
        tab.content.filterIsInstance<GeminiContent.EmbeddedMedia>()
            .mapNotNull { it.dataFilePath }
            .forEach { File(it).delete() }
    }

    private fun reloadRestoredTabs() {
        // Only load the active tab immediately; other tabs load on first selection
        activeTabId ?: return
        loadPage(addToHistory = false)
    }

    // Search delegation
    fun toggleSearch() = searchManager.toggleSearch()
    fun search(query: String) = searchManager.search(query, activeTab?.content)
    fun goToNextResult() = searchManager.goToNextResult()
    fun goToPreviousResult() = searchManager.goToPreviousResult()

    // Tab delegation
    fun addNewTab() {
        tabManager.addNewTab()
        loadPage(addToHistory = true)
    }

    fun closeTab(tabId: String) {
        loadingJobs[tabId]?.cancel()
        loadingJobs.remove(tabId)
        cancelEmbeddedMediaJobsForTab(tabId)
        if (playerManager.currentMediaKey?.startsWith("${tabId}:") == true) {
            playerManager.release()
        }
        tabManager.tabs.find { it.id == tabId }?.let { cleanupTempMediaFiles(it) }
        tabManager.closeTab(tabId)
    }

    fun selectTab(tabId: String) {
        tabManager.selectTab(tabId)
        bookmarkManager.updateBookmarkStatus(activeTab?.url)
        // Lazy-load tabs that haven't been loaded yet (e.g., restored from session)
        val tab = activeTab ?: return
        if (!tab.isLoading && tab.content.isEmpty() && tab.error == null && tab.rawBody == null) {
            loadPage(addToHistory = false)
        }
    }

    fun onUrlChange(newUrl: String) {
        historyManager.updateSuggestions(newUrl)
    }

    fun selectSuggestion(entry: HistoryEntry) {
        activeTab?.updateUrl(entry.url)
        historyManager.clearAutocomplete()
        loadPage(addToHistory = true)
    }

    fun dismissSuggestions() = historyManager.dismissSuggestions()

    // Input handling
    fun submitInput(userInput: String) {
        val prompt = dialogManager.getInputPrompt() ?: return
        dialogManager.dismissInputPrompt()

        val encodedInput = URLEncoder.encode(userInput, "UTF-8")
        val targetUrl = prompt.targetUrl
        val newUrl = if (targetUrl.contains("%s")) {
            targetUrl.replace("%s", encodedInput)
        } else {
            val uri = URI(targetUrl)
            URI(uri.scheme, uri.authority, uri.path, encodedInput, null).toString()
        }

        pendingInputCertAlias?.let { alias ->
            certificateManager.setPendingCertAlias(alias)
        }
        pendingInputCertAlias = null

        activeTab?.updateUrl(newUrl)
        loadPage(addToHistory = true)
    }

    fun dismissInputPrompt() {
        pendingInputCertAlias = null
        dialogManager.dismissInputPrompt()
    }

    // TOFU handling
    fun acceptTofuWarning() {
        if (dialogManager.acceptTofuWarning()) {
            loadPage(addToHistory = true)
        }
    }

    fun rejectTofuWarning() = dialogManager.rejectTofuWarning()

    // Domain mismatch handling
    fun acceptDomainMismatch() {
        if (dialogManager.acceptDomainMismatch() != null) {
            loadPage(addToHistory = true)
        }
    }

    fun rejectDomainMismatch() = dialogManager.rejectDomainMismatch()

    // Download handling
    fun confirmDownload() {
        val state = dialogManager.getDownloadPrompt() ?: return
        dialogManager.dismissDownloadPrompt()

        val result = DownloadUtils.saveToDownloads(
            context = getApplication(),
            data = state.data,
            fileName = state.fileName,
            mimeType = state.mimeType
        )

        val app = getApplication<Application>()
        dialogManager.setDownloadMessage(
            result.fold(
                onSuccess = { app.getString(R.string.download_saved, it) },
                onFailure = { app.getString(R.string.download_failed, it.message ?: it.toString()) }
            )
        )
    }

    fun dismissDownloadPrompt() = dialogManager.dismissDownloadPrompt()
    fun schedulePersistTabSession() {
        tabSessionPersistJob?.cancel()
        tabSessionPersistJob = viewModelScope.launch {
            delay(TAB_SESSION_PERSIST_DEBOUNCE_MILLIS)
            persistTabSession()
        }
    }
    fun onTabSessionStateChanged() = schedulePersistTabSession()
    fun persistTabSession() {
        tabSessionPersistJob?.cancel()
        tabSessionPersistJob = null
        val currentTabs = tabManager.tabs
        if (currentTabs.isEmpty()) {
            settingsRepository.tabSession = null
            return
        }
        val activeIndex = currentTabs.indexOfFirst { it.id == tabManager.activeTabId }
            .let { index -> if (index >= 0) index else 0 }
        settingsRepository.tabSession = TabSession(
            tabs = currentTabs.mapNotNull { tab ->
                val window = tab.buildPersistedHistoryWindow(TAB_HISTORY_PERSIST_LIMIT)
                if (window.entries.isEmpty()) {
                    return@mapNotNull null
                }
                TabSessionState(
                    entries = window.entries.map { url ->
                        val scroll = window.scrollPositions[url] ?: tab.getScrollPosition(url)
                        TabHistoryEntrySession(
                            url = url.trim().ifBlank { settingsManager.getHomePage() },
                            scrollIndex = scroll.firstVisibleItemIndex,
                            scrollOffset = scroll.firstVisibleItemScrollOffset
                        )
                    },
                    currentIndex = window.currentIndex.coerceIn(0, window.entries.lastIndex)
                )
            },
            activeTabIndex = activeIndex
        )
    }

    private fun loadLocalPage(url: String): String? {
        val assetName = when (url) {
            HOME_URL -> "home.gmi"
            "about:gemtext" -> "gemtext.gmi"
            else -> return null
        }
        return try {
            getApplication<Application>().assets.open(assetName).bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Log.e("BrowserViewModel", "Failed to load local page: $url", e)
            null
        }
    }

    private suspend fun fetchWithRedirects(
        initialUrl: String,
        maxRedirects: Int = 5,
        certAlias: String? = null
    ): FetchResult {
        return followRedirects(
            initialUrl = initialUrl,
            certAlias = certAlias,
            maxRedirects = maxRedirects,
            mapResult = { fetchResult, currentUrl ->
                when (fetchResult) {
                    is GeminiFetchResult.Error -> {
                        RedirectLoopResult.Complete(
                            FetchResult.Error(
                                GeminiError(
                                    statusCode = 0,
                                    message = fetchResult.exception.message ?: "Connection error",
                                    isTemporary = true,
                                    canRetry = true
                                )
                            )
                        )
                    }

                    is GeminiFetchResult.TofuWarning -> {
                        RedirectLoopResult.Complete(
                            FetchResult.TofuWarning(
                                TofuWarningState(
                                    host = fetchResult.host,
                                    port = fetchResult.port,
                                    oldFingerprint = fetchResult.oldFingerprint,
                                    newFingerprint = fetchResult.newFingerprint,
                                    newExpiry = fetchResult.newExpiry,
                                    isCATrusted = fetchResult.isCATrusted,
                                    pendingUrl = fetchResult.pendingUrl
                                )
                            )
                        )
                    }

                    is GeminiFetchResult.TofuDomainMismatch -> {
                        RedirectLoopResult.Complete(
                            FetchResult.TofuDomainMismatch(
                                host = fetchResult.host,
                                certDomains = fetchResult.certDomains,
                                pendingUrl = fetchResult.pendingUrl
                            )
                        )
                    }

                    is GeminiFetchResult.TofuExpired -> {
                        RedirectLoopResult.Complete(
                            FetchResult.TofuExpired(
                                host = fetchResult.host,
                                expiredAt = fetchResult.expiredAt,
                                pendingUrl = fetchResult.pendingUrl
                            )
                        )
                    }

                    is GeminiFetchResult.TofuNotYetValid -> {
                        RedirectLoopResult.Complete(
                            FetchResult.TofuNotYetValid(
                                host = fetchResult.host,
                                notBefore = fetchResult.notBefore,
                                pendingUrl = fetchResult.pendingUrl
                            )
                        )
                    }

                    is GeminiFetchResult.CertificateRequired -> {
                        RedirectLoopResult.Complete(
                            FetchResult.CertificateRequired(
                                statusCode = fetchResult.statusCode,
                                meta = fetchResult.meta,
                                url = fetchResult.url
                            )
                        )
                    }

                    is GeminiFetchResult.Success -> {
                        val response = fetchResult.response
                        when (response.status) {
                            in 10..19 -> {
                                RedirectLoopResult.Complete(
                                    FetchResult.InputRequired(
                                        promptText = response.meta,
                                        targetUrl = currentUrl,
                                        isSensitive = response.status == 11
                                    )
                                )
                            }

                            in 30..39 -> {
                                RedirectLoopResult.Redirect(
                                    statusCode = response.status,
                                    targetUrl = response.meta
                                )
                            }

                            44 -> {
                                RedirectLoopResult.Complete(
                                    FetchResult.SlowDown(meta = response.meta, url = currentUrl)
                                )
                            }

                            in 40..49 -> {
                                RedirectLoopResult.Complete(
                                    FetchResult.Error(
                                        GeminiError(
                                            statusCode = response.status,
                                            message = response.meta,
                                            isTemporary = true,
                                            canRetry = true
                                        )
                                    )
                                )
                            }

                            in 50..59 -> {
                                RedirectLoopResult.Complete(
                                    FetchResult.Error(
                                        GeminiError(
                                            statusCode = response.status,
                                            message = response.meta,
                                            isTemporary = false,
                                            canRetry = false
                                        )
                                    )
                                )
                            }

                            in 20..29 -> {
                                dialogManager.clearBackoff(currentUrl)
                                RedirectLoopResult.Complete(
                                    FetchResult.Success(
                                        response,
                                        currentUrl,
                                        fetchResult.serverCertInfo
                                    )
                                )
                            }

                            else -> {
                                RedirectLoopResult.Complete(
                                    FetchResult.Error(
                                        GeminiError(
                                            statusCode = response.status,
                                            message = response.meta.ifBlank { "Unknown status code" },
                                            isTemporary = true,
                                            canRetry = true
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            },
            onInvalidRedirect = { statusCode, message ->
                FetchResult.Error(
                    GeminiError(
                        statusCode = statusCode,
                        message = message,
                        isTemporary = true,
                        canRetry = false
                    )
                )
            },
            onTooManyRedirects = { redirectLimit ->
                FetchResult.Error(
                    GeminiError(
                        statusCode = 0,
                        message = "Too many redirects (max $redirectLimit)",
                        isTemporary = true,
                        canRetry = false
                    )
                )
            }
        )
    }

    private fun handleSuccess(
        result: FetchResult.Success,
        tab: TabState,
        targetUrl: String,
        addToHistory: Boolean
    ) {
        val response = result.response
        val finalUrl = result.finalUrl
        certificateManager.updateServerCertInfo(result.serverCertInfo)

        if (finalUrl != targetUrl) {
            tab.updateUrl(finalUrl)
            if (addToHistory) {
                tab.addToHistory(finalUrl)
            }
        }
        tab.capsuleIdentity = CapsuleIdentityGenerator.fromUrl(finalUrl)

        if (response.status !in 20..29) {
            tab.error = GeminiError(
                statusCode = response.status,
                message = response.meta,
                isTemporary = true,
                canRetry = true
            )
            return
        }

        val mimeType = response.meta.lowercase().split(";").first().trim()
        when {
            mimeType.startsWith("image/") -> handleImageResponse(response, tab, finalUrl, mimeType)
            mimeType == "text/gemini" || mimeType.isEmpty() ->
                handleGemtextResponse(response, tab, finalUrl)
            mimeType.startsWith("text/") -> handleTextResponse(response, tab, finalUrl, mimeType)
            else -> handleDownloadResponse(response, tab, finalUrl, mimeType)
        }

        tab.updateDisplayedUrl(finalUrl)
        tab.cachePage(finalUrl, tab.content, tab.rawBody, tab.title, tab.capsuleIdentity)
        if (addToHistory) {
            historyManager.record(finalUrl, tab.title)
        }
        bookmarkManager.updateBookmarkStatus(finalUrl)
    }

    private fun handleImageResponse(
        response: GeminiResponse,
        tab: TabState,
        finalUrl: String,
        mimeType: String
    ) {
        val imageBytes = response.body ?: ByteArray(0)
        tab.rawBody = null
        tab.content = listOf(
            GeminiContent.Image(
                id = 0,
                data = StableByteArray(imageBytes),
                mimeType = mimeType,
                url = finalUrl
            )
        ).toImmutableList()
        tab.title = finalUrl.substringAfterLast("/").ifEmpty { "Image" }
    }

    private fun handleGemtextResponse(response: GeminiResponse, tab: TabState, finalUrl: String) {
        val bodyString = response.body?.toString(Charsets.UTF_8) ?: ""
        tab.rawBody = bodyString
        val parsedContent = GeminiParser.parse(bodyString, baseUrl = finalUrl).toImmutableList()
        tab.content = parsedContent
        parsedContent.filterIsInstance<GeminiContent.Heading>()
            .firstOrNull { it.level == 1 }
            ?.let { tab.title = it.text }
    }

    private fun handleTextResponse(
        response: GeminiResponse,
        tab: TabState,
        finalUrl: String,
        mimeType: String
    ) {
        val bodyString = response.body?.toString(Charsets.UTF_8) ?: ""
        tab.rawBody = bodyString
        tab.content = listOf(
            GeminiContent.Preformatted(id = 0, text = bodyString, alt = mimeType)
        ).toImmutableList()
        tab.title = finalUrl.substringAfterLast("/").ifEmpty { finalUrl.removePrefix("gemini://") }
    }

    private fun handleDownloadResponse(
        response: GeminiResponse,
        tab: TabState,
        finalUrl: String,
        mimeType: String
    ) {
        val data = response.body ?: ByteArray(0)
        val fileName = DownloadUtils.suggestFileName(finalUrl, mimeType)
        dialogManager.showDownloadPrompt(
            DownloadPromptState(
                url = finalUrl,
                fileName = fileName,
                mimeType = mimeType,
                data = data
            )
        )
        tab.content = listOf(
            GeminiContent.Text(id = 0, text = getApplication<Application>().getString(R.string.download_available, fileName))
        ).toImmutableList()
        tab.title = fileName
    }

    private fun handleTofuDomainMismatch(result: FetchResult.TofuDomainMismatch) {
        dialogManager.showDomainMismatch(
            TofuDomainMismatchState(
                host = result.host,
                certDomains = result.certDomains,
                pendingUrl = result.pendingUrl
            )
        )
    }

    private fun handleTofuExpired(result: FetchResult.TofuExpired, tab: TabState) {
        val expiredDate = formatTimestamp(result.expiredAt)
        tab.error = GeminiError(
            statusCode = 0,
            message = "Server certificate expired on $expiredDate",
            isTemporary = false,
            canRetry = true
        )
    }

    private fun handleTofuNotYetValid(result: FetchResult.TofuNotYetValid, tab: TabState) {
        val validFrom = formatTimestamp(result.notBefore)
        tab.error = GeminiError(
            statusCode = 0,
            message = "Server certificate is not yet valid (valid from $validFrom)",
            isTemporary = false,
            canRetry = true
        )
    }

    fun loadPage(addToHistory: Boolean = false, forceReload: Boolean = false) {
        val currentTab = activeTab ?: return
        val tabId = currentTab.id

        loadingJobs[tabId]?.cancel()
        cancelEmbeddedMediaJobsForTab(tabId)
        cleanupTempMediaFiles(currentTab)

        val job = viewModelScope.launch {
            var targetUrl = currentTab.url.trim()
            val isLocalPage = targetUrl == HOME_URL || targetUrl == "about:gemtext"

            // Check if input looks like a domain/URL or a search query
            if (!isLocalPage && !targetUrl.contains("://")) {
                val looksLikeUrl = targetUrl.contains(".") && !targetUrl.contains(" ") && !targetUrl.startsWith(".")
                targetUrl = if (looksLikeUrl) {
                    "gemini://$targetUrl"
                } else {
                    settingsManager.getSearchEngine().buildSearchUrl(targetUrl)
                }
            }

            if (addToHistory) {
                currentTab.addToHistory(targetUrl)
                schedulePersistTabSession()
            } else {
                currentTab.updateUrl(targetUrl)
            }

            /*
               TODO: probably add sort of a enum in the future if we are going to have more than 1
               gemtext page included with the app
             */
            if (isLocalPage) {
                try {
                    if (forceReload) {
                        currentTab.isLoading = true
                        currentTab.error = null
                        yield()
                    }
                    val localContent = loadLocalPage(targetUrl)
                    currentTab.capsuleIdentity = CapsuleIdentityGenerator.fromUrl(targetUrl)
                    if (localContent != null) {
                        currentTab.rawBody = localContent
                        val parsedContent = GeminiParser.parse(
                            localContent,
                            baseUrl = targetUrl
                        ).toImmutableList()
                        currentTab.content = parsedContent
                        parsedContent.filterIsInstance<GeminiContent.Heading>()
                            .firstOrNull { it.level == 1 }
                            ?.let { currentTab.title = it.text }
                        currentTab.error = null
                        currentTab.updateDisplayedUrl(targetUrl)
                        currentTab.cachePage(
                            targetUrl,
                            currentTab.content,
                            currentTab.rawBody,
                            currentTab.title,
                            currentTab.capsuleIdentity
                        )
                    } else {
                        currentTab.error = GeminiError(
                            statusCode = 0,
                            message = "Local page not found (how?): $targetUrl",
                            isTemporary = false,
                            canRetry = false
                        )
                    }
                    return@launch
                } finally {
                    if (forceReload && loadingJobs[tabId] == coroutineContext[Job]) {
                        currentTab.isLoading = false
                        loadingJobs.remove(tabId)
                    }
                }
            }

            if (!addToHistory && !forceReload) {
                val cached = currentTab.getCachedPage(targetUrl)
                if (cached != null) {
                    currentTab.applyCachedPage(targetUrl, cached)
                    return@launch
                }
            }

            currentTab.isLoading = true
            currentTab.error = null

            try {
                val uri = URI(targetUrl)
                val autoSelectedCert = certificateManager.pendingCertAlias
                    ?: certificateManager.findBestMatch(uri.host ?: "", uri.path ?: "/")
                certificateManager.clearPendingCertAlias()

                val result = fetchWithRedirects(targetUrl, certAlias = autoSelectedCert)

                // Check if this job was cancelled while fetching
                ensureActive()

                when (result) {
                    is FetchResult.Success -> handleSuccess(
                        result, currentTab, targetUrl, addToHistory
                    )

                    is FetchResult.InputRequired -> {
                        pendingInputCertAlias = autoSelectedCert
                        dialogManager.showInputPrompt(
                            result.promptText, result.targetUrl, result.isSensitive
                        )
                    }

                    is FetchResult.TofuWarning -> dialogManager.showTofuWarning(result.state)
                    is FetchResult.TofuDomainMismatch -> handleTofuDomainMismatch(result)

                    is FetchResult.TofuExpired -> handleTofuExpired(result, currentTab)
                    is FetchResult.TofuNotYetValid -> handleTofuNotYetValid(result, currentTab)
                    is FetchResult.CertificateRequired -> certificateManager.showCertificateRequired(
                        result.statusCode, result.meta, result.url
                    )

                    is FetchResult.SlowDown -> dialogManager.showBackoff(result.url, result.meta)
                    is FetchResult.Error -> {
                        val shouldOfferIdentity = result.error.statusCode == 51 &&
                                autoSelectedCert == null &&
                                certificateManager.hasActiveCertificates()
                        if (shouldOfferIdentity) {
                            certificateManager.showCertificateRequired(
                                statusCode = 60,
                                meta = "This resource may require a client certificate. Select an identity to try.",
                                url = targetUrl
                            )
                        } else {
                            currentTab.error = result.error
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Don't treat cancellation as an error - just let it propagate
                throw e
            } catch (e: Exception) {
                currentTab.error = GeminiError(
                    statusCode = 0,
                    message = e.message ?: "Unknown Error",
                    isTemporary = true,
                    canRetry = true
                )
                Log.e("BrowserViewModel", "Failed to load page: ${currentTab.url}", e)
            } finally {
                // Only update state if this is still the active job for this tab
                if (loadingJobs[tabId] == coroutineContext[Job]) {
                    currentTab.isLoading = false
                    loadingJobs.remove(tabId)
                }
            }
        }
        loadingJobs[tabId] = job
    }

    fun onLinkClick(linkUrl: String) {
        try {
            val currentTab = activeTab ?: return
            val resolvedUrl = resolveUrl(linkUrl, currentTab.url)
            val resolvedUri = URI(resolvedUrl)
            val requiresInput = linkUrl.endsWith("?") || linkUrl.contains("%s")

            val scheme = resolvedUri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                openExternalUrl(resolvedUri.toString())
                return
            }

            val isGemini = scheme == null || scheme == "gemini"
            if (requiresInput && isGemini) {
                dialogManager.showInputPrompt(
                    promptText = "Input required",
                    targetUrl = resolvedUrl,
                    isSensitive = false
                )
                return
            }

            currentTab.updateUrl(resolvedUrl)
            loadPage(addToHistory = true)
        } catch (e: Exception) {
            activeTab?.error = GeminiError(
                statusCode = 0,
                message = "Invalid Link: $linkUrl. Exception: $e",
                isTemporary = false,
                canRetry = false
            )
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.e("BrowserViewModel", "Failed to open external URL: $url", e)
        }
    }

    fun goBack() {
        if (activeTab?.goBack() != null) {
            schedulePersistTabSession()
            loadPage(addToHistory = false)
        }
    }

    fun goForward() {
        if (activeTab?.goForward() != null) {
            schedulePersistTabSession()
            loadPage(addToHistory = false)
        }
    }

    fun reload() = loadPage(addToHistory = false, forceReload = true)

    fun goHome() {
        activeTab?.updateUrl(settingsManager.getHomePage())
        loadPage(addToHistory = true)
    }

    fun setCurrentPageAsHome() {
        activeTab?.url?.let { url: String -> settingsManager.setCurrentPageAsHome(url) }
    }

    // Settings delegation
    fun showSettingsScreen() = settingsManager.showScreen()
    fun dismissSettingsScreen() = settingsManager.dismissScreen()
    fun updateThemeMode(mode: ThemeMode) = settingsManager.updateThemeMode(mode)
    fun updateFontSize(size: FontSize) = settingsManager.updateFontSize(size)
    fun updateSearchEngine(engine: SearchEngine) = settingsManager.updateSearchEngine(engine)
    fun setHomePage(url: String) = settingsManager.setHomePage(url)
    fun updateDeveloperMode(enabled: Boolean) {
        settingsManager.updateDeveloperMode(enabled)
    }

    // Image handling
    fun openImageInNewTab(imageUrl: String) {
        tabManager.openImageInNewTab(imageUrl)
        loadPage(addToHistory = true)
    }

    // Bookmark delegation
    fun toggleBookmark(view: View? = null, density: Density? = null) {
        val currentTab = activeTab ?: return
        bookmarkManager.toggle(currentTab.url, currentTab.title, view, density)
    }

    fun removeBookmark(url: String) = bookmarkManager.remove(url, activeTab?.url)
    fun navigateToBookmark(bookmark: Bookmark) {
        activeTab?.updateUrl(bookmark.url)
        loadPage(addToHistory = true)
        bookmarkManager.dismissScreen()
    }

    fun showBookmarksScreen() = bookmarkManager.showScreen()
    fun dismissBookmarksScreen() = bookmarkManager.dismissScreen()

    // History delegation
    fun navigateToHistoryEntry(entry: HistoryEntry) {
        activeTab?.updateUrl(entry.url)
        loadPage(addToHistory = true)
        historyManager.dismissScreen()
    }

    fun clearHistory() = historyManager.clear()
    fun showHistoryScreen() = historyManager.showScreen()
    fun dismissHistoryScreen() = historyManager.dismissScreen()

    // Menu
    fun showMenuDropdown() {
        panelState = panelState.copy(showMenu = true)
    }

    fun dismissMenu() {
        panelState = panelState.copy(showMenu = false)
    }

    // Certificate insanity (delegation)
    fun showCertificatesScreen() = certificateManager.showScreen()
    fun dismissCertificatesScreen() = certificateManager.dismissScreen()

    fun selectCertificate(certificate: ClientCertificate) {
        val state = dialogManager.getState().certificateRequired ?: return
        certificateManager.selectCertificate(certificate, state.url)
        activeTab?.updateUrl(state.url)
        loadPage(addToHistory = true)
    }

    fun showCertificateGenerationDialog() = certificateManager.showGenerationDialog()
    fun dismissCertificateGenerationDialog() = certificateManager.dismissGenerationDialog()
    fun generateIdentity(params: IdentityParams) = certificateManager.generateIdentity(params)

    fun showIdentityMenuSheet() = certificateManager.showIdentityMenu()
    fun dismissIdentityMenu() = certificateManager.dismissIdentityMenu()
    fun showNewIdentityForDomain() = certificateManager.showNewIdentityForDomain()

    fun showIdentityUsageDialog(certificate: ClientCertificate) {
        activeTab?.url?.let { url: String -> certificateManager.showUsageDialog(certificate, url) }
    }

    fun dismissIdentityUsageDialog() = certificateManager.dismissUsageDialog()
    fun setIdentityUsage(alias: String, usage: IdentityUsage?) =
        certificateManager.setUsage(alias, usage)

    fun getCurrentHost(): String =
        activeTab?.url?.let { url: String -> certificateManager.getCurrentHost(url) } ?: ""

    fun getCurrentPath(): String =
        activeTab?.url?.let { url: String -> certificateManager.getCurrentPath(url) } ?: "/"

    fun dismissCertificateRequired() = certificateManager.dismissCertificateRequired()
    fun toggleCertificateActive(alias: String, isActive: Boolean) =
        certificateManager.toggleActive(alias, isActive)

    fun deleteCertificate(alias: String) = certificateManager.delete(alias)
    fun showCertificateDetails(certificate: ClientCertificate) =
        certificateManager.showDetails(certificate)

    fun dismissCertificateDetails() = certificateManager.dismissDetails()

    fun showTofuCertificateDetails() {
        val warning = dialogManager.getTofuWarning() ?: return
        certificateManager.showTofuDetails(warning.host, warning.newFingerprint, warning.newExpiry)
    }

    fun showConnectionInfo() = certificateManager.showConnectionInfo()

    // Identity Import/Export
    fun showIdentityImportDialog() {
        dialogManager.updateState(dialogManager.getState().copy(showIdentityImport = true))
    }

    fun dismissIdentityImportDialog() {
        dialogManager.updateState(dialogManager.getState().copy(showIdentityImport = false))
    }

    fun parseIdentityPem(pemData: String, passphrase: String?, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = certificateManager.parseIdentityPem(pemData, passphrase)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun importIdentity(
        pemData: String,
        passphrase: String?,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = certificateManager.importIdentity(pemData, passphrase)
            withContext(Dispatchers.Main) {
                when (result) {
                    is IdentityImportStoreResult.Success -> onResult(true, null)
                    is IdentityImportStoreResult.NeedsPassphrase -> {
                        onResult(false, getApplication<Application>().getString(R.string.identity_import_error_incorrect_passphrase))
                    }

                    is IdentityImportStoreResult.Error -> onResult(false, result.message)
                }
            }
        }
    }

    fun checkDuplicateIdentity(fingerprint: String): ClientCertificate? {
        return certificateManager.checkDuplicateIdentity(fingerprint)
    }

    fun replaceIdentity(
        existingAlias: String,
        newPemData: String,
        passphrase: String?,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = certificateManager.importIdentity(
                pemData = newPemData,
                passphrase = passphrase,
                replaceAlias = existingAlias
            )
            withContext(Dispatchers.Main) {
                when (result) {
                    is IdentityImportStoreResult.Success -> onResult(true, null)
                    is IdentityImportStoreResult.NeedsPassphrase -> {
                        onResult(false, getApplication<Application>().getString(R.string.identity_import_error_incorrect_passphrase))
                    }

                    is IdentityImportStoreResult.Error -> onResult(false, result.message)
                }
            }
        }
    }

    fun exportIdentity(certificate: ClientCertificate, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val exportResult = runCatching {
                val pem = certificateManager.exportIdentity(certificate.alias)
                    ?: throw IllegalStateException("No identity data available for export")
                val output = getApplication<Application>()
                    .contentResolver
                    .openOutputStream(targetUri, "wt")
                    ?: throw IllegalStateException("Failed to open export destination")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(pem)
                }
            }
            withContext(Dispatchers.Main) {
                val message = if (exportResult.isSuccess) {
                    getApplication<Application>().getString(R.string.identity_export_success)
                } else {
                    getApplication<Application>().getString(R.string.identity_export_failed)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Link context
    fun openLinkInNewTab(url: String) {
        tabManager.openInNewTab(resolveUrl(url))
        loadPage(addToHistory = true)
    }

    fun copyLinkToClipboard(url: String) {
        val clip = ClipData.newPlainText("Gemini Link", url)
        clipboardManager.setPrimaryClip(clip)
        viewModelScope.launch {
            snackbarHostState.showSnackbar(getApplication<Application>().getString(R.string.link_copied_to_clipboard))
        }
    }

    // Backoff
    fun cancelBackoff() = dialogManager.cancelBackoff()

    // Embedded media loading (Lagrange-style!)
    fun loadEmbeddedMedia(itemId: Int) {
        val tab = activeTab ?: return
        val tabId = tab.id
        val baseDisplayedUrl = tab.displayedUrl.ifBlank { tab.url }
        val media = tab.content.firstOrNull {
            it is GeminiContent.EmbeddedMedia && it.id == itemId
        } as? GeminiContent.EmbeddedMedia ?: return
        val loadingJobKey = embeddedMediaJobKey(tabId, itemId)
        cancelEmbeddedMediaLoadingJob(loadingJobKey)
        val resolvedUrl = resolveUrl(media.url, baseDisplayedUrl)
        val certAlias = try {
            val resolvedUri = URI(resolvedUrl)
            certificateManager.findBestMatch(resolvedUri.host ?: "", resolvedUri.path ?: "/")
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val cacheKey = buildEmbeddedMediaCacheKey(resolvedUrl, certAlias)
        embeddedMediaCache.get(cacheKey)?.let { cached ->
            updateEmbeddedMedia(
                tab = tab,
                itemId = itemId,
                expectedDisplayedUrl = baseDisplayedUrl,
                expectedStates = setOf(
                    GeminiContent.EmbeddedMediaState.COLLAPSED,
                    GeminiContent.EmbeddedMediaState.ERROR,
                    GeminiContent.EmbeddedMediaState.LOADING
                )
            ) { item ->
                item.copy(
                    state = GeminiContent.EmbeddedMediaState.LOADED,
                    mimeType = cached.mimeType,
                    data = cached.data,
                    errorMessage = null
                )
            }
            return
        }
 
        val job = viewModelScope.launch {
            try {
                val markedLoading = updateEmbeddedMedia(
                    tab = tab,
                    itemId = itemId,
                    expectedDisplayedUrl = baseDisplayedUrl,
                    expectedStates = setOf(
                        GeminiContent.EmbeddedMediaState.COLLAPSED,
                        GeminiContent.EmbeddedMediaState.ERROR,
                        GeminiContent.EmbeddedMediaState.LOADING
                    )
                ) { item ->
                    item.copy(
                        state = GeminiContent.EmbeddedMediaState.LOADING,
                        errorMessage = null
                    )
                }
                if (!markedLoading) {
                    return@launch
                }

                loadEmbeddedMediaToFile(tab, itemId, baseDisplayedUrl, resolvedUrl, certAlias, cacheKey, media)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateEmbeddedMedia(
                    tab = tab,
                    itemId = itemId,
                    expectedDisplayedUrl = baseDisplayedUrl,
                    expectedStates = setOf(GeminiContent.EmbeddedMediaState.LOADING)
                ) { item ->
                    item.copy(
                        state = GeminiContent.EmbeddedMediaState.ERROR,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            } finally {
                if (embeddedMediaLoadingJobs[loadingJobKey] == coroutineContext[Job]) {
                    embeddedMediaLoadingJobs.remove(loadingJobKey)
                }
            }
        }
        embeddedMediaLoadingJobs[loadingJobKey] = job
    }

    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private suspend fun loadEmbeddedMediaToFile(
        tab: TabState,
        itemId: Int,
        expectedDisplayedUrl: String,
        resolvedUrl: String,
        certAlias: String?,
        cacheKey: String,
        media: GeminiContent.EmbeddedMedia
    ) {
        var cacheDir = File(getApplication<Application>().cacheDir, "media")
        cacheDir.mkdirs()
        if (!cacheDir.exists() || !cacheDir.isDirectory) {
            Log.w("BrowserViewModel", "Failed to create media cache dir, falling back to app cacheDir")
            cacheDir = getApplication<Application>().cacheDir
        }
        val tempFile = File.createTempFile("media_", ".tmp", cacheDir)

        try {
            var lastReportedBytes = 0L
            when (val result = fetchEmbeddedMediaWithRedirects(
                initialUrl = resolvedUrl,
                certAlias = certAlias,
                outputFile = tempFile,
                onProgress = { bytesRead ->
                    if (bytesRead - lastReportedBytes >= 16384L) {
                        lastReportedBytes = bytesRead
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            updateEmbeddedMedia(
                                tab = tab,
                                itemId = itemId,
                                expectedDisplayedUrl = expectedDisplayedUrl,
                                expectedStates = setOf(GeminiContent.EmbeddedMediaState.LOADING)
                            ) { item ->
                                item.copy(
                                    downloadProgress = GeminiContent.DownloadProgress(
                                        fraction = (kotlin.math.ln(1f + bytesRead.toFloat()) / kotlin.math.ln(1f + DEFAULT_MAX_RESPONSE_BODY_BYTES.toFloat())).coerceAtMost(0.95f),
                                        bytesRead = bytesRead
                                    )
                                )
                            }
                        }
                    }
                }
            )) {
                is EmbeddedMediaFetchResult.Success -> {
                    val mimeType = result.response.meta.lowercase().split(";").first().trim()
                    val resolvedMimeType = mimeType.ifBlank { media.mimeType }
                    val mediaType = resolvedMimeType.substringBefore("/")

                    if (mediaType == "image" && tempFile.length() <= IMAGE_IN_MEMORY_MAX_BYTES) {
                        // Confirmed image with safe size — load into memory for display and caching
                        val bytes = tempFile.readBytes()
                        tempFile.delete()
                        val stableData = StableByteArray(bytes)
                        embeddedMediaCache.put(cacheKey, stableData, resolvedMimeType)
                        updateEmbeddedMedia(
                            tab = tab,
                            itemId = itemId,
                            expectedDisplayedUrl = expectedDisplayedUrl,
                            expectedStates = setOf(GeminiContent.EmbeddedMediaState.LOADING)
                        ) { item ->
                            item.copy(
                                state = GeminiContent.EmbeddedMediaState.LOADED,
                                mimeType = resolvedMimeType,
                                data = stableData,
                                dataFilePath = null,
                                errorMessage = null,
                                downloadProgress = null
                            )
                        }
                    } else {
                        val updated = updateEmbeddedMedia(
                            tab = tab,
                            itemId = itemId,
                            expectedDisplayedUrl = expectedDisplayedUrl,
                            expectedStates = setOf(GeminiContent.EmbeddedMediaState.LOADING)
                        ) { item ->
                            item.copy(
                                state = GeminiContent.EmbeddedMediaState.LOADED,
                                mimeType = resolvedMimeType,
                                dataFilePath = tempFile.absolutePath,
                                data = null,
                                errorMessage = null,
                                downloadProgress = null
                            )
                        }
                        if (updated && (mediaType == "audio" || mediaType == "video")) {
                            withContext(Dispatchers.Main) {
                                playerManager.playFromFile(tempFile, resolvedMimeType, "${tab.id}:${itemId}")
                            }
                        } else if (!updated) {
                            tempFile.delete()
                        }
                    }
                }

                is EmbeddedMediaFetchResult.Error -> {
                    tempFile.delete()
                    updateEmbeddedMedia(
                        tab = tab,
                        itemId = itemId,
                        expectedDisplayedUrl = expectedDisplayedUrl,
                        expectedStates = setOf(GeminiContent.EmbeddedMediaState.LOADING)
                    ) { item ->
                        item.copy(
                            state = GeminiContent.EmbeddedMediaState.ERROR,
                            errorMessage = result.message
                        )
                    }
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    fun playEmbeddedMedia(itemId: Int) {
        val tab = activeTab ?: return
        val media = tab.content.firstOrNull {
            it is GeminiContent.EmbeddedMedia && it.id == itemId
        } as? GeminiContent.EmbeddedMedia ?: return
        if (media.state != GeminiContent.EmbeddedMediaState.LOADED) return

        val filePath = media.dataFilePath
        if (filePath != null) {
            playerManager.playFromFile(File(filePath), media.mimeType, "${tab.id}:${itemId}")
        } else {
            val data = media.data?.bytes ?: return
            playerManager.play(data, media.mimeType, "${tab.id}:${itemId}")
        }
    }

    fun collapseEmbeddedMedia(itemId: Int) {
        val tab = activeTab ?: return
        // Delete temp file if media was loaded from disk
        val media = tab.content.firstOrNull {
            it is GeminiContent.EmbeddedMedia && it.id == itemId
        } as? GeminiContent.EmbeddedMedia
        if (media != null && playerManager.currentMediaKey == "${tab.id}:${media.id}") {
            playerManager.release()
        }
        media?.dataFilePath?.let { path ->
            File(path).delete()
        }
        cancelEmbeddedMediaLoadingJob(tab.id, itemId)
        updateEmbeddedMedia(tab, itemId) { item ->
            item.copy(
                state = GeminiContent.EmbeddedMediaState.COLLAPSED,
                data = null,
                dataFilePath = null,
                errorMessage = null
            )
        }
    }

    fun downloadEmbeddedMedia(url: String, data: StableByteArray?, dataFilePath: String?, mimeType: String) {
        val fileName = DownloadUtils.suggestFileName(url, mimeType)
        viewModelScope.launch(Dispatchers.IO) {
            val sourceFile = dataFilePath?.let { File(it).takeIf { f -> f.exists() } }
            val result = if (sourceFile != null) {
                DownloadUtils.saveToDownloads(
                    context = getApplication(),
                    sourceFile = sourceFile,
                    fileName = fileName,
                    mimeType = mimeType
                )
            } else {
                val bytes = data?.bytes
                if (bytes == null) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(
                            getApplication<Application>().getString(R.string.embedded_media_download_unavailable, fileName)
                        )
                    }
                    return@launch
                }
                DownloadUtils.saveToDownloads(
                    context = getApplication(),
                    data = bytes,
                    fileName = fileName,
                    mimeType = mimeType
                )
            }
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "Saved to $it" },
                        onFailure = { "Download failed: ${it.message}" }
                    )
                )
            }
        }
    }

    private fun resolveUrl(
        url: String,
        baseUrl: String? = null
    ): String {
        val effectiveBaseUrl = baseUrl ?: run {
            val current = activeTab
            current?.displayedUrl?.ifBlank { current.url } ?: url
        }
        return try {
            if (effectiveBaseUrl.isBlank()) {
                return url
            }
            URI(effectiveBaseUrl).resolve(url).toString()
        } catch (_: Exception) {
            url
        }
    }

    private fun embeddedMediaJobKey(tabId: String, itemId: Int): String {
        return "$tabId:$itemId"
    }

    private fun cancelEmbeddedMediaLoadingJob(tabId: String, itemId: Int) {
        cancelEmbeddedMediaLoadingJob(embeddedMediaJobKey(tabId, itemId))
    }

    private fun cancelEmbeddedMediaLoadingJob(jobKey: String) {
        embeddedMediaLoadingJobs.remove(jobKey)?.cancel()
    }

    private fun cancelEmbeddedMediaJobsForTab(tabId: String) {
        val tabPrefix = "$tabId:"
        val iterator = embeddedMediaLoadingJobs.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.startsWith(tabPrefix)) {
                entry.value.cancel()
                iterator.remove()
            }
        }
    }

    private fun buildEmbeddedMediaCacheKey(resolvedUrl: String, certAlias: String?): String {
        return if (certAlias.isNullOrBlank()) {
            resolvedUrl
        } else {
            "$resolvedUrl|$certAlias"
        }
    }

    private fun updateEmbeddedMedia(
        tab: TabState,
        itemId: Int,
        expectedDisplayedUrl: String? = null,
        expectedStates: Set<GeminiContent.EmbeddedMediaState>? = null,
        transform: (GeminiContent.EmbeddedMedia) -> GeminiContent.EmbeddedMedia
    ): Boolean {
        if (tabManager.tabs.none { it.id == tab.id }) {
            return false
        }
        val effectiveDisplayedUrl = tab.displayedUrl.ifBlank { tab.url }
        if (expectedDisplayedUrl != null && effectiveDisplayedUrl != expectedDisplayedUrl) {
            return false
        }
        var replaced = false
        tab.content = tab.content.map { item ->
            if (!replaced && item is GeminiContent.EmbeddedMedia && item.id == itemId) {
                if (expectedStates != null && item.state !in expectedStates) {
                    item
                } else {
                    replaced = true
                    transform(item)
                }
            } else {
                item
            }
        }.toImmutableList()
        return replaced
    }
 
    private suspend fun fetchEmbeddedMediaWithRedirects(
        initialUrl: String,
        certAlias: String?,
        maxRedirects: Int = 5,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
        outputFile: File? = null
    ): EmbeddedMediaFetchResult {
        val app = getApplication<Application>()
        return followRedirects(
            initialUrl = initialUrl,
            certAlias = certAlias,
            maxRedirects = maxRedirects,
            onProgress = onProgress,
            outputFile = outputFile,
            mapResult = { result, currentUrl ->
                when (result) {
                    is GeminiFetchResult.Success -> {
                        val response = result.response
                        when (response.status) {
                            in 20..29 -> {
                                RedirectLoopResult.Complete(
                                    EmbeddedMediaFetchResult.Success(response, currentUrl)
                                )
                            }

                            in 30..39 -> {
                                RedirectLoopResult.Redirect(
                                    statusCode = response.status,
                                    targetUrl = response.meta
                                )
                            }

                            in 10..19 -> {
                                val prompt = response.meta.ifBlank {
                                    app.getString(R.string.embedded_media_error_no_prompt)
                                }
                                RedirectLoopResult.Complete(
                                    EmbeddedMediaFetchResult.Error(
                                        app.getString(R.string.embedded_media_error_input_required, prompt)
                                    )
                                )
                            }

                            44 -> {
                                val meta = response.meta.ifBlank {
                                    app.getString(R.string.embedded_media_error_no_retry_hint)
                                }
                                RedirectLoopResult.Complete(
                                    EmbeddedMediaFetchResult.Error(
                                        app.getString(R.string.embedded_media_error_slow_down, meta)
                                    )
                                )
                            }

                            in 40..49 -> {
                                RedirectLoopResult.Complete(
                                    EmbeddedMediaFetchResult.Error(
                                        app.getString(
                                            R.string.embedded_media_error_temporary_failure,
                                            response.status,
                                            response.meta
                                        )
                                    )
                                )
                            }

                            in 50..59 -> {
                                RedirectLoopResult.Complete(
                                    EmbeddedMediaFetchResult.Error(
                                        app.getString(
                                            R.string.embedded_media_error_permanent_failure,
                                            response.status,
                                            response.meta
                                        )
                                    )
                                )
                            }

                            else -> {
                                val meta = response.meta.ifBlank {
                                    app.getString(R.string.embedded_media_error_unknown_status)
                                }
                                RedirectLoopResult.Complete(
                                    EmbeddedMediaFetchResult.Error(
                                        app.getString(
                                            R.string.embedded_media_error_unexpected_status,
                                            response.status,
                                            meta
                                        )
                                    )
                                )
                            }
                        }
                    }

                    else -> {
                        RedirectLoopResult.Complete(
                            EmbeddedMediaFetchResult.Error(formatEmbeddedMediaFetchError(result))
                        )
                    }
                }
            },
            onInvalidRedirect = { _, message ->
                EmbeddedMediaFetchResult.Error(message)
            },
            onTooManyRedirects = { redirectLimit ->
                EmbeddedMediaFetchResult.Error(
                    app.getString(R.string.embedded_media_error_too_many_redirects, redirectLimit)
                )
            }
        )
    }

    private suspend fun <T> followRedirects(
        initialUrl: String,
        certAlias: String?,
        maxRedirects: Int,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
        outputFile: File? = null,
        mapResult: (result: GeminiFetchResult, currentUrl: String) -> RedirectLoopResult<T>,
        onInvalidRedirect: (statusCode: Int, message: String) -> T,
        onTooManyRedirects: (redirectLimit: Int) -> T
    ): T {
        var currentUrl = initialUrl
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            val fetchResult = if (outputFile != null) {
                client.fetchToFile(currentUrl, outputFile, certAlias, onProgress)
            } else {
                client.fetch(currentUrl, certAlias, onProgress)
            }
            currentCoroutineContext().ensureActive()

            when (val mapped = mapResult(fetchResult, currentUrl)) {
                is RedirectLoopResult.Complete -> return mapped.value
                is RedirectLoopResult.Redirect -> {
                    val targetUrl = mapped.targetUrl
                    if (targetUrl.isBlank()) {
                        return onInvalidRedirect(mapped.statusCode, "Empty redirect target")
                    }
                    currentUrl = try {
                        URI(currentUrl).resolve(targetUrl).toString()
                    } catch (e: URISyntaxException) {
                        return onInvalidRedirect(
                            mapped.statusCode,
                            "Invalid redirect target: ${e.message ?: targetUrl}"
                        )
                    } catch (e: IllegalArgumentException) {
                        return onInvalidRedirect(
                            mapped.statusCode,
                            "Invalid redirect target: ${e.message ?: targetUrl}"
                        )
                    }
                    redirectCount++
                }
            }
        }

        return onTooManyRedirects(maxRedirects)
    }

    private fun formatEmbeddedMediaFetchError(result: GeminiFetchResult): String {
        val app = getApplication<Application>()
        return when (result) {
            is GeminiFetchResult.TofuDomainMismatch -> {
                val domains = result.certDomains.joinToString(", ")
                    .ifBlank { app.getString(R.string.embedded_media_error_tofu_domain_mismatch_unknown) }
                app.getString(R.string.embedded_media_error_tofu_domain_mismatch, result.host, domains)
            }

            is GeminiFetchResult.TofuWarning ->
                app.getString(R.string.embedded_media_error_tofu_warning, result.host)

            is GeminiFetchResult.TofuExpired -> {
                app.getString(R.string.embedded_media_error_tofu_expired, formatTimestamp(result.expiredAt))
            }

            is GeminiFetchResult.TofuNotYetValid -> {
                app.getString(R.string.embedded_media_error_tofu_not_yet_valid, formatTimestamp(result.notBefore))
            }

            is GeminiFetchResult.CertificateRequired -> {
                if (result.meta.isNotBlank()) {
                    app.getString(
                        R.string.embedded_media_error_client_cert_required_with_meta,
                        result.statusCode,
                        result.meta
                    )
                } else {
                    app.getString(R.string.embedded_media_error_client_cert_required, result.statusCode)
                }
            }

            is GeminiFetchResult.Error ->
                result.exception.message ?: app.getString(R.string.embedded_media_error_connection)

            is GeminiFetchResult.Success -> app.getString(R.string.embedded_media_error_failed_to_load_media)
        }
    }

    // TODO: finally add utils.kt or something
    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

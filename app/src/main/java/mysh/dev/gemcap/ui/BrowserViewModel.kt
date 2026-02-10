package mysh.dev.gemcap.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mysh.dev.gemcap.data.BrowserRepository
import mysh.dev.gemcap.data.ClientCertRepository
import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.SettingsRepository
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.domain.Bookmark
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
import mysh.dev.gemcap.network.CertificateGenerator
import mysh.dev.gemcap.network.GeminiClient
import mysh.dev.gemcap.network.GeminiFetchResult
import mysh.dev.gemcap.network.IdentityParams
import mysh.dev.gemcap.ui.managers.BookmarkManager
import mysh.dev.gemcap.ui.managers.CertificateManager
import mysh.dev.gemcap.ui.managers.DialogManager
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
import mysh.dev.gemcap.util.DownloadUtils
import java.net.URI
import java.net.URLEncoder

// Don't you love when one view model literally controls everything?
// TODO: try to refactor it
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val client = GeminiClient(application)
    private val repository = BrowserRepository(application)
    private val certRepository = ClientCertRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val certGenerator = CertificateGenerator(certRepository.getKeyStore(), certRepository)
    private val backoffManager = BackoffManager()
    private val clipboardManager = application.getSystemService(ClipboardManager::class.java)

    // Track loading jobs per tab for cancellation
    private val loadingJobs = mutableMapOf<String, Job>()

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
        onTabChanged = { bookmarkManager.updateBookmarkStatus(activeTab?.url) }
    )

    private val searchManager = SearchManager()

    private val historyManager = HistoryManager(
        repository = repository,
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

    init {
        tabManager.initialize()
        bookmarkManager.refresh()
        historyManager.refresh()
        certificateManager.refresh()
        loadPage(addToHistory = true)
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
        tabManager.closeTab(tabId)
    }

    fun selectTab(tabId: String) {
        tabManager.selectTab(tabId)
        bookmarkManager.updateBookmarkStatus(activeTab?.url)
    }

    fun onUrlChange(newUrl: String) {
        activeTab?.updateUrl(newUrl)
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

        val encodedInput = URLEncoder.encode(userInput, "UTF-8").replace("+", "%20")
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

        dialogManager.setDownloadMessage(
            result.fold(
                onSuccess = { "Saved to $it" },
                onFailure = { "Download failed: ${it.message}" }
            )
        )
    }

    fun dismissDownloadPrompt() = dialogManager.dismissDownloadPrompt()

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
        var currentUrl = initialUrl
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            when (val fetchResult = client.fetch(currentUrl, certAlias)) {
                is GeminiFetchResult.Error -> {
                    return FetchResult.Error(
                        GeminiError(
                            statusCode = 0,
                            message = fetchResult.exception.message ?: "Connection error",
                            isTemporary = true,
                            canRetry = true
                        )
                    )
                }

                is GeminiFetchResult.TofuWarning -> {
                    return FetchResult.TofuWarning(
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
                }

                is GeminiFetchResult.TofuDomainMismatch -> {
                    return FetchResult.TofuDomainMismatch(
                        host = fetchResult.host,
                        certDomains = fetchResult.certDomains,
                        pendingUrl = fetchResult.pendingUrl
                    )
                }

                is GeminiFetchResult.TofuExpired -> {
                    return FetchResult.TofuExpired(
                        host = fetchResult.host,
                        expiredAt = fetchResult.expiredAt,
                        pendingUrl = fetchResult.pendingUrl
                    )
                }

                is GeminiFetchResult.TofuNotYetValid -> {
                    return FetchResult.TofuNotYetValid(
                        host = fetchResult.host,
                        notBefore = fetchResult.notBefore,
                        pendingUrl = fetchResult.pendingUrl
                    )
                }

                is GeminiFetchResult.CertificateRequired -> {
                    return FetchResult.CertificateRequired(
                        statusCode = fetchResult.statusCode,
                        meta = fetchResult.meta,
                        url = fetchResult.url
                    )
                }

                is GeminiFetchResult.Success -> {
                    val response = fetchResult.response
                    when (response.status) {
                        in 10..19 -> {
                            return FetchResult.InputRequired(
                                promptText = response.meta,
                                targetUrl = currentUrl,
                                isSensitive = response.status == 11
                            )
                        }

                        in 30..39 -> {
                            val targetUrl = response.meta
                            if (targetUrl.isBlank()) {
                                return FetchResult.Error(
                                    GeminiError(
                                        statusCode = response.status,
                                        message = "Empty redirect target",
                                        isTemporary = true,
                                        canRetry = false
                                    )
                                )
                            }
                            currentUrl = URI(currentUrl).resolve(targetUrl).toString()
                            redirectCount++
                        }

                        44 -> {
                            return FetchResult.SlowDown(meta = response.meta, url = currentUrl)
                        }

                        in 40..49 -> {
                            return FetchResult.Error(
                                GeminiError(
                                    statusCode = response.status,
                                    message = response.meta,
                                    isTemporary = true,
                                    canRetry = true
                                )
                            )
                        }

                        in 50..59 -> {
                            return FetchResult.Error(
                                GeminiError(
                                    statusCode = response.status,
                                    message = response.meta,
                                    isTemporary = false,
                                    canRetry = false
                                )
                            )
                        }

                        in 20..29 -> {
                            dialogManager.clearBackoff(currentUrl)
                            return FetchResult.Success(
                                response,
                                currentUrl,
                                fetchResult.serverCertInfo
                            )
                        }

                        else -> {
                            return FetchResult.Error(
                                GeminiError(
                                    statusCode = response.status,
                                    message = response.meta.ifBlank { "Unknown status code" },
                                    isTemporary = true,
                                    canRetry = true
                                )
                            )
                        }
                    }
                }
            }
        }
        return FetchResult.Error(
            GeminiError(
                statusCode = 0,
                message = "Too many redirects (max $maxRedirects)",
                isTemporary = true,
                canRetry = false
            )
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
            mimeType == "text/gemini" || mimeType.isEmpty() -> handleGemtextResponse(response, tab)
            mimeType.startsWith("text/") -> handleTextResponse(response, tab, finalUrl, mimeType)
            else -> handleDownloadResponse(response, tab, finalUrl, mimeType)
        }

        tab.updateDisplayedUrl(finalUrl)
        tab.cachePage(finalUrl, tab.content, tab.rawBody, tab.title)
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

    private fun handleGemtextResponse(response: GeminiResponse, tab: TabState) {
        val bodyString = response.body?.toString(Charsets.UTF_8) ?: ""
        tab.rawBody = bodyString
        val parsedContent = GeminiParser.parse(bodyString).toImmutableList()
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
            GeminiContent.Text(id = 0, text = "Download available: $fileName")
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
        val expiredDate =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(result.expiredAt))
        tab.error = GeminiError(
            statusCode = 0,
            message = "Server certificate expired on $expiredDate",
            isTemporary = false,
            canRetry = true
        )
    }

    private fun handleTofuNotYetValid(result: FetchResult.TofuNotYetValid, tab: TabState) {
        val validFrom =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(result.notBefore))
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

        // Cancel any existing loading job for this tab
        loadingJobs[tabId]?.cancel()

        val job = viewModelScope.launch {
            var targetUrl = currentTab.url.trim()
            val isLocalPage = targetUrl == HOME_URL || targetUrl == "about:gemtext"

            // Check if input looks like a domain/URL or a search query
            if (!isLocalPage && !targetUrl.contains("://")) {
                val looksLikeUrl = targetUrl.contains(".") && !targetUrl.contains(" ")
                targetUrl = if (looksLikeUrl) {
                    "gemini://$targetUrl"
                } else {
                    val encodedQuery = URLEncoder.encode(targetUrl, "UTF-8")
                    "gemini://gemini-search.mysh.dev/?$encodedQuery"
                }
            }

            if (addToHistory) {
                currentTab.addToHistory(targetUrl)
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
                    if (localContent != null) {
                        currentTab.rawBody = localContent
                        val parsedContent = GeminiParser.parse(localContent).toImmutableList()
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
                            currentTab.title
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
            val currentUri = URI(currentTab.url)
            val resolvedUri = currentUri.resolve(linkUrl)
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
                    targetUrl = resolvedUri.toString(),
                    isSensitive = false
                )
                return
            }

            currentTab.updateUrl(resolvedUri.toString())
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
            loadPage(addToHistory = false)
        }
    }

    fun goForward() {
        if (activeTab?.goForward() != null) {
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
    fun setHomePage(url: String) = settingsManager.setHomePage(url)

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

    // Link context
    fun openLinkInNewTab(url: String) {
        tabManager.openInNewTab(url)
        loadPage(addToHistory = true)
    }

    fun copyLinkToClipboard(url: String) {
        val clip = ClipData.newPlainText("Gemini Link", url)
        clipboardManager.setPrimaryClip(clip)
        viewModelScope.launch {
            snackbarHostState.showSnackbar("Link copied to clipboard")
        }
    }

    // Backoff
    fun cancelBackoff() = dialogManager.cancelBackoff()
}

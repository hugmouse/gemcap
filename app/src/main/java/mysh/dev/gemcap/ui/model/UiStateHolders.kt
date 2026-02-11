package mysh.dev.gemcap.ui.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.domain.BackoffState
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.domain.CertificateDetailsState
import mysh.dev.gemcap.domain.CertificateRequiredState
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.DownloadPromptState
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.domain.InputPromptState
import mysh.dev.gemcap.domain.ServerCertInfo
import mysh.dev.gemcap.domain.TofuDomainMismatchState
import mysh.dev.gemcap.domain.TofuWarningState

/**
 * State holder for AddressBar
 * changes when URL / autocomplete / connection status change.
 */
@Stable
data class AddressBarState(
    val url: String,
    val hasSecureConnection: Boolean,
    val autocomplete: AutocompleteState
)

/**
 * State holder for toolbar buttons
 * changes when navigation / tabs / menu state change
 */
@Stable
data class ToolbarState(
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val tabCount: Int,
    val isCompactMode: Boolean,
    val isBookmarked: Boolean,
    val showMenu: Boolean,
    val searchActive: Boolean,
    val hasActiveIdentity: Boolean,
    val searchResultCount: Int = 0,
    val searchCurrentIndex: Int = -1
)


/**
 * State for in-page search functionality
 */
@Stable
data class SearchState(
    val query: String = "",
    val isActive: Boolean = false,
    val results: ImmutableList<Int> = persistentListOf(),
    val currentResultIndex: Int = -1
)

/**
 * State for all modal dialogs
 */
@Stable
data class DialogState(
    val inputPrompt: InputPromptState? = null,
    val tofuWarning: TofuWarningState? = null,
    val downloadPrompt: DownloadPromptState? = null,
    val certificateRequired: CertificateRequiredState? = null,
    val certificateDetails: CertificateDetailsState? = null,
    val identityUsage: IdentityUsageState? = null,
    val backoff: BackoffState? = null,
    val downloadMessage: String? = null,
    val tofuDomainMismatch: TofuDomainMismatchState? = null
)

/**
 * State for identity usage dialog
 */
@Stable
data class IdentityUsageState(
    val certificate: ClientCertificate,
    val currentHost: String,
    val currentPath: String
)

/**
 * State for all panel/screen visibility
 */
@Stable
data class PanelState(
    val showBookmarks: Boolean = false,
    val showHistory: Boolean = false,
    val showMenu: Boolean = false,
    val showSettings: Boolean = false,
    val showCertificateGeneration: Boolean = false,
    val showCertificateManagement: Boolean = false,
    val showIdentityMenu: Boolean = false
)

/**
 * State for address bar autocomplete
 */
@Stable
data class AutocompleteState(
    val suggestions: ImmutableList<HistoryEntry> = persistentListOf(),
    val showSuggestions: Boolean = false
)

/**
 * User-configurable settings state
 */
@Stable
data class SettingsState(
    val themeMode: ThemeMode,
    val fontSize: FontSize,
    val homePage: String
)

/**
 * State for client certificate management
 */
@Stable
data class CertificateState(
    val clientCertificates: ImmutableList<ClientCertificate> = persistentListOf(),
    val currentServerCertInfo: ServerCertInfo? = null
)

/**
 * State for tab-related UI
 */
@Stable
data class TabsUiState(
    val tabs: ImmutableList<TabState>,
    val activeTabId: String?,
    val activeTab: TabState?
)

/**
 * State for all dialogs and panels
 */
@Stable
data class DialogsUiState(
    val dialogState: DialogState,
    val panelState: PanelState,
    val settingsState: SettingsState,
    val certificateState: CertificateState,
    val bookmarks: ImmutableList<Bookmark>,
    val history: ImmutableList<HistoryEntry>,
    val currentHost: String,
    val currentPath: String
)

/**
 * State for main content area
 */
@Stable
data class ContentUiState(
    val activeTab: TabState?,
    val searchState: SearchState
)

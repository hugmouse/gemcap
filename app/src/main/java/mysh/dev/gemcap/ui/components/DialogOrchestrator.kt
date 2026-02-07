package mysh.dev.gemcap.ui.components

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.ui.BookmarksScreen
import mysh.dev.gemcap.ui.CertificateManagementScreen
import mysh.dev.gemcap.ui.HistoryScreen
import mysh.dev.gemcap.ui.SettingsScreen
import mysh.dev.gemcap.ui.callbacks.BrowserCallbacks
import mysh.dev.gemcap.ui.components.dialogs.BackoffCountdownDialog
import mysh.dev.gemcap.ui.components.dialogs.CertificateDetailsDialog
import mysh.dev.gemcap.ui.components.dialogs.CertificateSelectionDialog
import mysh.dev.gemcap.ui.components.dialogs.DomainMismatchDialog
import mysh.dev.gemcap.ui.components.dialogs.DownloadPromptDialog
import mysh.dev.gemcap.ui.components.dialogs.IdentityGenerationDialog
import mysh.dev.gemcap.ui.components.dialogs.IdentityUsageDialog
import mysh.dev.gemcap.ui.components.dialogs.InputPromptDialog
import mysh.dev.gemcap.ui.components.dialogs.TofuWarningDialog
import mysh.dev.gemcap.ui.components.sheets.IdentityMenuSheet
import mysh.dev.gemcap.ui.model.CertificateState
import mysh.dev.gemcap.ui.model.DialogState
import mysh.dev.gemcap.ui.model.PanelState
import mysh.dev.gemcap.ui.model.SettingsState

/**
 * Orchestrates all dialogs and overlays in the browser.
 * Centralizes dialog rendering logic to keep BrowserScreen clean.
 */
@Composable
fun DialogOrchestrator(
    dialogState: DialogState,
    panelState: PanelState,
    settingsState: SettingsState,
    certificateState: CertificateState,
    bookmarks: ImmutableList<Bookmark>,
    history: ImmutableList<HistoryEntry>,
    currentHost: String,
    currentPath: String,
    currentPageUrl: String,
    callbacks: BrowserCallbacks
) {
    // Bookmarks screen
    if (panelState.showBookmarks) {
        BookmarksScreen(
            bookmarks = bookmarks,
            onBookmarkClick = { callbacks.onBookmarkClick(it) },
            onBookmarkDelete = { callbacks.onBookmarkDelete(it) },
            onDismiss = { callbacks.onDismissBookmarks() }
        )
    }

    // History screen
    if (panelState.showHistory) {
        HistoryScreen(
            history = history,
            onHistoryClick = { callbacks.onHistoryClick(it) },
            onClearHistory = { callbacks.onClearHistory() },
            onDismiss = { callbacks.onDismissHistory() }
        )
    }

    // Input prompt dialog (for status 10/11 responses)
    dialogState.inputPrompt?.let { inputPrompt ->
        InputPromptDialog(
            promptState = inputPrompt,
            onSubmit = { callbacks.onSubmitInput(it) },
            onDismiss = { callbacks.onDismissInput() }
        )
    }

    // TOFU domain mismatch dialog
    dialogState.tofuDomainMismatch?.let { domainMismatch ->
        DomainMismatchDialog(
            state = domainMismatch,
            onAccept = { callbacks.onAcceptDomainMismatch() },
            onReject = { callbacks.onRejectDomainMismatch() }
        )
    }

    // TOFU certificate warning dialog
    dialogState.tofuWarning?.let { tofuWarning ->
        TofuWarningDialog(
            state = tofuWarning,
            onAccept = { callbacks.onAcceptTofuWarning() },
            onReject = { callbacks.onRejectTofuWarning() },
            onViewDetails = { callbacks.onViewTofuDetails() }
        )
    }

    // Download prompt dialog
    dialogState.downloadPrompt?.let { downloadPrompt ->
        DownloadPromptDialog(
            state = downloadPrompt,
            onDownload = { callbacks.onConfirmDownload() },
            onDismiss = { callbacks.onDismissDownload() }
        )
    }

    // Certificate management screen
    if (panelState.showCertificateManagement) {
        CertificateManagementScreen(
            certificates = certificateState.clientCertificates,
            onCertificateClick = { callbacks.onShowCertificateDetails(it) },
            onGenerateClick = { callbacks.onShowCertificateGenerationDialog() },
            onToggleActive = { alias, active ->
                callbacks.onToggleCertificateActive(alias, active)
            },
            onDelete = { callbacks.onDeleteCertificate(it) },
            onDismiss = { callbacks.onDismissCertificates() },
            currentHost = currentHost,
            currentPath = currentPath,
            onUseOnClick = { callbacks.onShowIdentityUsageDialog(it) }
        )
    }

    // Certificate selection dialog (for status 60/61/62 responses)
    dialogState.certificateRequired?.let { certRequired ->
        CertificateSelectionDialog(
            state = certRequired,
            onSelectCertificate = { callbacks.onSelectCertificate(it) },
            onGenerateNew = { callbacks.onShowCertificateGenerationDialog() },
            onCancel = { callbacks.onDismissCertificateRequired() }
        )
    }

    // Identity generation dialog
    if (panelState.showCertificateGeneration) {
        IdentityGenerationDialog(
            onGenerate = { callbacks.onGenerateIdentity(it) },
            onCancel = { callbacks.onDismissCertificateGeneration() }
        )
    }

    // Identity menu bottom sheet (Lagrange-style)
    if (panelState.showIdentityMenu) {
        IdentityMenuSheet(
            currentHost = currentHost,
            onNewIdentityForDomain = { callbacks.onNewIdentityForDomain() },
            onManageIdentities = { callbacks.onShowCertificates() },
            onDismiss = { callbacks.onDismissIdentityMenu() }
        )
    }

    // Identity usage dialog (Lagrange-style "Use on")
    dialogState.identityUsage?.let { identityUsage ->
        val currentUsage = identityUsage.certificate.usages.find {
            it.host == identityUsage.currentHost
        }
        IdentityUsageDialog(
            currentHost = identityUsage.currentHost,
            currentPath = identityUsage.currentPath,
            currentUsage = currentUsage,
            onSelectUsage = { usage ->
                callbacks.onSetIdentityUsage(identityUsage.certificate.alias, usage)
            },
            onDismiss = { callbacks.onDismissIdentityUsageDialog() }
        )
    }

    // Certificate details dialog
    dialogState.certificateDetails?.let { certDetails ->
        CertificateDetailsDialog(
            state = certDetails,
            onDismiss = { callbacks.onDismissCertificateDetails() }
        )
    }

    // Backoff countdown dialog (for status 44 slow down responses)
    dialogState.backoff?.let { backoff ->
        BackoffCountdownDialog(
            state = backoff,
            onCancel = { callbacks.onCancelBackoff() }
        )
    }

    // Settings screen
    if (panelState.showSettings) {
        SettingsScreen(
            themeMode = settingsState.themeMode,
            fontSize = settingsState.fontSize,
            homePage = settingsState.homePage,
            currentPageUrl = currentPageUrl,
            onThemeModeChange = { callbacks.onThemeModeChange(it) },
            onFontSizeChange = { callbacks.onFontSizeChange(it) },
            onHomePageChange = { callbacks.onHomePageChange(it) },
            onDismiss = { callbacks.onDismissSettings() }
        )
    }
}

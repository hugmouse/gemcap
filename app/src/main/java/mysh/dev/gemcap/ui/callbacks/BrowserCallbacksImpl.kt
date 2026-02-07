package mysh.dev.gemcap.ui.callbacks

import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.network.IdentityParams
import mysh.dev.gemcap.ui.BrowserViewModel

// TODO: either I have to do data class with remember {} upon calling this,
// or do this.
@Stable
class BrowserCallbacksImpl(
    private val viewModel: BrowserViewModel,
    private val onThemeModeChanged: (ThemeMode) -> Unit,
    private val onFontSizeChanged: (FontSize) -> Unit,
    private val view: View,
    private val density: Density
) : BrowserCallbacks {

    // NavigationCallbacks
    override fun onLinkClick(url: String) = viewModel.onLinkClick(url)
    override fun onUrlChange(url: String) = viewModel.onUrlChange(url)
    override fun onGo() = viewModel.loadPage(addToHistory = true)
    override fun onBack() = viewModel.goBack()
    override fun onForward() = viewModel.goForward()
    override fun onRefresh() = viewModel.reload()
    override fun onHome() = viewModel.goHome()

    // TabCallbacks
    override fun onNewTab() = viewModel.addNewTab()
    override fun onSelectTab(id: String) = viewModel.selectTab(id)
    override fun onCloseTab(id: String) = viewModel.closeTab(id)
    override fun onOpenInNewTab(url: String) = viewModel.openLinkInNewTab(url)
    override fun onOpenImageInNewTab(url: String) = viewModel.openImageInNewTab(url)

    // BookmarkCallbacks
    override fun onToggleBookmark() = viewModel.toggleBookmark(view, density)
    override fun onShowBookmarks() = viewModel.showBookmarksScreen()
    override fun onDismissBookmarks() = viewModel.dismissBookmarksScreen()
    override fun onBookmarkClick(bookmark: Bookmark) = viewModel.navigateToBookmark(bookmark)
    override fun onBookmarkDelete(url: String) = viewModel.removeBookmark(url)

    // HistoryCallbacks
    override fun onShowHistory() = viewModel.showHistoryScreen()
    override fun onDismissHistory() = viewModel.dismissHistoryScreen()
    override fun onHistoryClick(entry: HistoryEntry) = viewModel.navigateToHistoryEntry(entry)
    override fun onClearHistory() = viewModel.clearHistory()

    // CertificateCallbacks
    override fun onShowCertificates() = viewModel.showCertificatesScreen()
    override fun onDismissCertificates() = viewModel.dismissCertificatesScreen()
    override fun onSelectCertificate(certificate: ClientCertificate) =
        viewModel.selectCertificate(certificate)

    override fun onShowCertificateGenerationDialog() = viewModel.showCertificateGenerationDialog()
    override fun onDismissCertificateRequired() = viewModel.dismissCertificateRequired()
    override fun onDismissCertificateGeneration() = viewModel.dismissCertificateGenerationDialog()
    override fun onToggleCertificateActive(alias: String, active: Boolean) =
        viewModel.toggleCertificateActive(alias, active)

    override fun onDeleteCertificate(alias: String) = viewModel.deleteCertificate(alias)
    override fun onShowCertificateDetails(certificate: ClientCertificate) =
        viewModel.showCertificateDetails(certificate)

    override fun onDismissCertificateDetails() = viewModel.dismissCertificateDetails()
    override fun onIdentityClick() = viewModel.showIdentityMenuSheet()
    override fun onDismissIdentityMenu() = viewModel.dismissIdentityMenu()
    override fun onNewIdentityForDomain() = viewModel.showNewIdentityForDomain()
    override fun onGenerateIdentity(params: IdentityParams) = viewModel.generateIdentity(params)
    override fun onShowIdentityUsageDialog(certificate: ClientCertificate) =
        viewModel.showIdentityUsageDialog(certificate)

    override fun onDismissIdentityUsageDialog() = viewModel.dismissIdentityUsageDialog()
    override fun onSetIdentityUsage(alias: String, usage: IdentityUsage?) =
        viewModel.setIdentityUsage(alias, usage)

    override fun onConnectionInfoClick() = viewModel.showConnectionInfo()

    // DialogCallbacks
    override fun onSubmitInput(input: String) = viewModel.submitInput(input)
    override fun onDismissInput() = viewModel.dismissInputPrompt()
    override fun onAcceptTofuWarning() = viewModel.acceptTofuWarning()
    override fun onRejectTofuWarning() = viewModel.rejectTofuWarning()
    override fun onViewTofuDetails() = viewModel.showTofuCertificateDetails()
    override fun onAcceptDomainMismatch() = viewModel.acceptDomainMismatch()
    override fun onRejectDomainMismatch() = viewModel.rejectDomainMismatch()
    override fun onConfirmDownload() = viewModel.confirmDownload()
    override fun onDismissDownload() = viewModel.dismissDownloadPrompt()
    override fun onCancelBackoff() = viewModel.cancelBackoff()

    // SearchCallbacks
    override fun onToggleSearch() = viewModel.toggleSearch()
    override fun onSearch(query: String) = viewModel.search(query)
    override fun onGoToNextResult() = viewModel.goToNextResult()
    override fun onGoToPreviousResult() = viewModel.goToPreviousResult()

    // SettingsCallbacks
    override fun onShowSettings() = viewModel.showSettingsScreen()
    override fun onDismissSettings() = viewModel.dismissSettingsScreen()
    override fun onThemeModeChange(mode: ThemeMode) {
        viewModel.updateThemeMode(mode)
        onThemeModeChanged(mode)
    }

    override fun onFontSizeChange(size: FontSize) {
        viewModel.updateFontSize(size)
        onFontSizeChanged(size)
    }

    override fun onHomePageChange(url: String) = viewModel.setHomePage(url)
    override fun onSetAsHomePage() = viewModel.setCurrentPageAsHome()

    // MenuCallbacks
    override fun onShowMenu() = viewModel.showMenuDropdown()
    override fun onDismissMenu() = viewModel.dismissMenu()

    // AutocompleteCallbacks
    override fun onSuggestionClick(entry: HistoryEntry) = viewModel.selectSuggestion(entry)
    override fun onSuggestionsDismiss() = viewModel.dismissSuggestions()

    // LinkContextCallbacks
    override fun onCopyLink(url: String) = viewModel.copyLinkToClipboard(url)
}

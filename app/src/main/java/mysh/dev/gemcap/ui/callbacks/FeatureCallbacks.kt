package mysh.dev.gemcap.ui.callbacks

import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.network.IdentityParams

interface NavigationCallbacks {
    fun onLinkClick(url: String)
    fun onUrlChange(url: String)
    fun onGo()
    fun onBack()
    fun onForward()
    fun onRefresh()
    fun onHome()
}

interface TabCallbacks {
    fun onNewTab()
    fun onSelectTab(id: String)
    fun onCloseTab(id: String)
    fun onOpenInNewTab(url: String)
    fun onOpenImageInNewTab(url: String)
}

interface BookmarkCallbacks {
    fun onToggleBookmark()
    fun onShowBookmarks()
    fun onDismissBookmarks()
    fun onBookmarkClick(bookmark: Bookmark)
    fun onBookmarkDelete(url: String)
}

interface HistoryCallbacks {
    fun onShowHistory()
    fun onDismissHistory()
    fun onHistoryClick(entry: HistoryEntry)
    fun onClearHistory()
}

interface CertificateCallbacks {
    // Certificate management
    fun onShowCertificates()
    fun onDismissCertificates()
    fun onSelectCertificate(certificate: ClientCertificate)
    fun onShowCertificateGenerationDialog()
    fun onDismissCertificateRequired()
    fun onDismissCertificateGeneration()
    fun onToggleCertificateActive(alias: String, active: Boolean)
    fun onDeleteCertificate(alias: String)
    fun onShowCertificateDetails(certificate: ClientCertificate)
    fun onDismissCertificateDetails()

    fun onIdentityClick()
    fun onDismissIdentityMenu()
    fun onNewIdentityForDomain()
    fun onGenerateIdentity(params: IdentityParams)
    fun onShowIdentityUsageDialog(certificate: ClientCertificate)
    fun onDismissIdentityUsageDialog()
    fun onSetIdentityUsage(alias: String, usage: IdentityUsage?)

    fun onConnectionInfoClick()
}

interface DialogCallbacks {
    fun onSubmitInput(input: String)
    fun onDismissInput()
    fun onAcceptTofuWarning()
    fun onRejectTofuWarning()
    fun onViewTofuDetails()
    fun onAcceptDomainMismatch()
    fun onRejectDomainMismatch()
    fun onConfirmDownload()
    fun onDismissDownload()
    fun onCancelBackoff()
}

interface SearchCallbacks {
    fun onToggleSearch()
    fun onSearch(query: String)
    fun onGoToNextResult()
    fun onGoToPreviousResult()
}

interface SettingsCallbacks {
    fun onShowSettings()
    fun onDismissSettings()
    fun onThemeModeChange(mode: ThemeMode)
    fun onFontSizeChange(size: FontSize)
    fun onHomePageChange(url: String)
    fun onSetAsHomePage()
}

interface MenuCallbacks {
    fun onShowMenu()
    fun onDismissMenu()
}

interface AutocompleteCallbacks {
    fun onSuggestionClick(entry: HistoryEntry)
    fun onSuggestionsDismiss()
}

interface LinkContextCallbacks {
    fun onCopyLink(url: String)
}

interface BrowserCallbacks :
    NavigationCallbacks,
    TabCallbacks,
    BookmarkCallbacks,
    HistoryCallbacks,
    CertificateCallbacks,
    DialogCallbacks,
    SearchCallbacks,
    SettingsCallbacks,
    MenuCallbacks,
    AutocompleteCallbacks,
    LinkContextCallbacks

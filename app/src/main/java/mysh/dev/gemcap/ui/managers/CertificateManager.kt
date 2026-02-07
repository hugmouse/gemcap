package mysh.dev.gemcap.ui.managers

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mysh.dev.gemcap.data.ClientCertRepository
import mysh.dev.gemcap.domain.CertificateDetailsState
import mysh.dev.gemcap.domain.CertificateRequiredState
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.GeminiError
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.domain.ServerCertInfo
import mysh.dev.gemcap.network.CertificateGenerator
import mysh.dev.gemcap.network.IdentityParams
import mysh.dev.gemcap.ui.model.CertificateState
import mysh.dev.gemcap.ui.model.DialogState
import mysh.dev.gemcap.ui.model.IdentityUsageState
import mysh.dev.gemcap.ui.model.PanelState
import java.net.URI

class CertificateManager(
    private val certRepository: ClientCertRepository,
    private val certGenerator: CertificateGenerator,
    private val scope: CoroutineScope,
    private val getDialogState: () -> DialogState,
    private val updateDialogState: (DialogState) -> Unit,
    private val getPanelState: () -> PanelState,
    private val updatePanelState: (PanelState) -> Unit,
    private val onError: (GeminiError) -> Unit,
    private val onCertificateSelected: ((url: String) -> Unit)? = null
) {
    var certificateState by mutableStateOf(CertificateState())
        private set

    var pendingCertAlias: String? = null
        private set

    fun setPendingCertAlias(alias: String?) {
        pendingCertAlias = alias
    }

    fun clearPendingCertAlias() {
        pendingCertAlias = null
    }

    fun refresh() {
        certificateState = certificateState.copy(
            clientCertificates = certRepository.getCertificates().toImmutableList()
        )
    }

    fun updateServerCertInfo(info: ServerCertInfo?) {
        certificateState = certificateState.copy(currentServerCertInfo = info)
    }

    fun findBestMatch(host: String, path: String): String? {
        return certRepository.findBestMatch(host, path)?.alias
    }

    fun hasActiveCertificates(): Boolean {
        return certRepository.getCertificates()
            .any { it.isActive && !it.isExpired }
    }

    fun hasActiveIdentityForUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return false
            val path = uri.path ?: "/"
            certRepository.findBestMatch(host, path) != null
        } catch (e: Exception) {
            false
        }
    }

    // Screen management
    fun showScreen() {
        refresh()
        updatePanelState(getPanelState().copy(showCertificateManagement = true, showMenu = false))
    }

    fun dismissScreen() {
        updatePanelState(getPanelState().copy(showCertificateManagement = false))
    }

    // Certificate selection for auth
    fun selectCertificate(certificate: ClientCertificate, url: String): String {
        updateDialogState(getDialogState().copy(certificateRequired = null))
        pendingCertAlias = certificate.alias
        return url
    }

    fun showCertificateRequired(statusCode: Int, meta: String, url: String) {
        val uri = URI(url)
        val host = uri.host ?: ""
        val path = uri.path ?: "/"
        // Show all active, non-expired certs so user can pick any identity
        val allActiveCerts = certRepository.getCertificates()
            .filter { it.isActive && !it.isExpired }

        updateDialogState(
            getDialogState().copy(
                certificateRequired = CertificateRequiredState(
                    statusCode = statusCode,
                    message = meta,
                    url = url,
                    host = host,
                    path = path,
                    matchingCertificates = allActiveCerts
                )
            )
        )
    }

    fun dismissCertificateRequired() {
        updateDialogState(getDialogState().copy(certificateRequired = null))
    }

    // Generation dialog
    fun showGenerationDialog() {
        updatePanelState(getPanelState().copy(showCertificateGeneration = true))
    }

    fun dismissGenerationDialog() {
        updatePanelState(getPanelState().copy(showCertificateGeneration = false))
    }

    fun generateIdentity(params: IdentityParams) {
        val hasPendingCertRequired = getDialogState().certificateRequired != null

        updatePanelState(
            getPanelState().copy(
                showCertificateGeneration = false,
                showIdentityMenu = false
            )
        )

        scope.launch {
            try {
                val newCert = certGenerator.generateCertificate(params)
                refresh()

                // If we were in a cert-required flow, auto-select the new cert
                if (hasPendingCertRequired) {
                    val certReqState = getDialogState().certificateRequired
                    if (certReqState != null) {
                        updateDialogState(getDialogState().copy(certificateRequired = null))
                        pendingCertAlias = newCert.alias
                        onCertificateSelected?.invoke(certReqState.url)
                    }
                }
            } catch (e: Exception) {
                Log.e("CertificateManager", "Failed to generate identity", e)
                onError(
                    GeminiError(
                        statusCode = 0,
                        message = "Failed to generate identity: ${e.message}",
                        isTemporary = false,
                        canRetry = false
                    )
                )
            }
        }
    }

    // Identity menu
    fun showIdentityMenu() {
        updatePanelState(getPanelState().copy(showIdentityMenu = true))
    }

    fun dismissIdentityMenu() {
        updatePanelState(getPanelState().copy(showIdentityMenu = false))
    }

    fun showNewIdentityForDomain() {
        updatePanelState(
            getPanelState().copy(
                showIdentityMenu = false,
                showCertificateGeneration = true
            )
        )
    }

    // Identity usage dialog
    fun showUsageDialog(certificate: ClientCertificate, url: String) {
        try {
            val uri = URI(url)
            val host = uri.host ?: return
            val path = uri.path ?: "/"

            updateDialogState(
                getDialogState().copy(
                    identityUsage = IdentityUsageState(
                        certificate = certificate,
                        currentHost = host,
                        currentPath = path
                    )
                )
            )
        } catch (e: Exception) {
            Log.e("CertificateManager", "Failed to parse URL for usage dialog", e)
        }
    }

    fun dismissUsageDialog() {
        updateDialogState(getDialogState().copy(identityUsage = null))
    }

    fun setUsage(alias: String, usage: IdentityUsage?) {
        val state = getDialogState().identityUsage ?: return

        if (usage != null) {
            val currentUsagesForHost =
                state.certificate.usages.filter { it.host == state.currentHost }
            currentUsagesForHost.forEach { existingUsage ->
                certRepository.removeUsage(alias, existingUsage)
            }
            certRepository.addUsage(alias, usage)
        } else {
            state.certificate.usages.filter { it.host == state.currentHost }
                .forEach { existingUsage ->
                    certRepository.removeUsage(alias, existingUsage)
                }
        }

        refresh()
        updateDialogState(getDialogState().copy(identityUsage = null))
    }

    // Certificate management
    fun toggleActive(alias: String, isActive: Boolean) {
        certRepository.setActive(alias, isActive)
        refresh()
    }

    fun delete(alias: String) {
        certRepository.removeCertificate(alias)
        refresh()
    }

    // Certificate details
    fun showDetails(certificate: ClientCertificate) {
        val keyStore = certRepository.getKeyStore()
        val x509Cert = keyStore.getCertificate(certificate.alias)

        updateDialogState(
            getDialogState().copy(
                certificateDetails = CertificateDetailsState(
                    commonName = certificate.commonName,
                    fingerprint = certificate.fingerprint,
                    issuer = x509Cert?.issuerX500Principal?.name ?: "Unknown",
                    validFrom = x509Cert?.notBefore?.time ?: certificate.createdAt,
                    validUntil = x509Cert?.notAfter?.time ?: certificate.expiresAt,
                    isServerCert = false
                )
            )
        )
    }

    fun showTofuDetails(host: String, newFingerprint: String, newExpiry: Long) {
        updateDialogState(
            getDialogState().copy(
                certificateDetails = CertificateDetailsState(
                    commonName = host,
                    fingerprint = newFingerprint,
                    issuer = "Self-signed (TOFU)",
                    validFrom = System.currentTimeMillis(),
                    validUntil = newExpiry,
                    isServerCert = true
                )
            )
        )
    }

    fun showConnectionInfo() {
        val certInfo = certificateState.currentServerCertInfo ?: return
        updateDialogState(
            getDialogState().copy(
                certificateDetails = CertificateDetailsState(
                    commonName = certInfo.commonName,
                    fingerprint = certInfo.fingerprint,
                    issuer = certInfo.issuer,
                    validFrom = certInfo.validFrom,
                    validUntil = certInfo.validUntil,
                    isServerCert = true
                )
            )
        )
    }

    fun dismissDetails() {
        updateDialogState(getDialogState().copy(certificateDetails = null))
    }

    // TODO: this don't belong here
    fun getCurrentHost(url: String): String {
        return try {
            URI(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // TODO: this don't belong here
    fun getCurrentPath(url: String): String {
        return try {
            URI(url).path ?: "/"
        } catch (e: Exception) {
            "/"
        }
    }
}

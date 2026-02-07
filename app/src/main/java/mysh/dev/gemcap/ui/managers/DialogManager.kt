package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mysh.dev.gemcap.domain.DownloadPromptState
import mysh.dev.gemcap.domain.InputPromptState
import mysh.dev.gemcap.domain.TofuDomainMismatchState
import mysh.dev.gemcap.domain.TofuWarningState
import mysh.dev.gemcap.network.BackoffManager
import mysh.dev.gemcap.network.GeminiClient
import mysh.dev.gemcap.ui.model.DialogState

class DialogManager(
    private val client: GeminiClient,
    private val backoffManager: BackoffManager,
    private val scope: CoroutineScope,
    private val onBackoffExpired: (String) -> Unit
) {
    var dialogState by mutableStateOf(DialogState())
        private set

    private var backoffCountdownJob: Job? = null

    fun getState(): DialogState = dialogState

    fun updateState(newState: DialogState) {
        dialogState = newState
    }

    // Input prompts
    fun showInputPrompt(promptText: String, targetUrl: String, isSensitive: Boolean) {
        dialogState = dialogState.copy(
            inputPrompt = InputPromptState(
                promptText = promptText,
                targetUrl = targetUrl,
                isSensitive = isSensitive
            )
        )
    }

    fun getInputPrompt(): InputPromptState? = dialogState.inputPrompt

    fun dismissInputPrompt() {
        dialogState = dialogState.copy(inputPrompt = null)
    }

    // TOFU warnings
    fun showTofuWarning(state: TofuWarningState) {
        dialogState = dialogState.copy(tofuWarning = state)
    }

    fun getTofuWarning(): TofuWarningState? = dialogState.tofuWarning

    fun acceptTofuWarning(): Boolean {
        val warning = dialogState.tofuWarning ?: return false
        dialogState = dialogState.copy(tofuWarning = null)
        client.acceptCertificateAndRetry(
            warning.host,
            warning.port,
            warning.newFingerprint,
            warning.newExpiry
        )
        return true
    }

    fun rejectTofuWarning() {
        dialogState = dialogState.copy(tofuWarning = null)
    }

    // Domain mismatch
    fun showDomainMismatch(state: TofuDomainMismatchState) {
        dialogState = dialogState.copy(tofuDomainMismatch = state)
    }

    fun getDomainMismatch(): TofuDomainMismatchState? = dialogState.tofuDomainMismatch

    fun acceptDomainMismatch(): TofuDomainMismatchState? {
        val state = dialogState.tofuDomainMismatch ?: return null
        dialogState = dialogState.copy(tofuDomainMismatch = null)
        val port = try {
            java.net.URI(state.pendingUrl).port.let { if (it == -1) 1965 else it }
        } catch (_: Exception) {
            1965
        }
        client.bypassDomainCheck(state.host, port)
        return state
    }

    fun rejectDomainMismatch() {
        dialogState = dialogState.copy(tofuDomainMismatch = null)
    }

    // Download prompts
    fun showDownloadPrompt(state: DownloadPromptState) {
        dialogState = dialogState.copy(downloadPrompt = state)
    }

    fun getDownloadPrompt(): DownloadPromptState? = dialogState.downloadPrompt

    fun dismissDownloadPrompt() {
        dialogState = dialogState.copy(downloadPrompt = null)
    }

    fun setDownloadMessage(message: String?) {
        dialogState = dialogState.copy(downloadMessage = message)
    }

    fun clearDownloadMessage() {
        dialogState = dialogState.copy(downloadMessage = null)
    }

    // Backoff handling
    fun showBackoff(url: String, meta: String) {
        val newBackoffState = backoffManager.recordBackoff(url, meta)
        dialogState = dialogState.copy(backoff = newBackoffState)
        startBackoffCountdown(url)
    }

    private fun startBackoffCountdown(url: String) {
        backoffCountdownJob?.cancel()
        backoffCountdownJob = scope.launch {
            while (backoffManager.getBackoffState(url) != null) {
                dialogState = dialogState.copy(backoff = backoffManager.getBackoffState(url))
                delay(1000)
            }
            dialogState = dialogState.copy(backoff = null)
            onBackoffExpired(url)
        }
    }

    fun cancelBackoff() {
        backoffCountdownJob?.cancel()
        backoffCountdownJob = null
        dialogState = dialogState.copy(backoff = null)
        backoffManager.clearAll()
    }

    fun clearBackoff(url: String) {
        backoffManager.clearBackoff(url)
    }
}

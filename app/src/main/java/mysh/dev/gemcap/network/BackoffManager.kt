package mysh.dev.gemcap.network

import mysh.dev.gemcap.domain.BackoffState
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages exponential backoff for status 44 (Slow Down) responses
 *
 * On practice, I've never-ever encountered this status myself, so
 * I didn't test it much.
 */
class BackoffManager {

    companion object {
        const val INITIAL_DELAY_SECONDS = 1
        const val MAX_DELAY_SECONDS = 60
        const val MULTIPLIER = 2.0
    }

    private val backoffStates = mutableMapOf<String, BackoffState>()

    /**
     * Records a backoff for a URL after receiving status 44
     *
     * @param url The URL that returned status 44
     * @param serverMeta The meta string from the server (may contain suggested delay)
     * @return The BackoffState with calculated retry time
     */
    fun recordBackoff(url: String, serverMeta: String): BackoffState {
        val existingState = backoffStates[url]
        val retryCount = (existingState?.retryCount ?: 0) + 1

        val serverSuggestedDelay = parseServerDelay(serverMeta)
        val delaySeconds = serverSuggestedDelay ?: calculateExponentialDelay(retryCount)
        val retryAtMillis = System.currentTimeMillis() + (delaySeconds * 1000L)

        val state = BackoffState(
            url = url,
            retryAtMillis = retryAtMillis,
            retryCount = retryCount,
            serverSuggestedDelay = serverSuggestedDelay
        )

        backoffStates[url] = state
        return state
    }

    fun getBackoffState(url: String): BackoffState? {
        val state = backoffStates[url]
        if (state != null && state.isExpired) {
            // Don't remove yet - keep for retry counting
            return null
        }
        return state
    }

    fun clearBackoff(url: String) {
        backoffStates.remove(url)
    }

    fun clearAll() {
        backoffStates.clear()
    }

    private fun calculateExponentialDelay(retryCount: Int): Int {
        val delay = INITIAL_DELAY_SECONDS * MULTIPLIER.pow(retryCount - 1)
        return min(delay.toInt(), MAX_DELAY_SECONDS)
    }

    private fun parseServerDelay(meta: String): Int? {
        // Server may suggest a delay in the meta field
        // Common formats: "5", "5 seconds", "wait 5"
        val trimmed = meta.trim().lowercase()

        // Try direct number
        trimmed.toIntOrNull()?.let { return it.coerceIn(1, MAX_DELAY_SECONDS) }

        // Try to extract first number from string
        val numberMatch = Regex("(\\d+)").find(trimmed)
        numberMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
            return it.coerceIn(1, MAX_DELAY_SECONDS)
        }

        return null
    }
}

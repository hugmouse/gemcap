package mysh.dev.gemcap.domain

import androidx.compose.runtime.Immutable

data class GeminiResponse(
    val status: Int,
    val meta: String,
    val body: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeminiResponse

        if (status != other.status) return false
        if (meta != other.meta) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + meta.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}

@Immutable
data class StableByteArray(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StableByteArray
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

enum class LinkScheme {
    GEMINI,
    TITAN,
    GOPHER,
    FINGER,
    HTTP,
    FILE,
    DATA,
    ABOUT,
    MAILTO,
    SPARTAN,
    NEX,
    MISFIN,
    GUPPY,
    UNKNOWN
}

enum class LinkFlag {
    REMOTE,
    HUMAN_READABLE,
    IMAGE_EXT,
    AUDIO_EXT,
    FONTPACK_EXT,
    QUERY,
    ICON_FROM_LABEL,
    VISITED // TODO: add visited links styles and state
}

data class CapsuleIdentity(
    val icon: String?,
    val themeSeed: UInt,
    val paletteSeed: UInt,
    val themeSeedSource: String,
    val paletteSeedSource: String
)

sealed class GeminiContent {
    abstract val id: Int

    data class Text(override val id: Int, val text: String) : GeminiContent()
    data class Link(
        override val id: Int,
        val url: String,
        val text: String,
        val resolvedUrl: String? = null,
        val scheme: LinkScheme = LinkScheme.UNKNOWN,
        val flags: Set<LinkFlag> = emptySet(),
        val labelIcon: String? = null
    ) : GeminiContent()
    data class Heading(override val id: Int, val level: Int, val text: String) : GeminiContent()
    data class ListItem(override val id: Int, val text: String) : GeminiContent()
    data class Quote(override val id: Int, val text: String) : GeminiContent()
    data class Preformatted(override val id: Int, val text: String, val alt: String) :
        GeminiContent()

    // For images loaded as full-page content (when URL is opened directly)
    data class Image(
        override val id: Int,
        val data: StableByteArray,
        val mimeType: String,
        val url: String
    ) : GeminiContent()

    // For media embedded in gemini pages (in-place loading like Lagrange)
    data class EmbeddedMedia(
        override val id: Int,
        val url: String,
        val mimeType: String,
        val linkText: String,
        val state: EmbeddedMediaState,
        val data: StableByteArray? = null,  // Populated when state is LOADED
        val dataFilePath: String? = null,   // Path to temp file for large media (audio/video)
        val errorMessage: String? = null,
        val downloadProgress: Float? = null  // 0.0 to 1.0, null if unknown
    ) : GeminiContent()

    enum class EmbeddedMediaState {
        COLLAPSED,    // Shows as a link with "Load" indicator
        LOADING,      // Loading spinner
        LOADED,       // Media displayed inline
        ERROR         // Failed to load
    }
}

data class InputPromptState(
    val promptText: String,
    val targetUrl: String,
    val isSensitive: Boolean  // true for status 11
)

data class GeminiError(
    val statusCode: Int,
    val message: String,
    val isTemporary: Boolean,  // true for 4x, false for 5x
    val canRetry: Boolean
) {
    val title: String
        get() = when (statusCode) {
            0 -> "Connection Error"
            41 -> "Server Unavailable"
            42 -> "CGI Error"
            43 -> "Proxy Error"
            44 -> "Slow Down"
            51 -> "Not Found"
            52 -> "Gone"
            53 -> "Proxy Request Refused"
            59 -> "Bad Request"
            60 -> "Client Certificate Required"
            61 -> "Certificate Not Authorized"
            62 -> "Certificate Not Valid"
            else -> if (isTemporary) "Temporary Failure" else "Permanent Failure"
        }
}

data class TofuWarningState(
    val host: String,
    val port: Int = 1965,
    val oldFingerprint: String,
    val newFingerprint: String,
    val newExpiry: Long,
    val isCATrusted: Boolean = false,
    val pendingUrl: String
)

data class TofuDomainMismatchState(
    val host: String,
    val certDomains: List<String>,
    val pendingUrl: String
)

data class DownloadPromptState(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DownloadPromptState
        return url == other.url && fileName == other.fileName && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

@Immutable
data class BackoffState(
    val url: String,                    // URL that triggered the backoff
    val retryAtMillis: Long,            // When we can retry (System.currentTimeMillis based)
    val retryCount: Int,                // Number of retries attempted
    val serverSuggestedDelay: Int?      // Delay from server meta (if parseable)
) {
    val remainingSeconds: Int
        get() = ((retryAtMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= retryAtMillis
}

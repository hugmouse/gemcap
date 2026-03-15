package mysh.dev.gemcap.util

import java.net.URI

object GeminiUri {
    const val DEFAULT_PORT = 1965

    fun host(url: String): String {
        return try {
            URI(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun path(url: String): String {
        return try {
            URI(url).path.takeUnless { it.isNullOrEmpty() } ?: "/"
        } catch (e: Exception) {
            "/"
        }
    }

    fun port(url: String): Int {
        return try {
            val p = URI(url).port
            if (p == -1) DEFAULT_PORT else p
        } catch (e: Exception) {
            DEFAULT_PORT
        }
    }
}

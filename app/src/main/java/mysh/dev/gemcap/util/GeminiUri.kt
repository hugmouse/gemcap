package mysh.dev.gemcap.util

import java.net.URI
import java.nio.charset.StandardCharsets

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
    // TODO: building URIs this way feels fragile, needs testing before
    // pushing into google app store

    fun buildInputUrl(targetUrl: String, userInput: String): String {
        val encodedInput = encodeQueryComponent(userInput)
        if (targetUrl.contains("%s")) {
            return targetUrl.replace("%s", encodedInput)
        }

        val uri = URI(targetUrl)
        val baseUrl = URI(
            uri.scheme,
            uri.rawAuthority,
            uri.rawPath,
            null,
            null
        ).toASCIIString()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "$baseUrl?$encodedInput$fragment"
    }

    private fun encodeQueryComponent(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val builder = StringBuilder(bytes.size)

        for (byte in bytes) {
            val codePoint = byte.toInt() and 0xFF
            if (isQueryChar(codePoint)) {
                builder.append(codePoint.toChar())
            } else {
                builder.append('%')
                builder.append(HEX_DIGITS[codePoint ushr 4])
                builder.append(HEX_DIGITS[codePoint and 0x0F])
            }
        }

        return builder.toString()
    }

    private fun isQueryChar(codePoint: Int): Boolean {
        return when (codePoint.toChar()) {
            in 'a'..'z',
            in 'A'..'Z',
            in '0'..'9',
            '-', '.', '_', '~',
            '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
            ':', '@', '/', '?' -> true
            else -> false
        }
    }

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
}

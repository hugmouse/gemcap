package mysh.dev.gemcap.network

import java.security.MessageDigest
import java.security.cert.X509Certificate

object Fingerprints {
    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun certSha256Hex(
        cert: X509Certificate,
        uppercase: Boolean = false,
        separator: Char? = null
    ): String {
        return formatHex(sha256(cert.encoded), uppercase, separator)
    }

    fun publicKeySha256Hex(
        cert: X509Certificate,
        uppercase: Boolean = false,
        separator: Char? = null
    ): String {
        return formatHex(sha256(cert.publicKey.encoded), uppercase, separator)
    }

    private fun formatHex(bytes: ByteArray, uppercase: Boolean, separator: Char?): String {
        val format = if (uppercase) "%02X" else "%02x"
        return if (separator == null) {
            bytes.joinToString("") { format.format(it) }
        } else {
            bytes.joinToString(separator.toString()) { format.format(it) }
        }
    }
}

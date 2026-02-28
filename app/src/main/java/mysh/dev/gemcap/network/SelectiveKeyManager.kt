package mysh.dev.gemcap.network

import android.util.Log
import mysh.dev.gemcap.data.EncryptedIdentityStorage
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.X509KeyManager

private const val TAG = "SelectiveKeyManager"

/**
 * A KeyManager that selects a fixed client certificate alias for one request.
 *
 * The alias is immutable to avoid coroutine/thread hopping issues that can happen
 * when alias selection depends on thread-local state.
 */
class SelectiveKeyManager(
    private val identityStorage: EncryptedIdentityStorage,
    private val alias: String?
) : X509KeyManager {

    private val cachedIdentity: Pair<PrivateKey, X509Certificate>? by lazy {
        alias?.let { identityStorage.getIdentity(it) }
    }

    private fun selectedAliasOrNull(): String? {
        val selectedAlias = alias ?: return null
        if (cachedIdentity == null) {
            Log.w(TAG, "Selected alias not found in storage: $selectedAlias")
            return null
        }
        return selectedAlias
    }

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?
    ): Array<String>? {
        val selectedAlias = selectedAliasMatching(
            keyTypes = keyType?.let { arrayOf(it) },
            issuers = issuers
        ) ?: return null
        return arrayOf(selectedAlias)
    }

    override fun chooseClientAlias(
        keyTypes: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? {
        val selectedAlias = selectedAliasMatching(keyTypes, issuers)
        Log.d(TAG, "chooseClientAlias called, returning: $selectedAlias")
        return selectedAlias
    }

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<out Principal>?
    ): Array<String>? {
        // We don't handle server certificates
        return null
    }

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? {
        // We don't handle server certificates
        return null
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
        val selectedAlias = selectedAliasOrNull() ?: return null
        if (alias == null || alias != selectedAlias) return null
        val (_, certificate) = cachedIdentity ?: return null
        return arrayOf(certificate)
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        val selectedAlias = selectedAliasOrNull() ?: return null
        if (alias == null || alias != selectedAlias) return null
        val (privateKey, _) = cachedIdentity ?: return null
        return privateKey
    }

    private fun selectedAliasMatching(
        keyTypes: Array<out String>?,
        issuers: Array<out Principal>?
    ): String? {
        val selectedAlias = selectedAliasOrNull() ?: return null
        val (_, certificate) = cachedIdentity ?: return null
        val certificateChain = arrayOf(certificate)
        val leafCertificate = certificate

        if (!matchesAnyKeyType(leafCertificate, keyTypes)) {
            return null
        }
        if (!matchesIssuers(certificateChain, issuers)) {
            return null
        }
        return selectedAlias
    }

    private fun matchesAnyKeyType(
        certificate: X509Certificate,
        keyTypes: Array<out String>?
    ): Boolean {
        if (keyTypes.isNullOrEmpty()) {
            return true
        }
        val certAlgorithm = normalizeKeyType(certificate.publicKey.algorithm)
        return keyTypes.any { normalizeKeyType(it) == certAlgorithm }
    }

    private fun matchesIssuers(
        certificateChain: Array<X509Certificate>,
        issuers: Array<out Principal>?
    ): Boolean {
        if (issuers.isNullOrEmpty()) {
            return true
        }
        return certificateChain.any { certificate ->
            val subjectPrincipal = certificate.subjectX500Principal
            val issuerPrincipal = certificate.issuerX500Principal
            issuers.any { acceptedIssuer ->
                acceptedIssuer == subjectPrincipal ||
                    acceptedIssuer.name == subjectPrincipal.name ||
                    acceptedIssuer == issuerPrincipal ||
                    acceptedIssuer.name == issuerPrincipal.name
            }
        }
    }

    private fun normalizeKeyType(keyType: String): String {
        return when (keyType.uppercase(Locale.US)) {
            "RSA" -> "RSA"
            "EC", "ECDSA" -> "EC"
            "DSA" -> "DSA"
            else -> keyType.uppercase(Locale.US)
        }
    }
}

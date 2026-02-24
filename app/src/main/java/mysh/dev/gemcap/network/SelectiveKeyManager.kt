package mysh.dev.gemcap.network

import android.util.Log
import mysh.dev.gemcap.data.ClientCertKeyStore
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager

private const val TAG = "SelectiveKeyManager"

/**
 * A KeyManager that selects a fixed client certificate alias for one request.
 *
 * The alias is immutable to avoid coroutine/thread hopping issues that can happen
 * when alias selection depends on thread-local state.
 */
class SelectiveKeyManager(
    private val keyStore: ClientCertKeyStore,
    private val alias: String?
) : X509KeyManager {

    private fun selectedAliasOrNull(): String? {
        val selectedAlias = alias ?: return null
        if (!keyStore.containsAlias(selectedAlias)) {
            Log.w(TAG, "Selected alias not found in KeyStore: $selectedAlias")
            return null
        }
        return selectedAlias
    }

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?
    ): Array<String>? {
        val selectedAlias = selectedAliasOrNull() ?: return null
        return arrayOf(selectedAlias)
    }

    override fun chooseClientAlias(
        keyTypes: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? {
        val selectedAlias = selectedAliasOrNull()
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
        if (alias == null) return null
        return keyStore.getCertificateChain(alias)
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        if (alias == null) return null
        return keyStore.getPrivateKey(alias)
    }
}

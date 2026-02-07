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
 * A KeyManager that allows selecting which client certificate to use per-request.
 * Uses thread-local storage to support concurrent requests with different certificates.
 */
class SelectiveKeyManager(private val keyStore: ClientCertKeyStore) : X509KeyManager {

    private val currentAlias = ThreadLocal<String?>()

    /**
     * Sets the certificate alias to use for the current thread's request.
     * Call this before initiating an SSL connection.
     */
    fun setCurrentAlias(alias: String?) {
        currentAlias.set(alias)
        Log.d(TAG, "Set current alias: $alias")
    }

    /**
     * Clears the certificate alias for the current thread.
     * Call this after the SSL connection is complete.
     */
    fun clearCurrentAlias() {
        currentAlias.remove()
        Log.d(TAG, "Cleared current alias")
    }

    /**
     * Returns the current alias set for this thread.
     */
    fun getCurrentAlias(): String? = currentAlias.get()

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?
    ): Array<String>? {
        val alias = currentAlias.get() ?: return null
        if (keyStore.containsAlias(alias)) {
            return arrayOf(alias)
        }
        return null
    }

    override fun chooseClientAlias(
        keyTypes: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? {
        val alias = currentAlias.get()
        Log.d(TAG, "chooseClientAlias called, returning: $alias")
        return alias
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

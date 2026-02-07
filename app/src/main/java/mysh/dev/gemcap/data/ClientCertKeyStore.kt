package mysh.dev.gemcap.data

import android.util.Log
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

private const val TAG = "ClientCertKeyStore"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

class ClientCertKeyStore {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun reload() {
        try {
            keyStore.load(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload KeyStore", e)
        }
    }

    fun importKeyPair(
        alias: String,
        privateKey: PrivateKey,
        certificateChain: Array<X509Certificate>
    ) {
        keyStore.setKeyEntry(alias, privateKey, null, certificateChain)
        Log.d(TAG, "Imported key pair for alias: $alias")
    }

    fun getPrivateKey(alias: String): PrivateKey? {
        reload()
        return try {
            keyStore.getKey(alias, null) as? PrivateKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get private key for alias: $alias", e)
            null
        }
    }

    fun getCertificateChain(alias: String): Array<X509Certificate>? {
        reload()
        return try {
            keyStore.getCertificateChain(alias)?.map { it as X509Certificate }?.toTypedArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate chain for alias: $alias", e)
            null
        }
    }


    fun getCertificate(alias: String): X509Certificate? {
        reload()
        return try {
            keyStore.getCertificate(alias) as? X509Certificate
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate for alias: $alias", e)
            null
        }
    }

    fun containsAlias(alias: String): Boolean {
        reload()
        return keyStore.containsAlias(alias)
    }

    fun deleteEntry(alias: String) {
        reload()
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            Log.d(TAG, "Deleted entry for alias: $alias")
        }
    }
}

package mysh.dev.gemcap.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.domain.UsageType
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ClientCertRepository"

/**
 * Repository for client certificate (identity) metadata
 * Keys are stored in Android KeyStore (via ClientCertKeyStore)
 * Metadata (usages, name, fingerprint, etc.) is stored in SharedPreferences
 */
class ClientCertRepository(context: Context) {

    private val prefs = context.getSharedPreferences("client_certs", Context.MODE_PRIVATE)
    private val keyStore = ClientCertKeyStore()

    companion object {
        private const val KEY_CERTIFICATES = "certificates"
        const val ALIAS_PREFIX = "gemini_client_cert_"
    }

    /**
     * Retrieves all stored identities
     */
    fun getCertificates(): List<ClientCertificate> {
        val json = prefs.getString(KEY_CERTIFICATES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                try {
                    val obj = array.getJSONObject(i)

                    val usages = if (obj.has("usages")) {
                        val usagesArray = obj.getJSONArray("usages")
                        (0 until usagesArray.length()).map { j ->
                            val usageObj = usagesArray.getJSONObject(j)
                            IdentityUsage(
                                host = usageObj.getString("host"),
                                type = UsageType.valueOf(usageObj.getString("type")),
                                path = usageObj.optString("path", "/")
                            )
                        }
                    } else {
                        emptyList()
                    }

                    ClientCertificate(
                        alias = obj.getString("alias"),
                        commonName = obj.getString("commonName"),
                        email = obj.optString("email", "").takeIf { it.isNotEmpty() },
                        organization = obj.optString("organization", "").takeIf { it.isNotEmpty() },
                        usages = usages,
                        fingerprint = obj.getString("fingerprint"),
                        createdAt = obj.getLong("createdAt"),
                        expiresAt = obj.getLong("expiresAt"),
                        isActive = obj.optBoolean("isActive", true)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse certificate entry", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse certificates JSON", e)
            emptyList()
        }
    }

    fun addCertificate(certificate: ClientCertificate) {
        val certificates = getCertificates().toMutableList()
        // Remove any existing cert with the same alias
        certificates.removeAll { it.alias == certificate.alias }
        certificates.add(0, certificate)
        saveCertificates(certificates)
        Log.d(TAG, "Added certificate: ${certificate.alias}")
    }

    fun addUsage(alias: String, usage: IdentityUsage) {
        val certificates = getCertificates().map { cert ->
            if (cert.alias == alias) {
                // Remove any existing usage with same host/path, then add new one
                val updatedUsages = cert.usages.filterNot {
                    it.host == usage.host && it.path == usage.path
                } + usage
                cert.copy(usages = updatedUsages)
            } else cert
        }
        saveCertificates(certificates)
        Log.d(TAG, "Added usage to $alias: $usage")
    }

    fun removeUsage(alias: String, usage: IdentityUsage) {
        val certificates = getCertificates().map { cert ->
            if (cert.alias == alias) {
                cert.copy(usages = cert.usages.filterNot {
                    it.host == usage.host && it.type == usage.type && it.path == usage.path
                })
            } else cert
        }
        saveCertificates(certificates)
        Log.d(TAG, "Removed usage from $alias: $usage")
    }

    fun removeCertificate(alias: String) {
        keyStore.deleteEntry(alias)
        val certificates = getCertificates().toMutableList()
        certificates.removeAll { it.alias == alias }
        saveCertificates(certificates)
        Log.d(TAG, "Removed certificate: $alias")
    }

    fun setActive(alias: String, isActive: Boolean) {
        val certificates = getCertificates().map {
            if (it.alias == alias) it.copy(isActive = isActive) else it
        }
        saveCertificates(certificates)
        Log.d(TAG, "Set certificate $alias active=$isActive")
    }

    fun findMatchingCertificates(host: String, path: String): List<ClientCertificate> {
        return getCertificates()
            .filter { cert -> cert.matchesUrl(host, path) }
            .sortedByDescending { cert ->
                cert.getBestMatchingUsage(host, path)?.specificity ?: 0
            }
    }

    fun findBestMatch(host: String, path: String): ClientCertificate? {
        return findMatchingCertificates(host, path).firstOrNull()
    }
    
    fun generateAlias(): String {
        return "$ALIAS_PREFIX${System.currentTimeMillis()}"
    }

    fun getKeyStore(): ClientCertKeyStore = keyStore

    private fun saveCertificates(certificates: List<ClientCertificate>) {
        val array = JSONArray()
        certificates.forEach { cert ->
            val obj = JSONObject().apply {
                put("alias", cert.alias)
                put("commonName", cert.commonName)
                cert.email?.let { put("email", it) }
                cert.organization?.let { put("organization", it) }

                // Save usages as array
                val usagesArray = JSONArray()
                cert.usages.forEach { usage ->
                    val usageObj = JSONObject().apply {
                        put("host", usage.host)
                        put("type", usage.type.name)
                        put("path", usage.path)
                    }
                    usagesArray.put(usageObj)
                }
                put("usages", usagesArray)

                put("fingerprint", cert.fingerprint)
                put("createdAt", cert.createdAt)
                put("expiresAt", cert.expiresAt)
                put("isActive", cert.isActive)
            }
            array.put(obj)
        }
        prefs.edit { putString(KEY_CERTIFICATES, array.toString()) }
    }
}

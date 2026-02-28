package mysh.dev.gemcap.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.domain.UsageType
import mysh.dev.gemcap.util.PemUtils
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.naming.InvalidNameException
import javax.naming.ldap.LdapName

private const val TAG = "ClientCertRepository"

/**
 * Repository for client certificate (identity) metadata.
 * Private keys/certificates are stored via EncryptedIdentityStorage and
 * metadata (usages, names, fingerprints, state) is stored in SharedPreferences.
 */
class ClientCertRepository(context: Context) {

    private val prefs = context.getSharedPreferences("client_certs", Context.MODE_PRIVATE)
    private val identityStorage = EncryptedIdentityStorage(context)

    companion object {
        private const val KEY_CERTIFICATES = "certificates"
        private const val KEY_BETA_MIGRATION_DONE = "beta_storage_migration_done"
        private const val KEY_BETA_MIGRATION_NOTICE_PENDING = "beta_storage_migration_notice_pending"
        const val ALIAS_PREFIX = "gemini_client_cert_"
    }

    init {
        runBetaMigrationIfNeeded()
    }

    /**
     * Retrieves all stored identities.
     */
    fun getCertificates(): List<ClientCertificate> {
        val json = prefs.getString(KEY_CERTIFICATES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val parsed = (0 until array.length()).mapNotNull { i ->
                parseCertificateEntry(array.getJSONObject(i))
            }
            val available = parsed.filter { identityStorage.containsIdentity(it.alias) }
            if (available.size != parsed.size) {
                saveCertificates(available)
            }
            available
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse certificates JSON", e)
            emptyList()
        }
    }

    fun addCertificate(certificate: ClientCertificate) {
        val certificates = getCertificates().toMutableList()
        certificates.removeAll { it.alias == certificate.alias }
        certificates.add(0, certificate)
        saveCertificates(certificates)
        Log.d(TAG, "Added certificate: ${certificate.alias}")
    }

    fun addUsage(alias: String, usage: IdentityUsage) {
        val certificates = getCertificates().map { cert ->
            if (cert.alias == alias) {
                val updatedUsages = cert.usages.filterNot {
                    it.host == usage.host && it.path == usage.path
                } + usage
                cert.copy(usages = updatedUsages)
            } else {
                cert
            }
        }
        saveCertificates(certificates)
        Log.d(TAG, "Added usage to $alias: $usage")
    }

    fun removeUsage(alias: String, usage: IdentityUsage) {
        val certificates = getCertificates().map { cert ->
            if (cert.alias == alias) {
                cert.copy(
                    usages = cert.usages.filterNot {
                        it.host == usage.host && it.type == usage.type && it.path == usage.path
                    }
                )
            } else {
                cert
            }
        }
        saveCertificates(certificates)
        Log.d(TAG, "Removed usage from $alias: $usage")
    }

    fun removeCertificate(alias: String) {
        val deleteSucceeded = try {
            identityStorage.deleteIdentity(alias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete identity for alias: $alias", e)
            false
        }
        if (!deleteSucceeded) {
            Log.e(TAG, "Failed to delete identity for alias: $alias; metadata unchanged")
            return
        }
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

    fun findByFingerprint(fingerprint: String): ClientCertificate? {
        return getCertificates().firstOrNull { it.fingerprint == fingerprint }
    }

    fun generateAlias(): String {
        return "$ALIAS_PREFIX${System.currentTimeMillis()}"
    }

    fun getIdentityStorage(): EncryptedIdentityStorage = identityStorage

    fun parseIdentityPem(pemData: String, passphrase: String?): ImportResult {
        return identityStorage.importFromPem(pemData, passphrase)
    }

    fun importIdentity(
        pemData: String,
        passphrase: String?,
        replaceAlias: String? = null
    ): IdentityImportStoreResult {
        return when (val parsed = identityStorage.importFromPem(pemData, passphrase)) {
            is ImportResult.NeedsPassphrase -> IdentityImportStoreResult.NeedsPassphrase
            is ImportResult.Error -> IdentityImportStoreResult.Error(parsed.message)
            is ImportResult.Success -> {
                if (replaceAlias == null) {
                    val duplicate = findByFingerprint(parsed.fingerprint)
                    if (duplicate != null) {
                        return IdentityImportStoreResult.Error("An identity with this fingerprint already exists")
                    }
                }

                val alias = replaceAlias ?: generateAlias()
                val existing = replaceAlias?.let { existingAlias ->
                    getCertificates().find { it.alias == existingAlias }
                }
                val stored = identityStorage.storeIdentity(
                    alias = alias,
                    privateKey = parsed.privateKey,
                    certificate = parsed.certificate
                )
                if (!stored) {
                    IdentityImportStoreResult.Error("Failed to store imported identity")
                } else {
                    val imported = buildClientCertificate(
                        alias = alias,
                        certificate = parsed.certificate,
                        fingerprint = parsed.fingerprint,
                        previous = existing
                    )
                    addCertificate(imported)
                    IdentityImportStoreResult.Success(imported)
                }
            }
        }
    }

    fun exportIdentity(alias: String): String? {
        val pem = identityStorage.exportAsPem(alias) ?: return null
        return PemUtils.combinePem(
            PemUtils.PemEncoding(
                certificatePem = pem.certificatePem,
                privateKeyPem = pem.privateKeyPem
            )
        )
    }

    fun consumeBetaMigrationNotice(): Boolean {
        val pending = prefs.getBoolean(KEY_BETA_MIGRATION_NOTICE_PENDING, false)
        if (pending) {
            prefs.edit { putBoolean(KEY_BETA_MIGRATION_NOTICE_PENDING, false) }
        }
        return pending
    }

    private fun parseCertificateEntry(obj: JSONObject): ClientCertificate? {
        return try {
            val alias = obj.getString("alias")

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
                alias = alias,
                commonName = obj.getString("commonName"),
                email = obj.optString("email", "").takeIf { it.isNotEmpty() },
                organization = obj.optString("organization", "").takeIf { it.isNotEmpty() },
                usages = usages,
                fingerprint = obj.getString("fingerprint"),
                createdAt = obj.getLong("createdAt"),
                expiresAt = obj.getLong("expiresAt"),
                isActive = obj.optBoolean("isActive", true),
                isExportable = obj.optBoolean("isExportable", true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse certificate entry", e)
            null
        }
    }

    private fun buildClientCertificate(
        alias: String,
        certificate: java.security.cert.X509Certificate,
        fingerprint: String,
        previous: ClientCertificate?
    ): ClientCertificate {
        val subjectDn = certificate.subjectX500Principal.name
        var commonName: String? = null
        var email: String? = null
        var organization: String? = null

        try {
            val ldapName = LdapName(subjectDn)
            ldapName.rdns.forEach { rdn ->
                val value = rdn.value?.toString() ?: return@forEach
                when {
                    rdn.type.equals("CN", ignoreCase = true) && commonName == null -> commonName = value
                    rdn.type.equals("EMAILADDRESS", ignoreCase = true) && email == null -> email = value
                    rdn.type.equals("O", ignoreCase = true) && organization == null -> organization = value
                }
            }
        } catch (_: IllegalArgumentException) {
            // Fall back to the raw subject DN values below.
        } catch (_: InvalidNameException) {
            // Fall back to the raw subject DN values below.
        }

        return ClientCertificate(
            alias = alias,
            commonName = commonName ?: subjectDn,
            email = email,
            organization = organization,
            usages = previous?.usages ?: emptyList(),
            fingerprint = fingerprint,
            createdAt = System.currentTimeMillis(),
            expiresAt = certificate.notAfter.time,
            isActive = previous?.isActive ?: true
        )
    }

    private fun runBetaMigrationIfNeeded() {
        if (prefs.getBoolean(KEY_BETA_MIGRATION_DONE, false)) {
            return
        }

        var hadLegacyEntries = false
        var keyStoreCleanupSucceeded = true
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith(ALIAS_PREFIX)) {
                    hadLegacyEntries = true
                    val deleteResult = runCatching { keyStore.deleteEntry(alias) }
                    if (deleteResult.isFailure) {
                        keyStoreCleanupSucceeded = false
                        Log.e(
                            TAG,
                            "Failed to delete legacy KeyStore alias during beta migration: $alias",
                            deleteResult.exceptionOrNull()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed during beta KeyStore migration cleanup", e)
            return
        }

        if (hadLegacyEntries && !keyStoreCleanupSucceeded) {
            Log.e(TAG, "Beta migration cleanup incomplete; migration will retry on next startup")
            return
        }

        if (hadLegacyEntries) {
            val cleanupSucceeded = try {
                identityStorage.clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed during beta identity file cleanup", e)
                false
            }
            if (!cleanupSucceeded) {
                Log.e(TAG, "Beta migration cleanup incomplete; migration will retry on next startup")
                return
            }
        }

        prefs.edit {
            if (hadLegacyEntries) {
                remove(KEY_CERTIFICATES)
            }
            putBoolean(KEY_BETA_MIGRATION_DONE, true)
            putBoolean(KEY_BETA_MIGRATION_NOTICE_PENDING, hadLegacyEntries)
        }
    }

    private fun saveCertificates(certificates: List<ClientCertificate>) {
        val array = JSONArray()
        certificates.forEach { cert ->
            val obj = JSONObject().apply {
                put("alias", cert.alias)
                put("commonName", cert.commonName)
                cert.email?.let { put("email", it) }
                cert.organization?.let { put("organization", it) }

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
                put("isExportable", cert.isExportable)
            }
            array.put(obj)
        }
        prefs.edit { putString(KEY_CERTIFICATES, array.toString()) }
    }
}

sealed class IdentityImportStoreResult {
    data class Success(val certificate: ClientCertificate) : IdentityImportStoreResult()
    data class Error(val message: String) : IdentityImportStoreResult()
    object NeedsPassphrase : IdentityImportStoreResult()
}

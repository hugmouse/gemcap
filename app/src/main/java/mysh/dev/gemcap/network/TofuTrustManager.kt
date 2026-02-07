package mysh.dev.gemcap.network

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TAG = "TofuTrustManager"
private const val DEFAULT_GEMINI_PORT = 1965

sealed class TofuResult {
    data object Trusted : TofuResult()
    data object FirstUse : TofuResult()
    data class CertificateChanged(
        val host: String,
        val port: Int,
        val oldFingerprint: String,
        val newFingerprint: String,
        val oldExpiryTime: Long,
        val wasExpired: Boolean,
        val isCATrusted: Boolean
    ) : TofuResult()

    data class DomainMismatch(
        val host: String,
        val certDomains: List<String>
    ) : TofuResult()

    data class Expired(
        val host: String,
        val expiredAt: Long
    ) : TofuResult()

    data class NotYetValid(
        val host: String,
        val notBefore: Long
    ) : TofuResult()
}

/*
    Implementing a custom X509TrustManager is error-prone and likely to be insecure.
    It is likely to disable certificate validation altogether, and is non-trivial
    to implement correctly without calling Android's default implementation.
 */
class TofuTrustManager(private val context: Context) : X509TrustManager {

    private val sharedPreferences = context.getSharedPreferences("tofu_certs", Context.MODE_PRIVATE)

    // Session-only domain mismatch bypasses (cleared when app process dies)
    private val domainBypassHosts = mutableSetOf<String>()

    private val systemTrustManager: X509TrustManager? by lazy {
        try {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as java.security.KeyStore?)
            factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get system trust manager", e)
            null
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // noop
    }

    /**
     * Accepts all certificates during TLS handshake.
     *
     * IMPORTANT: This intentionally does NOT enforce trust at handshake time.
     * Callers MUST invoke [verify] after the handshake completes to enforce
     * TOFU pinning. Failure to call [verify] leaves connections unauthenticated.
     */
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw IllegalArgumentException("Certificate chain is empty")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    /**
     * Computes SHA-256 fingerprint of the certificate's public key.
     * Using public key fingerprint (like Lagrange) allows continuity across cert renewals
     * that reuse the same key pair.
     */
    private fun getPublicKeyFingerprint(cert: X509Certificate): String {
        return Fingerprints.publicKeySha256Hex(cert)
    }

    /**
     * Checks if the certificate is trusted by a system CA.
     */
    private fun isCATrusted(chain: Array<X509Certificate>): Boolean {
        val manager = systemTrustManager ?: return false
        return try {
            manager.checkServerTrusted(chain, "RSA")
            true
        } catch (e: CertificateException) {
            false
        }
    }

    /**
     * Verifies that the certificate is valid for the given domain.
     * Checks both CN and Subject Alternative Names (SANs).
     */
    private fun verifyDomain(cert: X509Certificate, domain: String): Boolean {
        val domainLower = domain.lowercase()

        // Check Subject Alternative Names first (preferred method)
        try {
            cert.subjectAlternativeNames?.forEach { san ->
                val type = san[0] as? Int ?: return@forEach
                val value = san[1] as? String ?: return@forEach

                // Type 2 = DNS name
                if (type == 2) {
                    if (matchesDomain(value.lowercase(), domainLower)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking SANs", e)
        }

        // Fall back to Common Name (only if no SANs present, per RFC 6125)
        val cn = extractCommonName(cert.subjectX500Principal.name)
        return cn != null && matchesDomain(cn.lowercase(), domainLower)
    }

    /**
     * Matches domain against a pattern (supports wildcard like *.example.com)
     */
    private fun matchesDomain(pattern: String, domain: String): Boolean {
        if (pattern == domain) return true

        // Wildcard matching: *.example.com matches sub.example.com but not example.com
        if (pattern.startsWith("*.")) {
            val suffix = pattern.substring(1) // ".example.com"
            if (domain.endsWith(suffix) && domain.count { it == '.' } >= suffix.count { it == '.' }) {
                return true
            }
        }

        return false
    }

    /**
     * Gets all domains from a certificate (CN + SANs).
     */
    private fun getCertDomains(cert: X509Certificate): List<String> {
        val domains = mutableListOf<String>()

        // Add CN
        extractCommonName(cert.subjectX500Principal.name)?.let { domains.add(it) }

        // Add SANs
        try {
            cert.subjectAlternativeNames?.forEach { san ->
                val type = san[0] as? Int ?: return@forEach
                val value = san[1] as? String ?: return@forEach
                if (type == 2) { // DNS name
                    domains.add(value)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return domains.distinct()
    }

    /**
     * Extracts Common Name from a Distinguished Name string using BouncyCastle.
     * Properly handles escaped commas, quoted strings, and multi-valued RDNs.
     */
    private fun extractCommonName(dn: String): String? {
        return try {
            val x500Name = X500Name(dn)
            x500Name.getRDNs(BCStyle.CN).firstOrNull()
                ?.first?.value?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse DN: $dn", e)
            null
        }
    }

    /**
     * Adds a session-only domain bypass for a host:port.
     * This allows connections where the certificate doesn't match the domain.
     */
    fun addDomainBypass(host: String, port: Int = DEFAULT_GEMINI_PORT) {
        val key = "${host.lowercase()};${if (port <= 0) DEFAULT_GEMINI_PORT else port}"
        domainBypassHosts.add(key)
        Log.d(TAG, "Added domain bypass for $key (session-only)")
    }

    /**
     * Creates a trust key from domain and port.
     * Format: "domain;port" (port defaults to 1965)
     */
    private fun makeTrustKey(domain: String, port: Int): String {
        val effectivePort = if (port <= 0) DEFAULT_GEMINI_PORT else port
        return "${domain.lowercase()};$effectivePort"
    }

    /**
     * Main TOFU verification method.
     *
     * Flow:
     * 1. Check if certificate is CA-trusted
     * 2. Verify domain matches certificate
     * 3. Check if certificate is not expired
     * 4. Look up existing trust entry by domain:port key
     * 5. If trusted cert exists:
     *    - Verify fingerprint matches
     *    - If CA-trusted, allow update
     * 6. If no trust exists:
     *    - Store fingerprint + validity date
     * 7. Auto-save on successful trust
     */
    fun verify(
        host: String,
        port: Int = DEFAULT_GEMINI_PORT,
        cert: X509Certificate,
        chain: Array<X509Certificate> = arrayOf(cert)
    ): TofuResult {
        // Step 1: Check CA trust
        val isCATrusted = isCATrusted(chain)
        Log.d(TAG, "Certificate for $host:$port - CA trusted: $isCATrusted")

        // Step 2: Verify domain matches certificate
        val isDomainValid = verifyDomain(cert, host)
        if (!isDomainValid) {
            // Check if user has bypassed domain check for this session
            val bypassKey = "${host.lowercase()};${if (port <= 0) DEFAULT_GEMINI_PORT else port}"
            if (bypassKey !in domainBypassHosts) {
                Log.w(TAG, "Domain mismatch for $host")
                return TofuResult.DomainMismatch(
                    host = host,
                    certDomains = getCertDomains(cert)
                )
            }
            Log.d(TAG, "Domain mismatch for $host bypassed (session-only)")
        }

        // Step 3: Check certificate validity dates
        val now = Date()
        if (cert.notBefore.after(now)) {
            Log.w(TAG, "Certificate not yet valid for $host until ${cert.notBefore}")
            return TofuResult.NotYetValid(
                host = host,
                notBefore = cert.notBefore.time
            )
        }
        if (cert.notAfter.before(now)) {
            Log.w(TAG, "Certificate expired for $host at ${cert.notAfter}")
            return TofuResult.Expired(
                host = host,
                expiredAt = cert.notAfter.time
            )
        }

        // Step 4: Look up existing trust entry
        val trustKey = makeTrustKey(host, port)
        val fingerprint = getPublicKeyFingerprint(cert)
        val savedFingerprint = sharedPreferences.getString("${trustKey}_fp", null)
        val savedExpiry = sharedPreferences.getLong("${trustKey}_expiry", 0L)

        // Step 5: If trusted cert exists
        if (savedFingerprint != null) {
            val storedCertStillValid = savedExpiry > 0 && Date(savedExpiry).after(now)

            if (storedCertStillValid) {
                // Stored cert is still valid
                if (savedFingerprint == fingerprint) {
                    // Fingerprint matches - trusted
                    return TofuResult.Trusted
                }

                // Fingerprint doesn't match - cert changed before expiry
                // If CA-trusted, allow silent update
                if (isCATrusted) {
                    Log.d(TAG, "CA-trusted cert change for $host, auto-updating trust")
                    saveCertificate(trustKey, fingerprint, cert.notAfter.time)
                    return TofuResult.Trusted
                }

                // Not CA-trusted and changed - warn user
                return TofuResult.CertificateChanged(
                    host = host,
                    port = port,
                    oldFingerprint = savedFingerprint,
                    newFingerprint = fingerprint,
                    oldExpiryTime = savedExpiry,
                    wasExpired = false,
                    isCATrusted = false
                )
            } else {
                // Stored cert expired - update with new cert
                if (savedFingerprint == fingerprint) {
                    // Same key, just update expiry
                    saveCertificate(trustKey, fingerprint, cert.notAfter.time)
                    return TofuResult.Trusted
                }

                // Different fingerprint but old cert expired - auto-accept
                Log.d(TAG, "Previous cert for $host expired, auto-accepting new cert")
                saveCertificate(trustKey, fingerprint, cert.notAfter.time)

                // Return CertificateChanged with wasExpired=true so caller knows it was auto-accepted
                return TofuResult.CertificateChanged(
                    host = host,
                    port = port,
                    oldFingerprint = savedFingerprint,
                    newFingerprint = fingerprint,
                    oldExpiryTime = savedExpiry,
                    wasExpired = true,
                    isCATrusted = isCATrusted
                )
            }
        }

        // Step 6: No existing trust - first use, store and trust
        Log.d(TAG, "First use for $host:$port, storing fingerprint")
        saveCertificate(trustKey, fingerprint, cert.notAfter.time)
        return TofuResult.FirstUse
    }

    /**
     * Manually accept a new certificate (user confirmed).
     */
    fun acceptNewCertificate(host: String, port: Int, fingerprint: String, expiry: Long) {
        val trustKey = makeTrustKey(host, port)
        Log.d(TAG, "User accepted new certificate for $host:$port")
        saveCertificate(trustKey, fingerprint, expiry)
    }

    private fun saveCertificate(trustKey: String, fingerprint: String, expiry: Long) {
        sharedPreferences.edit {
            putString("${trustKey}_fp", fingerprint)
            putLong("${trustKey}_expiry", expiry)
        }
    }
}

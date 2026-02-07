package mysh.dev.gemcap.domain

import androidx.compose.runtime.Immutable

// NOTICE: All code in here was essentially copied from Lagrange

/**
 * Defines the type of usage for an identity
 */
enum class UsageType {
    DOMAIN,     // Apply to entire domain (e.g., geminiprotocol.net)
    DIRECTORY,  // Apply to a directory and subdirectories (e.g., /docs/)
    PAGE        // Apply to a specific page only (e.g., /docs/faq.gmi)
}

/**
 * Defines where an identity is used
 * An identity can have multiple usages across different sites
 */
@Immutable
data class IdentityUsage(
    val host: String,
    val type: UsageType,
    val path: String = "/"  // Only relevant for DIRECTORY and PAGE types
) {
    /**
     * Checks if this usage matches a given URL's host and path
     */
    fun matches(urlHost: String, urlPath: String): Boolean {
        if (host != urlHost) return false
        return when (type) {
            UsageType.DOMAIN -> true
            UsageType.DIRECTORY -> urlPath.startsWith(path) || urlPath == path.trimEnd('/')
            UsageType.PAGE -> urlPath == path
        }
    }

    /**
     * Returns the specificity of this usage
     * Higher values mean more specific matches
     * PAGE > DIRECTORY > DOMAIN
     */
    val specificity: Int
        get() = when (type) {
            UsageType.PAGE -> 1000 + path.length
            UsageType.DIRECTORY -> 500 + path.length
            UsageType.DOMAIN -> 1
        }

    /**
     * For labels
     */
    fun toDisplayString(): String = when (type) {
        UsageType.DOMAIN -> host
        UsageType.DIRECTORY -> "$host$path"
        UsageType.PAGE -> "$host$path"
    }

    fun typeLabel(): String = when (type) {
        UsageType.DOMAIN -> "Domain"
        UsageType.DIRECTORY -> "Directory"
        UsageType.PAGE -> "Page"
    }
}

/**
 * Represents a client certificate (identity) stored in the app
 */
@Immutable
data class ClientCertificate(
    val alias: String,                      // KeyStore alias
    val commonName: String,                 // Display name / CN
    val email: String? = null,              // Optional email
    val organization: String? = null,       // Optional organization
    val usages: List<IdentityUsage> = emptyList(),  // Where this identity is used (Lagrange-style)
    val fingerprint: String,                // SHA-256 fingerprint for display
    val createdAt: Long,                    // Creation timestamp
    val expiresAt: Long,                    // Expiration timestamp
    val isActive: Boolean = true            // Can be temporarily deactivated
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    fun matchesUrl(urlHost: String, urlPath: String): Boolean {
        if (!isActive || isExpired) return false
        return usages.any { it.matches(urlHost, urlPath) }
    }

    fun getBestMatchingUsage(urlHost: String, urlPath: String): IdentityUsage? {
        if (!isActive || isExpired) return null
        return usages.filter { it.matches(urlHost, urlPath) }
            .maxByOrNull { it.specificity }
    }
}


@Immutable
data class CertificateRequiredState(
    val statusCode: Int,            // 60, 61, or 62
    val message: String,            // Server's meta message
    val url: String,                // URL that requires cert
    val host: String,               // Host for scope matching
    val path: String,               // Path for scope matching
    val matchingCertificates: List<ClientCertificate>  // Certs that match scope
) {
    val title: String
        get() = when (statusCode) {
            60 -> "Certificate Required"
            61 -> "Certificate Not Authorized"
            62 -> "Certificate Not Valid"
            else -> "Certificate Error"
        }

    val canSelectExisting: Boolean get() = matchingCertificates.isNotEmpty() && statusCode == 60
    val canGenerateNew: Boolean get() = statusCode == 60
}

/**
 * State for viewing certificate details
 */
@Immutable
data class CertificateDetailsState(
    val commonName: String,
    val fingerprint: String,  // SHA-256
    val issuer: String,
    val validFrom: Long,
    val validUntil: Long,
    val isServerCert: Boolean // true for TOFU certs, false for client certs
)

/**
 * Server certificate info captured during connection
 */
@Immutable
data class ServerCertInfo(
    val host: String,
    val commonName: String,
    val issuer: String,
    val fingerprint: String,
    val validFrom: Long,
    val validUntil: Long,
    val isTrusted: Boolean // true if TOFU verified
)

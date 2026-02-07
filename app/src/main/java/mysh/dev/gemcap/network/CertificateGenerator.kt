package mysh.dev.gemcap.network

import android.util.Log
import mysh.dev.gemcap.data.ClientCertKeyStore
import mysh.dev.gemcap.data.ClientCertRepository
import mysh.dev.gemcap.domain.ClientCertificate
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

private const val TAG = "CertificateGenerator"
private const val DEFAULT_VALIDITY_YEARS = 1
private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

data class IdentityParams(
    val commonName: String,
    val email: String? = null,
    val userId: String? = null,
    val domain: String? = null,
    val organization: String? = null,
    val country: String? = null,
    val validityYears: Int = DEFAULT_VALIDITY_YEARS
)

class CertificateGenerator(
    private val keyStore: ClientCertKeyStore,
    private val repository: ClientCertRepository
) {

    fun generateCertificate(params: IdentityParams): ClientCertificate {
        val alias = repository.generateAlias()

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()
        Log.d(TAG, "Generated RSA key pair for alias: $alias")

        val certificate = createSelfSignedCertificate(keyPair, params)
        Log.d(TAG, "Created certificate for CN: ${params.commonName}")

        keyStore.importKeyPair(alias, keyPair.private, arrayOf(certificate))
        Log.d(TAG, "Imported key pair into Android KeyStore: $alias")

        val fingerprint = Fingerprints.certSha256Hex(
            certificate,
            uppercase = true,
            separator = ':'
        )

        // Create metadata - identity starts with no usage (user assigns it later (or at least should))
        val clientCert = ClientCertificate(
            alias = alias,
            commonName = params.commonName,
            email = params.email,
            organization = params.organization,
            usages = emptyList(),
            fingerprint = fingerprint,
            createdAt = System.currentTimeMillis(),
            expiresAt = certificate.notAfter.time,
            isActive = true
        )

        repository.addCertificate(clientCert)

        Log.d(TAG, "Certificate generated and stored: $alias")
        return clientCert
    }

    private fun createSelfSignedCertificate(
        keyPair: KeyPair,
        params: IdentityParams
    ): X509Certificate {
        val now = Date()
        val notBefore = Date(now.time - 24 * 60 * 60 * 1000) // 1 day before now
        val notAfter = Date(now.time + params.validityYears.toLong() * 365 * 24 * 60 * 60 * 1000)

        val serial = BigInteger(128, SecureRandom())

        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, params.commonName)
        params.email?.let { nameBuilder.addRDN(BCStyle.EmailAddress, it) }
        params.organization?.let { nameBuilder.addRDN(BCStyle.O, it) }
        params.country?.let { nameBuilder.addRDN(BCStyle.C, it) }
        val subject = nameBuilder.build()

        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        builder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(false)
        )

        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )

        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .build(keyPair.private)

        val holder = builder.build(signer)
        return JcaX509CertificateConverter()
            .getCertificate(holder)
    }

}

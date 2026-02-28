package mysh.dev.gemcap.util

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder
import org.bouncycastle.openssl.PKCS8Generator
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

class PemUtilsTest {

    @Test
    fun parseUnencryptedPem_returnsSuccess() {
        val keyPair = generateKeyPair()
        val certificate = generateCertificate(keyPair)
        val pemData = buildPem(certificate, keyPair)

        val result = PemUtils.parseIdentity(pemData)

        assertTrue(result is PemUtils.PemParseResult.Success)
        result as PemUtils.PemParseResult.Success
        assertEquals(certificate.subjectX500Principal.name, result.certificate.subjectX500Principal.name)
        assertEquals(keyPair.private.algorithm, result.privateKey.algorithm)
    }

    @Test
    fun parseEncryptedPkcs8_withoutPassphrase_returnsNeedsPassphrase() {
        val keyPair = generateKeyPair()
        val certificate = generateCertificate(keyPair)
        val pemData = buildPemWithEncryptedPkcs8(certificate, keyPair, "secret-passphrase")

        val result = PemUtils.parseIdentity(pemData)

        assertTrue(result is PemUtils.PemParseResult.NeedsPassphrase)
    }

    @Test
    fun parseEncryptedPkcs8_withPassphrase_returnsSuccess() {
        val keyPair = generateKeyPair()
        val certificate = generateCertificate(keyPair)
        val passphrase = "secret-passphrase"
        val pemData = buildPemWithEncryptedPkcs8(certificate, keyPair, passphrase)

        val result = PemUtils.parseIdentity(pemData, passphrase)

        assertTrue(result is PemUtils.PemParseResult.Success)
        result as PemUtils.PemParseResult.Success
        assertEquals(certificate.subjectX500Principal.name, result.certificate.subjectX500Principal.name)
        assertEquals(keyPair.private.algorithm, result.privateKey.algorithm)
    }

    @Test
    fun encodeAndParse_roundTrip_returnsSameIdentity() {
        val keyPair = generateKeyPair()
        val certificate = generateCertificate(keyPair)

        val encoded = PemUtils.encodeIdentity(certificate, keyPair.private)
        val combinedPem = PemUtils.combinePem(encoded)
        val parsed = PemUtils.parseIdentity(combinedPem)

        assertTrue(parsed is PemUtils.PemParseResult.Success)
        parsed as PemUtils.PemParseResult.Success
        assertEquals(certificate.subjectX500Principal.name, parsed.certificate.subjectX500Principal.name)
        assertEquals(keyPair.private.algorithm, parsed.privateKey.algorithm)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        return generator.generateKeyPair()
    }

    private fun generateCertificate(keyPair: KeyPair): X509Certificate {
        val now = Date()
        val subject = X500Name("CN=Pem Test,O=Gemcap,EMAILADDRESS=test@gemcap.dev")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(128, SecureRandom()),
            Date(now.time - 60_000),
            Date(now.time + 86_400_000),
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(builder.build(signer))
    }

    private fun buildPem(certificate: X509Certificate, keyPair: KeyPair): String {
        return StringWriter().use { writer ->
            JcaPEMWriter(writer).use { pemWriter ->
                pemWriter.writeObject(certificate)
                pemWriter.writeObject(keyPair)
            }
            writer.toString()
        }
    }

    private fun buildPemWithEncryptedPkcs8(
        certificate: X509Certificate,
        keyPair: KeyPair,
        passphrase: String
    ): String {
        @Suppress("DEPRECATION")
        val encryptor = JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC)
            .setProvider(BouncyCastleProvider())
            .setRandom(SecureRandom())
            .setPasssword(passphrase.toCharArray())
            .build()
        val pkcs8Generator = JcaPKCS8Generator(keyPair.private, encryptor)
        return StringWriter().use { writer ->
            JcaPEMWriter(writer).use { pemWriter ->
                pemWriter.writeObject(certificate)
                pemWriter.writeObject(pkcs8Generator)
            }
            writer.toString()
        }
    }
}

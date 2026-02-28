package mysh.dev.gemcap.data

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.crypto.KeyGenerator
import kotlin.io.path.createTempDirectory

class EncryptedIdentityStorageTest {

    @Test
    fun storeAndRetrieveIdentity_roundTrip_preservesKeyAndCertificate() {
        val tempDir = createTempDirectory(prefix = "identity-storage-test").toFile()
        try {
            val storage = createStorage(tempDir)
            val keyPair = generateKeyPair()
            val certificate = generateCertificate(keyPair)

            val stored = storage.storeIdentity("test_alias", keyPair.private, certificate)
            val retrieved = storage.getIdentity("test_alias")

            assertTrue(stored)
            assertNotNull(retrieved)
            retrieved!!
            assertEquals(keyPair.private.algorithm, retrieved.first.algorithm)
            assertArrayEquals(certificate.encoded, retrieved.second.encoded)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun exportAsPem_thenParse_roundTripSucceeds() {
        val tempDir = createTempDirectory(prefix = "identity-storage-pem-test").toFile()
        try {
            val storage = createStorage(tempDir)
            val keyPair = generateKeyPair()
            val certificate = generateCertificate(keyPair)
            storage.storeIdentity("roundtrip_alias", keyPair.private, certificate)

            val pem = storage.exportAsPem("roundtrip_alias")
            assertNotNull(pem)

            val parsed = storage.importFromPem(
                pemData = pem!!.certificatePem + "\n" + pem.privateKeyPem,
                passphrase = null
            )

            assertTrue(parsed is ImportResult.Success)
            parsed as ImportResult.Success
            assertEquals(certificate.subjectX500Principal.name, parsed.certificate.subjectX500Principal.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun clearAll_removesAllIdentityFiles() {
        val tempDir = createTempDirectory(prefix = "identity-storage-clear-test").toFile()
        try {
            val storage = createStorage(tempDir)
            val keyPair = generateKeyPair()
            val certificate = generateCertificate(keyPair)

            storage.storeIdentity("first_alias", keyPair.private, certificate)
            storage.storeIdentity("second_alias", keyPair.private, certificate)
            assertTrue(storage.getAllAliases().isNotEmpty())

            val cleared = storage.clearAll()

            assertTrue(cleared)
            assertTrue(storage.getAllAliases().isEmpty())
            assertTrue(storage.getIdentity("first_alias") == null)
            assertTrue(storage.getIdentity("second_alias") == null)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun createStorage(tempDir: File): EncryptedIdentityStorage {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val masterKey = keyGenerator.generateKey()
        return EncryptedIdentityStorage(tempDir, masterKey)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        return generator.generateKeyPair()
    }

    private fun generateCertificate(keyPair: KeyPair): X509Certificate {
        val now = Date()
        val subject = X500Name("CN=Storage Test,O=Gemcap,EMAILADDRESS=storage@gemcap.dev")
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
}

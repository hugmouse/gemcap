package mysh.dev.gemcap.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import mysh.dev.gemcap.util.generateCertificate
import mysh.dev.gemcap.util.generateKeyPair
import java.io.File
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
            assertArrayEquals(keyPair.private.encoded, retrieved.first.encoded)
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
            val stored = storage.storeIdentity("roundtrip_alias", keyPair.private, certificate)
            assertTrue(stored)

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
}

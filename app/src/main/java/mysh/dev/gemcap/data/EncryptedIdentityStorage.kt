package mysh.dev.gemcap.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import mysh.dev.gemcap.network.Fingerprints
import mysh.dev.gemcap.util.PemUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "gemcap_identity_master_key"
private const val AES_ALGORITHM = "AES"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH = 128
private const val IDENTITIES_DIR = "identities"

private val FILE_MAGIC = "GCID".toByteArray(StandardCharsets.US_ASCII)
private const val FILE_VERSION: Int = 1
private const val IDENTITY_EXTENSION = ".identity"

/**
 * Storage for identity private keys using app-managed encrypted files.
 *
 * The master encryption key is stored in AndroidKeyStore and never leaves the device.
 * Each identity is stored in a separate encrypted file with AES-256-GCM.
 */
class EncryptedIdentityStorage private constructor(
    private val identitiesDir: File,
    private val masterKeyProvider: () -> SecretKey
) {

    constructor(context: Context) : this(
        identitiesDir = File(context.filesDir, IDENTITIES_DIR),
        masterKeyProvider = { getOrCreateMasterKey() }
    )

    internal constructor(
        identitiesDir: File,
        masterKey: SecretKey
    ) : this(
        identitiesDir = identitiesDir,
        masterKeyProvider = { masterKey }
    )

    private val masterKey: SecretKey by lazy { masterKeyProvider() }

    init {
        if (!identitiesDir.exists()) {
            identitiesDir.mkdirs()
        }
    }

    /**
     * Store an identity (private key + certificate) encrypted to file.
     * @return true on success
     */
    fun storeIdentity(
        alias: String,
        privateKey: PrivateKey,
        certificate: X509Certificate
    ): Boolean {
        val keyBytes = privateKey.encoded ?: return false
        val certBytes = certificate.encoded
        val algorithmBytes = privateKey.algorithm.toByteArray(StandardCharsets.UTF_8)
        val payload = buildPayload(algorithmBytes, keyBytes, certBytes)
        keyBytes.fill(0)
        val (ciphertext, iv) = encrypt(payload)
        payload.fill(0)

        val target = getIdentityFile(alias)
        val temp = File(target.parentFile, "${target.name}.tmp")
        return try {
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { output ->
                output.write(FILE_MAGIC)
                output.writeByte(FILE_VERSION)
                output.writeByte(iv.size)
                output.write(iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                return false
            }
            true
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }

    /**
     * Retrieve and decrypt an identity.
     * @return Pair of (privateKey, certificate) or null if not found/error
     */
    fun getIdentity(alias: String): Pair<PrivateKey, X509Certificate>? {
        val file = getIdentityFile(alias)
        if (!file.exists()) {
            return null
        }

        return try {
            val decrypted = readAndDecrypt(file) ?: return null
            try {
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(decrypted.certificateBytes)) as X509Certificate
                val privateKey = parsePrivateKey(
                    encodedKey = decrypted.privateKeyBytes,
                    preferredAlgorithms = listOf(
                        decrypted.privateKeyAlgorithm,
                        certificate.publicKey.algorithm
                    )
                ) ?: return null
                privateKey to certificate
            } finally {
                decrypted.privateKeyBytes.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Delete an identity file.
     */
    fun deleteIdentity(alias: String): Boolean {
        return try {
            val identityFile = getIdentityFile(alias)
            if (identityFile.exists()) {
                identityFile.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if an identity exists.
     */
    fun containsIdentity(alias: String): Boolean {
        return getIdentityFile(alias).exists()
    }

    /**
     * Export an identity as PEM-formatted strings.
     * @return PemIdentity with certificate and private key PEM, or null if not found
     */
    fun exportAsPem(alias: String): PemIdentity? {
        val (privateKey, certificate) = getIdentity(alias) ?: return null
        val pem = PemUtils.encodeIdentity(certificate, privateKey)
        return PemIdentity(
            certificatePem = pem.certificatePem,
            privateKeyPem = pem.privateKeyPem
        )
    }

    /**
     * Parse/import an identity from PEM data.
     * This validates and parses but does not persist.
     */
    fun importFromPem(pemData: String, passphrase: String? = null): ImportResult {
        return when (val result = PemUtils.parseIdentity(pemData, passphrase)) {
            is PemUtils.PemParseResult.Success -> {
                ImportResult.Success(
                    certificate = result.certificate,
                    privateKey = result.privateKey,
                    fingerprint = Fingerprints.certSha256Hex(
                        result.certificate,
                        uppercase = true,
                        separator = ':'
                    )
                )
            }

            is PemUtils.PemParseResult.NeedsPassphrase -> ImportResult.NeedsPassphrase
            is PemUtils.PemParseResult.Error -> ImportResult.Error(result.message)
        }
    }

    /**
     * Get all stored identity aliases.
     */
    fun getAllAliases(): List<String> {
        return identitiesDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(IDENTITY_EXTENSION) }
            ?.map { it.name.removeSuffix(IDENTITY_EXTENSION) }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Clear all stored identities (for migration purposes).
     */
    fun clearAll(): Boolean {
        val files = identitiesDir.listFiles() ?: return true
        var success = true
        files.forEach { file ->
            if (file.isFile && file.name.endsWith(IDENTITY_EXTENSION) && !file.delete()) {
                success = false
            }
        }
        return success
    }

    private fun getIdentityFile(alias: String): File {
        require(alias.isNotBlank()) { "Identity alias must not be blank" }
        require(!alias.contains('/') && !alias.contains('\\') && !alias.contains("..")) {
            "Identity alias contains invalid path characters"
        }
        return File(identitiesDir, "$alias$IDENTITY_EXTENSION")
    }

    private fun buildPayload(
        privateKeyAlgorithm: ByteArray,
        privateKeyBytes: ByteArray,
        certificateBytes: ByteArray
    ): ByteArray {
        return ByteArrayOutputStream().use { byteStream ->
            DataOutputStream(byteStream).use { output ->
                output.writeInt(privateKeyAlgorithm.size)
                output.write(privateKeyAlgorithm)
                output.writeInt(privateKeyBytes.size)
                output.write(privateKeyBytes)
                output.writeInt(certificateBytes.size)
                output.write(certificateBytes)
            }
            byteStream.toByteArray()
        }
    }

    private fun readAndDecrypt(file: File): DecryptedIdentity? {
        val encryptedPayload = DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            val magic = ByteArray(FILE_MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(FILE_MAGIC)) {
                return null
            }

            val version = input.readUnsignedByte()
            if (version != FILE_VERSION) {
                return null
            }

            val ivLength = input.readUnsignedByte()
            if (ivLength <= 0 || ivLength > 32) {
                return null
            }
            val iv = ByteArray(ivLength)
            input.readFully(iv)

            val ciphertextLength = input.readInt()
            if (ciphertextLength <= 0 || ciphertextLength > file.length()) {
                return null
            }
            val ciphertext = ByteArray(ciphertextLength)
            input.readFully(ciphertext)

            decrypt(ciphertext, iv)
        }

        return DataInputStream(ByteArrayInputStream(encryptedPayload)).use { input ->
            val algorithmBytes = readSizedBytes(input) ?: return null
            val keyBytes = readSizedBytes(input) ?: return null
            val certificateBytes = readSizedBytes(input) ?: return null
            DecryptedIdentity(
                privateKeyAlgorithm = algorithmBytes.toString(StandardCharsets.UTF_8),
                privateKeyBytes = keyBytes,
                certificateBytes = certificateBytes
            )
        }
    }

    private fun readSizedBytes(input: DataInputStream): ByteArray? {
        val size = input.readInt()
        if (size <= 0 || size > 16 * 1024 * 1024) {
            return null
        }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return bytes
    }

    private fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        // AndroidKeyStore GCM keys (including Android 10) reject caller-supplied IVs.
        // Let the provider generate a fresh IV and persist it alongside ciphertext.
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val ciphertext = cipher.doFinal(plaintext)
        val iv: ByteArray = cipher.iv ?: throw IllegalStateException(
            "Cipher did not provide IV for AES-GCM encryption"
        )
        require(iv.isNotEmpty()) { "Cipher provided empty IV for AES-GCM encryption" }
        return ciphertext to iv
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun parsePrivateKey(
        encodedKey: ByteArray,
        preferredAlgorithms: List<String>
    ): PrivateKey? {
        val algorithms = buildList {
            preferredAlgorithms
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { add(it) }
            add("RSA")
            add("EC")
            add("DSA")
            add("Ed25519")
            add("Ed448")
        }.distinct()

        val keySpec = PKCS8EncodedKeySpec(encodedKey)
        algorithms.forEach { algorithm ->
            val parsed = runCatching {
                KeyFactory.getInstance(algorithm).generatePrivate(keySpec)
            }.getOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        return null
    }

    private data class DecryptedIdentity(
        val privateKeyAlgorithm: String,
        val privateKeyBytes: ByteArray,
        val certificateBytes: ByteArray
    )

    companion object {
        private fun getOrCreateMasterKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                return entry.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
    }
}

data class PemIdentity(
    val certificatePem: String,
    val privateKeyPem: String
)

sealed class ImportResult {
    data class Success(
        val certificate: X509Certificate,
        val privateKey: PrivateKey,
        val fingerprint: String
    ) : ImportResult()

    data class Error(val message: String) : ImportResult()
    object NeedsPassphrase : ImportResult()
}

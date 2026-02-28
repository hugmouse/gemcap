package mysh.dev.gemcap.util

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import java.io.StringReader
import java.io.StringWriter
import java.security.PrivateKey
import java.security.Provider
import java.security.cert.X509Certificate

object PemUtils {

    private val provider: Provider by lazy { BouncyCastleProvider() }

    private fun keyConverter(): JcaPEMKeyConverter {
        return JcaPEMKeyConverter().setProvider(provider)
    }

    private fun certConverter(): JcaX509CertificateConverter {
        return JcaX509CertificateConverter().setProvider(provider)
    }

    fun parseIdentity(
        pemData: String,
        passphrase: String? = null
    ): PemParseResult {
        val normalizedPassphrase = passphrase?.takeIf { it.isNotEmpty() }
        return try {
            var certificate: X509Certificate? = null
            var privateKey: PrivateKey? = null
            var hasEncryptedPrivateKey = false

            PEMParser(StringReader(pemData)).use { parser ->
                while (true) {
                    val parsed = parser.readObject() ?: break
                    when (parsed) {
                        is X509CertificateHolder -> {
                            certificate = certConverter().getCertificate(parsed)
                        }

                        is PEMKeyPair -> {
                            privateKey = keyConverter().getKeyPair(parsed).private
                        }

                        is PrivateKeyInfo -> {
                            privateKey = keyConverter().getPrivateKey(parsed)
                        }

                        is PEMEncryptedKeyPair -> {
                            hasEncryptedPrivateKey = true
                            if (normalizedPassphrase != null) {
                                privateKey = decryptPemKeyPair(parsed, normalizedPassphrase)
                            }
                        }

                        is PKCS8EncryptedPrivateKeyInfo -> {
                            hasEncryptedPrivateKey = true
                            if (normalizedPassphrase != null) {
                                privateKey = decryptPkcs8Key(parsed, normalizedPassphrase)
                            }
                        }
                    }
                }
            }

            when {
                certificate == null -> PemParseResult.Error("No certificate found in PEM data")
                privateKey == null && hasEncryptedPrivateKey -> PemParseResult.NeedsPassphrase
                privateKey == null -> PemParseResult.Error("No private key found in PEM data")
                else -> PemParseResult.Success(certificate = certificate, privateKey = privateKey)
            }
        } catch (_: Exception) {
            if (normalizedPassphrase != null) {
                PemParseResult.Error("Incorrect passphrase or invalid PEM data")
            } else {
                PemParseResult.Error("Failed to parse PEM data")
            }
        }
    }

    fun encodeIdentity(
        certificate: X509Certificate,
        privateKey: PrivateKey
    ): PemEncoding {
        val certificatePem = StringWriter().use { writer ->
            JcaPEMWriter(writer).use { pemWriter ->
                pemWriter.writeObject(certificate)
            }
            writer.toString().trimEnd() + "\n"
        }

        val privateKeyPem = StringWriter().use { writer ->
            JcaPEMWriter(writer).use { pemWriter ->
                pemWriter.writeObject(privateKey)
            }
            writer.toString().trimEnd() + "\n"
        }

        return PemEncoding(
            certificatePem = certificatePem,
            privateKeyPem = privateKeyPem
        )
    }

    fun combinePem(encoding: PemEncoding): String {
        return buildString {
            append(encoding.certificatePem.trimEnd())
            append('\n')
            append(encoding.privateKeyPem.trimEnd())
            append('\n')
        }
    }

    private fun decryptPemKeyPair(
        encryptedKeyPair: PEMEncryptedKeyPair,
        passphrase: String
    ): PrivateKey {
        val decryptor = JcePEMDecryptorProviderBuilder()
            .setProvider(provider)
            .build(passphrase.toCharArray())
        val decrypted = encryptedKeyPair.decryptKeyPair(decryptor)
        return keyConverter().getKeyPair(decrypted).private
    }

    private fun decryptPkcs8Key(
        encryptedPrivateKeyInfo: PKCS8EncryptedPrivateKeyInfo,
        passphrase: String
    ): PrivateKey {
        val decryptor = JceOpenSSLPKCS8DecryptorProviderBuilder()
            .setProvider(provider)
            .build(passphrase.toCharArray())
        val privateKeyInfo = encryptedPrivateKeyInfo.decryptPrivateKeyInfo(decryptor)
        return keyConverter().getPrivateKey(privateKeyInfo)
    }

    data class PemEncoding(
        val certificatePem: String,
        val privateKeyPem: String
    )

    sealed class PemParseResult {
        data class Success(
            val certificate: X509Certificate,
            val privateKey: PrivateKey
        ) : PemParseResult()

        data class Error(val message: String) : PemParseResult()
        object NeedsPassphrase : PemParseResult()
    }
}

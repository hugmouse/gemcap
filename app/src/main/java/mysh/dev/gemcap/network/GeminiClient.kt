package mysh.dev.gemcap.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import mysh.dev.gemcap.data.ClientCertKeyStore
import mysh.dev.gemcap.domain.GeminiResponse
import mysh.dev.gemcap.domain.ServerCertInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

private const val TAG = "GeminiClient"
private const val MAX_URI_BYTES = 1024
private const val MAX_SSL_RETRIES = 3
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

class UriTooLongException(length: Int) :
    Exception("URI exceeds maximum length of $MAX_URI_BYTES bytes (was $length)")

sealed class GeminiFetchResult {
    data class Success(
        val response: GeminiResponse,
        val tofuResult: TofuResult,
        val serverCertInfo: ServerCertInfo? = null
    ) : GeminiFetchResult()

    data class TofuWarning(
        val host: String,
        val port: Int,
        val oldFingerprint: String,
        val newFingerprint: String,
        val newExpiry: Long,
        val wasExpired: Boolean,
        val isCATrusted: Boolean,
        val pendingUrl: String
    ) : GeminiFetchResult()

    data class TofuDomainMismatch(
        val host: String,
        val certDomains: List<String>,
        val pendingUrl: String
    ) : GeminiFetchResult()

    data class TofuExpired(
        val host: String,
        val expiredAt: Long,
        val pendingUrl: String
    ) : GeminiFetchResult()

    data class TofuNotYetValid(
        val host: String,
        val notBefore: Long,
        val pendingUrl: String
    ) : GeminiFetchResult()

    data class CertificateRequired(
        val statusCode: Int,
        val meta: String,
        val url: String
    ) : GeminiFetchResult()

    data class Error(val exception: Exception) : GeminiFetchResult()
}

class GeminiClient(context: Context) {

    private val tofuTrustManager = TofuTrustManager(context)
    private val keyStore = ClientCertKeyStore()

    private fun createSslContext(certAlias: String?): SSLContext {
        val keyManager = SelectiveKeyManager(keyStore, certAlias)
        return SSLContext.getInstance("TLS").apply {
            init(
                arrayOf<KeyManager>(keyManager),
                arrayOf<TrustManager>(tofuTrustManager),
                SecureRandom()
            )
        }
    }

    private fun getSocketFactory(certAlias: String?): javax.net.ssl.SSLSocketFactory {
        return createSslContext(certAlias).socketFactory
    }

    /**
     * Fetches a Gemini URL.
     *
     * @param url The URL to fetch
     * @param certAlias Optional client certificate alias to use for this request
     */
    suspend fun fetch(url: String, certAlias: String? = null): GeminiFetchResult =
        withContext(Dispatchers.IO) {
            fetchWithRetry(url, certAlias)
        }

    /**
     * Retries the fetch on transient SSL errors (BAD_DECRYPT / BAD_RECORD_MAC).
     *
     * Some Android TLS 1.3 implementations (BoringSSL/Conscrypt) can intermittently
     * fail with DECRYPTION_FAILED_OR_BAD_RECORD_MAC when processing post-handshake
     * messages (e.g. NewSessionTicket). A fresh connection usually succeeds.
     *
     * This is a stupid hack and should be removed if I ever understand the issue.
     */
    private suspend fun fetchWithRetry(url: String, certAlias: String?): GeminiFetchResult {
        var lastError: GeminiFetchResult.Error? = null
        for (attempt in 1..MAX_SSL_RETRIES) {
            val result = fetchInternal(url, getSocketFactory(certAlias))
            val sslException = (result as? GeminiFetchResult.Error)?.exception as? SSLException
            if (sslException == null || !isTransientSslException(sslException)) {
                return result
            }
            lastError = result
            Log.w(
                TAG,
                "Transient SSL error on attempt $attempt/$MAX_SSL_RETRIES for $url: ${sslException.message}"
            )
            if (attempt < MAX_SSL_RETRIES) {
                delay(100L * attempt)
            }
        }
        return requireNotNull(lastError) {
            "Transient SSL retry exhausted without capturing an error result"
        }
    }

    private suspend fun fetchInternal(
        url: String,
        socketFactory: javax.net.ssl.SSLSocketFactory
    ): GeminiFetchResult {
        // Normalize and validate URL per Gemini spec
        val normalizedUrl = try {
            normalizeUrl(url)
        } catch (e: Exception) {
            return GeminiFetchResult.Error(e)
        }

        val uri = URI(normalizedUrl)
        val host = uri.host ?: return GeminiFetchResult.Error(
            IllegalArgumentException("Invalid Host")
        )
        val port = if (uri.port != -1) uri.port else 1965
        Log.d(
            TAG,
            "Fetch start url=$url normalized=$normalizedUrl host=$host port=$port path=${uri.path} query=${uri.query}"
        )

        // First create a plain TCP socket and connect with timeout
        val rawSocket = Socket()
        fun closeRawSocket() {
            runCatching {
                if (!rawSocket.isClosed) {
                    rawSocket.close()
                }
            }
        }

        // Close socket when coroutine is cancelled - interrupts blocking I/O
        currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) {
                closeRawSocket()
            }
        }

        try {
            Log.d(TAG, "Connecting to $host:$port...")
            rawSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

            // Wrap with SSL using the hostname - this properly binds the hostname
            // to the native SSL object for correct SNI, session management, and
            // TLS record processing. Using createSocket(socket, host, port, autoClose)
            // avoids BAD_DECRYPT errors that occur when hostname is only set via
            // sslParameters after socket creation.
            val sslSocket = socketFactory.createSocket(
                rawSocket, host, port, true  // autoClose = true
            ) as SSLSocket

            sslSocket.use {
                sslSocket.soTimeout = READ_TIMEOUT_MS

                Log.d(TAG, "Starting TLS handshake for $host...")
                sslSocket.startHandshake()
                Log.d(TAG, "TLS handshake completed for $host")
                try {
                    Log.d(
                        TAG,
                        "TLS session protocol=${sslSocket.session.protocol} cipher=${sslSocket.session.cipherSuite} peerHost=${sslSocket.session.peerHost} address=${sslSocket.inetAddress.hostAddress}"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read TLS session details", e)
                }

                val session = sslSocket.session
                val peerCerts = session.peerCertificates
                if (peerCerts.isEmpty()) {
                    return GeminiFetchResult.Error(
                        IllegalStateException("No peer certificates received from server")
                    )
                }
                val serverCert = peerCerts[0] as? X509Certificate
                    ?: return GeminiFetchResult.Error(
                        IllegalStateException("Server certificate is not X509Certificate")
                    )

                // Build full certificate chain
                val certChain = peerCerts.filterIsInstance<X509Certificate>().toTypedArray()

                // Perform TOFU check
                when (val tofuResult =
                    tofuTrustManager.verify(host, port, serverCert, certChain)) {
                    is TofuResult.CertificateChanged -> {
                        if (!tofuResult.wasExpired) {
                            // Certificate changed before expiry - warn user
                            return GeminiFetchResult.TofuWarning(
                                host = host,
                                port = port,
                                oldFingerprint = tofuResult.oldFingerprint,
                                newFingerprint = tofuResult.newFingerprint,
                                newExpiry = serverCert.notAfter.time,
                                wasExpired = false,
                                isCATrusted = tofuResult.isCATrusted,
                                pendingUrl = normalizedUrl
                            )
                        }
                        // Was expired, already auto-accepted in TofuTrustManager
                    }

                    is TofuResult.DomainMismatch -> {
                        return GeminiFetchResult.TofuDomainMismatch(
                            host = host,
                            certDomains = tofuResult.certDomains,
                            pendingUrl = normalizedUrl
                        )
                    }

                    is TofuResult.Expired -> {
                        return GeminiFetchResult.TofuExpired(
                            host = host,
                            expiredAt = tofuResult.expiredAt,
                            pendingUrl = normalizedUrl
                        )
                    }

                    is TofuResult.NotYetValid -> {
                        return GeminiFetchResult.TofuNotYetValid(
                            host = host,
                            notBefore = tofuResult.notBefore,
                            pendingUrl = normalizedUrl
                        )
                    }

                    else -> { /* Trusted or FirstUse - proceed */
                    }
                }

                // Send Request
                val writer = sslSocket.outputStream.bufferedWriter()
                writer.write("$normalizedUrl\r\n")
                writer.flush()

                // Read response
                val inputStream = sslSocket.inputStream
                val response = parseResponse(inputStream)
                Log.d(TAG, "Fetch response status=${response.status} meta='${response.meta}'")

                // Handle client certificate required responses
                if (response.status in 60..62) {
                    Log.d(TAG, "Client cert required status=${response.status} url=$normalizedUrl")
                    return GeminiFetchResult.CertificateRequired(
                        statusCode = response.status,
                        meta = response.meta,
                        url = normalizedUrl
                    )
                }

                // Build server cert info for the padlock icon
                val certInfo = ServerCertInfo(
                    host = host,
                    commonName = extractCommonName(serverCert.subjectX500Principal.name),
                    issuer = serverCert.issuerX500Principal.name,
                    fingerprint = Fingerprints.certSha256Hex(serverCert),
                    validFrom = serverCert.notBefore.time,
                    validUntil = serverCert.notAfter.time,
                    isTrusted = true
                )

                return GeminiFetchResult.Success(
                    response,
                    TofuResult.Trusted,
                    certInfo
                )
            }
        } catch (e: java.net.SocketException) {
            closeRawSocket()
            // Check if socket was closed due to cancellation
            currentCoroutineContext().ensureActive()  // Throws CancellationException if cancelled
            Log.e(TAG, "Failed to fetch: $url", e)
            return GeminiFetchResult.Error(e)
        } catch (e: SSLException) {
            closeRawSocket()
            Log.e(TAG, "SSL error fetching: $url", e)
            return GeminiFetchResult.Error(e)
        } catch (e: CancellationException) {
            closeRawSocket()
            throw e
        } catch (e: Exception) {
            closeRawSocket()
            Log.e(TAG, "Failed to fetch: $url", e)
            return GeminiFetchResult.Error(e)
        }
    }

    private fun isTransientSslException(exception: SSLException): Boolean {
        val message = buildString {
            exception.message?.let { append(it) }
            exception.cause?.message?.let { causeMessage ->
                if (isNotEmpty()) {
                    append(' ')
                }
                append(causeMessage)
            }
        }
        if (message.isBlank()) {
            return false
        }
        val upper = message.uppercase()
        return upper.contains("BAD_RECORD_MAC") ||
            upper.contains("BAD_DECRYPT") ||
            upper.contains("DECRYPTION_FAILED")
    }

    fun bypassDomainCheck(host: String, port: Int = 1965) {
        tofuTrustManager.addDomainBypass(host, port)
    }

    fun acceptCertificateAndRetry(host: String, port: Int, fingerprint: String, expiry: Long) {
        tofuTrustManager.acceptNewCertificate(host, port, fingerprint, expiry)
    }

    private fun parseResponse(inputStream: InputStream): GeminiResponse {
        val allBytes = inputStream.readBytes()

        var headerEndIndex = -1
        for (i in 0 until allBytes.size - 1) {
            if (allBytes[i] == '\r'.code.toByte() && allBytes[i + 1] == '\n'.code.toByte()) {
                headerEndIndex = i
                break
            }
        }

        if (headerEndIndex == -1) throw Exception("Invalid response: no header terminator found")

        val headerLine = String(allBytes, 0, headerEndIndex, Charsets.UTF_8)
        val parts = headerLine.trim().split(Regex("\\s+"), 2)
        val statusString = parts.getOrNull(0) ?: throw Exception("Invalid status")
        val meta = parts.getOrNull(1) ?: ""

        val status = statusString.toIntOrNull() ?: throw Exception("Non-numeric status code")

        var body: ByteArray? = null
        if (status in 20..29) {
            val bodyStart = headerEndIndex + 2
            body = allBytes.copyOfRange(bodyStart, allBytes.size)
        }

        return GeminiResponse(status, meta, body)
    }

    private fun normalizeUrl(url: String): String {
        val uri = URI(url)

        // Strip fragment (everything after #)
        val pathWithoutFragment = uri.path ?: ""

        // Add trailing slash for empty paths per Gemini spec
        val normalizedPath = pathWithoutFragment.ifEmpty { "/" }

        // Rebuild URI without fragment
        val normalized = URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            normalizedPath,
            uri.query,
            null // No fragment
        ).toString()

        // Validate length
        val bytes = normalized.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_URI_BYTES) {
            throw UriTooLongException(bytes.size)
        }

        return normalized
    }

    private fun extractCommonName(dn: String): String {
        return try {
            val x500Name = X500Name(dn)
            x500Name.getRDNs(BCStyle.CN).firstOrNull()
                ?.first?.value?.toString()
                ?: dn
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse DN: $dn", e)
            dn
        }
    }
}

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
import mysh.dev.gemcap.data.EncryptedIdentityStorage
import mysh.dev.gemcap.domain.GeminiResponse
import mysh.dev.gemcap.domain.ServerCertInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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
private const val MAX_HEADER_BYTES = 1024
internal const val DEFAULT_MAX_RESPONSE_BODY_BYTES = 16 * 1024 * 1024
private const val MAX_FILE_RESPONSE_BODY_BYTES = 500 * 1024 * 1024

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

class GeminiClient(
    context: Context,
    private val identityStorage: EncryptedIdentityStorage,
    private val maxResponseBodyBytes: Int = DEFAULT_MAX_RESPONSE_BODY_BYTES
) {

    private val tofuTrustManager = TofuTrustManager(context)

    init {
        require(maxResponseBodyBytes > 0) { "maxResponseBodyBytes must be > 0" }
    }

    private fun createSslContext(certAlias: String?): SSLContext {
        val keyManager = SelectiveKeyManager(identityStorage, certAlias)
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
    suspend fun fetch(
        url: String,
        certAlias: String? = null,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): GeminiFetchResult =
        withContext(Dispatchers.IO) {
            fetchWithRetry(url, certAlias, onProgress)
        }

    /**
     * Fetches a Gemini URL and streams the response body to a file on disk.
     *
     * Unlike [fetch], this method does not enforce [maxResponseBodyBytes] and can handle
     * arbitrarily large responses (up to 500MB safety limit). The returned [GeminiResponse]
     * will have `body = null`; the data is written to [outputFile].
     *
     * @param url The URL to fetch
     * @param outputFile The file to write the response body to
     * @param certAlias Optional client certificate alias to use for this request
     * @param onProgress Callback invoked with total bytes read so far
     */
    suspend fun fetchToFile(
        url: String,
        outputFile: File,
        certAlias: String? = null,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): GeminiFetchResult = withContext(Dispatchers.IO) {
        fetchWithRetry(url, certAlias, onProgress, outputFile)
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
    private suspend fun fetchWithRetry(
        url: String,
        certAlias: String?,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
        outputFile: File? = null
    ): GeminiFetchResult {
        var lastError: GeminiFetchResult.Error? = null
        for (attempt in 1..MAX_SSL_RETRIES) {
            val result = fetchInternal(url, getSocketFactory(certAlias), onProgress, outputFile)
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

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchInternal(
        url: String,
        socketFactory: javax.net.ssl.SSLSocketFactory,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
        outputFile: File? = null
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
                val response = if (outputFile != null) {
                    parseResponseToFile(inputStream, outputFile, onProgress)
                } else {
                    parseResponse(inputStream, onProgress)
                }
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
            // SocketException, SSLException, and CancellationException are handled above.
            // Keep this fallback so fetchWithRetry can surface unexpected runtime failures.
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

    private data class ParsedHeader(val status: Int, val meta: String)

    private fun parseHeader(inputStream: InputStream): ParsedHeader {
        val headerBuffer = ByteArrayOutputStream()
        var previous = -1
        var current: Int
        var headerEndIndex = -1

        while (true) {
            current = inputStream.read()
            if (current == -1) {
                break
            }
            headerBuffer.write(current)
            if (headerBuffer.size() > MAX_HEADER_BYTES) {
                throw Exception("Invalid response: header exceeds $MAX_HEADER_BYTES bytes")
            }
            if (previous == '\r'.code && current == '\n'.code) {
                headerEndIndex = headerBuffer.size() - 2
                break
            }
            previous = current
        }

        if (headerEndIndex == -1) throw Exception("Invalid response: no header terminator found")

        val headerBytes = headerBuffer.toByteArray()
        val headerLine = String(headerBytes, 0, headerEndIndex, Charsets.UTF_8)
        val spaceIndex = headerLine.indexOf(' ')
        val statusString = if (spaceIndex != -1) headerLine.substring(0, spaceIndex) else headerLine
        val meta = if (spaceIndex != -1) headerLine.substring(spaceIndex + 1) else ""

        val status = statusString.toIntOrNull() ?: throw Exception("Non-numeric status code")

        return ParsedHeader(status, meta)
    }

    private fun parseResponse(
        inputStream: InputStream,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): GeminiResponse {
        val header = parseHeader(inputStream)
        var body: ByteArray? = null
        if (header.status in 20..29) {
            body = readResponseBodyWithLimit(inputStream, onProgress)
        }
        return GeminiResponse(header.status, header.meta, body)
    }

    private fun parseResponseToFile(
        inputStream: InputStream,
        outputFile: File,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): GeminiResponse {
        val header = parseHeader(inputStream)
        if (header.status in 20..29) {
            val tempFile = File(outputFile.parent, outputFile.name + ".tmp")
            try {
                readResponseBodyToFile(inputStream, tempFile, onProgress)
                if (!tempFile.renameTo(outputFile)) {
                    tempFile.inputStream().use { input ->
                        outputFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile.delete()
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
        return GeminiResponse(header.status, header.meta, body = null)
    }

    private fun readResponseBodyWithLimit(
        inputStream: InputStream,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var totalBytes = 0L

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) {
                break
            }
            totalBytes += read
            if (totalBytes > maxResponseBodyBytes) {
                throw Exception(
                    "Response body exceeds max size of $maxResponseBodyBytes bytes"
                )
            }
            output.write(buffer, 0, read)
            onProgress?.invoke(totalBytes)
        }
        return output.toByteArray()
    }

    private fun readResponseBodyToFile(
        inputStream: InputStream,
        outputFile: File,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        BufferedOutputStream(FileOutputStream(outputFile)).use { bos ->
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > MAX_FILE_RESPONSE_BODY_BYTES) {
                    throw Exception(
                        "Response body exceeds max file size of $MAX_FILE_RESPONSE_BODY_BYTES bytes"
                    )
                }
                bos.write(buffer, 0, read)
                onProgress?.invoke(totalBytes)
            }
        }
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

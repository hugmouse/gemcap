package mysh.dev.gemcap.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlin.math.min

@OptIn(UnstableApi::class)
class ByteArrayDataSource(
    private val data: ByteArray
) : BaseDataSource(/* isNetwork= */ false) {

    private var uri: Uri? = null
    private var readPosition = 0
    private var bytesRemaining = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        if (dataSpec.position > data.size ||
            (dataSpec.position == data.size.toLong() && dataSpec.length > 0L)
        ) {
            throw java.io.IOException(
                "Position ${dataSpec.position} exceeds data size ${data.size}"
            )
        }
        readPosition = dataSpec.position.toInt()
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length.toInt(), data.size - readPosition).coerceAtLeast(0)
        } else {
            data.size - readPosition
        }
        transferStarted(dataSpec)
        return bytesRemaining.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT
        val bytesToRead = min(length, bytesRemaining)
        System.arraycopy(data, readPosition, buffer, offset, bytesToRead)
        readPosition += bytesToRead
        bytesRemaining -= bytesToRead
        bytesTransferred(bytesToRead)
        return bytesToRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        transferEnded()
    }

    class Factory(
        private val data: ByteArray
    ) : DataSource.Factory {
        override fun createDataSource(): ByteArrayDataSource {
            return ByteArrayDataSource(data)
        }
    }
}

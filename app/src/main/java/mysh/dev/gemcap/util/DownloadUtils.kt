package mysh.dev.gemcap.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

object DownloadUtils {

    fun saveToDownloads(
        context: Context,
        data: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> = writeToMediaStore(context, fileName, mimeType) { outputStream ->
        outputStream.write(data)
    }

    fun saveToDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): Result<String> = writeToMediaStore(context, fileName, mimeType) { outputStream ->
        sourceFile.inputStream().buffered().use { input ->
            input.copyTo(outputStream)
        }
    }

    private fun writeToMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        writeContent: (java.io.OutputStream) -> Unit
    ): Result<String> {
        var pendingUri: Uri? = null
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return Result.failure(Exception("Failed to create download entry"))
            pendingUri = uri

            resolver.openOutputStream(uri)?.use { outputStream ->
                writeContent(outputStream)
            } ?: run {
                resolver.delete(uri, null, null)
                return Result.failure(Exception("Failed to open output stream"))
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            val updated = resolver.update(uri, contentValues, null, null)
            if (updated == 0) {
                resolver.delete(uri, null, null)
                return Result.failure(Exception("Failed to finalize download entry"))
            }

            Result.success("Downloads/$fileName")
        } catch (e: Exception) {
            pendingUri?.let { context.contentResolver.delete(it, null, null) }
            Result.failure(e)
        }
    }

    fun suggestFileName(url: String, mimeType: String): String {
        val urlFileName =
            url.substringAfterLast("/").takeIf { it.isNotEmpty() && !it.contains("?") }

        if (urlFileName != null && urlFileName.contains(".")) {
            return urlFileName
        }

        val extension = mimeTypeToExtension(mimeType)
        val baseName = urlFileName ?: "download_${System.currentTimeMillis()}"

        return if (baseName.contains(".")) baseName else "$baseName.$extension"
    }

    private fun mimeTypeToExtension(mimeType: String): String {
        val normalized = mimeType.lowercase()
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(normalized)
        return ext ?: "bin"
    }
}

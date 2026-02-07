package mysh.dev.gemcap.util

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap

object DownloadUtils {

    fun saveToDownloads(
        context: Context,
        data: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure(Exception("Failed to create download entry"))

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(data)
        } ?: return Result.failure(Exception("Failed to open output stream"))

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return Result.success("Downloads/$fileName")
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

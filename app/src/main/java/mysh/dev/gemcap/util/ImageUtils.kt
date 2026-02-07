package mysh.dev.gemcap.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

private const val TAG = "ImageUtils"

object ImageUtils {

    fun copyImageToClipboard(context: Context, bytes: ByteArray, mimeType: String) {
        try {
            // Create temp file
            val extension = mimeType.substringAfter("/").takeIf { it.isNotEmpty() } ?: "png"
            val tempFile = File(context.cacheDir, "clipboard_image.$extension")
            tempFile.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newUri(context.contentResolver, "Image", uri)
            clipboardManager.setPrimaryClip(clipData)

            Toast.makeText(context, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to clipboard", e)
            Toast.makeText(context, "Failed to copy image", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun downloadImage(context: Context, bytes: ByteArray, mimeType: String, filename: String) {
        try {
            val extension = mimeType.substringAfter("/").takeIf { it.isNotEmpty() } ?: "png"
            val sanitizedFilename = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val finalFilename =
                if (sanitizedFilename.contains(".")) sanitizedFilename else "$sanitizedFilename.$extension"

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, finalFilename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Toast.makeText(context, "Downloaded: $finalFilename", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to download image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image", e)
            Toast.makeText(context, "Failed to download: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

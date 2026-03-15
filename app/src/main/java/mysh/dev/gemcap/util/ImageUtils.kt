package mysh.dev.gemcap.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        val extension = mimeType.substringAfter("/").takeIf { it.isNotEmpty() } ?: "png"
        val sanitizedFilename = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalFilename =
            if (sanitizedFilename.contains(".")) sanitizedFilename else "$sanitizedFilename.$extension"

        DownloadUtils.saveToDownloads(context, bytes, finalFilename, mimeType)
            .onSuccess {
                Toast.makeText(context, "Downloaded: $finalFilename", Toast.LENGTH_SHORT).show()
            }
            .onFailure { e ->
                Log.e(TAG, "Failed to download image", e)
                Toast.makeText(context, "Failed to download: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

package mysh.dev.gemcap.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ScreenshotUtils"
private const val BOOKMARK_PREVIEWS_DIR = "bookmark_previews"

// TODO: both methods are quite similar, need to merge them
object ScreenshotUtils {

    /**
     * Captures a screenshot of the given view, crops the top UI area, and scales it down
     *
     * @param view The view to capture
     * @param density The density to use for dp to px conversion
     * @param topCropDp The height in dp to crop from the top (status bar + control bar)
     * @return The processed ImageBitmap, or null if capture fails
     */
    fun captureAndCropScreenshot(
        view: View,
        density: Density,
        topCropDp: Int = 110
    ): ImageBitmap? {
        return try {
            val bitmap = view.drawToBitmap()

            // TODO: calculate this dynamically somehow? Hardcode doesn't feel right
            val topCropHeight = with(density) { topCropDp.dp.toPx() }.toInt()

            val croppedBitmap = if (bitmap.height > topCropHeight) {
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    topCropHeight,
                    bitmap.width,
                    bitmap.height - topCropHeight
                )
            } else {
                bitmap
            }

            val scaledBitmap = croppedBitmap.scale(
                croppedBitmap.width / 4,
                croppedBitmap.height / 4
            )

            val result = scaledBitmap.asImageBitmap()

            if (bitmap != croppedBitmap) bitmap.recycle()
            if (croppedBitmap != scaledBitmap) croppedBitmap.recycle()

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            null
        }
    }

    /**
     * Saves a bookmark preview screenshot to internal storage
     * @return the file path if successful, null otherwise
     */
    fun saveBookmarkPreview(
        context: Context,
        view: View,
        density: Density,
        bookmarkId: String
    ): String? {
        return try {
            val bitmap = view.drawToBitmap()

            // TODO: calculate this dynamically somehow? Hardcode doesn't feel right
            val topCropHeight = with(density) { 110.dp.toPx() }.toInt()
            val croppedBitmap = if (bitmap.height > topCropHeight) {
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    topCropHeight,
                    bitmap.width,
                    bitmap.height - topCropHeight
                )
            } else {
                bitmap
            }

            val scaledBitmap = croppedBitmap.scale(
                croppedBitmap.width / 4,
                croppedBitmap.height / 4
            )

            val dir = File(context.filesDir, BOOKMARK_PREVIEWS_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "$bookmarkId.jpg")
            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            // Cleanup
            if (bitmap != croppedBitmap) bitmap.recycle()
            if (croppedBitmap != scaledBitmap) croppedBitmap.recycle()
            scaledBitmap.recycle()

            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bookmark preview", e)
            null
        }
    }

    /**
     * Loads a bookmark preview from storage
     */
    fun loadBookmarkPreview(path: String?): ImageBitmap? {
        if (path == null) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bookmark preview", e)
            null
        }
    }

    /**
     * Deletes a bookmark preview file
     */
    fun deleteBookmarkPreview(path: String?) {
        if (path == null) return
        try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete bookmark preview", e)
        }
    }
}

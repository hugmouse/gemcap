package mysh.dev.gemcap.data

import android.content.Context
import androidx.core.content.edit
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.domain.HistoryEntry
import mysh.dev.gemcap.util.ScreenshotUtils
import org.json.JSONArray
import org.json.JSONObject

class BrowserRepository(context: Context) {

    private val bookmarksPrefs =
        context.getSharedPreferences("browser_bookmarks", Context.MODE_PRIVATE)
    private val historyPrefs = context.getSharedPreferences("browser_history", Context.MODE_PRIVATE)

    // TODO: add ability to customize the history limit?
    companion object {
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_ENTRIES = 500
    }

    fun getBookmarks(): List<Bookmark> {
        val json = bookmarksPrefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Bookmark(
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    addedAt = obj.getLong("addedAt"),
                    previewPath = if (obj.has("previewPath")) obj.getString("previewPath") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBookmark(bookmark: Bookmark) {
        val bookmarks = getBookmarks().toMutableList()
        // Remove existing bookmark with same URL (to update/move to top)
        bookmarks.removeAll { it.url == bookmark.url }
        // Add new bookmark at the beginning
        bookmarks.add(0, bookmark)
        saveBookmarks(bookmarks)
    }

    fun removeBookmark(url: String) {
        val bookmarks = getBookmarks().toMutableList()
        bookmarks.find { it.url == url }?.previewPath?.let { path ->
            ScreenshotUtils.deleteBookmarkPreview(path)
        }
        bookmarks.removeAll { it.url == url }
        saveBookmarks(bookmarks)
    }

    fun isBookmarked(url: String): Boolean {
        return getBookmarks().any { it.url == url }
    }

    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val array = JSONArray()
        bookmarks.forEach { bookmark ->
            val obj = JSONObject().apply {
                put("url", bookmark.url)
                put("title", bookmark.title)
                put("addedAt", bookmark.addedAt)
                bookmark.previewPath?.let { put("previewPath", it) }
            }
            array.put(obj)
        }
        bookmarksPrefs.edit { putString(KEY_BOOKMARKS, array.toString()) }
    }


    fun getHistory(): List<HistoryEntry> {
        val json = historyPrefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HistoryEntry(
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    visitedAt = obj.getLong("visitedAt")
                )
            }
        } catch (e: Exception) {
            // TODO: maybe not ignore the exception here
            emptyList()
        }
    }

    fun addHistoryEntry(entry: HistoryEntry) {
        val history = getHistory().toMutableList()
        history.add(0, entry)
        val cappedHistory = history.take(MAX_HISTORY_ENTRIES)
        saveHistory(cappedHistory)
    }

    fun clearHistory() {
        historyPrefs.edit { remove(KEY_HISTORY) }
    }

    private fun saveHistory(history: List<HistoryEntry>) {
        val array = JSONArray()
        history.forEach { entry ->
            val obj = JSONObject().apply {
                put("url", entry.url)
                put("title", entry.title)
                put("visitedAt", entry.visitedAt)
            }
            array.put(obj)
        }
        historyPrefs.edit { putString(KEY_HISTORY, array.toString()) }
    }
}

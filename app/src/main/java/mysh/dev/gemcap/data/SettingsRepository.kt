package mysh.dev.gemcap.data

import android.content.Context
import androidx.core.content.edit
import mysh.dev.gemcap.domain.GeminiConstants.HOME_URL
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class FontSize(val scaleFactor: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}

enum class SearchEngine(val displayName: String, private val template: String) {
    GEMCAP("Gemcap", "gemini://gemini-search.mysh.dev/?%s"),
    KENNEDY("Kennedy", "gemini://kennedy.gemi.dev/search?%s"),
    TLGS("TLGS", "gemini://tlgs.one/search?%s");
    fun buildSearchUrl(query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return template.format(encodedQuery)
    }
}

data class TabSession(
    val tabs: List<TabSessionState>,
    val activeTabIndex: Int
)
data class TabSessionState(
    val entries: List<TabHistoryEntrySession>,
    val currentIndex: Int
)
data class TabHistoryEntrySession(
    val url: String,
    val scrollIndex: Int,
    val scrollOffset: Int
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOME_PAGE = "home_page"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_DEVELOPER_MODE = "developer_mode"
        private const val KEY_TAB_SESSION = "tab_session"
        internal fun parseTabSession(rawSession: String): TabSession? {
            return try {
                val json = JSONObject(rawSession)
                val tabsArray = json.optJSONArray("tabs") ?: return null
                val tabs = buildList {
                    for (index in 0 until tabsArray.length()) {
                        val tabJson = tabsArray.optJSONObject(index) ?: continue
                        val entriesArray = tabJson.optJSONArray("entries") ?: continue
                        val entries = buildList {
                            for (entryIndex in 0 until entriesArray.length()) {
                                val entryJson = entriesArray.optJSONObject(entryIndex) ?: continue
                                val url = entryJson.optString("url", "").trim()
                                if (url.isBlank()) {
                                    continue
                                }
                                add(
                                    TabHistoryEntrySession(
                                        url = url,
                                        scrollIndex = entryJson.optInt("scrollIndex", 0)
                                            .coerceAtLeast(0),
                                        scrollOffset = entryJson.optInt("scrollOffset", 0)
                                            .coerceAtLeast(0)
                                    )
                                )
                            }
                        }
                        if (entries.isNotEmpty()) {
                            val currentIndex = tabJson.optInt("currentIndex", entries.lastIndex)
                                .coerceIn(0, entries.lastIndex)
                            add(
                                TabSessionState(
                                    entries = entries,
                                    currentIndex = currentIndex
                                )
                            )
                        }
                    }
                }
                if (tabs.isEmpty()) {
                    return null
                }
                val activeTabIndex = json.optInt("activeTabIndex", 0).coerceIn(0, tabs.lastIndex)
                TabSession(tabs = tabs, activeTabIndex = activeTabIndex)
            } catch (_: Exception) {
                null
            }
        }
        internal fun serializeTabSession(value: TabSession?): String? {
            if (value == null || value.tabs.isEmpty()) {
                return null
            }
            val tabsArray = JSONArray()
            value.tabs.forEach { tab ->
                if (tab.entries.isEmpty()) {
                    return@forEach
                }
                val entriesArray = JSONArray()
                tab.entries.forEach { entry ->
                    entriesArray.put(
                        JSONObject().apply {
                            put("url", entry.url)
                            put("scrollIndex", entry.scrollIndex.coerceAtLeast(0))
                            put("scrollOffset", entry.scrollOffset.coerceAtLeast(0))
                        }
                    )
                }
                if (entriesArray.length() == 0) {
                    return@forEach
                }
                tabsArray.put(
                    JSONObject().apply {
                        put("entries", entriesArray)
                        put("currentIndex", tab.currentIndex.coerceIn(0, tab.entries.lastIndex))
                    }
                )
            }
            if (tabsArray.length() == 0) {
                return null
            }
            return JSONObject().apply {
                put("tabs", tabsArray)
                put("activeTabIndex", value.activeTabIndex.coerceIn(0, tabsArray.length() - 1))
            }.toString()
        }
    }

    var homePage: String
        get() = prefs.getString(KEY_HOME_PAGE, HOME_URL) ?: HOME_URL
        set(value) {
            prefs.edit { putString(KEY_HOME_PAGE, value) }
        }

    var themeMode: ThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, null)
            return name?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }
        set(value) {
            prefs.edit { putString(KEY_THEME_MODE, value.name) }
        }

    var fontSize: FontSize
        get() {
            val name = prefs.getString(KEY_FONT_SIZE, null)
            return name?.let { runCatching { FontSize.valueOf(it) }.getOrNull() } ?: FontSize.MEDIUM
        }
        set(value) {
            prefs.edit { putString(KEY_FONT_SIZE, value.name) }
        }
    var searchEngine: SearchEngine
        get() {
            val name = prefs.getString(KEY_SEARCH_ENGINE, null)
            return name?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() } ?: SearchEngine.GEMCAP
        }
        set(value) {
            prefs.edit { putString(KEY_SEARCH_ENGINE, value.name) }
        }

    var developerMode: Boolean
        get() = prefs.getBoolean(KEY_DEVELOPER_MODE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_DEVELOPER_MODE, value) }
        }
    var tabSession: TabSession?
        get() {
            val rawSession = prefs.getString(KEY_TAB_SESSION, null) ?: return null
            return parseTabSession(rawSession)
        }
        set(value) {
            prefs.edit {
                val encoded = serializeTabSession(value)
                if (encoded == null) {
                    remove(KEY_TAB_SESSION)
                    return@edit
                }
                putString(KEY_TAB_SESSION, encoded)
            }
        }
}

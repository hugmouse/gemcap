package mysh.dev.gemcap.data

import android.content.Context
import androidx.core.content.edit
import mysh.dev.gemcap.ui.model.HOME_URL
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class FontSize(val scaleFactor: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}
data class TabSession(
    val tabUrls: List<String>,
    val activeTabIndex: Int
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOME_PAGE = "home_page"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_TAB_SESSION = "tab_session"
    }

    var homePage: String
        get() = prefs.getString(KEY_HOME_PAGE, HOME_URL) ?: HOME_URL
        set(value) {
            prefs.edit { putString(KEY_HOME_PAGE, value) }
        }

    var themeMode: ThemeMode
        get() {
            val ordinal = prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal)
            return ThemeMode.entries.getOrElse(ordinal) { ThemeMode.SYSTEM }
        }
        set(value) {
            prefs.edit { putInt(KEY_THEME_MODE, value.ordinal) }
        }

    var fontSize: FontSize
        get() {
            val ordinal = prefs.getInt(KEY_FONT_SIZE, FontSize.MEDIUM.ordinal)
            return FontSize.entries.getOrElse(ordinal) { FontSize.MEDIUM }
        }
        set(value) {
            prefs.edit { putInt(KEY_FONT_SIZE, value.ordinal) }
        }
    var tabSession: TabSession?
        get() {
            val rawSession = prefs.getString(KEY_TAB_SESSION, null) ?: return null
            return try {
                val json = JSONObject(rawSession)
                val tabsArray = json.optJSONArray("tabs") ?: return null
                val tabs = buildList {
                    for (index in 0 until tabsArray.length()) {
                        val value = tabsArray.optString(index)
                        if (value.isNotBlank()) {
                            add(value)
                        }
                    }
                }
                if (tabs.isEmpty()) {
                    return null
                }
                val activeTabIndex = json.optInt("activeTabIndex", 0).coerceIn(0, tabs.lastIndex)
                TabSession(tabUrls = tabs, activeTabIndex = activeTabIndex)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            prefs.edit {
                if (value == null || value.tabUrls.isEmpty()) {
                    remove(KEY_TAB_SESSION)
                    return@edit
                }
                val tabsArray = JSONArray()
                value.tabUrls.forEach { tabsArray.put(it) }
                val json = JSONObject().apply {
                    put("tabs", tabsArray)
                    put("activeTabIndex", value.activeTabIndex.coerceIn(0, value.tabUrls.lastIndex))
                }
                putString(KEY_TAB_SESSION, json.toString())
            }
        }
}

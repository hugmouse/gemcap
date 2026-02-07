package mysh.dev.gemcap.data

import android.content.Context
import androidx.core.content.edit
import mysh.dev.gemcap.ui.model.HOME_URL

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class FontSize(val scaleFactor: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOME_PAGE = "home_page"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SIZE = "font_size"
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
}

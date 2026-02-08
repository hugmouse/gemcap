package mysh.dev.gemcap

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import mysh.dev.gemcap.data.SettingsRepository
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.ui.BrowserScreen
import mysh.dev.gemcap.ui.theme.GeminiBrowserTheme

// TODO: reimplement everything using MVVM, because right now codebase stinks.
// And this will also allow this codebase be a little bit more portable.
// TODO: check how other developers implement custom themes for their apps.
// TODO: check how can I move some of the components to use Material3 UI.
// TODO: check how to make icon work both on dark and light android themes.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(this)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )

        setContent {
            var themeMode by remember { mutableStateOf(settingsRepository.themeMode) }
            var fontScale by remember { mutableFloatStateOf(settingsRepository.fontSize.scaleFactor) }

            val systemDarkTheme = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            GeminiBrowserTheme(
                darkTheme = isDarkTheme,
                fontScale = fontScale
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val activity = view.context as? Activity ?: return@SideEffect
                        WindowCompat.getInsetsController(activity.window, view).apply {
                            isAppearanceLightStatusBars = !isDarkTheme
                            isAppearanceLightNavigationBars = !isDarkTheme
                        }
                    }
                }


                // TODO: Android Studio 2025.2.3 thinks that those callbacks are not used,
                // which is not true.
                BrowserScreen(
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                    },
                    onFontSizeChanged = { size ->
                        fontScale = size.scaleFactor
                    }
                )
            }
        }
    }
}

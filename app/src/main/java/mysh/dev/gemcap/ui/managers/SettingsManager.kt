package mysh.dev.gemcap.ui.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mysh.dev.gemcap.data.FontSize
import mysh.dev.gemcap.data.SettingsRepository
import mysh.dev.gemcap.data.ThemeMode
import mysh.dev.gemcap.ui.model.PanelState
import mysh.dev.gemcap.ui.model.SettingsState

class SettingsManager(
    private val settingsRepository: SettingsRepository,
    private val getPanelState: () -> PanelState,
    private val updatePanelState: (PanelState) -> Unit
) {
    var settingsState by mutableStateOf(
        SettingsState(
            themeMode = settingsRepository.themeMode,
            fontSize = settingsRepository.fontSize,
            homePage = settingsRepository.homePage
        )
    )
        private set

    fun getHomePage(): String = settingsRepository.homePage

    fun showScreen() {
        updatePanelState(getPanelState().copy(showSettings = true, showMenu = false))
    }

    fun dismissScreen() {
        updatePanelState(getPanelState().copy(showSettings = false))
    }

    fun updateThemeMode(mode: ThemeMode) {
        settingsState = settingsState.copy(themeMode = mode)
        settingsRepository.themeMode = mode
    }

    fun updateFontSize(size: FontSize) {
        settingsState = settingsState.copy(fontSize = size)
        settingsRepository.fontSize = size
    }

    fun setHomePage(url: String) {
        settingsState = settingsState.copy(homePage = url)
        settingsRepository.homePage = url
    }

    fun setCurrentPageAsHome(url: String) {
        settingsRepository.homePage = url
        settingsState = settingsState.copy(homePage = url)
    }
}

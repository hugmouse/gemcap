package mysh.dev.gemcap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import mysh.dev.gemcap.domain.CapsuleIdentity

// "Chrome" refers to the browser's own UI frame (tabs, toolbar, address bar) as opposed
// to the page content area. These are the capsule-derived colors for rendering tab UI.
// And also because I've copied style of tabs from Google Chrome.
data class TabChrome(
    val capsuleStyle: CapsuleStyle?,
    val titleColor: Color
)

@Composable
fun rememberTabChrome(capsuleIdentity: CapsuleIdentity?): TabChrome {
    val isDarkMode = isDarkMode()
    val capsuleStyle = remember(capsuleIdentity, isDarkMode) {
        CapsuleStyleGenerator.fromIdentity(capsuleIdentity, isDarkMode)
    }
    val titleColor = capsuleStyle?.chromeTextColor ?: MaterialTheme.colorScheme.onSurface
    return TabChrome(capsuleStyle = capsuleStyle, titleColor = titleColor)
}

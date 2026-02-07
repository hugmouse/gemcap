package mysh.dev.gemcap.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// TODO: it was used to fix recomposition upon scrolling, is it still an issue?
@Stable
data class CachedTextStyles(
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelSmall: TextStyle,
    val linkStyle: TextStyle,
    val quoteStyle: TextStyle,
    val monoStyle: TextStyle,
    val primaryColor: Color,
    val tertiaryColor: Color,
    val surfaceVariantColor: Color,
    val onSurfaceVariantColor: Color
)

@Composable
fun rememberCachedTextStyles(): CachedTextStyles {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme

    return remember(typography, colorScheme) {
        CachedTextStyles(
            headlineLarge = typography.headlineLarge,
            headlineMedium = typography.headlineMedium,
            headlineSmall = typography.headlineSmall,
            bodyLarge = typography.bodyLarge,
            bodyMedium = typography.bodyMedium,
            bodySmall = typography.bodySmall,
            labelSmall = typography.labelSmall,
            linkStyle = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            quoteStyle = typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            monoStyle = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            primaryColor = colorScheme.primary,
            tertiaryColor = colorScheme.tertiary,
            surfaceVariantColor = colorScheme.surfaceVariant,
            onSurfaceVariantColor = colorScheme.onSurfaceVariant
        )
    }
}

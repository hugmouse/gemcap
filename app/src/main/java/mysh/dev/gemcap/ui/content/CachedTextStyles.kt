package mysh.dev.gemcap.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.ui.theme.CapsuleStyle

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
    val highlightColor: Color,
    val bodyColor: Color,
    val heading1Color: Color,
    val heading2Color: Color,
    val heading3Color: Color,
    val linkTextColor: Color,
    val linkIconColor: Color,
    val quoteTextColor: Color,
    val quoteIndicatorColor: Color,
    val bulletColor: Color,
    val preformattedTextColor: Color,
    val preformattedAltColor: Color,
    val preformattedBackgroundColor: Color,
    val chromeAccentColor: Color,
    val chromeTextColor: Color,
    val linkIconIndent: Dp
)

@Composable
fun rememberCachedTextStyles(capsuleStyle: CapsuleStyle?): CachedTextStyles {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(typography, colorScheme, capsuleStyle, density) {
        val iconWidth = textMeasurer.measure("\u27A4", typography.bodyLarge).size.width
        val indent = with(density) { iconWidth.toDp() } + 6.dp
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
            highlightColor = colorScheme.primaryContainer,
            bodyColor = capsuleStyle?.bodyColor ?: colorScheme.onSurface,
            heading1Color = capsuleStyle?.heading1Color ?: colorScheme.onSurface,
            heading2Color = capsuleStyle?.heading2Color ?: colorScheme.onSurface,
            heading3Color = capsuleStyle?.heading3Color ?: colorScheme.onSurface,
            linkTextColor = capsuleStyle?.linkTextColor ?: colorScheme.primary,
            linkIconColor = capsuleStyle?.linkIconColor ?: colorScheme.primary,
            quoteTextColor = capsuleStyle?.quoteTextColor ?: colorScheme.onSurfaceVariant,
            quoteIndicatorColor = capsuleStyle?.quoteIndicatorColor ?: colorScheme.tertiary,
            bulletColor = capsuleStyle?.bulletColor ?: colorScheme.onSurfaceVariant,
            preformattedTextColor = capsuleStyle?.preformattedTextColor ?: colorScheme.onSurfaceVariant,
            preformattedAltColor = capsuleStyle?.preformattedAltColor ?: colorScheme.onSurfaceVariant,
            preformattedBackgroundColor = capsuleStyle?.preformattedBackgroundColor
                ?: colorScheme.surfaceVariant,
            chromeAccentColor = capsuleStyle?.chromeAccentColor ?: colorScheme.primary,
            chromeTextColor = capsuleStyle?.chromeTextColor ?: colorScheme.onSurface,
            linkIconIndent = indent
        )
    }
}

package mysh.dev.gemcap.ui.content

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun HeadingContent(
    item: GeminiContent.Heading,
    styles: CachedTextStyles,
    searchQuery: String
) {
    val style = when (item.level) {
        1 -> styles.headlineLarge
        2 -> styles.headlineMedium
        else -> styles.headlineSmall
    }
    val color = when (item.level) {
        1 -> styles.heading1Color
        2 -> styles.heading2Color
        else -> styles.heading3Color
    }
    Text(
        text = highlight(item.text, searchQuery, styles.highlightColor),
        style = style,
        color = color
    )
}

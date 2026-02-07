package mysh.dev.gemcap.ui.content

import androidx.compose.material3.MaterialTheme
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
    // TODO: check if lagrange and similar clients support more than 3 headline styles
    val style = when (item.level) {
        1 -> styles.headlineLarge
        2 -> styles.headlineMedium
        else -> styles.headlineSmall
    }
    Text(
        text = highlight(item.text, searchQuery, MaterialTheme.colorScheme.primaryContainer),
        style = style
    )
}

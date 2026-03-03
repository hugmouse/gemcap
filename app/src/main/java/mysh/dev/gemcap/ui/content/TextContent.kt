package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun TextContent(
    item: GeminiContent.Text,
    styles: CachedTextStyles,
    searchQuery: String
) {
    Text(
        text = highlight(item.text, searchQuery, styles.highlightColor),
        color = styles.bodyColor,
        style = styles.bodyLarge,
        modifier = Modifier.padding(start = styles.linkIconIndent)
    )
}

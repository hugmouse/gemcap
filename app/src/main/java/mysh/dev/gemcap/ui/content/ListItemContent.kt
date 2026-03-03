package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun ListItemContent(
    item: GeminiContent.ListItem,
    styles: CachedTextStyles,
    searchQuery: String
) {
    Row {
        Text(
            text = "\u2022 ", // "• "
            style = styles.bodyMedium,
            color = styles.bulletColor
        )
        Text(
            text = highlight(item.text, searchQuery, styles.highlightColor),
            style = styles.bodyMedium,
            color = styles.bodyColor
        )
    }
}

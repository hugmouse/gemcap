package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
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
        // TODO: replace dot lol?
        Text(text = "• ", style = styles.bodyMedium)
        Text(
            text = highlight(item.text, searchQuery, MaterialTheme.colorScheme.primaryContainer),
            style = styles.bodyMedium
        )
    }
}

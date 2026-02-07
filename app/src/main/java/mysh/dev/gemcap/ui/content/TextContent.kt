package mysh.dev.gemcap.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun TextContent(item: GeminiContent.Text, searchQuery: String) {
    Text(
        text = highlight(item.text, searchQuery, MaterialTheme.colorScheme.primaryContainer)
    )
}

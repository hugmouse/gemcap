package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun QuoteContent(
    item: GeminiContent.Quote,
    styles: CachedTextStyles,
    searchQuery: String
) {
    val borderColor = styles.tertiaryColor
    Text(
        modifier = Modifier
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
            .padding(start = 8.dp),
        text = highlight(item.text, searchQuery, MaterialTheme.colorScheme.primaryContainer),
        style = styles.quoteStyle
    )
}

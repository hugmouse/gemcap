package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun PreformattedContent(
    item: GeminiContent.Preformatted,
    styles: CachedTextStyles,
    searchQuery: String,
    highlight: Boolean
) {
    val highlightColor =
        if (highlight) styles.highlightColor else Color.Transparent
    Card(
        colors = CardDefaults.cardColors(containerColor = styles.preformattedBackgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (item.alt.isNotEmpty()) {
                Text(
                    text = item.alt,
                    style = styles.monoStyle,
                    color = styles.preformattedAltColor
                )
            }
            Text(
                text = highlight(
                    item.text,
                    searchQuery,
                    highlightColor,
                    baseStyle = SpanStyle(
                        fontFamily = styles.monoStyle.fontFamily ?: FontFamily.Monospace
                    )
                ),
                style = styles.monoStyle,
                color = styles.preformattedTextColor
            )
        }
    }
}

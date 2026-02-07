package mysh.dev.gemcap.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun highlight(
    text: String,
    query: String,
    highlightColor: Color,
    baseStyle: SpanStyle? = null
): AnnotatedString {
    return buildAnnotatedString {
        if (baseStyle != null) {
            pushStyle(baseStyle)
        }

        if (query.isBlank()) {
            append(text)
        } else {
            var lastIndex = 0
            while (true) {
                val startIndex = text.indexOf(query, lastIndex, ignoreCase = true)
                if (startIndex == -1) {
                    append(text.substring(lastIndex))
                    break
                }
                append(text.substring(lastIndex, startIndex))
                withStyle(style = SpanStyle(background = highlightColor)) {
                    append(text.substring(startIndex, startIndex + query.length))
                }
                lastIndex = startIndex + query.length
            }
        }

        if (baseStyle != null) {
            pop()
        }
    }
}

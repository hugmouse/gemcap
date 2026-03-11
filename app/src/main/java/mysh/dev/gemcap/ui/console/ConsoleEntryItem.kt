package mysh.dev.gemcap.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mysh.dev.gemcap.domain.ConsoleEntry
import mysh.dev.gemcap.domain.ConsoleLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

@Composable
fun ConsoleEntryItem(
    entry: ConsoleEntry,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetail = entry.detail != null

    val titleColor = when (entry.level) {
        ConsoleLevel.ERROR -> Color(0xFFFF5252)
        ConsoleLevel.WARNING -> Color(0xFFFFB74D)
        ConsoleLevel.INFO -> Color(0xFFCCCCCC)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = buildString {
                append(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(timeFormat))
                append("  ")
                append(entry.title)
            },
            color = titleColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )

        AnimatedVisibility(visible = expanded && hasDetail) {
            Text(
                text = entry.detail.orEmpty(),
                color = Color(0xFF999999),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 64.dp, top = 2.dp)
            )
        }
    }

    HorizontalDivider(color = Color(0xFF2A2A3A), thickness = 0.5.dp)
}

package mysh.dev.gemcap.ui.console

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class LogLevel(val label: String, val color: Color) {
    VERBOSE("V", ConsoleColors.muted),
    DEBUG("D", ConsoleColors.accent),
    INFO("I", ConsoleColors.info),
    WARN("W", ConsoleColors.warning),
    ERROR("E", ConsoleColors.error)
}

@Composable
fun LogcatFilterBar(
    enabledLevels: Set<LogLevel>,
    onToggleLevel: (LogLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LogLevel.entries.forEach { level ->
            FilterChip(
                selected = level in enabledLevels,
                onClick = { onToggleLevel(level) },
                label = { Text(level.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ConsoleColors.surface,
                    selectedContainerColor = level.color.copy(alpha = 0.2f),
                    labelColor = ConsoleColors.muted,
                    selectedLabelColor = level.color
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = ConsoleColors.divider,
                    selectedBorderColor = level.color.copy(alpha = 0.5f),
                    enabled = true,
                    selected = level in enabledLevels
                )
            )
        }
    }
}

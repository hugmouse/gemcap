package mysh.dev.gemcap.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.domain.ConsoleCategory
import mysh.dev.gemcap.domain.ConsoleEntry

@Composable
fun ConsolePanel(
    entries: ImmutableList<ConsoleEntry>,
    errorCount: Int,
    developerMode: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onLogcatTabSelected: () -> Unit,
    onLogcatTabDeselected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var panelHeight by remember { mutableStateOf(250.dp) }

    var selectedTab by remember { mutableStateOf(ConsoleTab.ALL) }
    var logcatLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }

    val filteredEntries = remember(entries, selectedTab, logcatLevels) {
        when (selectedTab) {
            ConsoleTab.ALL -> entries.filter { it.category != ConsoleCategory.LOGCAT }
            ConsoleTab.NETWORK -> entries.filter { it.category == ConsoleCategory.NETWORK }
            ConsoleTab.ERRORS -> entries.filter { it.category == ConsoleCategory.ERROR }
            ConsoleTab.SECURITY -> entries.filter { it.category == ConsoleCategory.SECURITY }
            ConsoleTab.LOGCAT -> entries.filter { entry ->
                entry.category == ConsoleCategory.LOGCAT && logcatLevels.any { level ->
                    entry.title.startsWith("[${level.label}/")
                }
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(filteredEntries.size) {
        if (filteredEntries.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0 || lastVisible >= totalItems - 3) {
                listState.animateScrollToItem(filteredEntries.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(Color(0xFF1E1E2E))
    ) {
        // Drag handle to resize
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF333333))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        with(density) {
                            panelHeight = (panelHeight - delta.toDp()).coerceIn(100.dp, 500.dp)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .background(Color(0xFF666666), RoundedCornerShape(1.5.dp))
            )
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Console",
                color = Color(0xFFE0E0E0),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear console",
                        tint = Color(0xFF888888)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close console",
                        tint = Color(0xFF888888)
                    )
                }
            }
        }

        // Tab bar
        ConsoleTabBar(
            selectedTab = selectedTab,
            errorCount = errorCount,
            developerMode = developerMode,
            onTabSelected = { tab ->
                val wasLogcat = selectedTab == ConsoleTab.LOGCAT
                val isLogcat = tab == ConsoleTab.LOGCAT
                selectedTab = tab
                if (isLogcat && !wasLogcat) onLogcatTabSelected()
                if (!isLogcat && wasLogcat) onLogcatTabDeselected()
            }
        )

        // Logcat level filter
        if (selectedTab == ConsoleTab.LOGCAT) {
            LogcatFilterBar(
                enabledLevels = logcatLevels,
                onToggleLevel = { level ->
                    logcatLevels = if (level in logcatLevels) {
                        logcatLevels - level
                    } else {
                        logcatLevels + level
                    }
                }
            )
        }

        // Entry list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                items = filteredEntries,
                key = { it.id }
            ) { entry ->
                ConsoleEntryItem(entry = entry)
            }

            if (filteredEntries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No entries",
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

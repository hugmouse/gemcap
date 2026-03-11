package mysh.dev.gemcap.ui.console

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ConsoleTab(val label: String) {
    ALL("All"),
    NETWORK("Network"),
    ERRORS("Errors"),
    SECURITY("Security"),
    LOGCAT("Logcat")
}

@Composable
fun ConsoleTabBar(
    selectedTab: ConsoleTab,
    errorCount: Int,
    developerMode: Boolean,
    onTabSelected: (ConsoleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = if (developerMode) ConsoleTab.entries else ConsoleTab.entries.filter { it != ConsoleTab.LOGCAT }

    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        modifier = modifier,
        containerColor = Color(0xFF1E1E2E),
        contentColor = Color(0xFF82AAFF),
        edgePadding = 8.dp,
        divider = { HorizontalDivider(color = Color(0xFF333333)) }
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    if (tab == ConsoleTab.ERRORS && errorCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = Color(0xFFFF5252),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = errorCount.toString(),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        ) {
                            Text(tab.label)
                        }
                    } else {
                        Text(tab.label)
                    }
                },
                unselectedContentColor = Color(0xFF888888)
            )
        }
    }
}

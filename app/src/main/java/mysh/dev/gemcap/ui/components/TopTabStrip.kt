package mysh.dev.gemcap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.ui.model.TabState

@Composable
fun TopTabStrip(
    tabs: ImmutableList<TabState>,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(start = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        tabs.forEach { tab ->
            TabItem(
                tab = tab,
                isActive = tab.id == activeTabId,
                width = 250.dp, // Simplified width
                onSelected = { onTabSelected(tab.id) },
                onClosed = { onTabClosed(tab.id) }
            )
        }

        IconButton(
            onClick = onNewTab,
            modifier = Modifier.offset(x = (-16).dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Tab")
        }
    }
}

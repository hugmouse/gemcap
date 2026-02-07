package mysh.dev.gemcap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.ui.model.TabState

@Composable
fun TabItem(
    tab: TabState,
    isActive: Boolean,
    width: Dp,
    onSelected: () -> Unit,
    onClosed: () -> Unit
) {
    val shape = SimpleChromeTabShape()

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
    ) {
        Surface(
            color = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
            shape = shape,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onSelected)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = tab.title.ifEmpty { "New Tab" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    IconButton(
                        onClick = onClosed,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Tab",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

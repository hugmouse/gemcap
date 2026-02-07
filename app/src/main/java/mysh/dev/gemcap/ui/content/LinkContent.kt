package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.highlight

@Composable
fun LinkContent(
    item: GeminiContent.Link,
    styles: CachedTextStyles,
    searchQuery: String,
    onLinkClick: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val gestureModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onLinkClick(item.url) },
            onLongPress = { showMenu = true }
        )
    }

    Text(
        text = highlight(
            "=> ${item.text}",
            searchQuery,
            MaterialTheme.colorScheme.primaryContainer
        ),
        color = styles.primaryColor,
        style = styles.linkStyle,
        modifier = gestureModifier
    )

    DisableSelection {
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open in new tab") },
                onClick = {
                    onOpenInNewTab(item.url)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Copy link address") },
                onClick = {
                    onCopyLink(item.url)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }
            )
        }
    }
}

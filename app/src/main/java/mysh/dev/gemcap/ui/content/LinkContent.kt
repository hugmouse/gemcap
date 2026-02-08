package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
    val isHttpLink = remember(item.url) {
        item.url.startsWith("http://", ignoreCase = true) || item.url.startsWith("https://", ignoreCase = true)
    }
    val linkIcon = if (isHttpLink) Icons.Default.Language else Icons.Default.KeyboardDoubleArrowRight
    val linkIconDescription = if (isHttpLink) "HTTP/HTTPS link" else "Gemini link"

    val gestureModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onLinkClick(item.url) },
            onLongPress = { showMenu = true }
        )
    }

    // TODO: padding for Icons.Default.Language is a little bit too tight, it needs to be bigger
    Row(
        modifier = gestureModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = linkIcon,
            contentDescription = linkIconDescription,
            tint = styles.primaryColor
        )
        Text(
            text = highlight(
                item.text,
                searchQuery,
                MaterialTheme.colorScheme.primaryContainer
            ),
            color = styles.primaryColor,
            style = styles.linkStyle
        )
    }

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

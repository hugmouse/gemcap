package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import mysh.dev.gemcap.domain.LinkIconResolver
import mysh.dev.gemcap.util.highlight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.res.stringResource
import mysh.dev.gemcap.R

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
    val iconGlyph = remember(item) { LinkIconResolver.iconFor(item) }
    val linkIconDescription = remember(item) { LinkIconResolver.descriptionFor(item) }
    val targetUrl = remember(item.url, item.resolvedUrl) { item.resolvedUrl ?: item.url }

    val gestureModifier = Modifier.pointerInput(targetUrl) {
        detectTapGestures(
            onTap = { onLinkClick(targetUrl) },
            onLongPress = { showMenu = true }
        )
    }

    Row(
        modifier = gestureModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = iconGlyph,
            style = styles.bodyLarge,
            color = styles.linkIconColor
        )
        Text(
            text = highlight(
                item.text,
                searchQuery,
                styles.highlightColor
            ),
            color = styles.linkTextColor,
            style = styles.linkStyle
        )
    }

    DisableSelection {
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.link_menu_open_new_tab)) },
                onClick = {
                    onOpenInNewTab(targetUrl)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = linkIconDescription
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.link_menu_copy_address)) },
                onClick = {
                    onCopyLink(targetUrl)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }
            )
        }
    }
}

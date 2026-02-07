package mysh.dev.gemcap.ui.components.controlBarComponents

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ToolbarMenu(
    showMenu: Boolean,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    isCompactMode: Boolean,
    isBookmarked: Boolean,
    hasActiveIdentity: Boolean,
    onToggleSearch: () -> Unit,
    onIdentityClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSetAsHomePage: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onMenuDismiss
        ) {
            if (isCompactMode) {
                DropdownMenuItem(
                    text = { Text("Find in Page") },
                    onClick = {
                        onToggleSearch()
                        onMenuDismiss()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Identity") },
                    onClick = {
                        onIdentityClick()
                        onMenuDismiss()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = if (hasActiveIdentity)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(if (isBookmarked) "Remove Bookmark" else "Add Bookmark") },
                onClick = {
                    onToggleBookmark()
                    onMenuDismiss()
                },
                leadingIcon = {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null
                    )
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Bookmarks") },
                onClick = {
                    onBookmarksClick()
                    onMenuDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Bookmarks, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("History") },
                onClick = {
                    onHistoryClick()
                    onMenuDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Default.History, contentDescription = null)
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Set as Home Page") },
                onClick = {
                    onSetAsHomePage()
                    onMenuDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Default.Home, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    onSettingsClick()
                    onMenuDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }
            )
        }
    }
}

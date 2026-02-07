package mysh.dev.gemcap.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.IdentityUsage
import mysh.dev.gemcap.domain.UsageType

/**
 * Lagrange-style "Use on" dialog for setting where an identity is used.
 * Options:
 * - Not Used
 * - Current Domain (e.g., geminiprotocol.net)
 * - Current Directory (e.g., /docs/)
 * - Current Page (e.g., /docs/faq.gmi)
 */
@Composable
fun IdentityUsageDialog(
    currentHost: String,
    currentPath: String,
    currentUsage: IdentityUsage?,  // null means not used for this host
    onSelectUsage: (IdentityUsage?) -> Unit,  // null to remove usage
    onDismiss: () -> Unit
) {
    // Determine current directory from path
    val currentDirectory = currentPath.substringBeforeLast("/", "/") + "/"

    // Determine selected option
    var selectedOption by remember {
        mutableStateOf(
            when {
                currentUsage == null -> UsageOption.NOT_USED
                currentUsage.type == UsageType.DOMAIN -> UsageOption.DOMAIN
                currentUsage.type == UsageType.DIRECTORY -> UsageOption.DIRECTORY
                currentUsage.type == UsageType.PAGE -> UsageOption.PAGE
                else -> UsageOption.NOT_USED
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Use on")
        },
        text = {
            Column {
                Text(
                    text = "Select where to use this identity:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Not Used
                UsageOptionRow(
                    icon = Icons.Default.Close,
                    title = "Not Used",
                    subtitle = "Don't use for this site",
                    selected = selectedOption == UsageOption.NOT_USED,
                    onClick = { selectedOption = UsageOption.NOT_USED }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Current Domain
                UsageOptionRow(
                    icon = Icons.Default.Language,
                    title = "Current Domain",
                    subtitle = currentHost,
                    selected = selectedOption == UsageOption.DOMAIN,
                    onClick = { selectedOption = UsageOption.DOMAIN }
                )

                // Current Directory
                UsageOptionRow(
                    icon = Icons.Default.Folder,
                    title = "Current Directory",
                    subtitle = "$currentHost$currentDirectory",
                    selected = selectedOption == UsageOption.DIRECTORY,
                    onClick = { selectedOption = UsageOption.DIRECTORY }
                )

                // Current Page
                UsageOptionRow(
                    icon = Icons.Default.Description,
                    title = "Current Page",
                    subtitle = "$currentHost$currentPath",
                    selected = selectedOption == UsageOption.PAGE,
                    onClick = { selectedOption = UsageOption.PAGE }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newUsage = when (selectedOption) {
                        UsageOption.NOT_USED -> null
                        UsageOption.DOMAIN -> IdentityUsage(currentHost, UsageType.DOMAIN, "/")
                        UsageOption.DIRECTORY -> IdentityUsage(
                            currentHost,
                            UsageType.DIRECTORY,
                            currentDirectory
                        )

                        UsageOption.PAGE -> IdentityUsage(currentHost, UsageType.PAGE, currentPath)
                    }
                    onSelectUsage(newUsage)
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private enum class UsageOption {
    NOT_USED,
    DOMAIN,
    DIRECTORY,
    PAGE
}

@Composable
private fun UsageOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

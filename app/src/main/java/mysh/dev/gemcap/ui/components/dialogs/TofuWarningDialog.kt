package mysh.dev.gemcap.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.TofuWarningState

@Composable
fun TofuWarningDialog(
    state: TofuWarningState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onViewDetails: (() -> Unit)? = null
) {
    val hostDisplay = if (state.port != 1965) "${state.host}:${state.port}" else state.host

    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                text = "Certificate Changed",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = "The certificate for $hostDisplay has changed before the previous certificate expired. This could indicate a security issue.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Old public key fingerprint:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.oldFingerprint.take(32) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "New public key fingerprint:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.newFingerprint.take(32) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            Row {
                if (onViewDetails != null) {
                    TextButton(onClick = onViewDetails) {
                        Text("View Details")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onAccept) {
                    Text("Accept New Certificate")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Cancel")
            }
        }
    )
}

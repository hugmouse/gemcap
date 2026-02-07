package mysh.dev.gemcap.ui.components.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.CertificateDetailsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CertificateDetailsDialog(
    state: CertificateDetailsState,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (state.isServerCert) "Server Certificate" else "Client Certificate"
            )
        },
        text = {
            Column {
                DetailRow(label = "Common Name", value = state.commonName)
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow(label = "Issuer", value = state.issuer)
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow(
                    label = "Valid From",
                    value = dateFormat.format(Date(state.validFrom))
                )
                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = "Valid Until",
                    value = dateFormat.format(Date(state.validUntil)),
                    valueColor = if (System.currentTimeMillis() > state.validUntil)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SHA-256 Fingerprint:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFingerprint(state.fingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

private fun formatFingerprint(fingerprint: String): String {
    // If already formatted with colons, keep it
    if (fingerprint.contains(":")) {
        return fingerprint.uppercase()
    }
    // Otherwise format with colons every 2 chars
    return fingerprint.uppercase().chunked(2).joinToString(":")
}

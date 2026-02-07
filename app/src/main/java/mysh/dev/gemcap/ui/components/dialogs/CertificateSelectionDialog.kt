package mysh.dev.gemcap.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.CertificateRequiredState
import mysh.dev.gemcap.domain.ClientCertificate

@Composable
fun CertificateSelectionDialog(
    state: CertificateRequiredState,
    onSelectCertificate: (ClientCertificate) -> Unit,
    onGenerateNew: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = state.title,
                color = if (state.statusCode == 60) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = state.message.ifBlank { "This server requires a client certificate." },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Host: ${state.host}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.matchingCertificates.isNotEmpty() && state.canSelectExisting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Available certificates:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(state.matchingCertificates) { cert ->
                            CertificateItem(
                                certificate = cert,
                                onClick = { onSelectCertificate(cert) }
                            )
                            HorizontalDivider()
                        }
                    }
                }

                if (state.statusCode == 61) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The certificate you provided is not authorized for this resource.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (state.statusCode == 62) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The certificate you provided is not valid (may be expired or malformed).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (state.canGenerateNew) {
                TextButton(onClick = onGenerateNew) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate New")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CertificateItem(
    certificate: ClientCertificate,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = certificate.commonName,
                style = MaterialTheme.typography.bodyMedium
            )
            // Show usages summary instead of old scope
            val usagesSummary = when {
                certificate.usages.isEmpty() -> "No sites configured"
                certificate.usages.size == 1 -> certificate.usages.first().toDisplayString()
                else -> "${certificate.usages.size} sites"
            }
            Text(
                text = usagesSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = certificate.fingerprint.take(23) + "...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

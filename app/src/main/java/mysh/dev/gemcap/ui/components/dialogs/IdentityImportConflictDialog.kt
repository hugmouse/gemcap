package mysh.dev.gemcap.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.ClientCertificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IdentityImportConflictDialog(
    existingCertificate: ClientCertificate,
    importedCommonName: String,
    fingerprint: String,
    onSkip: () -> Unit,
    onReplace: () -> Unit
) {
    var showExistingDetails by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(text = "Identity Already Exists") },
        text = {
            Column {
                Text(
                    text = "An identity with this fingerprint already exists.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Existing: ${existingCertificate.commonName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Import: $importedCommonName",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (showExistingDetails) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Created: ${dateFormat.format(Date(existingCertificate.createdAt))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Fingerprint: ${fingerprint.take(23)}...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onReplace) {
                Text(text = "Replace Existing")
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onSkip) {
                    Text(text = "Skip Import")
                }
                TextButton(onClick = { showExistingDetails = !showExistingDetails }) {
                    Text(text = if (showExistingDetails) "Hide Existing" else "View Existing")
                }
            }
        }
    )
}

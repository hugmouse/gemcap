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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.R
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
    val toggleDescription = stringResource(R.string.toggle_existing_details)
    val expandedState = stringResource(R.string.toggle_existing_state_expanded)
    val collapsedState = stringResource(R.string.toggle_existing_state_collapsed)

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(text = stringResource(R.string.identity_already_exists)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.identity_exists_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.existing_label,
                        existingCertificate.commonName
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.import_label, importedCommonName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    modifier = Modifier.semantics {
                        contentDescription = toggleDescription
                        stateDescription = if (showExistingDetails) expandedState else collapsedState
                    },
                    onClick = { showExistingDetails = !showExistingDetails }
                ) {
                    Text(
                        text = stringResource(
                            if (showExistingDetails) {
                                R.string.hide_existing
                            } else {
                                R.string.view_existing
                            }
                        )
                    )
                }

                if (showExistingDetails) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.created_label,
                            dateFormat.format(Date(existingCertificate.createdAt))
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.fingerprint_short_label,
                            fingerprint.take(23)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onReplace) {
                Text(text = stringResource(R.string.replace_existing))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(text = stringResource(R.string.skip_import))
            }
        }
    )
}

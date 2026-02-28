package mysh.dev.gemcap.ui.components.dialogs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.R
import mysh.dev.gemcap.data.ImportResult
import mysh.dev.gemcap.domain.ClientCertificate
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_PEM_FILE_BYTES = 1024 * 1024L // 1 MB
private class PemFileTooLargeException : Exception()

private sealed class IdentityImportDialogState {
    object SelectingFile : IdentityImportDialogState()
    object Parsing : IdentityImportDialogState()
    data class RequiresPassphrase(val pemData: String) : IdentityImportDialogState()
    data class Preview(val parsed: ParsedIdentity) : IdentityImportDialogState()
    data class Conflict(
        val existing: ClientCertificate,
        val parsed: ParsedIdentity
    ) : IdentityImportDialogState()

    object Importing : IdentityImportDialogState()
    data class Error(val message: String) : IdentityImportDialogState()
    object Success : IdentityImportDialogState()
}

private data class ParsedIdentity(
    val pemData: String,
    val passphrase: String?,
    val certificate: X509Certificate,
    val fingerprint: String,
    val commonName: String,
    val email: String?,
    val organization: String?
)

@Throws(PemFileTooLargeException::class)
private fun readPemDataWithLimit(context: Context, uri: Uri): String? {
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BufferedInputStream(stream).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                totalBytes += read
                if (totalBytes > MAX_PEM_FILE_BYTES) {
                    throw PemFileTooLargeException()
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }
}

/**
 * Dialog for importing a client identity from a PEM file.
 *
 * Flow:
 * 1) User picks file.
 * 2) PEM is parsed (and passphrase requested if required).
 * 3) Duplicate fingerprint check runs.
 * 4) User confirms import (or replace on conflict).
 */
@Composable
fun IdentityImportDialog(
    onDismiss: () -> Unit,
    onParseIdentity: (pemData: String, passphrase: String?, onResult: (ImportResult) -> Unit) -> Unit,
    onImportIdentity: (
        pemData: String,
        passphrase: String?,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) -> Unit,
    onCheckDuplicate: (fingerprint: String) -> ClientCertificate?,
    onReplaceIdentity: (
        existingAlias: String,
        newPemData: String,
        passphrase: String?,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember {
        mutableStateOf<IdentityImportDialogState>(IdentityImportDialogState.SelectingFile)
    }

    fun mapParsedIdentity(
        pemData: String,
        passphrase: String?,
        result: ImportResult.Success
    ): ParsedIdentity {
        val subject = result.certificate.subjectX500Principal.name
        return ParsedIdentity(
            pemData = pemData,
            passphrase = passphrase,
            certificate = result.certificate,
            fingerprint = result.fingerprint,
            commonName = Regex("CN=([^,]+)").find(subject)?.groupValues?.get(1) ?: subject,
            email = Regex("EMAILADDRESS=([^,]+)").find(subject)?.groupValues?.get(1),
            organization = Regex("O=([^,]+)").find(subject)?.groupValues?.get(1)
        )
    }

    fun handleParseResult(pemData: String, passphrase: String?, result: ImportResult) {
        state = when (result) {
            is ImportResult.NeedsPassphrase -> IdentityImportDialogState.RequiresPassphrase(pemData)
            is ImportResult.Error -> IdentityImportDialogState.Error(result.message)
            is ImportResult.Success -> {
                val parsedIdentity = mapParsedIdentity(pemData, passphrase, result)
                val duplicate = onCheckDuplicate(result.fingerprint)
                if (duplicate != null) {
                    IdentityImportDialogState.Conflict(
                        existing = duplicate,
                        parsed = parsedIdentity
                    )
                } else {
                    IdentityImportDialogState.Preview(parsedIdentity)
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        try {
            val fileSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                } else null
            }
            if (fileSize != null && fileSize > MAX_PEM_FILE_BYTES) {
                state = IdentityImportDialogState.Error(
                    context.getString(R.string.identity_import_error_file_too_large)
                )
                return@rememberLauncherForActivityResult
            }
            val pemData = readPemDataWithLimit(context, uri)
            if (pemData.isNullOrBlank()) {
                state = IdentityImportDialogState.Error(
                    context.getString(R.string.identity_import_error_file_read, "empty file")
                )
            } else {
                state = IdentityImportDialogState.Parsing
                onParseIdentity(pemData, null) { result ->
                    handleParseResult(pemData, null, result)
                }
            }
        } catch (_: PemFileTooLargeException) {
            state = IdentityImportDialogState.Error(
                context.getString(R.string.identity_import_error_file_too_large)
            )
        } catch (e: Exception) {
            state = IdentityImportDialogState.Error(
                context.getString(
                    R.string.identity_import_error_file_read,
                    e.message ?: "unknown error"
                )
            )
        }
    }

    LaunchedEffect(state) {
        if (state is IdentityImportDialogState.SelectingFile) {
            filePickerLauncher.launch(arrayOf("text/plain", "application/x-pem-file", "*/*"))
        }
    }

    when (val dialogState = state) {
        IdentityImportDialogState.SelectingFile,
        IdentityImportDialogState.Parsing -> {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text(text = context.getString(R.string.identity_import_title)) },
                text = { CircularProgressIndicator() },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(context.getString(R.string.identity_import_button_cancel))
                    }
                }
            )
        }

        is IdentityImportDialogState.RequiresPassphrase -> {
            PassphraseDialog(
                onDismiss = onDismiss,
                onSubmit = { passphrase ->
                    state = IdentityImportDialogState.Parsing
                    onParseIdentity(dialogState.pemData, passphrase) { result ->
                        handleParseResult(dialogState.pemData, passphrase, result)
                    }
                }
            )
        }

        is IdentityImportDialogState.Preview -> {
            val parsed = dialogState.parsed
            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text(text = context.getString(R.string.identity_import_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = stringResource(R.string.identity_import_review_details),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        IdentityDetailRow(
                            stringResource(R.string.identity_import_common_name_label),
                            parsed.commonName
                        )
                        parsed.email?.let {
                            IdentityDetailRow(
                                stringResource(R.string.identity_import_email_label),
                                it
                            )
                        }
                        parsed.organization?.let {
                            IdentityDetailRow(
                                stringResource(R.string.identity_import_organization_label),
                                it
                            )
                        }
                        IdentityDetailRow(
                            stringResource(R.string.identity_import_valid_until_label),
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                .format(Date(parsed.certificate.notAfter.time))
                        )
                        IdentityDetailRow(
                            stringResource(R.string.identity_import_fingerprint_label),
                            parsed.fingerprint.take(23) + "..."
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            state = IdentityImportDialogState.Importing
                            onImportIdentity(parsed.pemData, parsed.passphrase) { success, error ->
                                state = if (success) {
                                    IdentityImportDialogState.Success
                                } else {
                                    IdentityImportDialogState.Error(
                                        error ?: context.getString(R.string.identity_import_error_invalid_pem, "")
                                    )
                                }
                            }
                        }
                    ) {
                        Text(context.getString(R.string.identity_import_button_import))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(context.getString(R.string.identity_import_button_cancel))
                    }
                }
            )
        }

        is IdentityImportDialogState.Conflict -> {
            IdentityImportConflictDialog(
                existingCertificate = dialogState.existing,
                importedCommonName = dialogState.parsed.commonName,
                fingerprint = dialogState.parsed.fingerprint,
                onSkip = onDismiss,
                onReplace = {
                    state = IdentityImportDialogState.Importing
                    onReplaceIdentity(
                        dialogState.existing.alias,
                        dialogState.parsed.pemData,
                        dialogState.parsed.passphrase
                    ) { success, error ->
                        state = if (success) {
                            IdentityImportDialogState.Success
                        } else {
                            IdentityImportDialogState.Error(
                                error ?: context.getString(R.string.identity_import_error_invalid_pem, "")
                            )
                        }
                    }
                }
            )
        }

        IdentityImportDialogState.Importing -> {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = {},
                title = { Text(stringResource(R.string.identity_importing)) },
                text = { CircularProgressIndicator() },
                confirmButton = {},
                dismissButton = {}
            )
        }

        is IdentityImportDialogState.Error -> {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = { state = IdentityImportDialogState.SelectingFile },
                title = { Text(stringResource(R.string.identity_import_error_title)) },
                text = { Text(dialogState.message) },
                confirmButton = {
                    Button(onClick = { state = IdentityImportDialogState.SelectingFile }) {
                        Text(stringResource(R.string.identity_import_try_again))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(context.getString(R.string.identity_import_button_cancel))
                    }
                }
            )
        }

        IdentityImportDialogState.Success -> {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.identity_import_success_title)) },
                text = { Text(context.getString(R.string.identity_import_success)) },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.identity_import_done))
                    }
                },
                dismissButton = {}
            )
        }
    }
}

@Composable
private fun IdentityDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

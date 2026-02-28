package mysh.dev.gemcap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.R
import mysh.dev.gemcap.domain.ClientCertificate
import mysh.dev.gemcap.domain.IdentityUsage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CertificateManagementScreen(
    certificates: ImmutableList<ClientCertificate>,
    onCertificateClick: (ClientCertificate) -> Unit,
    onGenerateClick: () -> Unit,
    onImportClick: () -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onExport: ((ClientCertificate, android.net.Uri) -> Unit)? = null,
    currentHost: String = "",
    currentPath: String = "/",
    onUseOnClick: ((ClientCertificate) -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)
    var pendingExportCertificate by remember { mutableStateOf<ClientCertificate?>(null) }
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pem-file")
    ) { uri ->
        val certificate = pendingExportCertificate
        pendingExportCertificate = null
        if (uri != null && certificate != null && onExport != null) {
            onExport(certificate, uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        topBar = {
            Surface(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Identities",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.End
                    )
                )
            ) {
                FloatingActionButton(
                    onClick = onImportClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.import_identity_content_description))
                }
                FloatingActionButton(
                    onClick = onGenerateClick
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_identity_content_description))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Content
            if (certificates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No identities",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+' to create a new identity for authentication.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(certificates, key = { it.alias }) { certificate ->
                        IdentityCard(
                            certificate = certificate,
                            onClick = { onCertificateClick(certificate) },
                            onToggleActive = {
                                onToggleActive(
                                    certificate.alias,
                                    !certificate.isActive
                                )
                            },
                            onDelete = { onDelete(certificate.alias) },
                            onExport = onExport?.let {
                                {
                                    pendingExportCertificate = certificate
                                    exportDocumentLauncher.launch(suggestExportFileName(certificate))
                                }
                            },
                            onUseOnClick = onUseOnClick?.let { { it(certificate) } },
                            currentHost = currentHost
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityCard(
    certificate: ClientCertificate,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onExport: (() -> Unit)?,
    onUseOnClick: (() -> Unit)?,
    currentHost: String
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val isExpired = certificate.isExpired

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!certificate.isActive)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (certificate.isActive && !isExpired)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = certificate.commonName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        certificate.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            imageVector = if (certificate.isActive)
                                Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                            contentDescription = if (certificate.isActive) "Deactivate" else "Activate",
                            tint = if (certificate.isActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onExport != null && certificate.isExportable) {
                        IconButton(onClick = onExport) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Show usages as chips (Lagrange-style)
            if (certificate.usages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    certificate.usages.forEach { usage ->
                        UsageChip(usage = usage)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Not used on any site",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // "Use on" button if we have a current host
            if (onUseOnClick != null && currentHost.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = onUseOnClick,
                    label = { Text("Use on $currentHost...") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = certificate.fingerprint.take(47) + "...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Created: ${dateFormat.format(Date(certificate.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isExpired) "Expired" else "Expires: ${
                        dateFormat.format(
                            Date(
                                certificate.expiresAt
                            )
                        )
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpired) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!certificate.isActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Deactivated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UsageChip(usage: IdentityUsage) {
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = "${usage.typeLabel()}: ${usage.toDisplayString()}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

private fun suggestExportFileName(certificate: ClientCertificate): String {
    val fallback = "identity-${certificate.alias.takeLast(8)}"
    val baseName = certificate.commonName.trim().ifBlank { fallback }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    return if (baseName.endsWith(".pem", ignoreCase = true)) {
        baseName
    } else {
        "$baseName.pem"
    }
}

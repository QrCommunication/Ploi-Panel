package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.qrcommunication.ploipanel.ssh.JSchHostKeyTransport
import com.qrcommunication.ploipanel.ssh.KnownHostsImport
import com.qrcommunication.ploipanel.ssh.SshHostKey
import com.qrcommunication.ploipanel.ssh.SshHostTrustStore
import com.qrcommunication.ploipanel.ssh.SshKeyEntry
import com.qrcommunication.ploipanel.ssh.SshBookmarkStore
import com.qrcommunication.ploipanel.ssh.SshKeyMaterial
import com.qrcommunication.ploipanel.ssh.SshKeyVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Keystore alias dedicated to the SSH key vault; never reused for Ploi tokens or templates. */
private const val SSH_VAULT_ALIAS = SSH_VAULT_KEYSTORE_ALIAS

/** Maps vault/trust-store failures to operator-readable messages without leaking internals. */
private fun mapSshError(failure: Throwable): Int = when (failure.message) {
    "Duplicate key label" -> R.string.ssh_error_duplicate_label
    "Too many SSH keys" -> R.string.ssh_error_limit
    "Invalid key label" -> R.string.ssh_error_invalid_label
    "Unsupported key type" -> R.string.ssh_error_key_type
    "Expected key type and key data" -> R.string.ssh_error_public_line
    "Encrypted PEM keys are unsupported",
    "Passphrase-protected keys are unsupported" -> R.string.ssh_error_encrypted_pem
    "Public key required" -> R.string.ssh_error_public_required
    else -> if (failure is IllegalStateException) R.string.ssh_error_vault
        else R.string.ssh_error_malformed_key
}

/**
 * Device-side SSH management for the active profile: imported private keys (write-only, the
 * PEM is never read back for display) and pinned host keys (TOFU). Destructive actions stay
 * explicit; key deletion is gated by a fresh PIN/biometric confirmation. The host probe opens
 * [SshHostProbeDialog], the only connection-time UI allowed to trust or re-pin a host key.
 * Interactive sessions live in [SshTerminalScreen]; this screen never opens one.
 */
@Composable
internal fun SshDeviceSection(profileId: String?, lock: AppLock, activity: FragmentActivity) {
    if (profileId == null) {
        QuietNote(Icons.Outlined.Info, stringResource(R.string.ssh_device_need_profile))
        return
    }
    val context = LocalContext.current
    val vault = remember(context, profileId) {
        SshKeyVault(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher(SSH_VAULT_ALIAS))
    }
    val trustStore = remember(context, profileId) {
        SshHostTrustStore(SharedPreferencesProfilePrefs(context), profileId)
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    var keys by remember(profileId) { mutableStateOf<List<SshKeyEntry>?>(null) }
    var hosts by remember(profileId) { mutableStateOf<List<SshHostKey>?>(null) }
    var keysVersion by remember(profileId) { mutableIntStateOf(0) }
    var hostsVersion by remember(profileId) { mutableIntStateOf(0) }
    var keysError by remember(profileId) { mutableIntStateOf(0) }
    var hostsError by remember(profileId) { mutableIntStateOf(0) }
    var feedback by remember(profileId) { mutableIntStateOf(0) }
    var importingKey by remember { mutableStateOf(false) }
    var importingHosts by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SshKeyEntry?>(null) }
    var pendingRevoke by remember { mutableStateOf<SshHostKey?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }

    LaunchedEffect(profileId, keysVersion) {
        keys = null
        keysError = 0
        try {
            keys = withContext(Dispatchers.IO) { vault.list(profileId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            keysError = mapSshError(failure)
        }
    }
    LaunchedEffect(profileId, hostsVersion) {
        hosts = null
        hostsError = 0
        try {
            hosts = withContext(Dispatchers.IO) { trustStore.entries() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            hostsError = mapSshError(failure)
        }
    }

    // Plain column: the Settings screen already wraps this section in a SectionCard.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        Text(
            stringResource(R.string.ssh_device_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (feedback != 0) SuccessBanner(stringResource(feedback))

        SectionHeader(stringResource(R.string.ssh_keys_heading))
        when {
            keysError != 0 -> InlineNotice(stringResource(keysError))
            keys == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BusyIndicator() }
            keys!!.isEmpty() -> QuietNote(Icons.Outlined.Key, stringResource(R.string.ssh_keys_empty))
            else -> keys!!.forEach { entry ->
                ResourceCard(
                    title = entry.label,
                    icon = Icons.Outlined.Key,
                    subtitle = stringResource(R.string.ssh_key_added, dateFormat.format(Date(entry.createdAtEpochMillis))),
                    facts = listOf(ResourceFact(stringResource(R.string.g5_ssh_fact_type), entry.keyType, mono = true))
                ) {
                    if (entry.passphraseProtected) CardNote(
                        Icons.Outlined.Lock, stringResource(R.string.ssh_key_passphrase_protected),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.keyType == "ssh-rsa") WeakKeyNote()
                    DangerAction(stringResource(R.string.ssh_key_delete), onClick = { pendingDelete = entry })
                }
            }
        }
        FilledTonalButton(onClick = { importingKey = true }, enabled = keys != null) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.ssh_key_import), Modifier.padding(start = PanelSpacing.sm))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionHeader(stringResource(R.string.ssh_hosts_heading))
        when {
            hostsError != 0 -> InlineNotice(stringResource(hostsError))
            hosts == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BusyIndicator() }
            hosts!!.isEmpty() -> QuietNote(Icons.Outlined.Dns, stringResource(R.string.ssh_hosts_empty))
            else -> hosts!!.forEach { entry ->
                ResourceCard(
                    title = "${entry.host}:${entry.port}",
                    monoTitle = true,
                    icon = Icons.Outlined.Dns,
                    facts = listOf(
                        ResourceFact(stringResource(R.string.g5_ssh_fact_type), entry.keyType, mono = true),
                        ResourceFact(stringResource(R.string.g5_ssh_fact_fingerprint), entry.fingerprint(), mono = true)
                    )
                ) {
                    if (entry.isWeakSignature) WeakKeyNote()
                    DangerAction(stringResource(R.string.ssh_host_revoke), onClick = { pendingRevoke = entry })
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
        ) {
            FilledTonalButton(onClick = { probing = true }) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.ssh_probe_title), Modifier.padding(start = PanelSpacing.sm))
            }
            CardAction(
                stringResource(R.string.ssh_hosts_import), icon = Icons.Outlined.FileDownload,
                enabled = hosts != null, onClick = { importingHosts = true }
            )
            DangerAction(
                stringResource(R.string.ssh_hosts_clear), onClick = { confirmClear = true },
                enabled = hosts != null || hostsError != 0
            )
        }
    }

    if (importingKey) SshKeyImportDialog(
        onDismiss = { importingKey = false },
        onImport = { label, publicLine, privatePem ->
            // The public key is derived from the PEM when possible; a pasted line must match it.
            val inspection = SshKeyMaterial.inspect(privatePem)
            val (keyType, publicKey) = if (publicLine.isNotBlank()) {
                SshKeyVault.parsePublicKeyLine(publicLine)
            } else {
                inspection.keyType to (inspection.publicKeyBase64 ?: throw IllegalArgumentException("Public key required"))
            }
            require(keyType == inspection.keyType) { "Public key does not match ${inspection.keyType}" }
            if (inspection.publicKeyBase64 != null) {
                require(inspection.publicKeyBase64 == publicKey.trim()) { "Public key does not match private key" }
            }
            vault.import(profileId, label, keyType, privatePem, publicKey, passphraseProtected = inspection.encrypted)
        },
        onImported = {
            importingKey = false
            feedback = R.string.ssh_key_imported
            keysVersion++
        }
    )
    if (importingHosts) KnownHostsImportDialog(
        trustStore = trustStore,
        onDismiss = { importingHosts = false },
        onImported = { hostsVersion++ }
    )
    if (probing) SshHostProbeDialog(
        trustStore = trustStore,
        transport = remember { JSchHostKeyTransport() },
        lock = lock,
        activity = activity,
        onDismiss = { probing = false },
        onPinnedChanged = { hostsVersion++ }
    )
    pendingDelete?.let { entry ->
        SensitiveConfirmDialog(
            lock, activity,
            stringResource(R.string.ssh_key_delete_confirm, entry.label),
            R.string.ssh_key_delete,
            onConfirmed = {
                pendingDelete = null
                feedback = 0
                keysError = 0
                try {
                    vault.remove(profileId, entry.id)
                    SshBookmarkStore(SharedPreferencesProfilePrefs(context), profileId).forgetKey(entry.id)
                    feedback = R.string.ssh_key_deleted
                    keysVersion++
                } catch (failure: Exception) {
                    keysError = mapSshError(failure)
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
    pendingRevoke?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.ssh_host_revoke)) },
            text = {
                Text(stringResource(R.string.ssh_host_revoke_confirm, "${entry.host}:${entry.port}"))
            },
            confirmButton = {
                Button(colors = dangerButtonColors(), onClick = {
                    pendingRevoke = null
                    hostsError = 0
                    try {
                        trustStore.revoke(entry.host, entry.port, entry.keyType)
                        hostsVersion++
                    } catch (failure: Exception) {
                        hostsError = mapSshError(failure)
                    }
                }) { Text(stringResource(R.string.ssh_host_revoke)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRevoke = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.ssh_hosts_clear)) },
        text = { Text(stringResource(R.string.ssh_hosts_clear_confirm)) },
        confirmButton = {
            Button(colors = dangerButtonColors(), onClick = {
                confirmClear = false
                hostsError = 0
                trustStore.clear()
                hostsVersion++
            }) { Text(stringResource(R.string.ssh_hosts_clear)) }
        },
        dismissButton = {
            OutlinedButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Import form; stays open on validation failure so the operator can fix the pasted material. */
@Composable
private fun SshKeyImportDialog(
    onDismiss: () -> Unit,
    onImport: (label: String, publicLine: String, privatePem: String) -> Unit,
    onImported: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var publicLine by remember { mutableStateOf("") }
    var privatePem by remember { mutableStateOf("") }
    var error by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.ssh_key_import)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
            ) {
                OutlinedTextField(label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.ssh_key_label)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(publicLine, onValueChange = { publicLine = it },
                    label = { Text(stringResource(R.string.ssh_key_public_line)) },
                    placeholder = { Text(stringResource(R.string.ssh_key_public_hint), style = panelMonoStyle) },
                    textStyle = panelMonoStyle,
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(privatePem, onValueChange = { privatePem = it },
                    label = { Text(stringResource(R.string.ssh_key_private_pem)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4, maxLines = 10, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (error != 0) InlineNotice(stringResource(error))
                if (busy) BusyIndicator()
            }
        },
        confirmButton = {
            Button(onClick = {
                error = 0
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            onImport(label.trim(), publicLine.trim(), privatePem)
                        }
                        onImported()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = mapSshError(failure)
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy && label.isNotBlank() && privatePem.isNotBlank()) {
                Text(stringResource(R.string.ssh_key_import))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** known_hosts paste import; the per-line report stays visible until the operator closes it. */
@Composable
private fun KnownHostsImportDialog(
    trustStore: SshHostTrustStore,
    onDismiss: () -> Unit,
    onImported: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<KnownHostsImport?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
        title = { Text(stringResource(R.string.ssh_hosts_import)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
            ) {
                val outcome = report
                if (outcome == null) {
                    Text(stringResource(R.string.ssh_hosts_import_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(text, onValueChange = { text = it },
                        label = { Text("known_hosts") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4, maxLines = 10, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (busy) BusyIndicator()
                } else {
                    SuccessBanner(stringResource(R.string.ssh_hosts_imported, outcome.imported))
                    if (outcome.errors.isNotEmpty()) {
                        InlineNotice(stringResource(R.string.ssh_hosts_import_errors))
                        outcome.errors.forEach { line ->
                            Text(line, style = panelMonoStyle, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (report == null) {
                Button(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { trustStore.importKnownHosts(text) }
                            report = result
                            onImported()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy && text.isNotBlank()) { Text(stringResource(R.string.ssh_hosts_import)) }
            } else {
                Button(onClick = onDismiss) { Text(stringResource(R.string.ssh_close)) }
            }
        },
        dismissButton = {
            if (report == null) {
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/** Small muted line with a decorative icon, used for empty lists inside the settings card. */
@Composable
private fun QuietNote(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-width note placed first in a card's action row so it wraps onto its own line. */
@Composable
private fun CardNote(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs + PanelSpacing.xxs)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

/** ssh-rsa (SHA-1) warning, error-coloured with an icon. */
@Composable
private fun WeakKeyNote() = CardNote(
    Icons.Outlined.WarningAmber, stringResource(R.string.ssh_weak_rsa), MaterialTheme.colorScheme.error
)

@Composable
private fun dangerButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
)

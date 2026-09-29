package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
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
import com.qrcommunication.ploipanel.ssh.SshKeyVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Keystore alias dedicated to the SSH key vault; never reused for Ploi tokens or templates. */
private const val SSH_VAULT_ALIAS = "ploi-panel.ssh-keys"

/** Maps vault/trust-store failures to operator-readable messages without leaking internals. */
private fun mapSshError(failure: Throwable): Int = when (failure.message) {
    "Duplicate key label" -> R.string.ssh_error_duplicate_label
    "Too many SSH keys" -> R.string.ssh_error_limit
    "Invalid key label" -> R.string.ssh_error_invalid_label
    "Unsupported key type" -> R.string.ssh_error_key_type
    "Expected key type and key data" -> R.string.ssh_error_public_line
    "Encrypted PEM keys are unsupported",
    "Passphrase-protected keys are unsupported" -> R.string.ssh_error_encrypted_pem
    else -> if (failure is IllegalStateException) R.string.ssh_error_vault
        else R.string.ssh_error_malformed_key
}

/**
 * Device-side SSH management for the active profile: imported private keys (write-only, the
 * PEM is never read back for display) and pinned host keys (TOFU). Destructive actions stay
 * explicit; key deletion is gated by a fresh PIN/biometric confirmation. The host probe opens
 * [SshHostProbeDialog], the only connection-time UI allowed to trust or re-pin a host key.
 * No authenticated SSH session or terminal exists yet.
 */
@Composable
internal fun SshDeviceSection(profileId: String?, lock: AppLock, activity: FragmentActivity) {
    if (profileId == null) {
        Text(stringResource(R.string.ssh_device_need_profile), style = MaterialTheme.typography.bodySmall)
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

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ssh_device_hint), style = MaterialTheme.typography.bodySmall)
        if (feedback != 0) Text(stringResource(feedback), color = MaterialTheme.colorScheme.primary)

        Text(stringResource(R.string.ssh_keys_heading), style = MaterialTheme.typography.titleMedium)
        when {
            keysError != 0 -> Text(stringResource(keysError), color = MaterialTheme.colorScheme.error)
            keys == null -> BusyIndicator()
            keys!!.isEmpty() -> Text(
                stringResource(R.string.ssh_keys_empty), style = MaterialTheme.typography.bodySmall
            )
            else -> keys!!.forEach { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.label, style = MaterialTheme.typography.titleSmall)
                        Text(entry.keyType, style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(R.string.ssh_key_added, dateFormat.format(Date(entry.createdAtEpochMillis))),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (entry.keyType == "ssh-rsa") Text(
                            stringResource(R.string.ssh_weak_rsa), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { pendingDelete = entry }) {
                            Text(stringResource(R.string.ssh_key_delete))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { importingKey = true }, enabled = keys != null) {
            Text(stringResource(R.string.ssh_key_import))
        }

        Text(stringResource(R.string.ssh_hosts_heading), style = MaterialTheme.typography.titleMedium)
        when {
            hostsError != 0 -> Text(stringResource(hostsError), color = MaterialTheme.colorScheme.error)
            hosts == null -> BusyIndicator()
            hosts!!.isEmpty() -> Text(
                stringResource(R.string.ssh_hosts_empty), style = MaterialTheme.typography.bodySmall
            )
            else -> hosts!!.forEach { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${entry.host}:${entry.port}", style = MaterialTheme.typography.titleSmall)
                        Text(entry.keyType, style = MaterialTheme.typography.bodySmall)
                        Text(
                            entry.fingerprint(), style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        if (entry.isWeakSignature) Text(
                            stringResource(R.string.ssh_weak_rsa), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { pendingRevoke = entry }) {
                            Text(stringResource(R.string.ssh_host_revoke))
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { probing = true }) {
                Text(stringResource(R.string.ssh_probe_title))
            }
            OutlinedButton(onClick = { importingHosts = true }, enabled = hosts != null) {
                Text(stringResource(R.string.ssh_hosts_import))
            }
            OutlinedButton(onClick = { confirmClear = true }, enabled = hosts != null || hostsError != 0) {
                Text(stringResource(R.string.ssh_hosts_clear))
            }
        }
    }

    if (importingKey) SshKeyImportDialog(
        onDismiss = { importingKey = false },
        onImport = { label, publicLine, privatePem ->
            val (keyType, publicKey) = SshKeyVault.parsePublicKeyLine(publicLine)
            vault.import(profileId, label, keyType, privatePem, publicKey)
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
            title = { Text(stringResource(R.string.ssh_host_revoke)) },
            text = {
                Text(stringResource(R.string.ssh_host_revoke_confirm, "${entry.host}:${entry.port}"))
            },
            confirmButton = {
                Button(onClick = {
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
        title = { Text(stringResource(R.string.ssh_hosts_clear)) },
        text = { Text(stringResource(R.string.ssh_hosts_clear_confirm)) },
        confirmButton = {
            Button(onClick = {
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
        title = { Text(stringResource(R.string.ssh_key_import)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.ssh_key_label)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(publicLine, onValueChange = { publicLine = it },
                    label = { Text(stringResource(R.string.ssh_key_public_line)) },
                    placeholder = { Text(stringResource(R.string.ssh_key_public_hint)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(privatePem, onValueChange = { privatePem = it },
                    label = { Text(stringResource(R.string.ssh_key_private_pem)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4, maxLines = 10, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
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
            }, enabled = !busy && label.isNotBlank() && publicLine.isNotBlank() && privatePem.isNotBlank()) {
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
        title = { Text(stringResource(R.string.ssh_hosts_import)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val outcome = report
                if (outcome == null) {
                    Text(stringResource(R.string.ssh_hosts_import_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, onValueChange = { text = it },
                        label = { Text("known_hosts") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4, maxLines = 10, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (busy) BusyIndicator()
                } else {
                    Text(stringResource(R.string.ssh_hosts_imported, outcome.imported))
                    if (outcome.errors.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ssh_hosts_import_errors),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall
                        )
                        outcome.errors.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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

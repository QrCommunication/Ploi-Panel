package com.qrcommunication.ploipanel

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Never passes plaintext credentials through an Intent: only a SAF Uri or encrypted bytes. */
@Composable
internal fun ConfigurationTransferSection(
    manager: PortableConfigurationManager,
    lock: AppLock,
    activity: FragmentActivity,
    importUri: Uri?,
    onImportUriConsumed: () -> Unit,
    onChooseImport: () -> Unit,
    onSaveEncryptedArchive: (ByteArray) -> Unit,
    transferStatus: Int,
    onImported: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember(importUri) { mutableStateOf(importUri != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var importedCount by remember { mutableIntStateOf(-1) }

    // Plain column: the Settings screen already wraps this section in a SectionCard.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        Text(
            stringResource(R.string.config_transfer_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
        ) {
            FilledTonalButton(onClick = { error = 0; confirming = 1 }, enabled = !busy) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.config_export), Modifier.padding(start = PanelSpacing.sm))
            }
            OutlinedButton(onClick = { error = 0; confirming = 2 }, enabled = !busy) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.config_import), Modifier.padding(start = PanelSpacing.sm))
            }
        }
        if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BusyIndicator() }
        if (error != 0) InlineNotice(stringResource(error))
        if (transferStatus != 0) {
            if (transferStatus == R.string.config_export_error || transferStatus == R.string.config_import_error)
                InlineNotice(stringResource(transferStatus))
            else SuccessBanner(stringResource(transferStatus))
        }
        if (importedCount >= 0) SuccessBanner(pluralStringResource(R.plurals.config_imported, importedCount, importedCount))
    }
    if (confirming != 0) {
        SensitiveConfirmDialog(
            lock, activity,
            stringResource(if (confirming == 1) R.string.config_export_confirm else R.string.config_import_confirm),
            if (confirming == 1) R.string.config_export else R.string.config_import,
            onConfirmed = {
                when (confirming) {
                    1 -> exporting = true
                    2 -> onChooseImport()
                }
                confirming = 0
            },
            onDismiss = { confirming = 0 }
        )
    }
    if (exporting) ArchivePassphraseDialog(
        exporting = true, busy = busy, error = error,
        onDismiss = { if (!busy) exporting = false },
        onSubmit = { passphrase ->
            busy = true
            error = 0
            scope.launch {
                try {
                    val encrypted = withContext(Dispatchers.Default) { manager.export(passphrase) }
                    exporting = false
                    onSaveEncryptedArchive(encrypted)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = R.string.config_export_error
                } finally { busy = false }
            }
        }
    )
    if (importing && importUri != null) ArchivePassphraseDialog(
        exporting = false, busy = busy, error = error,
        onDismiss = { if (!busy) { importing = false; onImportUriConsumed() } },
        onSubmit = { passphrase ->
            busy = true
            error = 0
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        val bytes = activity.contentResolver.openInputStream(importUri)?.use(::readLimitedArchive)
                            ?: throw IllegalArgumentException("Unreadable archive")
                        manager.import(bytes, passphrase)
                    }
                    importing = false
                    onImportUriConsumed()
                    importedCount = count
                    onImported()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IllegalArgumentException) {
                    error = if (failure.message == "Conflicting profile label") R.string.config_conflict
                        else if (failure.message == "Too many profiles") R.string.config_limit
                        else R.string.config_invalid_archive
                } catch (_: Exception) {
                    error = R.string.config_import_error
                } finally { busy = false }
            }
        }
    )
}

@Composable
private fun ArchivePassphraseDialog(
    exporting: Boolean, busy: Boolean, error: Int, onDismiss: () -> Unit, onSubmit: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) { passphrase = ""; confirmation = ""; onDismiss() } },
        icon = { Icon(Icons.Outlined.Password, contentDescription = null) },
        title = { Text(stringResource(if (exporting) R.string.config_export else R.string.config_import)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                Text(
                    stringResource(if (exporting) R.string.config_passphrase_new_hint else R.string.config_passphrase_import_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(passphrase, onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.config_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (exporting) OutlinedTextField(confirmation, onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.config_confirm_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (invalid) InlineNotice(stringResource(R.string.config_passphrase_invalid))
                if (error != 0) InlineNotice(stringResource(error))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (passphrase.length < PortableConfigurationCodec.MIN_PASSPHRASE_LENGTH ||
                    passphrase.length > 256 || (exporting && passphrase != confirmation)) {
                    invalid = true
                } else {
                    invalid = false
                    onSubmit(passphrase)
                    passphrase = ""
                    confirmation = ""
                }
            }, enabled = !busy && passphrase.isNotBlank()) {
                Text(stringResource(if (exporting) R.string.config_export else R.string.config_import))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { passphrase = ""; confirmation = ""; onDismiss() }, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

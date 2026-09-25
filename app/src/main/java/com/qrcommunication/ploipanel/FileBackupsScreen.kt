package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backups tab of the server screen: database backups and site file backups. */
@Composable
internal fun BackupsTab(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    var section by remember(serverId, token) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { section = 0 }, enabled = section != 0) {
                Text(stringResource(R.string.backups_databases_tab))
            }
            OutlinedButton(onClick = { section = 1 }, enabled = section != 1) {
                Text(stringResource(R.string.backups_files_tab))
            }
        }
        if (section == 0) {
            DatabaseBackupsScreen(token, serverId, lock, activity)
        } else {
            FileBackupsScreen(token, serverId, lock, activity)
        }
    }
}

/**
 * Site file backups domain (`site`: /backups/file) filtered to one server:
 * paginated list, create (per-site paths), update, manual run, delete (PIN/biometric)
 * and per-backup notification channels (list / attach / detach per documented location).
 */
@Composable
internal fun FileBackupsScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<FileBackupPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FileBackup?>(null) }
    var channelsFor by remember { mutableStateOf<FileBackup?>(null) }
    var confirmDelete by remember { mutableStateOf<FileBackup?>(null) }

    val createdMessage = stringResource(R.string.backup_created)
    val runMessage = stringResource(R.string.backup_run_started)
    val updatedMessage = stringResource(R.string.backup_updated)
    val deletedMessage = stringResource(R.string.backup_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) {
                PloiApi.fileBackups(token, serverId = serverId, page = page)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    fun runAction(fallback: String, block: suspend () -> String) {
        busy = true
        error = null
        scope.launch {
            try {
                val message = withContext(Dispatchers.IO) { block() }
                feedback = message.ifBlank { fallback }
                refresh++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
            OutlinedButton(onClick = { creating = true }, enabled = !busy) {
                Text(stringResource(R.string.new_file_backup))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.backups.isEmpty()) Text(stringResource(R.string.empty_file_backups))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 1) {
                    Text(stringResource(R.string.previous))
                }
                Text(
                    stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()),
                    Modifier.padding(top = 12.dp)
                )
                OutlinedButton(onClick = { page++ }, enabled = data.hasNext) {
                    Text(stringResource(R.string.next))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.backups, key = { it.id }) { backup ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val title = backup.label.ifBlank {
                                backup.backupConfigurationLabel.ifBlank { backup.typeHuman.ifBlank { backup.type } }
                            }
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            if (backup.siteDomain.isNotBlank()) {
                                Text(stringResource(R.string.backup_site, backup.siteDomain))
                            }
                            if (backup.path.isNotBlank()) {
                                Text(stringResource(R.string.backup_path, backup.path))
                            }
                            if (backup.typeHuman.isNotBlank()) {
                                Text(stringResource(R.string.backup_type, backup.typeHuman))
                            }
                            val intervalMinutes = backup.intervalMinutes
                            Text(
                                when {
                                    intervalMinutes == 0 -> stringResource(R.string.backup_interval_nightly)
                                    intervalMinutes != null ->
                                        stringResource(R.string.backup_interval_minutes, intervalMinutes)
                                    backup.intervalLabel.isNotBlank() ->
                                        stringResource(R.string.backup_interval_text, backup.intervalLabel)
                                    else -> stringResource(R.string.backup_interval_unknown)
                                }
                            )
                            Text(stringResource(R.string.backup_keep_count, backup.keepBackupAmount))
                            if (!backup.active) {
                                Text(
                                    stringResource(R.string.backup_inactive),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (backup.lastBackupAt.isNotBlank()) {
                                Text(stringResource(R.string.backup_last_run, backup.lastBackupAt))
                            }
                            if (backup.nextBackupAt.isNotBlank()) {
                                Text(stringResource(R.string.backup_next_run, backup.nextBackupAt))
                            }
                            if (backup.createdAt.isNotBlank()) {
                                Text(stringResource(R.string.detail_created, backup.createdAt))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        runAction(runMessage) { PloiApi.runFileBackup(token, backup.id) }
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.run_backup)) }
                                OutlinedButton(onClick = { editing = backup }, enabled = !busy) {
                                    Text(stringResource(R.string.edit_backup))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { channelsFor = backup }, enabled = !busy) {
                                    Text(stringResource(R.string.backup_channels))
                                }
                                OutlinedButton(onClick = { confirmDelete = backup }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.delete_backup),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateFileBackupDialog(
            token = token, serverId = serverId, busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createFileBackup(token, request) }
            },
            onDismiss = { creating = false }
        )
    }
    editing?.let { backup ->
        EditFileBackupDialog(
            backup = backup, busy = busy,
            onSave = { request ->
                editing = null
                runAction(updatedMessage) {
                    PloiApi.updateFileBackup(token, backup.id, request)
                    ""
                }
            },
            onDismiss = { editing = null }
        )
    }
    channelsFor?.let { backup ->
        BackupChannelsDialog(
            token = token,
            backupId = backup.id,
            title = backup.siteDomain.ifBlank { backup.label },
            listChannels = { PloiApi.fileBackupNotificationChannels(token, backup.id) },
            attachChannel = { channelId, location ->
                PloiApi.attachFileBackupNotificationChannel(token, backup.id, channelId, location)
            },
            detachChannel = { channelId, location ->
                PloiApi.detachFileBackupNotificationChannel(token, backup.id, channelId, location)
            },
            onDismiss = { channelsFor = null; refresh++ }
        )
    }
    confirmDelete?.let { backup ->
        val label = backup.label.ifBlank { backup.siteDomain }
        SensitiveConfirmDialog(
            lock = lock, activity = activity,
            message = stringResource(R.string.confirm_delete_backup, label),
            confirmLabel = R.string.delete_backup,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteFileBackup(token, backup.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/**
 * Create form for POST /backups/file: backup configuration and server sites are loaded
 * from the account so the user picks documented identifiers; each selected site gets
 * its own absolute path field (the documented `path` object).
 */
@Composable
private fun CreateFileBackupDialog(
    token: String, serverId: Long, busy: Boolean,
    onCreate: (CreateFileBackupRequest) -> Unit, onDismiss: () -> Unit
) {
    var configurations by remember { mutableStateOf<List<BackupConfiguration>?>(null) }
    var sites by remember { mutableStateOf<List<Site>?>(null) }
    var optionsError by remember { mutableStateOf<Throwable?>(null) }
    var configurationId by remember { mutableLongStateOf(0L) }
    var selectedSites by remember { mutableStateOf(setOf<Long>()) }
    val sitePaths = remember { mutableStateMapOf<Long, String>() }
    var interval by remember { mutableStateOf("0") }
    var keep by remember { mutableStateOf("") }
    var locations by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var localPath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nextBackupAt by remember { mutableStateOf("") }
    var deleteOnFail by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(token, serverId) {
        try {
            configurations = withContext(Dispatchers.IO) {
                PloiApi.backupConfigurations(token, perPage = 50).configurations
            }
            sites = withContext(Dispatchers.IO) {
                PloiApi.sites(token, serverId, perPage = 50).sites
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            optionsError = failure
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_file_backup)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (optionsError != null) ApiErrorText(optionsError!!)
                if (configurations == null || sites == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.backup_loading_options), Modifier.padding(top = 12.dp))
                    }
                }
                configurations?.let { list ->
                    Text(stringResource(R.string.backup_configuration_label))
                    if (list.isEmpty()) Text(stringResource(R.string.backup_no_configurations))
                    list.forEach { configuration ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = configurationId == configuration.id,
                                onClick = { configurationId = configuration.id }
                            )
                            Text(
                                configuration.label.ifBlank { configuration.humanType.ifBlank { configuration.type } },
                                Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
                sites?.let { list ->
                    Text(stringResource(R.string.backup_sites_label))
                    if (list.isEmpty()) Text(stringResource(R.string.backup_no_sites))
                    list.forEach { site ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = site.id in selectedSites,
                                onCheckedChange = { checked ->
                                    selectedSites =
                                        if (checked) selectedSites + site.id else selectedSites - site.id
                                }
                            )
                            Text(site.domain, Modifier.padding(top = 12.dp))
                        }
                        if (site.id in selectedSites) {
                            OutlinedTextField(
                                value = sitePaths[site.id].orEmpty(),
                                onValueChange = { sitePaths[site.id] = it },
                                label = { Text(stringResource(R.string.backup_site_path_label, site.domain)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                OutlinedTextField(value = interval, onValueChange = { interval = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.backup_interval_label)) },
                    supportingText = { Text(stringResource(R.string.file_backup_create_interval_hint)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = keep, onValueChange = { keep = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.backup_keep_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = locations, onValueChange = { locations = it },
                    label = { Text(stringResource(R.string.backup_locations_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = customName, onValueChange = { customName = it },
                    label = { Text(stringResource(R.string.backup_custom_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = localPath, onValueChange = { localPath = it },
                    label = { Text(stringResource(R.string.backup_local_path_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nextBackupAt, onValueChange = { nextBackupAt = it },
                    label = { Text(stringResource(R.string.backup_next_at_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.backup_delete_on_fail), Modifier.padding(top = 12.dp))
                    Switch(checked = deleteOnFail, onCheckedChange = { deleteOnFail = it })
                }
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val request = try {
                    CreateFileBackupRequest(
                        backupConfiguration = configurationId,
                        server = serverId,
                        sites = selectedSites.toList(),
                        interval = interval.toIntOrNull() ?: -1,
                        paths = selectedSites.associateWith { sitePaths[it].orEmpty().trim() },
                        locations = locations.trim(),
                        keepBackupAmount = keep.toIntOrNull(),
                        customName = customName.trim(),
                        localPath = localPath.trim(),
                        password = password,
                        nextBackupAt = nextBackupAt.trim(),
                        deleteOnFail = deleteOnFail
                    )
                } catch (invalidInput: IllegalArgumentException) {
                    invalid = true
                    null
                }
                if (request != null) {
                    invalid = false
                    onCreate(request)
                }
            }, enabled = !busy && configurationId > 0 && selectedSites.isNotEmpty()) {
                Text(stringResource(R.string.create_backup_submit))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Edit form for PATCH /backups/file/{id}: documented required + optional fields. */
@Composable
private fun EditFileBackupDialog(
    backup: FileBackup, busy: Boolean,
    onSave: (UpdateFileBackupRequest) -> Unit, onDismiss: () -> Unit
) {
    var interval by remember { mutableStateOf(backup.intervalMinutes?.toString() ?: "") }
    var keep by remember { mutableStateOf(backup.keepBackupAmount.toString()) }
    var path by remember { mutableStateOf(backup.path) }
    var deleteOnFail by remember { mutableStateOf(backup.deleteOnFail) }
    var customName by remember { mutableStateOf(backup.customName) }
    var localPath by remember { mutableStateOf(backup.localPath) }
    var compression by remember { mutableStateOf(backup.compression) }
    var excluded by remember { mutableStateOf(backup.excluded.joinToString(",")) }
    var locations by remember { mutableStateOf(backup.locations) }
    var nextBackupAt by remember { mutableStateOf(backup.nextBackupAt) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_backup)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = interval, onValueChange = { interval = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.backup_interval_label)) },
                    supportingText = { Text(stringResource(R.string.file_backup_update_interval_hint)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = keep, onValueChange = { keep = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.backup_keep_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = path, onValueChange = { path = it },
                    label = { Text(stringResource(R.string.backup_server_path_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.backup_delete_on_fail), Modifier.padding(top = 12.dp))
                    Switch(checked = deleteOnFail, onCheckedChange = { deleteOnFail = it })
                }
                OutlinedTextField(value = customName, onValueChange = { customName = it },
                    label = { Text(stringResource(R.string.backup_custom_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = localPath, onValueChange = { localPath = it },
                    label = { Text(stringResource(R.string.backup_local_path_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = compression, onValueChange = { compression = it },
                    label = { Text(stringResource(R.string.backup_compression_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = excluded, onValueChange = { excluded = it },
                    label = { Text(stringResource(R.string.backup_excluded_dirs_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = locations, onValueChange = { locations = it },
                    label = { Text(stringResource(R.string.backup_locations_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nextBackupAt, onValueChange = { nextBackupAt = it },
                    label = { Text(stringResource(R.string.backup_next_at_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val request = try {
                    UpdateFileBackupRequest(
                        interval = interval.toIntOrNull() ?: -1,
                        keepBackupAmount = keep.toIntOrNull() ?: -1,
                        path = path.trim(),
                        deleteOnFail = deleteOnFail,
                        customName = customName.trim(),
                        localPath = localPath.trim(),
                        compression = compression.trim(),
                        excluded = excluded.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        locations = locations.trim(),
                        nextBackupAt = nextBackupAt.trim()
                    )
                } catch (invalidInput: IllegalArgumentException) {
                    invalid = true
                    null
                }
                if (request != null) {
                    invalid = false
                    onSave(request)
                }
            }, enabled = !busy && interval.isNotBlank() && keep.isNotBlank() && path.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

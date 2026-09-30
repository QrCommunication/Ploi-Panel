package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.pluralStringResource
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = PanelSpacing.sm), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            FilterChip(
                selected = section == 0, onClick = { section = 0 },
                label = { Text(stringResource(R.string.backups_databases_tab)) },
                leadingIcon = { Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = section == 1, onClick = { section = 1 },
                label = { Text(stringResource(R.string.backups_files_tab)) },
                leadingIcon = { Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { if (section == 0) {
            DatabaseBackupsScreen(token, serverId, lock, activity)
        } else {
            FileBackupsScreen(token, serverId, lock, activity)
        } }
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
    val runMessage = stringResource(R.string.backup_run_requested)
    val updatedMessage = stringResource(R.string.backup_updated)
    val deletedMessage = stringResource(R.string.backup_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        result = null
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
        feedback = ""
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

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                primaryLabel = stringResource(R.string.new_file_backup), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.backups.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(plainFromMarkdown(feedback)) }
        result?.let { data ->
            if (data.backups.isEmpty()) item { EmptyState(Icons.Outlined.FolderZip, stringResource(R.string.empty_file_backups)) }
            items(data.backups, key = { it.id }) { backup ->
                val title = backup.label.ifBlank {
                    backup.backupConfigurationLabel.ifBlank { backup.typeHuman.ifBlank { backup.type } }
                }
                val intervalMinutes = backup.intervalMinutes
                val interval = when {
                    intervalMinutes == 0 -> stringResource(R.string.backup_interval_nightly)
                    intervalMinutes != null ->
                        stringResource(R.string.backup_interval_minutes, intervalMinutes)
                    backup.intervalLabel.isNotBlank() -> backup.intervalLabel
                    else -> stringResource(R.string.backup_interval_unknown)
                }
                ResourceCard(
                    title = title, icon = Icons.Outlined.FolderZip,
                    subtitle = if (backup.active) null else stringResource(R.string.backup_inactive),
                    facts = listOfNotNull(
                        backup.siteDomain.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_site_label), it, mono = true) },
                        backup.path.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_path_label), it, mono = true) },
                        backup.typeHuman.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.server_type_label), it) },
                        ResourceFact(stringResource(R.string.g2_interval_label), interval),
                        ResourceFact(stringResource(R.string.g2_kept_label), backup.keepBackupAmount.toString()),
                        backup.lastBackupAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_last_run_label), it) },
                        backup.nextBackupAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_next_run_label), it) },
                        backup.createdAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_created_label), it) }
                    )
                ) {
                    CardAction(stringResource(R.string.run_backup), icon = Icons.Outlined.PlayArrow, enabled = !busy, onClick = {
                        runAction(runMessage) { PloiApi.runFileBackup(token, backup.id); "" }
                    })
                    CardAction(stringResource(R.string.edit_backup), icon = Icons.Outlined.Edit, enabled = !busy, onClick = { editing = backup })
                    CardAction(stringResource(R.string.backup_channels), icon = Icons.Outlined.Notifications, enabled = !busy, onClick = { channelsFor = backup })
                    DangerAction(stringResource(R.string.delete_backup), onClick = { confirmDelete = backup }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
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
            lock = lock, activity = activity,
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
    var configurationId by remember { mutableLongStateOf(0L) }
    var configurationLabel by remember { mutableStateOf("") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_file_backup)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PagedOptionPicker(
                    key = token to "configurations", label = stringResource(R.string.backup_configuration_label),
                    emptyLabel = stringResource(R.string.backup_no_configurations),
                    load = { page -> PloiApi.backupConfigurations(token, page = page, perPage = 50).let {
                        PickerOptions(it.configurations, it.currentPage, it.lastPage)
                    } }
                ) { configuration ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = configurationId == configuration.id,
                                onClick = {
                                    configurationId = configuration.id
                                    configurationLabel = configuration.label.ifBlank {
                                        configuration.humanType.ifBlank { configuration.type }
                                    }
                                }
                            )
                            Text(
                                configuration.label.ifBlank { configuration.humanType.ifBlank { configuration.type } },
                                Modifier.padding(top = 12.dp)
                            )
                        }
                }
                if (configurationId > 0L) Text(stringResource(R.string.picker_selected_configuration, configurationLabel))
                PagedOptionPicker(
                    key = token to serverId, label = stringResource(R.string.backup_sites_label),
                    emptyLabel = stringResource(R.string.backup_no_sites),
                    load = { page -> PloiApi.sites(token, serverId, page = page, perPage = 50).let {
                        PickerOptions(it.sites, it.currentPage, it.lastPage)
                    } }
                ) { site ->
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
                if (selectedSites.isNotEmpty()) Text(pluralStringResource(R.plurals.picker_selected_items,
                    selectedSites.size, selectedSites.size))
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

package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Daemons domain: paginated background processes of a server with create, restart,
 * toggle-pause and delete (PIN/biometric) actions — the six documented
 * /servers/{server}/daemons routes.
 */
@Composable
internal fun DaemonsScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<DaemonPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Daemon?>(null) }

    val createdMessage = stringResource(R.string.daemon_created)
    val restartedFallback = stringResource(R.string.daemon_restarted)
    val pauseToggledMessage = stringResource(R.string.daemon_pause_toggled)
    val deletedMessage = stringResource(R.string.daemon_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.daemons(token, serverId, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    fun runAction(message: String, block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                feedback = message
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
                Text(stringResource(R.string.new_daemon))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.daemons.isEmpty()) Text(stringResource(R.string.empty_daemons))
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
                items(data.daemons, key = { it.id }) { daemon ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(daemon.command, style = MaterialTheme.typography.titleMedium)
                            if (daemon.status.isNotBlank()) {
                                Text(stringResource(R.string.daemon_status, daemon.status))
                            }
                            Text(stringResource(R.string.daemon_processes, daemon.processes))
                            if (daemon.systemUser.isNotBlank()) {
                                Text(stringResource(R.string.daemon_user, daemon.systemUser))
                            }
                            if (daemon.directory.isNotBlank()) {
                                Text(stringResource(R.string.daemon_directory, daemon.directory))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        error = null
                                        scope.launch {
                                            try {
                                                val message = withContext(Dispatchers.IO) {
                                                    PloiApi.restartDaemon(token, serverId, daemon.id)
                                                }
                                                feedback = message.ifBlank { restartedFallback }
                                                refresh++
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                error = failure
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.restart_daemon)) }
                                OutlinedButton(
                                    onClick = {
                                        runAction(pauseToggledMessage) {
                                            PloiApi.togglePauseDaemon(token, serverId, daemon.id)
                                        }
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.toggle_pause_daemon)) }
                                OutlinedButton(onClick = { confirmDelete = daemon }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.delete_daemon),
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
        CreateDaemonDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createDaemon(token, serverId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { daemon ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_daemon, daemon.command),
            confirmLabel = R.string.delete_daemon,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteDaemon(token, serverId, daemon.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create dialog for POST /servers/{server}/daemons: command, system_user, processes (+ optional directory). */
@Composable
private fun CreateDaemonDialog(
    busy: Boolean,
    onCreate: (CreateDaemonRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var command by remember { mutableStateOf("") }
    var systemUser by remember { mutableStateOf("ploi") }
    var processes by remember { mutableStateOf("1") }
    var directory by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_daemon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text(stringResource(R.string.daemon_command_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemUser, onValueChange = { systemUser = it },
                    label = { Text(stringResource(R.string.daemon_user_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = processes, onValueChange = { processes = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.daemon_processes_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = directory, onValueChange = { directory = it },
                    label = { Text(stringResource(R.string.daemon_directory_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateDaemonRequest(
                            command = command.trim(),
                            systemUser = systemUser.trim(),
                            processes = processes.toIntOrNull() ?: 0,
                            directory = directory.trim()
                        )
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onCreate(request)
                    }
                },
                enabled = !busy && command.isNotBlank()
            ) { Text(stringResource(R.string.create_daemon_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

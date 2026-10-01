package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        result = null
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
        feedback = ""
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

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                primaryLabel = stringResource(R.string.new_daemon), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.daemons.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.daemons.isEmpty()) item { EmptyState(Icons.Outlined.Autorenew, stringResource(R.string.empty_daemons)) }
            items(data.daemons, key = { it.id }) { daemon ->
                ResourceCard(
                    title = daemon.command, monoTitle = true, icon = Icons.Outlined.Autorenew,
                    status = daemon.status,
                    facts = listOfNotNull(
                        ResourceFact(stringResource(R.string.daemon_processes_label), daemon.processes.toString()),
                        daemon.systemUser.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.daemon_user_label), it, mono = true) },
                        daemon.directory.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.daemon_directory_label), it, mono = true) }
                    )
                ) {
                    CardAction(stringResource(R.string.restart_daemon), icon = Icons.Outlined.RestartAlt, enabled = !busy, onClick = {
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
                    })
                    CardAction(stringResource(R.string.toggle_pause_daemon), icon = Icons.Outlined.PauseCircle, enabled = !busy, onClick = {
                        runAction(pauseToggledMessage) {
                            PloiApi.togglePauseDaemon(token, serverId, daemon.id)
                        }
                    })
                    DangerAction(stringResource(R.string.delete_daemon), onClick = { confirmDelete = daemon }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
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

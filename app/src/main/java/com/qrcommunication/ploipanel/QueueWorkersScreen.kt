package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Queue workers domain: Laravel queue workers of a site with lifecycle actions. */
@Composable
internal fun QueueWorkersScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<List<QueueWorker>?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<QueueWorker?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.queueWorkers(token, serverId, siteId) }
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
                primaryLabel = stringResource(R.string.new_queue_worker), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading && !busy) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { workers ->
            if (workers.isEmpty()) item { EmptyState(Icons.Outlined.Queue, stringResource(R.string.empty_queue_workers)) }
            items(workers, key = { it.id }) { worker ->
                ResourceCard(
                    title = worker.queue, monoTitle = true, icon = Icons.Outlined.Queue,
                    status = worker.status,
                    facts = listOfNotNull(
                        worker.connection.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.queue_connection_label), it, mono = true) },
                        ResourceFact(stringResource(R.string.queue_processes_label), worker.processes.toString())
                    )
                ) {
                    CardAction(stringResource(R.string.queue_worker_restart), icon = Icons.Outlined.RestartAlt, enabled = !busy, onClick = {
                        runAction(doneMessage) {
                            PloiApi.restartQueueWorker(token, serverId, siteId, worker.id)
                        }
                    })
                    CardAction(stringResource(R.string.queue_worker_toggle_pause), icon = Icons.Outlined.PauseCircle, enabled = !busy, onClick = {
                        runAction(doneMessage) {
                            PloiApi.togglePauseQueueWorker(token, serverId, siteId, worker.id)
                        }
                    })
                    DangerAction(stringResource(R.string.queue_worker_delete), onClick = { confirmDelete = worker }, enabled = !busy)
                }
            }
        }
    }

    if (creating) {
        CreateQueueWorkerDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(doneMessage) { PloiApi.createQueueWorker(token, serverId, siteId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { worker ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_queue_worker_delete, worker.queue),
            confirmLabel = R.string.queue_worker_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteQueueWorker(token, serverId, siteId, worker.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create dialog for POST …/queues with the documented required fields. */
@Composable
private fun CreateQueueWorkerDialog(
    busy: Boolean,
    onCreate: (CreateQueueWorkerRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var connection by remember { mutableStateOf("database") }
    var queue by remember { mutableStateOf("default") }
    var maximumSeconds by remember { mutableStateOf("30") }
    var sleep by remember { mutableStateOf("10") }
    var processes by remember { mutableStateOf("1") }
    var backoff by remember { mutableStateOf("10") }
    var maximumTries by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_queue_worker)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = connection, onValueChange = { connection = it },
                    label = { Text(stringResource(R.string.queue_connection_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = queue, onValueChange = { queue = it },
                    label = { Text(stringResource(R.string.queue_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maximumSeconds, onValueChange = { maximumSeconds = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.queue_max_seconds_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sleep, onValueChange = { sleep = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.queue_sleep_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = processes, onValueChange = { processes = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.queue_processes_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = backoff, onValueChange = { backoff = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.queue_backoff_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maximumTries, onValueChange = { maximumTries = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.queue_max_tries_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateQueueWorkerRequest(
                            connection.trim(), queue.trim(),
                            maximumSeconds.toInt(), sleep.toInt(), processes.toInt(), backoff.toInt(),
                            maximumTries.toIntOrNull()
                        )
                    } catch (invalidRequest: Exception) {
                        null
                    }
                    if (request == null) invalid = true else onCreate(request)
                },
                enabled = !busy && connection.isNotBlank() && queue.isNotBlank() &&
                    maximumSeconds.isNotBlank() && sleep.isNotBlank() && processes.isNotBlank() && backoff.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

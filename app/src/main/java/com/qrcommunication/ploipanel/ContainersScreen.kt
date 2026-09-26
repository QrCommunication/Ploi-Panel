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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Containers domain: Docker containers of a server with lifecycle, logs and site linking. */
@Composable
internal fun ContainersScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<ContainerPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ContainerRequest?>(null) }
    var editingId by remember { mutableStateOf<Long?>(null) } // null = create
    var logs by remember { mutableStateOf<Pair<String, String>?>(null) } // name to content
    var linkDialog by remember { mutableStateOf<DockerContainer?>(null) }
    var confirmDelete by remember { mutableStateOf<DockerContainer?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.containers(token, serverId, page) }
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
            OutlinedButton(onClick = { editingId = null; editing = ContainerRequest("", "version: '3'\nservices:\n") }, enabled = !busy) {
                Text(stringResource(R.string.new_container))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.containers.isEmpty()) Text(stringResource(R.string.empty_containers))
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
                items(data.containers, key = { it.id }) { container ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(container.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.container_state, container.status, container.state))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { runAction(doneMessage) { PloiApi.startContainer(token, serverId, container.id) } },
                                    enabled = !busy && container.state != "running"
                                ) { Text(stringResource(R.string.container_start)) }
                                OutlinedButton(
                                    onClick = { runAction(doneMessage) { PloiApi.stopContainer(token, serverId, container.id) } },
                                    enabled = !busy && container.state == "running"
                                ) { Text(stringResource(R.string.container_stop)) }
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        error = null
                                        scope.launch {
                                            try {
                                                val content = withContext(Dispatchers.IO) {
                                                    PloiApi.containerLogs(token, serverId, container.id)
                                                }
                                                logs = container.name to content
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
                                ) { Text(stringResource(R.string.container_logs)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        editingId = container.id
                                        editing = ContainerRequest(container.name, container.deployScript)
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.container_edit)) }
                                OutlinedButton(onClick = { linkDialog = container }, enabled = !busy) {
                                    Text(stringResource(R.string.container_link))
                                }
                                OutlinedButton(onClick = { confirmDelete = container }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.container_delete),
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

    editing?.let { initial ->
        ContainerDialog(
            initial = initial,
            isCreate = editingId == null,
            busy = busy,
            onSubmit = { request ->
                val targetId = editingId
                editing = null
                runAction(doneMessage) {
                    if (targetId == null) PloiApi.createContainer(token, serverId, request)
                    else PloiApi.updateContainer(token, serverId, targetId, request)
                }
            },
            onDismiss = { editing = null }
        )
    }

    logs?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { logs = null },
            title = { Text(stringResource(R.string.container_logs_title, name)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(content, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { logs = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    linkDialog?.let { container ->
        LinkContainerDialog(
            container = container,
            busy = busy,
            onLink = { siteId, port, host ->
                linkDialog = null
                runAction(doneMessage) { PloiApi.linkContainerSite(token, serverId, container.id, siteId, port, host) }
            },
            onUnlink = {
                linkDialog = null
                runAction(doneMessage) { PloiApi.unlinkContainerSite(token, serverId, container.id) }
            },
            onDismiss = { linkDialog = null }
        )
    }

    confirmDelete?.let { container ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_container_delete, container.name),
            confirmLabel = R.string.container_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteContainer(token, serverId, container.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun ContainerDialog(
    initial: ContainerRequest, isCreate: Boolean, busy: Boolean,
    onSubmit: (ContainerRequest) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var deployScript by remember { mutableStateOf(initial.deployScript) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isCreate) R.string.new_container else R.string.container_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.container_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deployScript, onValueChange = { deployScript = it },
                    label = { Text(stringResource(R.string.container_script_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        ContainerRequest(name.trim(), deployScript)
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) invalid = true else onSubmit(request)
                },
                enabled = !busy && name.isNotBlank() && deployScript.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun LinkContainerDialog(
    container: DockerContainer, busy: Boolean,
    onLink: (Long, Int, String) -> Unit, onUnlink: () -> Unit, onDismiss: () -> Unit
) {
    var siteId by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.container_link_title, container.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = siteId, onValueChange = { siteId = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.container_link_site)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.container_link_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text(stringResource(R.string.container_link_host)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val site = siteId.toLongOrNull()
                    val targetPort = port.toIntOrNull()
                    if (site == null || site <= 0 || targetPort == null || targetPort !in 1..65535) {
                        invalid = true
                    } else {
                        onLink(site, targetPort, host.trim())
                    }
                },
                enabled = !busy && siteId.isNotBlank() && port.isNotBlank()
            ) { Text(stringResource(R.string.container_link)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUnlink, enabled = !busy) {
                    Text(stringResource(R.string.container_unlink), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

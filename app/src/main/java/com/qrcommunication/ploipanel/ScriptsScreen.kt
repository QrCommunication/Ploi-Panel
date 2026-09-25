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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scripts domain: account-level reusable scripts with create, edit, run-on-servers
 * and PIN/biometric-protected delete — the six documented /api/scripts routes.
 */
@Composable
internal fun ScriptsScreen(token: String, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token) { mutableIntStateOf(1) }
    var refresh by remember(token) { mutableIntStateOf(0) }
    var result by remember(token) { mutableStateOf<ScriptPage?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token) { mutableStateOf("") }
    var busy by remember(token) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PloiScript?>(null) }
    var running by remember { mutableStateOf<PloiScript?>(null) }
    var confirmDelete by remember { mutableStateOf<PloiScript?>(null) }
    // Names of the servers a run was started on, shown until the next action.
    var startedOn by remember { mutableStateOf<List<String>?>(null) }

    val createdMessage = stringResource(R.string.script_created)
    val updatedMessage = stringResource(R.string.script_updated)
    val deletedMessage = stringResource(R.string.script_deleted)

    LaunchedEffect(token, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.scripts(token, page) }
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
        startedOn = null
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
                Text(stringResource(R.string.new_script))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        startedOn?.let { names ->
            if (names.isNotEmpty()) Text(stringResource(R.string.script_started_on, names.joinToString(", ")))
        }
        result?.let { data ->
            if (data.scripts.isEmpty()) Text(stringResource(R.string.empty_scripts))
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
                items(data.scripts, key = { it.id }) { script ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(script.label, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.script_run_as, script.user))
                            if (script.createdAt.isNotBlank()) {
                                Text(stringResource(R.string.system_user_created_at, script.createdAt))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { running = script }, enabled = !busy) {
                                    Text(stringResource(R.string.run_script))
                                }
                                OutlinedButton(onClick = { editing = script }, enabled = !busy) {
                                    Text(stringResource(R.string.edit_site))
                                }
                                OutlinedButton(onClick = { confirmDelete = script }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.delete_script),
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
        ScriptFormDialog(
            busy = busy,
            title = stringResource(R.string.new_script),
            submitLabel = stringResource(R.string.create_script_submit),
            initial = null,
            build = { label, user, content -> CreateScriptRequest(label = label, user = user, content = content) },
            onSubmit = { request ->
                creating = false
                runAction(createdMessage) {
                    PloiApi.createScript(token, request)
                }
            },
            onDismiss = { creating = false }
        )
    }
    editing?.let { script ->
        ScriptFormDialog(
            busy = busy,
            title = stringResource(R.string.edit_script_title, script.label),
            submitLabel = stringResource(R.string.edit_site),
            initial = script,
            build = { label, user, content -> UpdateScriptRequest(label = label, user = user, content = content) },
            onSubmit = { request ->
                editing = null
                runAction(updatedMessage) { PloiApi.updateScript(token, script.id, request) }
            },
            onDismiss = { editing = null }
        )
    }
    running?.let { script ->
        RunScriptDialog(
            token = token,
            script = script,
            busy = busy,
            onRun = { serverIds ->
                running = null
                busy = true
                error = null
                feedback = ""
                startedOn = null
                scope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) { PloiApi.runScript(token, script.id, serverIds) }
                        startedOn = started.map { it.name }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { running = null }
        )
    }
    confirmDelete?.let { script ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_script, script.label),
            confirmLabel = R.string.delete_script,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteScript(token, script.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Shared create/edit form: label, run-as user and content (documented attributes). */
@Composable
private fun <T> ScriptFormDialog(
    busy: Boolean,
    title: String,
    submitLabel: String,
    initial: PloiScript?,
    build: (label: String, user: String, content: String) -> T,
    onSubmit: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user ?: "ploi") }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.script_label_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.script_user_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text(stringResource(R.string.script_content_label)) },
                    modifier = Modifier.fillMaxWidth()
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
                        build(label.trim(), user.trim(), content)
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onSubmit(request)
                    }
                },
                enabled = !busy && label.isNotBlank() && user.isNotBlank() && content.isNotBlank()
            ) { Text(submitLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Run dialog for POST /api/scripts/{script}/run: checkbox selection of the account's servers. */
@Composable
private fun RunScriptDialog(
    token: String,
    script: PloiScript,
    busy: Boolean,
    onRun: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var servers by remember { mutableStateOf<List<Server>?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(token) {
        try {
            val first = withContext(Dispatchers.IO) { PloiApi.servers(token, perPage = 50) }
            val all = first.servers.toMutableList()
            var nextPage = 2
            while (first.hasNext && nextPage <= first.lastPage) {
                val extra = withContext(Dispatchers.IO) { PloiApi.servers(token, page = nextPage, perPage = 50) }
                all.addAll(extra.servers)
                nextPage++
            }
            servers = all
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_script_title, script.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loadError != null -> ApiErrorText(loadError!!)
                    servers == null -> CircularProgressIndicator()
                    servers!!.isEmpty() -> Text(stringResource(R.string.empty_run_servers))
                    else -> servers!!.forEach { server ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = server.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + server.id else selected - server.id
                                }
                            )
                            Text(server.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRun(selected.toList()) },
                enabled = !busy && selected.isNotEmpty()
            ) { Text(stringResource(R.string.run_script_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * One-off script run on a single server: POST /servers/{server}/scripts/run plus
 * manual polling of GET /servers/{server}/scripts/run/{execution}.
 */
@Composable
internal fun OneOffScriptScreen(token: String, serverId: Long) {
    val scope = rememberCoroutineScope()
    var content by remember(token, serverId) { mutableStateOf("") }
    var user by remember(token, serverId) { mutableStateOf("ploi") }
    var execution by remember(token, serverId) { mutableStateOf<ScriptExecution?>(null) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var invalid by remember(token, serverId) { mutableStateOf(false) }

    fun launchAction(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
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
        Text(stringResource(R.string.one_off_script_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text(stringResource(R.string.script_user_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = content, onValueChange = { content = it },
            label = { Text(stringResource(R.string.one_off_script_content_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (invalid) {
            Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                if (content.isBlank() || content.length > ONE_OFF_SCRIPT_MAX_LENGTH || user.isBlank()) {
                    invalid = true
                } else {
                    invalid = false
                    launchAction { execution = PloiApi.runOneOffScript(token, serverId, content, user.trim()) }
                }
            },
            enabled = !busy && content.isNotBlank()
        ) { Text(stringResource(R.string.run_script_submit)) }
        if (error != null) ApiErrorText(error!!)
        if (busy) CircularProgressIndicator()
        execution?.let { exec ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.execution_status, exec.status))
                    exec.exitCode?.let { code -> Text(stringResource(R.string.execution_exit_code, code)) }
                    if (exec.startedAt.isNotBlank()) {
                        Text(stringResource(R.string.execution_started_at, exec.startedAt))
                    }
                    if (exec.finishedAt.isNotBlank()) {
                        Text(stringResource(R.string.execution_finished_at, exec.finishedAt))
                    }
                    if (exec.output.isNotBlank()) {
                        Text(stringResource(R.string.execution_output), style = MaterialTheme.typography.titleSmall)
                        Text(exec.output)
                    }
                    OutlinedButton(
                        onClick = { launchAction { execution = PloiApi.scriptExecution(token, serverId, exec.id) } },
                        enabled = !busy
                    ) { Text(stringResource(R.string.refresh_execution)) }
                }
            }
        }
    }
}

package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scripts domain: account-level reusable scripts with create, edit, run-on-servers,
 * PIN/biometric-protected delete and cron schedules (Pro plan) — the documented
 * /api/scripts and /api/scripts/{script}/schedules routes.
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
    var pendingRun by remember { mutableStateOf<Pair<PloiScript, List<Long>>?>(null) }
    var scheduling by remember { mutableStateOf<PloiScript?>(null) }
    var actioning by remember { mutableStateOf<PloiScript?>(null) }
    var confirmDelete by remember { mutableStateOf<PloiScript?>(null) }
    // Names of the servers a run was started on, shown until the next action.
    var startedOn by remember { mutableStateOf<List<String>?>(null) }

    val createdMessage = stringResource(R.string.script_created)
    val updatedMessage = stringResource(R.string.script_updated)
    val deletedMessage = stringResource(R.string.script_deleted)

    LaunchedEffect(token, page, refresh) {
        loading = true
        result = null
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

    fun runAction(message: String, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        busy = true
        error = null
        feedback = ""
        startedOn = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                feedback = message
                refresh++
                onSuccess()
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
                primaryLabel = stringResource(R.string.new_script), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.scripts.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading && !busy) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        startedOn?.let { names ->
            if (names.isNotEmpty()) item { SuccessBanner(stringResource(R.string.script_started_on, names.joinToString(", "))) }
        }
        result?.let { data ->
            if (data.scripts.isEmpty()) item { EmptyState(Icons.Outlined.Terminal, stringResource(R.string.empty_scripts)) }
            items(data.scripts, key = { it.id }) { script ->
                ResourceCard(
                    title = script.label, icon = Icons.Outlined.Terminal,
                    facts = listOfNotNull(
                        ResourceFact(stringResource(R.string.g4_run_as_label), script.user, mono = true),
                        script.createdAt.takeIf { it.isNotBlank() }?.let {
                            ResourceFact(stringResource(R.string.g4_created_label), it)
                        }
                    )
                ) {
                    CardAction(stringResource(R.string.run_script), icon = Icons.Outlined.PlayArrow, enabled = !busy,
                        onClick = { running = script })
                    CardAction(stringResource(R.string.script_schedules), icon = Icons.Outlined.Schedule, enabled = !busy,
                        onClick = { scheduling = script })
                    CardAction(stringResource(R.string.script_actions), icon = Icons.Outlined.Bolt, enabled = !busy,
                        onClick = { actioning = script })
                    CardAction(stringResource(R.string.edit_site), icon = Icons.Outlined.Edit, enabled = !busy,
                        onClick = { editing = script })
                    DangerAction(stringResource(R.string.delete_script), onClick = { confirmDelete = script }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
            }
        }
    }

    if (creating) {
        ScriptFormDialog(
            busy = busy,
            title = stringResource(R.string.new_script),
            submitLabel = stringResource(R.string.create_script_submit),
            initial = null,
            submitError = error,
            build = { label, user, content -> CreateScriptRequest(label = label, user = user, content = content) },
            onSubmit = { request ->
                runAction(createdMessage, onSuccess = { creating = false }) {
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
            submitError = error,
            build = { label, user, content -> UpdateScriptRequest(label = label, user = user, content = content) },
            onSubmit = { request ->
                runAction(updatedMessage, onSuccess = { editing = null }) {
                    PloiApi.updateScript(token, script.id, request)
                }
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
                pendingRun = script to serverIds
            },
            onDismiss = { running = null }
        )
    }
    pendingRun?.let { (script, serverIds) ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = pluralStringResource(R.plurals.confirm_run_script, serverIds.size, script.label, serverIds.size),
            confirmLabel = R.string.run_script_submit,
            onConfirmed = {
                pendingRun = null
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
            onDismiss = { pendingRun = null }
        )
    }
    scheduling?.let { script ->
        ScriptSchedulesDialog(
            token = token,
            script = script,
            lock = lock,
            activity = activity,
            onDismiss = { scheduling = null }
        )
    }
    actioning?.let { script ->
        ScriptActionsDialog(
            token = token,
            script = script,
            lock = lock,
            activity = activity,
            onDismiss = { actioning = null }
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
    submitError: Throwable?,
    build: (label: String, user: String, content: String) -> T,
    onSubmit: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user ?: "ploi") }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    textStyle = panelMonoStyle,
                    minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
                if (submitError != null) ApiErrorText(submitError)
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
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Shared server multi-picker: loads every account server (page size 50) and renders checkboxes. */
@Composable
private fun ServerMultiSelect(
    token: String,
    selected: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit
) {
    var servers by remember { mutableStateOf<List<Server>?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
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
    when {
        loadError != null -> ApiErrorText(loadError!!)
        servers == null -> BusyIndicator()
        servers!!.isEmpty() -> Text(stringResource(R.string.empty_run_servers))
        else -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(servers!!, key = { it.id }) { server ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = server.id in selected,
                        onCheckedChange = { checked ->
                            onSelectionChange(if (checked) selected + server.id else selected - server.id)
                        }
                    )
                    Text(server.name)
                }
            }
        }
    }
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
    var selected by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_script_title, script.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerMultiSelect(token, selected) { selected = it }
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
 * Script-schedules domain (Pro plan or higher): list/create/edit/pause/delete the
 * cron schedules of one script — the six documented /api/scripts/{script}/schedules routes.
 */
@Composable
private fun ScriptSchedulesDialog(
    token: String,
    script: PloiScript,
    lock: AppLock,
    activity: FragmentActivity,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ScriptSchedulePage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScriptSchedule?>(null) }
    var confirmDelete by remember { mutableStateOf<ScriptSchedule?>(null) }

    val createdMessage = stringResource(R.string.schedule_created)
    val updatedMessage = stringResource(R.string.schedule_updated)
    val deletedMessage = stringResource(R.string.schedule_deleted)
    val pausedMessage = stringResource(R.string.schedule_paused_feedback)
    val resumedMessage = stringResource(R.string.schedule_resumed_feedback)

    LaunchedEffect(token, script.id, page, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.scriptSchedules(token, script.id, page) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedules_title, script.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                Text(
                    stringResource(R.string.schedule_pro_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ListToolbar(
                    onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                    primaryLabel = stringResource(R.string.new_schedule), onPrimary = { creating = true }, primaryEnabled = !busy
                )
                if (loading) BusyIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) SuccessBanner(feedback)
                result?.let { data ->
                    if (data.schedules.isEmpty()) Text(
                        stringResource(R.string.empty_schedules), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (data.lastPage > 1) {
                        PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
                    }
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
                    ) {
                        items(data.schedules, key = { it.id }) { schedule ->
                            ResourceCard(
                                title = schedule.cronExpression, monoTitle = true, icon = Icons.Outlined.Schedule,
                                facts = listOfNotNull(
                                    ResourceFact(
                                        stringResource(R.string.g4_state_label),
                                        stringResource(if (schedule.isPaused) R.string.schedule_paused else R.string.schedule_active)
                                    ),
                                    ResourceFact(stringResource(R.string.g4_servers_label), schedule.servers.size.toString()),
                                    schedule.nextRunAt.takeIf { it.isNotBlank() }?.let {
                                        ResourceFact(stringResource(R.string.g4_next_run_label), it)
                                    },
                                    schedule.lastRunAt.takeIf { it.isNotBlank() }?.let {
                                        ResourceFact(stringResource(R.string.g4_last_run_label), it)
                                    }
                                )
                            ) {
                                CardAction(
                                    stringResource(if (schedule.isPaused) R.string.resume_schedule else R.string.pause_schedule),
                                    icon = if (schedule.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        error = null
                                        feedback = ""
                                        scope.launch {
                                            try {
                                                val toggled = withContext(Dispatchers.IO) {
                                                    PloiApi.toggleScriptSchedule(token, script.id, schedule.id)
                                                }
                                                feedback = if (toggled.isPaused) pausedMessage else resumedMessage
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
                                )
                                CardAction(stringResource(R.string.edit_site), icon = Icons.Outlined.Edit, enabled = !busy,
                                    onClick = { editing = schedule })
                                DangerAction(stringResource(R.string.delete_schedule), onClick = { confirmDelete = schedule },
                                    enabled = !busy)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )

    if (creating) {
        ScheduleFormDialog(
            token = token,
            busy = busy,
            title = stringResource(R.string.new_schedule),
            submitLabel = stringResource(R.string.create_schedule_submit),
            initial = null,
            build = { cron, servers -> CreateScriptScheduleRequest(cronExpression = cron, servers = servers) },
            onSubmit = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createScriptSchedule(token, script.id, request) }
            },
            onDismiss = { creating = false }
        )
    }
    editing?.let { schedule ->
        ScheduleFormDialog(
            token = token,
            busy = busy,
            title = stringResource(R.string.edit_schedule_title),
            submitLabel = stringResource(R.string.edit_site),
            initial = schedule,
            build = { cron, servers -> UpdateScriptScheduleRequest(cronExpression = cron, servers = servers) },
            onSubmit = { request ->
                editing = null
                runAction(updatedMessage) {
                    PloiApi.updateScriptSchedule(token, script.id, schedule.id, request)
                }
            },
            onDismiss = { editing = null }
        )
    }
    confirmDelete?.let { schedule ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_schedule, schedule.cronExpression),
            confirmLabel = R.string.delete_schedule,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteScriptSchedule(token, script.id, schedule.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Shared create/edit schedule form: cron expression + documented server ID selection. */
@Composable
private fun <T> ScheduleFormDialog(
    token: String,
    busy: Boolean,
    title: String,
    submitLabel: String,
    initial: ScriptSchedule?,
    build: (cron: String, servers: List<Long>) -> T,
    onSubmit: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var cron by remember { mutableStateOf(initial?.cronExpression.orEmpty()) }
    var selected by remember { mutableStateOf(initial?.servers?.toSet() ?: emptySet()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cron, onValueChange = { cron = it },
                    label = { Text(stringResource(R.string.schedule_cron_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.schedule_servers_label))
                ServerMultiSelect(token, selected) { selected = it }
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        build(cron.trim(), selected.toList())
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onSubmit(request)
                    }
                },
                enabled = !busy && cron.isNotBlank() && selected.isNotEmpty()
            ) { Text(submitLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Localized label for the four documented per-server install statuses. */
private fun actionServerStatusLabel(status: String): Int = when (status) {
    "installed" -> R.string.action_status_installed
    "failed" -> R.string.action_status_failed
    "uninstalling" -> R.string.action_status_uninstalling
    else -> R.string.action_status_pending
}

/**
 * Script-actions domain (Unlimited plan): list/create/edit/pause/rotate-secret/delete
 * the event-driven actions of one script — the seven documented
 * /api/scripts/{script}/actions routes. Delete and secret rotation are protected
 * by PIN/biometric because they tear down units or invalidate signed webhook URLs.
 */
@Composable
private fun ScriptActionsDialog(
    token: String,
    script: PloiScript,
    lock: AppLock,
    activity: FragmentActivity,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<List<ScriptAction>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScriptAction?>(null) }
    var confirmRotate by remember { mutableStateOf<ScriptAction?>(null) }
    var confirmDelete by remember { mutableStateOf<ScriptAction?>(null) }

    val createdMessage = stringResource(R.string.action_created)
    val updatedMessage = stringResource(R.string.action_updated)
    val deletedMessage = stringResource(R.string.action_deleted)
    val rotatedMessage = stringResource(R.string.action_secret_rotated)
    val pausedMessage = stringResource(R.string.action_paused_feedback)
    val resumedMessage = stringResource(R.string.action_resumed_feedback)

    LaunchedEffect(token, script.id, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.scriptActions(token, script.id) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.actions_title, script.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                Text(
                    stringResource(R.string.action_unlimited_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ListToolbar(
                    onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                    primaryLabel = stringResource(R.string.new_action), onPrimary = { creating = true }, primaryEnabled = !busy
                )
                if (loading) BusyIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) SuccessBanner(feedback)
                result?.let { actions ->
                    if (actions.isEmpty()) Text(
                        stringResource(R.string.empty_actions), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
                    ) {
                        items(actions, key = { it.id }) { action ->
                            val serverFacts = action.servers.map { server ->
                                ResourceFact(
                                    stringResource(R.string.g4_server_number, server.serverId),
                                    stringResource(actionServerStatusLabel(server.status))
                                )
                            }
                            ResourceCard(
                                title = action.triggerLabel.ifBlank { action.trigger }, icon = Icons.Outlined.Bolt,
                                facts = listOfNotNull(
                                    ResourceFact(
                                        stringResource(R.string.g4_state_label),
                                        stringResource(if (action.isPaused) R.string.schedule_paused else R.string.schedule_active)
                                    ),
                                    ResourceFact(stringResource(R.string.g4_delay_label), stringResource(R.string.g4_seconds_value, action.delaySeconds)),
                                    action.lastTriggeredAt.takeIf { it.isNotBlank() }?.let {
                                        ResourceFact(stringResource(R.string.g4_last_triggered_label), it)
                                    }
                                ) + serverFacts
                            ) {
                                // Per-server install errors: worded and error-coloured, each on its own line.
                                action.servers.filter { it.lastError.isNotBlank() }.forEach { server ->
                                    Text(
                                        stringResource(R.string.g4_server_error, server.serverId, server.lastError),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                CardAction(
                                    stringResource(if (action.isPaused) R.string.resume_schedule else R.string.pause_schedule),
                                    icon = if (action.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        error = null
                                        feedback = ""
                                        scope.launch {
                                            try {
                                                val toggled = withContext(Dispatchers.IO) {
                                                    PloiApi.toggleScriptAction(token, script.id, action.id)
                                                }
                                                feedback = if (toggled.isPaused) pausedMessage else resumedMessage
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
                                )
                                CardAction(stringResource(R.string.edit_site), icon = Icons.Outlined.Edit, enabled = !busy,
                                    onClick = { editing = action })
                                CardAction(stringResource(R.string.rotate_action_secret), icon = Icons.Outlined.Key, enabled = !busy,
                                    onClick = { confirmRotate = action })
                                DangerAction(stringResource(R.string.delete_action), onClick = { confirmDelete = action },
                                    enabled = !busy)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )

    if (creating) {
        ActionFormDialog(
            token = token,
            busy = busy,
            title = stringResource(R.string.new_action),
            submitLabel = stringResource(R.string.create_action_submit),
            initial = null,
            build = { trigger, delay, servers ->
                CreateScriptActionRequest(trigger = trigger, servers = servers, delaySeconds = delay)
            },
            onSubmit = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createScriptAction(token, script.id, request) }
            },
            onDismiss = { creating = false }
        )
    }
    editing?.let { action ->
        ActionFormDialog(
            token = token,
            busy = busy,
            title = stringResource(R.string.edit_action_title),
            submitLabel = stringResource(R.string.edit_site),
            initial = action,
            build = { trigger, delay, servers ->
                UpdateScriptActionRequest(trigger = trigger, servers = servers, delaySeconds = delay)
            },
            onSubmit = { request ->
                editing = null
                runAction(updatedMessage) {
                    PloiApi.updateScriptAction(token, script.id, action.id, request)
                }
            },
            onDismiss = { editing = null }
        )
    }
    confirmRotate?.let { action ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_rotate_action_secret),
            confirmLabel = R.string.rotate_action_secret,
            onConfirmed = {
                confirmRotate = null
                runAction(rotatedMessage) { PloiApi.rotateScriptActionSecret(token, script.id, action.id) }
            },
            onDismiss = { confirmRotate = null }
        )
    }
    confirmDelete?.let { action ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_action),
            confirmLabel = R.string.delete_action,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteScriptAction(token, script.id, action.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/**
 * Shared create/edit action form: the two documented triggers (radio), the
 * optional delay in seconds (0–7200, blank keeps the documented default) and
 * the documented server ID selection.
 */
@Composable
private fun <T> ActionFormDialog(
    token: String,
    busy: Boolean,
    title: String,
    submitLabel: String,
    initial: ScriptAction?,
    build: (trigger: String, delaySeconds: Int?, servers: List<Long>) -> T,
    onSubmit: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var trigger by remember { mutableStateOf(initial?.trigger ?: "server.booted") }
    var delay by remember { mutableStateOf(initial?.delaySeconds?.toString().orEmpty()) }
    var selected by remember { mutableStateOf(initial?.servers?.map { it.serverId }?.toSet() ?: emptySet()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.action_trigger_label))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = trigger == "server.booted", onClick = { trigger = "server.booted" })
                    Text(stringResource(R.string.action_trigger_booted))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = trigger == "server.shutdown", onClick = { trigger = "server.shutdown" })
                    Text(stringResource(R.string.action_trigger_shutdown))
                }
                OutlinedTextField(
                    value = delay, onValueChange = { delay = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.action_delay_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.schedule_servers_label))
                ServerMultiSelect(token, selected) { selected = it }
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        build(trigger, delay.toIntOrNull(), selected.toList())
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onSubmit(request)
                    }
                },
                enabled = !busy && selected.isNotEmpty()
            ) { Text(submitLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

internal fun shouldPollExecution(execution: ScriptExecution?): Boolean =
    execution != null && execution.finishedAt.isBlank() &&
        execution.status.lowercase(java.util.Locale.ROOT) !in setOf("completed", "finished", "failed", "cancelled", "canceled")

/**
 * One-off script run on a single server: POST /servers/{server}/scripts/run plus
 * bounded best-effort polling of GET /servers/{server}/scripts/run/{execution}.
 */
@Composable
internal fun OneOffScriptScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var content by remember(token, serverId) { mutableStateOf("") }
    var user by remember(token, serverId) { mutableStateOf("ploi") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var polling by remember(token, serverId) { mutableStateOf(false) }
    var pendingRun by remember(token, serverId) { mutableStateOf<Pair<String, String>?>(null) }
    var execution by remember(token, serverId) { mutableStateOf<ScriptExecution?>(null) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var invalid by remember(token, serverId) { mutableStateOf(false) }

    fun launchAction(block: suspend () -> ScriptExecution) {
        busy = true
        error = null
        scope.launch {
            try {
                execution = withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(token, serverId, execution?.id) {
        val id = execution?.id ?: return@LaunchedEffect
        if (!shouldPollExecution(execution)) return@LaunchedEffect
        polling = true
        try {
            repeat(8) {
                kotlinx.coroutines.delay(5_000)
                val latest = withContext(Dispatchers.IO) { PloiApi.scriptExecution(token, serverId, id) }
                execution = latest
                if (!shouldPollExecution(latest)) return@LaunchedEffect
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        } finally {
            polling = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = PanelSpacing.maxContentWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
        ) {
            SectionCard(title = stringResource(R.string.one_off_script_title), icon = Icons.Outlined.Terminal) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.script_user_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text(stringResource(R.string.one_off_script_content_label)) },
                    textStyle = panelMonoStyle,
                    minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth()
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
                            pendingRun = content to user.trim()
                        }
                    },
                    enabled = !busy && !polling && content.isNotBlank()
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.run_script_submit), Modifier.padding(start = PanelSpacing.sm))
                }
            }
            if (error != null) ErrorState(error!!)
            if (busy) LoadingState(rows = 1)
            if (polling) Text(
                stringResource(R.string.execution_following), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!polling && shouldPollExecution(execution)) Text(
                stringResource(R.string.execution_manual_refresh), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            execution?.let { exec ->
                ResourceCard(
                    title = stringResource(R.string.g4_execution_title), icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    status = exec.status,
                    facts = listOfNotNull(
                        exec.exitCode?.let { code -> ResourceFact(stringResource(R.string.g4_exit_code_label), code.toString(), mono = true) },
                        exec.startedAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g4_started_label), it) },
                        exec.finishedAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g4_finished_label), it) }
                    )
                ) {
                    if (exec.output.isNotBlank()) ExpandableMono(exec.output, collapsedLines = 8)
                    CardAction(
                        stringResource(R.string.refresh_execution), icon = Icons.Outlined.Refresh,
                        onClick = { launchAction { PloiApi.scriptExecution(token, serverId, exec.id) } },
                        enabled = !busy && !polling
                    )
                }
            }
        }
    }
    pendingRun?.let { (script, scriptUser) ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_one_off_script),
            confirmLabel = R.string.run_script_submit,
            onConfirmed = {
                pendingRun = null
                execution = null
                launchAction { PloiApi.runOneOffScript(token, serverId, script, scriptUser) }
            },
            onDismiss = { pendingRun = null }
        )
    }
}

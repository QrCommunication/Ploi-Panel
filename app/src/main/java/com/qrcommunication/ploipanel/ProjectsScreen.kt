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

/** Projects domain: account-level projects grouping servers and sites. */
@Composable
internal fun ProjectsScreen(token: String, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token) { mutableIntStateOf(1) }
    var refresh by remember(token) { mutableIntStateOf(0) }
    var result by remember(token) { mutableStateOf<ProjectPage?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token) { mutableStateOf("") }
    var busy by remember(token) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PloiProject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<PloiProject?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.projects(token, page) }
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
                Text(stringResource(R.string.new_project))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.projects.isEmpty()) Text(stringResource(R.string.empty_projects))
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
                items(data.projects, key = { it.id }) { project ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.project_attachments,
                                    project.serverIds.size.toString(),
                                    project.sites.size.toString()
                                )
                            )
                            if (project.createdAt.isNotBlank()) {
                                Text(stringResource(R.string.detail_created, project.createdAt))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editing = project }, enabled = !busy) {
                                    Text(stringResource(R.string.project_edit))
                                }
                                OutlinedButton(onClick = { confirmDelete = project }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.project_delete),
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

    if (creating || editing != null) {
        ProjectDialog(
            initial = editing,
            busy = busy,
            onSubmit = { request ->
                val target = editing
                creating = false
                editing = null
                runAction(doneMessage) {
                    if (target == null) PloiApi.createProject(token, request)
                    else PloiApi.updateProject(token, target.id, request)
                }
            },
            onDismiss = { creating = false; editing = null }
        )
    }
    confirmDelete?.let { project ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_project_delete, project.title),
            confirmLabel = R.string.project_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteProject(token, project.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create/edit dialog of a project: title plus comma-separated server and site ids. */
@Composable
private fun ProjectDialog(
    initial: PloiProject?, busy: Boolean,
    onSubmit: (ProjectRequest) -> Unit, onDismiss: () -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var servers by remember(initial?.id) {
        mutableStateOf(initial?.serverIds?.joinToString(",").orEmpty())
    }
    var sites by remember(initial?.id) {
        mutableStateOf(initial?.sites?.joinToString(",") { it.id.toString() }.orEmpty())
    }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_project else R.string.project_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.project_title_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = servers, onValueChange = { servers = it.filter { c -> c.isDigit() || c == ',' || c == ' ' } },
                    label = { Text(stringResource(R.string.project_servers_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sites, onValueChange = { sites = it.filter { c -> c.isDigit() || c == ',' || c == ' ' } },
                    label = { Text(stringResource(R.string.project_sites_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    fun parseIds(raw: String): List<Long> =
                        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toLong() }
                    val request = try {
                        ProjectRequest(title.trim(), parseIds(servers), parseIds(sites))
                    } catch (invalidRequest: Exception) {
                        null
                    }
                    if (request == null) invalid = true else onSubmit(request)
                },
                enabled = !busy && title.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

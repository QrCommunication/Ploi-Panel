package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Status pages domain: account-level status pages with their incidents — the five
 * documented /api/status-pages routes. Incident deletion is protected by
 * PIN/biometric because it rewrites the public incident history.
 */
@Composable
internal fun StatusPagesScreen(token: String, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token) { mutableIntStateOf(1) }
    var refresh by remember(token) { mutableIntStateOf(0) }
    var result by remember(token) { mutableStateOf<StatusPagePage?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }
    var viewing by remember { mutableStateOf<StatusPage?>(null) }

    LaunchedEffect(token, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.statusPages(token, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading) {
                Text(stringResource(R.string.reload))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        result?.let { data ->
            if (data.statusPages.isEmpty()) Text(stringResource(R.string.empty_status_pages))
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
                items(data.statusPages, key = { it.id }) { statusPage ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(statusPage.name, style = MaterialTheme.typography.titleMedium)
                            if (statusPage.slug.isNotBlank()) {
                                Text(stringResource(R.string.status_page_slug, statusPage.slug))
                            }
                            if (statusPage.description.isNotBlank()) Text(statusPage.description)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewing = statusPage }) {
                                    Text(stringResource(R.string.status_page_incidents))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { statusPage ->
        StatusPageIncidentsDialog(
            token = token,
            statusPage = statusPage,
            lock = lock,
            activity = activity,
            onDismiss = { viewing = null }
        )
    }
}

/** Localized label for the four documented incident severities. */
private fun incidentSeverityLabel(severity: String): Int = when (severity) {
    "high" -> R.string.severity_high
    "maintenance" -> R.string.severity_maintenance
    "resolved" -> R.string.severity_resolved
    else -> R.string.severity_normal
}

/**
 * Paginated incidents of one status page (latest first, per the documentation),
 * with validated create and PIN/biometric-protected delete.
 */
@Composable
private fun StatusPageIncidentsDialog(
    token: String,
    statusPage: StatusPage,
    lock: AppLock,
    activity: FragmentActivity,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<StatusPageIncidentPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<StatusPageIncident?>(null) }

    val createdMessage = stringResource(R.string.incident_created)
    val deletedMessage = stringResource(R.string.incident_deleted)

    LaunchedEffect(token, statusPage.id, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.statusPageIncidents(token, statusPage.id, page) }
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
        title = { Text(stringResource(R.string.incidents_title, statusPage.name)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                        Text(stringResource(R.string.reload))
                    }
                    OutlinedButton(onClick = { creating = true }, enabled = !busy) {
                        Text(stringResource(R.string.new_incident))
                    }
                }
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) Text(feedback)
                result?.let { data ->
                    if (data.incidents.isEmpty()) Text(stringResource(R.string.empty_incidents))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { page-- }, enabled = page > 1 && !busy) {
                            Text(stringResource(R.string.previous))
                        }
                        Text(
                            stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()),
                            Modifier.padding(top = 12.dp)
                        )
                        OutlinedButton(onClick = { page++ }, enabled = data.hasNext && !busy) {
                            Text(stringResource(R.string.next))
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.incidents, key = { it.id }) { incident ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(incident.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(
                                            R.string.incident_severity_value,
                                            stringResource(incidentSeverityLabel(incident.severity))
                                        )
                                    )
                                    if (incident.description.isNotBlank()) Text(incident.description)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { confirmDelete = incident }, enabled = !busy) {
                                            Text(
                                                stringResource(R.string.delete_incident),
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
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )

    if (creating) {
        IncidentFormDialog(
            busy = busy,
            onSubmit = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createStatusPageIncident(token, statusPage.id, request) }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { incident ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_incident, incident.title),
            confirmLabel = R.string.delete_incident,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteStatusPageIncident(token, statusPage.id, incident.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create form: documented required title, optional description and severity. */
@Composable
private fun IncidentFormDialog(
    busy: Boolean,
    onSubmit: (CreateStatusPageIncidentRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("normal") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_incident)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.incident_title_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.incident_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.incident_severity_label))
                STATUS_PAGE_INCIDENT_SEVERITIES.toList().forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = severity == option, onClick = { severity = option })
                        Text(stringResource(incidentSeverityLabel(option)))
                    }
                }
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateStatusPageIncidentRequest(
                            title = title, description = description, severity = severity
                        )
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onSubmit(request)
                    }
                },
                enabled = !busy && title.isNotBlank()
            ) { Text(stringResource(R.string.create_incident_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

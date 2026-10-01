package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
        result = null
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

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading,
                summary = result?.let { stringResource(R.string.items_count, it.statusPages.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        result?.let { data ->
            if (data.statusPages.isEmpty()) item { EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.empty_status_pages)) }
            items(data.statusPages, key = { it.id }) { statusPage ->
                ResourceCard(
                    title = statusPage.name, icon = Icons.Outlined.MonitorHeart,
                    subtitle = statusPage.description.takeIf { it.isNotBlank() }?.let { plainFromMarkdown(it) },
                    facts = listOfNotNull(
                        statusPage.slug.takeIf { it.isNotBlank() }?.let {
                            ResourceFact(stringResource(R.string.g4_slug_label), it, mono = true)
                        }
                    )
                ) {
                    CardAction(stringResource(R.string.status_page_incidents), icon = Icons.Outlined.ReportProblem,
                        onClick = { viewing = statusPage })
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
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
        result = null
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

    fun runAction(message: String, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        busy = true
        error = null
        feedback = ""
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.incidents_title, statusPage.name)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
            ) {
                ListToolbar(
                    onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                    primaryLabel = stringResource(R.string.new_incident), onPrimary = { creating = true }, primaryEnabled = !busy
                )
                if (loading) BusyIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) SuccessBanner(feedback)
                result?.let { data ->
                    if (data.incidents.isEmpty()) Text(
                        stringResource(R.string.empty_incidents), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PageBar(data.currentPage, data.lastPage, data.hasNext,
                        onPrevious = { page-- }, onNext = { page++ }, enabled = !busy)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                        items(data.incidents, key = { it.id }) { incident ->
                            ResourceCard(
                                title = incident.title, icon = Icons.Outlined.ReportProblem,
                                subtitle = incident.description.takeIf { it.isNotBlank() }?.let { plainFromMarkdown(it) },
                                facts = listOf(
                                    ResourceFact(
                                        stringResource(R.string.incident_severity_label),
                                        stringResource(incidentSeverityLabel(incident.severity))
                                    )
                                )
                            ) {
                                DangerAction(stringResource(R.string.delete_incident), onClick = { confirmDelete = incident },
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
        IncidentFormDialog(
            busy = busy,
            submitError = error,
            onSubmit = { request ->
                runAction(createdMessage, onSuccess = { creating = false }) {
                    PloiApi.createStatusPageIncident(token, statusPage.id, request)
                }
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
    submitError: Throwable?,
    onSubmit: (CreateStatusPageIncidentRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("normal") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.new_incident)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (submitError != null) ApiErrorText(submitError)
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
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    )
}

package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.util.Locale

/**
 * Site monitoring domain: uptime monitors attached to a site (read-only plus delete;
 * the API documents no creation route, so none is offered).
 */
@Composable
internal fun SiteMonitorsScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId, siteId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<SiteMonitorPage?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var uptime by remember { mutableStateOf<Pair<SiteMonitor, List<UptimeResponse>>?>(null) }
    var confirmDelete by remember { mutableStateOf<SiteMonitor?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.siteMonitors(token, serverId, siteId, page) }
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
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.monitors.isEmpty()) Text(stringResource(R.string.empty_monitors))
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
                items(data.monitors, key = { it.id }) { monitor ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(monitor.label.ifBlank { "#${monitor.id}" }, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.monitor_uptime, monitor.location, monitor.averageUptime))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        error = null
                                        scope.launch {
                                            try {
                                                val responses = withContext(Dispatchers.IO) {
                                                    PloiApi.uptimeResponses(token, serverId, siteId, monitor.id)
                                                }
                                                uptime = monitor to responses
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
                                ) { Text(stringResource(R.string.monitor_responses)) }
                                OutlinedButton(onClick = { confirmDelete = monitor }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.delete_monitor),
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

    uptime?.let { (monitor, responses) ->
        AlertDialog(
            onDismissRequest = { uptime = null },
            title = { Text(stringResource(R.string.monitor_responses_title, monitor.label.ifBlank { "#${monitor.id}" })) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (responses.isEmpty()) Text(stringResource(R.string.empty_uptime_responses))
                    responses.take(50).forEach { response ->
                        Text(
                            stringResource(
                                R.string.monitor_response_entry,
                                response.createdAt,
                                String.format(Locale.US, "%.3f", response.responseTime)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { uptime = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    confirmDelete?.let { monitor ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_monitor, monitor.label.ifBlank { "#${monitor.id}" }),
            confirmLabel = R.string.delete_monitor,
            onConfirmed = {
                confirmDelete = null
                busy = true
                error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PloiApi.deleteSiteMonitor(token, serverId, siteId, monitor.id) }
                        feedback = doneMessage
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
            onDismiss = { confirmDelete = null }
        )
    }
}

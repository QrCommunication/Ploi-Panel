package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
        result = null
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

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                summary = result?.let { stringResource(R.string.items_count, it.monitors.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading && !busy) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.monitors.isEmpty()) item { EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.empty_monitors)) }
            items(data.monitors, key = { it.id }) { monitor ->
                ResourceCard(
                    title = monitor.label.ifBlank { "#${monitor.id}" }, icon = Icons.Outlined.MonitorHeart,
                    facts = listOfNotNull(
                        monitor.location.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_location), it) },
                        monitor.averageUptime.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_average_uptime), "$it %", mono = true) }
                    )
                ) {
                    // The uptime bar spans the whole first line of the action area (fillMaxWidth
                    // inside the card's FlowRow); the buttons wrap below it.
                    monitoringPercent(monitor.averageUptime)?.let { percent ->
                        val healthy = percent >= UPTIME_HEALTHY_PERCENT
                        PercentProgress(
                            percent,
                            if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = PanelSpacing.xs),
                            description = stringResource(
                                if (healthy) R.string.a11y_uptime_healthy else R.string.a11y_uptime_degraded,
                                formatPercent(percent).orEmpty()
                            )
                        )
                    }
                    CardAction(stringResource(R.string.monitor_responses), icon = Icons.Outlined.Timeline, enabled = !busy, onClick = {
                        busy = true
                        error = null
                        feedback = ""
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
                    })
                    DangerAction(stringResource(R.string.delete_monitor), onClick = { confirmDelete = monitor }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ }, enabled = !loading)
            }
        }
    }

    uptime?.let { (monitor, responses) ->
        AlertDialog(
            onDismissRequest = { uptime = null },
            title = { Text(stringResource(R.string.monitor_responses_title, monitor.label.ifBlank { "#${monitor.id}" })) },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
                ) {
                    if (responses.isEmpty()) item {
                        Text(stringResource(R.string.empty_uptime_responses), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { ResponseTimeTrend(responses) }
                    items(responses) { response ->
                        Text(
                            stringResource(
                                R.string.monitor_response_entry,
                                response.createdAt,
                                String.format(Locale.US, "%.3f", response.responseTime)
                            ),
                            style = panelMonoStyle
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
                feedback = ""
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

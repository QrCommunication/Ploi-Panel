package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Server detail with monitoring, sites, live info/actions and activity logs. */
@Composable
internal fun ServerDetailScreen(
    token: String, server: Server, lock: AppLock, activity: FragmentActivity, refresh: Int,
    onChanged: () -> Unit, onDeleted: () -> Unit
) {
    var tab by remember(server.id, token) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tab = 0 }, enabled = tab != 0) { Text(stringResource(R.string.monitoring)) }
            OutlinedButton(onClick = { tab = 1 }, enabled = tab != 1) { Text(stringResource(R.string.sites)) }
            OutlinedButton(onClick = { tab = 2 }, enabled = tab != 2) { Text(stringResource(R.string.databases_tab)) }
            OutlinedButton(onClick = { tab = 5 }, enabled = tab != 5) { Text(stringResource(R.string.backups_tab)) }
            OutlinedButton(onClick = { tab = 6 }, enabled = tab != 6) { Text(stringResource(R.string.crontabs_tab)) }
            OutlinedButton(onClick = { tab = 7 }, enabled = tab != 7) { Text(stringResource(R.string.daemons_tab)) }
            OutlinedButton(onClick = { tab = 8 }, enabled = tab != 8) { Text(stringResource(R.string.network_rules_tab)) }
            OutlinedButton(onClick = { tab = 9 }, enabled = tab != 9) { Text(stringResource(R.string.system_users_tab)) }
            OutlinedButton(onClick = { tab = 10 }, enabled = tab != 10) { Text(stringResource(R.string.one_off_script_tab)) }
            OutlinedButton(onClick = { tab = 3 }, enabled = tab != 3) { Text(stringResource(R.string.infos)) }
            OutlinedButton(onClick = { tab = 4 }, enabled = tab != 4) { Text(stringResource(R.string.logs)) }
        }
        when (tab) {
            0 -> MonitoringView(token, server, refresh)
            1 -> SitesScreen(token, server.id, lock, activity)
            2 -> DatabasesScreen(token, server.id, lock, activity)
            5 -> BackupsTab(token, server.id, lock, activity)
            6 -> CrontabsScreen(token, server.id, lock, activity)
            7 -> DaemonsScreen(token, server.id, lock, activity)
            8 -> NetworkRulesScreen(token, server.id, lock, activity)
            9 -> SystemUsersScreen(token, server.id, lock, activity)
            10 -> OneOffScriptScreen(token, server.id)
            3 -> ServerInfoTab(token, server, lock, activity, onChanged, onDeleted)
            else -> ServerLogsTab(token, server.id)
        }
    }
}

@Composable
internal fun MonitoringView(token: String, server: Server, refresh: Int) {
    var sample by remember(server.id, token) { mutableStateOf<MonitorSample?>(null) }
    var loading by remember(server.id, token) { mutableStateOf(true) }
    var error by remember(server.id, token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(server.id, token, refresh) {
        loading = true
        error = null
        try {
            sample = withContext(Dispatchers.IO) { PloiApi.monitoring(token, server.id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            sample = null
            error = failure
        } finally {
            loading = false
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(server.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.monitoring), style = MaterialTheme.typography.titleLarge)
        if (loading) CircularProgressIndicator()
        if (error is PloiHttpException && (error as PloiHttpException).status == 422) {
            Text(stringResource(R.string.monitoring_unavailable))
        } else if (error != null) ApiErrorText(error!!)
        else if (!loading && sample == null) Text(stringResource(R.string.monitoring_unavailable))
        if (sample != null) {
            Text(stringResource(R.string.stale_warning))
            Text(stringResource(R.string.updated, sample!!.date))
            Metric(stringResource(R.string.metric_cpu), "${sample!!.cpu} %")
            Metric(stringResource(R.string.metric_ram), "${sample!!.ram} %")
            Metric(stringResource(R.string.metric_disk), "${sample!!.disk} %")
            Metric(stringResource(R.string.metric_load), sample!!.load)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** Live server details plus update/restart/delete actions. Destructive actions re-authenticate. */
@Composable
private fun ServerInfoTab(
    token: String, server: Server, lock: AppLock, activity: FragmentActivity,
    onChanged: () -> Unit, onDeleted: () -> Unit
) {
    val updatedMessage = stringResource(R.string.server_updated)
    val scope = rememberCoroutineScope()
    var detail by remember(server.id, token) { mutableStateOf<ServerDetail?>(null) }
    var loading by remember(server.id, token) { mutableStateOf(true) }
    var error by remember(server.id, token) { mutableStateOf<Throwable?>(null) }
    var refresh by remember(server.id, token) { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf<Throwable?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(server.id, token, refresh) {
        loading = true
        error = null
        try {
            detail = withContext(Dispatchers.IO) { PloiApi.server(token, server.id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            detail = null
            error = failure
        } finally {
            loading = false
        }
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(server.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        detail?.let { current ->
            Text(stringResource(R.string.server_status, current.status))
            if (current.type.isNotBlank()) Text(stringResource(R.string.detail_type, current.type))
            if (current.ipAddress.isNotBlank()) Text(stringResource(R.string.detail_ip, current.ipAddress))
            if (current.sshPort > 0) Text(stringResource(R.string.detail_ssh_port, current.sshPort))
            if (current.phpVersion.isNotBlank()) Text(stringResource(R.string.detail_php, current.phpVersion))
            if (current.mysqlVersion.isNotBlank()) Text(stringResource(R.string.detail_mysql, current.mysqlVersion))
            Text(stringResource(R.string.detail_sites_count, current.sitesCount))
            Text(stringResource(if (current.monitoring) R.string.detail_monitoring_on else R.string.detail_monitoring_off))
            if (current.rebootRequired) Text(stringResource(R.string.detail_reboot_required), color = MaterialTheme.colorScheme.error)
            if (current.updatesPackages > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.detail_updates, current.updatesPackages,
                        current.updatesPackages, current.updatesSecurity
                    )
                )
            }
            if (current.providerName.isNotBlank()) Text(stringResource(R.string.detail_provider, current.providerName))
            if (current.description.isNotBlank()) Text(stringResource(R.string.detail_description, current.description))
            if (current.createdAt.isNotBlank()) Text(stringResource(R.string.detail_created, current.createdAt))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editing = true }, enabled = !busy && detail != null) {
                Text(stringResource(R.string.edit_server))
            }
            OutlinedButton(onClick = { confirmRestart = true }, enabled = !busy) {
                Text(stringResource(R.string.restart_server))
            }
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Text(stringResource(R.string.delete_server), color = MaterialTheme.colorScheme.error)
            }
        }
        if (actionError != null) ApiErrorText(actionError!!)
        if (actionFeedback.isNotEmpty()) Text(actionFeedback)
    }

    if (editing && detail != null) {
        EditServerDialog(
            current = detail!!,
            busy = busy,
            onSave = { name, ip, port ->
                editing = false
                busy = true
                actionError = null
                scope.launch {
                    try {
                        detail = withContext(Dispatchers.IO) { PloiApi.updateServer(token, server.id, name, ip, port) }
                        actionFeedback = updatedMessage
                        onChanged()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        actionError = failure
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { editing = false }
        )
    }
    if (confirmRestart) {
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_restart_server, server.name),
            confirmLabel = R.string.restart_server,
            onConfirmed = {
                confirmRestart = false
                busy = true
                actionError = null
                scope.launch {
                    try {
                        actionFeedback = withContext(Dispatchers.IO) { PloiApi.restartServer(token, server.id) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        actionError = failure
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { confirmRestart = false }
        )
    }
    if (confirmDelete) {
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_server, server.name),
            confirmLabel = R.string.delete_server,
            onConfirmed = {
                confirmDelete = false
                busy = true
                actionError = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PloiApi.deleteServer(token, server.id) }
                        onDeleted()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        actionError = failure
                        busy = false
                    }
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Edit dialog for PATCH /servers/{id}: name always, optional IP + SSH port (sent together). */
@Composable
private fun EditServerDialog(
    current: ServerDetail, busy: Boolean,
    onSave: (name: String, ip: String, sshPort: Int?) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(current.name) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.ip_optional_label, current.ipAddress)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.ssh_port_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val trimmedIp = ip.trim()
                    val portValue = port.trim().toIntOrNull()
                    invalid = (port.isNotBlank() && portValue == null) || (trimmedIp.isNotEmpty() && portValue == null)
                    if (!invalid) onSave(name.trim(), trimmedIp, portValue)
                },
                enabled = !busy && name.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Paginated activity logs for a server. */
@Composable
private fun ServerLogsTab(token: String, serverId: Long) {
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<ServerLogPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(false) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.serverLogs(token, serverId, page) }
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
        OutlinedButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.reload)) }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        result?.let { data ->
            if (data.logs.isEmpty()) Text(stringResource(R.string.empty_logs))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(
                    stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()),
                    Modifier.padding(top = 12.dp)
                )
                OutlinedButton(onClick = { page++ }, enabled = data.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.logs) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.description, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.createdAt, style = MaterialTheme.typography.bodySmall)
                            if (entry.content.isNotBlank()) {
                                Text(entry.content, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** All monitored servers with their latest statistics in one call (GET /servers/monitored). */
@Composable
internal fun MonitoredServersScreen(token: String) {
    var servers by remember(token) { mutableStateOf<List<MonitoredServer>?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }
    var refresh by remember(token) { mutableIntStateOf(0) }

    LaunchedEffect(token, refresh) {
        loading = true
        error = null
        try {
            servers = withContext(Dispatchers.IO) { PloiApi.monitoredServers(token) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            servers = null
            error = failure
        } finally {
            loading = false
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.reload)) }
        Text(stringResource(R.string.stale_warning), style = MaterialTheme.typography.bodySmall)
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        servers?.let { list ->
            if (list.isEmpty()) Text(stringResource(R.string.no_monitored_servers))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { server ->
                    val latest = server.statistics.maxByOrNull { it.date }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(server.name, style = MaterialTheme.typography.titleMedium)
                            if (server.ip.isNotBlank()) Text(server.ip)
                            if (latest == null) {
                                Text(stringResource(R.string.monitoring_unavailable))
                            } else {
                                Text(stringResource(R.string.updated, latest.date))
                                Metric(stringResource(R.string.metric_cpu), "${latest.cpu} %")
                                Metric(stringResource(R.string.metric_ram), "${latest.ram} %")
                                Metric(stringResource(R.string.metric_disk), "${latest.disk} %")
                                Metric(stringResource(R.string.metric_load), latest.load)
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.qrcommunication.ploipanel

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Groups of the server detail hub. Every [ServerSection] belongs to exactly one category. */
internal enum class ServerCategory(@param:StringRes val title: Int, @param:StringRes val description: Int) {
    OVERVIEW(R.string.server_category_overview, R.string.server_category_overview_description),
    APPS(R.string.server_category_apps, R.string.server_category_apps_description),
    DATA(R.string.server_category_data, R.string.server_category_data_description),
    SYSTEM(R.string.server_category_system, R.string.server_category_system_description),
    ACCESS(R.string.server_category_access, R.string.server_category_access_description)
}

/**
 * The 16 server sub-screens. [id] keeps the historical tab numbers so behaviour and tests that
 * reason about them stay stable.
 */
internal enum class ServerSection(
    val id: Int, val category: ServerCategory,
    @param:StringRes val title: Int, @param:StringRes val description: Int
) {
    MONITORING(0, ServerCategory.OVERVIEW, R.string.monitoring, R.string.server_section_monitoring_description),
    INFOS(3, ServerCategory.OVERVIEW, R.string.infos, R.string.server_section_infos_description),
    LOGS(4, ServerCategory.OVERVIEW, R.string.logs, R.string.server_section_logs_description),
    INSIGHTS(14, ServerCategory.OVERVIEW, R.string.insights_tab, R.string.server_section_insights_description),
    SITES(1, ServerCategory.APPS, R.string.sites, R.string.server_section_sites_description),
    LOAD_BALANCER(13, ServerCategory.APPS, R.string.load_balancer_tab, R.string.server_section_load_balancer_description),
    CONTAINERS(15, ServerCategory.APPS, R.string.containers_tab, R.string.server_section_containers_description),
    DATABASES(2, ServerCategory.DATA, R.string.databases_tab, R.string.server_section_databases_description),
    BACKUPS(5, ServerCategory.DATA, R.string.backups_tab, R.string.server_section_backups_description),
    SERVICES(11, ServerCategory.SYSTEM, R.string.services_tab, R.string.server_section_services_description),
    DAEMONS(7, ServerCategory.SYSTEM, R.string.daemons_tab, R.string.server_section_daemons_description),
    CRONTABS(6, ServerCategory.SYSTEM, R.string.crontabs_tab, R.string.server_section_crontabs_description),
    SYSTEM_USERS(9, ServerCategory.SYSTEM, R.string.system_users_tab, R.string.server_section_system_users_description),
    NETWORK_RULES(8, ServerCategory.SYSTEM, R.string.network_rules_tab, R.string.server_section_network_rules_description),
    ONE_OFF_SCRIPT(10, ServerCategory.SYSTEM, R.string.one_off_script_tab, R.string.server_section_one_off_script_description),
    SSH_KEYS(12, ServerCategory.ACCESS, R.string.ssh_keys_tab, R.string.server_section_ssh_keys_description)
}

internal fun serverSectionIcon(section: ServerSection): ImageVector = when (section) {
    ServerSection.MONITORING -> Icons.Outlined.MonitorHeart
    ServerSection.INFOS -> Icons.Outlined.Info
    ServerSection.LOGS -> Icons.AutoMirrored.Outlined.ReceiptLong
    ServerSection.INSIGHTS -> Icons.Outlined.Insights
    ServerSection.SITES -> Icons.Outlined.Language
    ServerSection.LOAD_BALANCER -> Icons.AutoMirrored.Outlined.AltRoute
    ServerSection.CONTAINERS -> Icons.Outlined.Inventory2
    ServerSection.DATABASES -> Icons.Outlined.Storage
    ServerSection.BACKUPS -> Icons.Outlined.Backup
    ServerSection.SERVICES -> Icons.Outlined.Tune
    ServerSection.DAEMONS -> Icons.Outlined.Autorenew
    ServerSection.CRONTABS -> Icons.Outlined.Schedule
    ServerSection.SYSTEM_USERS -> Icons.Outlined.Group
    ServerSection.NETWORK_RULES -> Icons.Outlined.Shield
    ServerSection.ONE_OFF_SCRIPT -> Icons.Outlined.Bolt
    ServerSection.SSH_KEYS -> Icons.Outlined.Key
}

internal fun serverCategoryIcon(category: ServerCategory): ImageVector = when (category) {
    ServerCategory.OVERVIEW -> Icons.Outlined.MonitorHeart
    ServerCategory.APPS -> Icons.Outlined.Language
    ServerCategory.DATA -> Icons.Outlined.Storage
    ServerCategory.SYSTEM -> Icons.Outlined.Tune
    ServerCategory.ACCESS -> Icons.Outlined.Key
}

/** Opens the in-app terminal on this server's address (port refined by the Infos screen). */
private fun openServerTerminal(server: Server) {
    TerminalNavigator.open(SshTarget(server.ipAddress, SSH_DEFAULT_PORT, server.id, server.name))
}

/**
 * Server detail: a hero (name, IP, status, quick Terminal/Monitoring) above a category hub. Each
 * sub-screen opens in a bounded viewport under a back row; Back returns to the hub, never further.
 */
@Composable
internal fun ServerDetailScreen(
    token: String, server: Server, lock: AppLock, activity: FragmentActivity, refresh: Int,
    profileId: String? = null,
    onChanged: () -> Unit, onDeleted: () -> Unit
) {
    var section by remember(server.id, token) { mutableStateOf<ServerSection?>(null) }
    BackHandler(enabled = section != null) { section = null }
    Column(Modifier.fillMaxSize()) {
        val current = section
        if (current == null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
                contentPadding = PaddingValues(bottom = PanelSpacing.xl)
            ) {
                item { ServerHero(server, onMonitoring = { section = ServerSection.MONITORING }) }
                items(ServerCategory.entries, key = { it.name }) { category ->
                    SectionCard(
                        title = stringResource(category.title),
                        description = stringResource(category.description),
                        icon = serverCategoryIcon(category)
                    ) {
                        ServerSection.entries.filter { it.category == category }.forEach { entry ->
                            PanelListItem(
                                icon = serverSectionIcon(entry),
                                title = stringResource(entry.title),
                                description = stringResource(entry.description)
                            ) { section = entry }
                        }
                        if (category == ServerCategory.ACCESS && server.ipAddress.isNotBlank()) PanelListItem(
                            icon = Icons.Outlined.Terminal,
                            title = stringResource(R.string.ssh_term_open_server),
                            description = stringResource(R.string.server_section_terminal_description)
                        ) { openServerTerminal(server) }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = PanelSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) {
                IconButton(onClick = { section = null }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.server_all_categories))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(current.title), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${server.name} · ${stringResource(current.category.title)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (server.ipAddress.isNotBlank() && current != ServerSection.INFOS) IconButton(onClick = { openServerTerminal(server) }) {
                    Icon(Icons.Outlined.Terminal, contentDescription = stringResource(R.string.ssh_term_open_server))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (current) {
                    ServerSection.MONITORING -> MonitoringView(token, server, refresh, profileId)
                    ServerSection.SITES -> SitesScreen(token, server.id, lock, activity)
                    ServerSection.DATABASES -> DatabasesScreen(token, server.id, lock, activity)
                    ServerSection.BACKUPS -> BackupsTab(token, server.id, lock, activity)
                    ServerSection.CRONTABS -> CrontabsScreen(token, server.id, lock, activity)
                    ServerSection.DAEMONS -> DaemonsScreen(token, server.id, lock, activity)
                    ServerSection.NETWORK_RULES -> NetworkRulesScreen(token, server.id, lock, activity)
                    ServerSection.SYSTEM_USERS -> SystemUsersScreen(token, server.id, lock, activity)
                    ServerSection.ONE_OFF_SCRIPT -> OneOffScriptScreen(token, server.id, lock, activity)
                    ServerSection.SERVICES -> ServicesScreen(token, server, lock, activity)
                    ServerSection.SSH_KEYS -> SshKeysScreen(token, server.id, lock, activity)
                    ServerSection.LOAD_BALANCER -> LoadBalancerScreen(token, server, lock, activity)
                    ServerSection.INSIGHTS -> InsightsScreen(token, server.id, lock, activity)
                    ServerSection.CONTAINERS -> ContainersScreen(token, server.id, lock, activity)
                    ServerSection.INFOS -> ServerInfoTab(token, server, lock, activity, onChanged, onDeleted)
                    ServerSection.LOGS -> ServerLogsTab(token, server.id)
                }
            }
        }
    }
}

/** Identity of the server and its two most used actions. */
@Composable
private fun ServerHero(server: Server, onMonitoring: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(PanelSpacing.lg), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                IconBadge(Icons.Outlined.Dns, container = colors.surfaceContainerLowest, content = colors.primary, size = 48)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                    Text(server.name, style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() })
                    if (server.ipAddress.isNotBlank()) Text(server.ipAddress, style = panelMonoStyle)
                }
            }
            StatusPill(server.status)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                if (server.ipAddress.isNotBlank()) Button(onClick = { openServerTerminal(server) }) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ssh_term_open_server), Modifier.padding(start = PanelSpacing.sm))
                }
                OutlinedButton(onClick = onMonitoring) {
                    Icon(Icons.Outlined.MonitorHeart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.monitoring), Modifier.padding(start = PanelSpacing.sm))
                }
            }
        }
    }
}

@Composable
internal fun MonitoringView(token: String, server: Server, refresh: Int, profileId: String? = null) {
    var samples by remember(server.id, token) { mutableStateOf<List<MonitorSample>>(emptyList()) }
    var loading by remember(server.id, token) { mutableStateOf(true) }
    var error by remember(server.id, token) { mutableStateOf<Throwable?>(null) }
    var retry by remember(server.id, token) { mutableIntStateOf(0) }
    LaunchedEffect(server.id, token, refresh, retry) {
        loading = true
        error = null
        samples = emptyList()
        try {
            samples = withContext(Dispatchers.IO) { PloiApi.monitoringHistory(token, server.id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            samples = emptyList()
            error = failure
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        if (loading) LoadingState(rows = 2)
        if (error is PloiHttpException && (error as PloiHttpException).status == 422) {
            EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.monitoring_unavailable))
        } else if (error != null) ErrorState(error!!, onRetry = { retry++ }, retryEnabled = !loading)
        else if (!loading && samples.isEmpty()) EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.monitoring_unavailable))
        if (!loading && error == null && samples.isNotEmpty()) {
            val latest = samples.last()
            Text(stringResource(R.string.stale_warning), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.updated, latest.date), style = MaterialTheme.typography.labelLarge)
            MonitoringCharts(samples)
            Metric(stringResource(R.string.metric_load), latest.load.ifBlank { "—" })
        }
        if (profileId != null && !loading && error == null && samples.isNotEmpty()) ThresholdAlertCard(profileId, server)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = PanelSpacing.xs), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = panelMonoStyle, fontWeight = FontWeight.SemiBold)
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
        detail = null
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        if (loading) LoadingState(rows = 2)
        if (error != null) ErrorState(error!!, onRetry = { refresh++ }, retryEnabled = !loading)
        detail?.let { current ->
            SectionCard(title = current.name, icon = Icons.Outlined.Dns) {
                StatusPill(current.status)
                Text(stringResource(R.string.server_status, current.status), style = MaterialTheme.typography.bodyMedium)
                if (current.type.isNotBlank()) InfoLine(stringResource(R.string.detail_type, current.type))
                if (current.ipAddress.isNotBlank()) InfoLine(stringResource(R.string.detail_ip, current.ipAddress), mono = true)
                if (current.sshPort > 0) InfoLine(stringResource(R.string.detail_ssh_port, current.sshPort), mono = true)
                if (current.phpVersion.isNotBlank()) InfoLine(stringResource(R.string.detail_php, current.phpVersion))
                if (current.mysqlVersion.isNotBlank()) InfoLine(stringResource(R.string.detail_mysql, current.mysqlVersion))
                InfoLine(stringResource(R.string.detail_sites_count, current.sitesCount))
                InfoLine(stringResource(if (current.monitoring) R.string.detail_monitoring_on else R.string.detail_monitoring_off))
                if (current.providerName.isNotBlank()) InfoLine(stringResource(R.string.detail_provider, current.providerName))
                if (current.description.isNotBlank()) InfoLine(stringResource(R.string.detail_description, current.description))
                if (current.createdAt.isNotBlank()) InfoLine(stringResource(R.string.detail_created, current.createdAt))
                if (current.ipAddress.isNotBlank()) FilledTonalButton(onClick = {
                    TerminalNavigator.open(
                        SshTarget(current.ipAddress, current.sshPort.takeIf { it > 0 } ?: SSH_DEFAULT_PORT, current.id, current.name)
                    )
                }) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ssh_term_open_server), Modifier.padding(start = PanelSpacing.sm))
                }
            }
            if (current.rebootRequired || current.updatesPackages > 0) Surface(
                color = PanelTheme.status.warningContainer, contentColor = PanelTheme.status.onWarningContainer,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(PanelSpacing.lg), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                        if (current.rebootRequired) Text(stringResource(R.string.detail_reboot_required),
                            style = MaterialTheme.typography.titleSmall)
                        if (current.updatesPackages > 0) {
                            Text(
                                pluralStringResource(
                                    R.plurals.detail_updates, current.updatesPackages,
                                    current.updatesPackages, current.updatesSecurity
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
            OutlinedButton(onClick = { editing = true }, enabled = !busy && detail != null) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.edit_server), Modifier.padding(start = PanelSpacing.sm))
            }
            OutlinedButton(onClick = { confirmRestart = true }, enabled = !busy) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.restart_server), Modifier.padding(start = PanelSpacing.sm))
            }
            OutlinedButton(
                onClick = { confirmDelete = true }, enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.delete_server), Modifier.padding(start = PanelSpacing.sm))
            }
        }
        if (busy) BusyIndicator()
        if (actionError != null) ApiErrorText(actionError!!)
        if (actionFeedback.isNotEmpty()) Text(actionFeedback, color = PanelTheme.status.success)
    }

    if (editing && detail != null) {
        EditServerDialog(
            current = detail!!,
            busy = busy,
            onSave = { name, ip, port ->
                editing = false
                busy = true
                actionError = null
                actionFeedback = ""
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
                actionFeedback = ""
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
                actionFeedback = ""
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

@Composable
private fun InfoLine(text: String, mono: Boolean = false) {
    Text(text, style = if (mono) panelMonoStyle else MaterialTheme.typography.bodyMedium)
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
        result = null
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

    Column(Modifier.fillMaxSize().padding(vertical = PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        OutlinedButton(onClick = { refresh++ }, enabled = !loading) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.reload), Modifier.padding(start = PanelSpacing.sm))
        }
        if (loading) LoadingState()
        if (error != null) ErrorState(error!!, onRetry = { refresh++ }, retryEnabled = !loading)
        result?.let { data ->
            if (data.logs.isEmpty()) EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.empty_logs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(
                    stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()),
                    Modifier.padding(top = PanelSpacing.md), style = MaterialTheme.typography.labelLarge
                )
                OutlinedButton(onClick = { page++ }, enabled = data.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                items(data.logs) { entry ->
                    SectionCard {
                        Text(entry.description, style = MaterialTheme.typography.bodyLarge)
                        Text(entry.createdAt, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.content.isNotBlank()) {
                            Text(entry.content, style = panelMonoStyle)
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
        servers = null
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

    Column(Modifier.fillMaxSize().padding(vertical = PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        OutlinedButton(onClick = { refresh++ }, enabled = !loading) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.reload), Modifier.padding(start = PanelSpacing.sm))
        }
        Text(stringResource(R.string.stale_warning), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) LoadingState()
        if (error != null) ErrorState(error!!, onRetry = { refresh++ }, retryEnabled = !loading)
        servers?.let { list ->
            if (list.isEmpty()) EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.no_monitored_servers))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                items(list, key = { it.id }) { server ->
                    val latest = server.statistics.lastOrNull()
                    SectionCard(title = server.name, icon = Icons.Outlined.Dns) {
                        if (server.ip.isNotBlank()) Text(server.ip, style = panelMonoStyle)
                        if (latest == null) {
                            Text(stringResource(R.string.monitoring_unavailable))
                        } else {
                            Text(stringResource(R.string.updated, latest.date), style = MaterialTheme.typography.labelLarge)
                            MonitoringCharts(server.statistics)
                            Metric(stringResource(R.string.metric_load), latest.load.ifBlank { "—" })
                        }
                    }
                }
            }
        }
    }
}

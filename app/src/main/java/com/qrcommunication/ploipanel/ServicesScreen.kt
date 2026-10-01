package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Php
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Services + PHP domains: restart/reload of system services, OPcache lifecycle,
 * installed PHP versions with install and CLI switch, and the server-level WP-CLI.
 */
@Composable
internal fun ServicesScreen(token: String, server: Server, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, server.id) { mutableIntStateOf(0) }
    var detail by remember(token, server.id) { mutableStateOf<ServerDetail?>(null) }
    var versions by remember(token, server.id) { mutableStateOf<List<String>?>(null) }
    var loading by remember(token, server.id) { mutableStateOf(true) }
    var error by remember(token, server.id) { mutableStateOf<Throwable?>(null) }
    var phpError by remember(token, server.id) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, server.id) { mutableStateOf("") }
    var busy by remember(token, server.id) { mutableStateOf(false) }
    var serviceAction by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // service to restart(isRestart) / reload
    var confirmOpcache by remember { mutableStateOf<String?>(null) } // refresh / enable / disable
    var confirmWpCli by remember { mutableStateOf<String?>(null) } // install / uninstall
    var phpDialog by remember { mutableStateOf(false) }
    var cliDialog by remember { mutableStateOf(false) }
    var wpCliDialog by remember { mutableStateOf(false) }
    var pendingWpCommand by remember { mutableStateOf<String?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, server.id, refresh) {
        loading = true
        error = null
        phpError = null
        detail = null
        versions = null
        try {
            detail = withContext(Dispatchers.IO) { PloiApi.server(token, server.id) }
            try {
                versions = withContext(Dispatchers.IO) { PloiApi.phpVersions(token, server.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                phpError = failure
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
    ) {
        ListToolbar(onRefresh = { refresh++ }, refreshEnabled = !loading && !busy)
        if (loading) LoadingState(rows = 2)
        error?.let { failure -> ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) }
        if (feedback.isNotEmpty()) SuccessBanner(feedback)

        SectionHeader(stringResource(R.string.services_section))
        listOf("nginx", "mysql", "redis", "supervisor").forEach { service ->
            ResourceCard(title = service, monoTitle = true, icon = serviceIcon(service)) {
                CardAction(stringResource(R.string.restart_service), icon = Icons.Outlined.RestartAlt, enabled = !busy,
                    onClick = { serviceAction = service to true })
                CardAction(stringResource(R.string.reload_service), icon = Icons.Outlined.Refresh, enabled = !busy,
                    onClick = { serviceAction = service to false })
            }
        }

        SectionCard(title = stringResource(R.string.opcache_section), icon = Icons.Outlined.Speed) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                CardAction(stringResource(R.string.opcache_refresh), icon = Icons.Outlined.Refresh, enabled = !busy,
                    onClick = { confirmOpcache = "refresh" })
                CardAction(stringResource(R.string.opcache_enable), icon = Icons.Outlined.ToggleOn, enabled = !busy,
                    onClick = { confirmOpcache = "enable" })
                CardAction(stringResource(R.string.opcache_disable), icon = Icons.Outlined.ToggleOff, enabled = !busy,
                    onClick = { confirmOpcache = "disable" })
            }
        }

        SectionCard(title = stringResource(R.string.php_versions_section), icon = Icons.Outlined.Php) {
            detail?.let { serverDetail ->
                val cli = serverDetail.phpCliVersion.ifBlank { serverDetail.phpVersion }
                if (cli.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                    Text(stringResource(R.string.g2_php_cli_label), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cli, style = panelMonoStyle)
                }
            }
            phpError?.let { failure -> ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) }
            versions?.let { installed ->
                if (installed.isEmpty()) {
                    Text(stringResource(R.string.empty_php_versions), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                        installed.forEach { version ->
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                                Text(version, style = panelMonoStyle,
                                    modifier = Modifier.padding(horizontal = PanelSpacing.sm, vertical = PanelSpacing.xs))
                            }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                CardAction(stringResource(R.string.php_install_version), icon = Icons.Outlined.Download,
                    enabled = !busy && versions != null, onClick = { phpDialog = true })
                CardAction(stringResource(R.string.php_switch_cli), icon = Icons.Outlined.SwapHoriz,
                    enabled = !busy && !versions.isNullOrEmpty(), onClick = { cliDialog = true })
            }
        }

        SectionCard(title = stringResource(R.string.wpcli_section), icon = Icons.Outlined.Terminal) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                CardAction(stringResource(R.string.wpcli_run), icon = Icons.Outlined.PlayArrow, enabled = !busy,
                    onClick = { wpCliDialog = true })
                CardAction(stringResource(R.string.wpcli_install), icon = Icons.Outlined.Download, enabled = !busy,
                    onClick = { confirmWpCli = "install" })
                DangerAction(stringResource(R.string.wpcli_uninstall), onClick = { confirmWpCli = "uninstall" }, enabled = !busy)
            }
        }
    }

    serviceAction?.let { (service, isRestart) ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(
                if (isRestart) R.string.confirm_restart_service else R.string.confirm_reload_service,
                service
            ),
            confirmLabel = if (isRestart) R.string.restart_service else R.string.reload_service,
            onConfirmed = {
                serviceAction = null
                runAction(doneMessage) {
                    if (isRestart) PloiApi.restartService(token, server.id, service)
                    else PloiApi.reloadService(token, server.id, service)
                }
            },
            onDismiss = { serviceAction = null }
        )
    }

    confirmOpcache?.let { action ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(
                when (action) {
                    "refresh" -> R.string.confirm_opcache_refresh
                    "enable" -> R.string.confirm_opcache_enable
                    else -> R.string.confirm_opcache_disable
                }
            ),
            confirmLabel = when (action) {
                "refresh" -> R.string.opcache_refresh
                "enable" -> R.string.opcache_enable
                else -> R.string.opcache_disable
            },
            onConfirmed = {
                confirmOpcache = null
                runAction(doneMessage) {
                    when (action) {
                        "refresh" -> PloiApi.refreshOpcache(token, server.id)
                        "enable" -> PloiApi.enableOpcache(token, server.id)
                        else -> PloiApi.disableOpcache(token, server.id)
                    }
                }
            },
            onDismiss = { confirmOpcache = null }
        )
    }

    confirmWpCli?.let { action ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(
                if (action == "install") R.string.confirm_wpcli_install else R.string.confirm_wpcli_uninstall
            ),
            confirmLabel = if (action == "install") R.string.wpcli_install else R.string.wpcli_uninstall,
            onConfirmed = {
                confirmWpCli = null
                runAction(doneMessage) {
                    if (action == "install") PloiApi.installWpCli(token, server.id)
                    else PloiApi.uninstallWpCli(token, server.id)
                }
            },
            onDismiss = { confirmWpCli = null }
        )
    }

    if (phpDialog) {
        PhpVersionDialog(
            title = R.string.php_install_version,
            busy = busy,
            onSubmit = { version ->
                phpDialog = false
                runAction(doneMessage) { PloiApi.installPhpVersion(token, server.id, version) }
            },
            onDismiss = { phpDialog = false }
        )
    }
    if (cliDialog) {
        PhpVersionDialog(
            title = R.string.php_switch_cli,
            busy = busy,
            options = versions.orEmpty(),
            onSubmit = { version ->
                cliDialog = false
                runAction(doneMessage) { PloiApi.switchPhpCliVersion(token, server.id, version) }
            },
            onDismiss = { cliDialog = false }
        )
    }
    if (wpCliDialog) {
        var command by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { wpCliDialog = false },
            title = { Text(stringResource(R.string.wpcli_run)) },
            text = {
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text(stringResource(R.string.wpcli_command_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        wpCliDialog = false
                        pendingWpCommand = command.trim()
                    },
                    enabled = !busy && command.isNotBlank()
                ) { Text(stringResource(R.string.wpcli_run)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { wpCliDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    pendingWpCommand?.let { command ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_wpcli_run),
            confirmLabel = R.string.wpcli_run,
            onConfirmed = {
                pendingWpCommand = null
                runAction(doneMessage) { PloiApi.runServerWpCli(token, server.id, command) }
            },
            onDismiss = { pendingWpCommand = null }
        )
    }
}

/** Decorative leading icon of a system service row. */
private fun serviceIcon(service: String): ImageVector = when (service) {
    "mysql" -> Icons.Outlined.Storage
    "redis" -> Icons.Outlined.Memory
    "supervisor" -> Icons.Outlined.Autorenew
    else -> Icons.Outlined.Dns
}

@Composable
private fun PhpVersionDialog(
    title: Int, busy: Boolean, options: List<String> = emptyList(),
    onSubmit: (String) -> Unit, onDismiss: () -> Unit
) {
    var version by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                if (options.isEmpty()) {
                    OutlinedTextField(
                        value = version, onValueChange = { version = it },
                        label = { Text(stringResource(R.string.php_version_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                        options.forEach { option ->
                            FilterChip(selected = version == option, onClick = { version = option },
                                label = { Text(option) })
                        }
                    }
                }
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        validatePhpVersionString(version.trim())
                        onSubmit(version.trim())
                    } catch (invalidVersion: IllegalArgumentException) {
                        invalid = true
                    }
                },
                enabled = !busy && version.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

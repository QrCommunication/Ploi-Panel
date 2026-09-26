package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
    var feedback by remember(token, server.id) { mutableStateOf("") }
    var busy by remember(token, server.id) { mutableStateOf(false) }
    var serviceAction by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // service to restart(isRestart) / reload
    var confirmOpcache by remember { mutableStateOf<String?>(null) } // refresh / enable / disable
    var confirmWpCli by remember { mutableStateOf<String?>(null) } // install / uninstall
    var phpDialog by remember { mutableStateOf(false) }
    var cliDialog by remember { mutableStateOf(false) }
    var wpCliDialog by remember { mutableStateOf(false) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, server.id, refresh) {
        loading = true
        error = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                PloiApi.server(token, server.id) to runCatching { PloiApi.phpVersions(token, server.id) }.getOrNull()
            }
            detail = loaded.first
            versions = loaded.second
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            detail = null
            versions = null
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

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)

        Text(stringResource(R.string.services_section), style = MaterialTheme.typography.titleMedium)
        listOf("nginx", "mysql", "redis", "supervisor").forEach { service ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(service, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { serviceAction = service to true }, enabled = !busy) {
                            Text(stringResource(R.string.restart_service))
                        }
                        OutlinedButton(onClick = { serviceAction = service to false }, enabled = !busy) {
                            Text(stringResource(R.string.reload_service))
                        }
                    }
                }
            }
        }
        detail?.let { serverDetail ->
            Text(
                stringResource(
                    R.string.php_cli_current,
                    serverDetail.phpCliVersion.ifBlank { serverDetail.phpVersion }
                )
            )
        }

        Text(stringResource(R.string.opcache_section), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmOpcache = "refresh" }, enabled = !busy) {
                Text(stringResource(R.string.opcache_refresh))
            }
            OutlinedButton(onClick = { confirmOpcache = "enable" }, enabled = !busy) {
                Text(stringResource(R.string.opcache_enable))
            }
            OutlinedButton(onClick = { confirmOpcache = "disable" }, enabled = !busy) {
                Text(stringResource(R.string.opcache_disable))
            }
        }

        Text(stringResource(R.string.php_versions_section), style = MaterialTheme.typography.titleMedium)
        versions?.let { installed ->
            if (installed.isEmpty()) Text(stringResource(R.string.empty_php_versions))
            Text(installed.joinToString(", "))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { phpDialog = true }, enabled = !busy) {
                Text(stringResource(R.string.php_install_version))
            }
            OutlinedButton(onClick = { cliDialog = true }, enabled = !busy) {
                Text(stringResource(R.string.php_switch_cli))
            }
        }

        Text(stringResource(R.string.wpcli_section), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmWpCli = "install" }, enabled = !busy) {
                Text(stringResource(R.string.wpcli_install))
            }
            OutlinedButton(onClick = { confirmWpCli = "uninstall" }, enabled = !busy) {
                Text(stringResource(R.string.wpcli_uninstall), color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = { wpCliDialog = true }, enabled = !busy) {
                Text(stringResource(R.string.wpcli_run))
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
                        runAction(doneMessage) { PloiApi.runServerWpCli(token, server.id, command.trim()) }
                    },
                    enabled = !busy && command.isNotBlank()
                ) { Text(stringResource(R.string.wpcli_run)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { wpCliDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun PhpVersionDialog(title: Int, busy: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var version by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = version, onValueChange = { version = it },
                    label = { Text(stringResource(R.string.php_version_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
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

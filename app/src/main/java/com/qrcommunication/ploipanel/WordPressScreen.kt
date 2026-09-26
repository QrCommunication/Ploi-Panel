package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WordPress management domain: plugins, themes, XML-RPC, WP-CLI, search-replace,
 * complete-install and per-site repositories (21 documented routes).
 */
@Composable
internal fun WordPressScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    var tab by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tab = 0 }, enabled = tab != 0) { Text(stringResource(R.string.wp_plugins_tab)) }
            OutlinedButton(onClick = { tab = 1 }, enabled = tab != 1) { Text(stringResource(R.string.wp_themes_tab)) }
            OutlinedButton(onClick = { tab = 2 }, enabled = tab != 2) { Text(stringResource(R.string.wp_tools_tab)) }
            OutlinedButton(onClick = { tab = 3 }, enabled = tab != 3) { Text(stringResource(R.string.wp_repositories_tab)) }
        }
        when (tab) {
            0 -> WpExtensionsTab(token, serverId, siteId, themes = false, lock, activity)
            1 -> WpExtensionsTab(token, serverId, siteId, themes = true, lock, activity)
            2 -> WpToolsTab(token, serverId, siteId, lock, activity)
            else -> WpRepositoriesTab(token, serverId, siteId, lock, activity)
        }
    }
}

@Composable
private fun WpExtensionsTab(
    token: String, serverId: Long, siteId: Long, themes: Boolean, lock: AppLock, activity: FragmentActivity
) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId, themes) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId, themes) { mutableStateOf<List<WpExtension>?>(null) }
    var loading by remember(token, serverId, siteId, themes) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId, themes) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId, themes) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId, themes) { mutableStateOf(false) }
    var installDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WpExtension?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, themes, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) {
                if (themes) PloiApi.wpThemes(token, serverId, siteId) else PloiApi.wpPlugins(token, serverId, siteId)
            }
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
            OutlinedButton(onClick = { installDialog = true }, enabled = !busy) {
                Text(stringResource(if (themes) R.string.wp_install_theme else R.string.wp_install_plugin))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { extensions ->
            if (extensions.isEmpty()) {
                Text(stringResource(if (themes) R.string.empty_wp_themes else R.string.empty_wp_plugins))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(extensions, key = { it.name }) { extension ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(extension.title.ifBlank { extension.name }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.wp_extension_status,
                                    extension.status,
                                    extension.version,
                                    extension.updateVersion.ifBlank { "—" }
                                )
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (extension.status != "active") {
                                    OutlinedButton(
                                        onClick = {
                                            runAction(doneMessage) {
                                                if (themes) PloiApi.activateWpTheme(token, serverId, siteId, extension.name)
                                                else PloiApi.activateWpPlugin(token, serverId, siteId, extension.name)
                                            }
                                        },
                                        enabled = !busy
                                    ) { Text(stringResource(R.string.wp_activate)) }
                                } else if (!themes) {
                                    OutlinedButton(
                                        onClick = {
                                            runAction(doneMessage) {
                                                PloiApi.deactivateWpPlugin(token, serverId, siteId, extension.name)
                                            }
                                        },
                                        enabled = !busy
                                    ) { Text(stringResource(R.string.wp_deactivate)) }
                                }
                                if (extension.updateVersion.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            runAction(doneMessage) {
                                                if (themes) PloiApi.updateWpTheme(token, serverId, siteId, extension.name)
                                                else PloiApi.updateWpPlugin(token, serverId, siteId, extension.name)
                                            }
                                        },
                                        enabled = !busy
                                    ) { Text(stringResource(R.string.wp_update)) }
                                }
                                OutlinedButton(onClick = { confirmDelete = extension }, enabled = !busy) {
                                    Text(stringResource(R.string.wp_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (installDialog) {
        var slug by remember { mutableStateOf("") }
        var activate by remember { mutableStateOf(false) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { installDialog = false },
            title = { Text(stringResource(if (themes) R.string.wp_install_theme else R.string.wp_install_plugin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = slug, onValueChange = { slug = it },
                        label = { Text(stringResource(R.string.wp_slug_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { activate = !activate }) {
                            Text(
                                stringResource(if (activate) R.string.wp_activate_after_yes else R.string.wp_activate_after_no)
                            )
                        }
                    }
                    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val valid = try {
                            validateWpSlug(slug.trim())
                        } catch (invalidSlug: IllegalArgumentException) {
                            null
                        }
                        if (valid == null) {
                            invalid = true
                        } else {
                            installDialog = false
                            runAction(doneMessage) {
                                if (themes) PloiApi.installWpTheme(token, serverId, siteId, valid, activate)
                                else PloiApi.installWpPlugin(token, serverId, siteId, valid, activate)
                            }
                        }
                    },
                    enabled = !busy && slug.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { installDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    confirmDelete?.let { extension ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_wp_delete, extension.name),
            confirmLabel = R.string.wp_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) {
                    if (themes) PloiApi.deleteWpTheme(token, serverId, siteId, extension.name)
                    else PloiApi.deleteWpPlugin(token, serverId, siteId, extension.name)
                }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun WpToolsTab(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var output by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var cliDialog by remember { mutableStateOf(false) }
    var searchReplaceDialog by remember { mutableStateOf(false) }
    var completeInstallDialog by remember { mutableStateOf(false) }
    var confirmXmlrpc by remember { mutableStateOf<Boolean?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    fun runAction(block: suspend () -> String) {
        busy = true
        error = null
        scope.launch {
            try {
                output = withContext(Dispatchers.IO) { block() }
                feedback = doneMessage
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
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (busy) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        if (output.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(output, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { cliDialog = true }, enabled = !busy) {
                Text(stringResource(R.string.wp_cli_run))
            }
            OutlinedButton(onClick = { searchReplaceDialog = true }, enabled = !busy) {
                Text(stringResource(R.string.wp_search_replace))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmXmlrpc = true }, enabled = !busy) {
                Text(stringResource(R.string.wp_xmlrpc_block))
            }
            OutlinedButton(onClick = { confirmXmlrpc = false }, enabled = !busy) {
                Text(stringResource(R.string.wp_xmlrpc_allow))
            }
        }
        OutlinedButton(onClick = { completeInstallDialog = true }, enabled = !busy) {
            Text(stringResource(R.string.wp_complete_install))
        }
    }

    if (cliDialog) {
        var command by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cliDialog = false },
            title = { Text(stringResource(R.string.wp_cli_run)) },
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
                        cliDialog = false
                        runAction { PloiApi.runWpCli(token, serverId, siteId, command.trim()) }
                    },
                    enabled = !busy && command.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { cliDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (searchReplaceDialog) {
        var search by remember { mutableStateOf("") }
        var replace by remember { mutableStateOf("") }
        var dryRun by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { searchReplaceDialog = false },
            title = { Text(stringResource(R.string.wp_search_replace)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        label = { Text(stringResource(R.string.wp_search_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replace, onValueChange = { replace = it },
                        label = { Text(stringResource(R.string.wp_replace_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { dryRun = !dryRun }) {
                        Text(stringResource(if (dryRun) R.string.wp_dry_run_yes else R.string.wp_dry_run_no))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        searchReplaceDialog = false
                        runAction { PloiApi.searchReplaceWp(token, serverId, siteId, search, replace, dryRun) }
                    },
                    enabled = !busy && search.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { searchReplaceDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (completeInstallDialog) {
        var siteTitle by remember { mutableStateOf("") }
        var adminUsername by remember { mutableStateOf("") }
        var adminEmail by remember { mutableStateOf("") }
        var adminPassword by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { completeInstallDialog = false },
            title = { Text(stringResource(R.string.wp_complete_install)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = siteTitle, onValueChange = { siteTitle = it },
                        label = { Text(stringResource(R.string.wp_site_title_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adminUsername, onValueChange = { adminUsername = it },
                        label = { Text(stringResource(R.string.wp_admin_username_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adminEmail, onValueChange = { adminEmail = it },
                        label = { Text(stringResource(R.string.wp_admin_email_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adminPassword, onValueChange = { adminPassword = it },
                        label = { Text(stringResource(R.string.wp_admin_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            completeInstallDialog = false
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        PloiApi.completeWordpressInstall(
                                            token, serverId, siteId,
                                            siteTitle, adminUsername, adminEmail, adminPassword
                                        )
                                    }
                                    feedback = doneMessage
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure
                                } finally {
                                    busy = false
                                }
                            }
                        } catch (invalidInput: IllegalArgumentException) {
                            invalid = true
                        }
                    },
                    enabled = !busy && siteTitle.isNotBlank() && adminUsername.isNotBlank() &&
                        adminEmail.contains("@") && adminPassword.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { completeInstallDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    confirmXmlrpc?.let { block ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(if (block) R.string.confirm_xmlrpc_block else R.string.confirm_xmlrpc_allow),
            confirmLabel = if (block) R.string.wp_xmlrpc_block else R.string.wp_xmlrpc_allow,
            onConfirmed = {
                confirmXmlrpc = null
                busy = true
                error = null
                scope.launch {
                    try {
                        val ack = withContext(Dispatchers.IO) { PloiApi.toggleWpXmlrpc(token, serverId, siteId, block) }
                        feedback = ack.message
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { confirmXmlrpc = null }
        )
    }
}

@Composable
private fun WpRepositoriesTab(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<List<WpRepository>?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var installDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf<WpRepository?>(null) }
    var scriptDialog by remember { mutableStateOf<WpRepository?>(null) }
    var confirmDeploy by remember { mutableStateOf<WpRepository?>(null) }
    var confirmDeployAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WpRepository?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.wpRepositories(token, serverId, siteId) }
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
            OutlinedButton(onClick = { installDialog = true }, enabled = !busy) {
                Text(stringResource(R.string.wp_repo_install))
            }
            OutlinedButton(onClick = { confirmDeployAll = true }, enabled = !busy) {
                Text(stringResource(R.string.wp_repo_deploy_all))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { repositories ->
            if (repositories.isEmpty()) Text(stringResource(R.string.empty_wp_repositories))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repositories, key = { it.id }) { repository ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${repository.user}/${repository.name}", style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.wp_repo_status, repository.branch, repository.status))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { confirmDeploy = repository }, enabled = !busy) {
                                    Text(stringResource(R.string.wp_repo_deploy))
                                }
                                OutlinedButton(onClick = { editDialog = repository }, enabled = !busy) {
                                    Text(stringResource(R.string.wp_repo_edit))
                                }
                                OutlinedButton(onClick = { scriptDialog = repository }, enabled = !busy) {
                                    Text(stringResource(R.string.wp_repo_script))
                                }
                                OutlinedButton(onClick = { confirmDelete = repository }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.wp_repo_delete),
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

    if (installDialog) {
        var provider by remember { mutableStateOf("github") }
        var name by remember { mutableStateOf("") }
        var branch by remember { mutableStateOf("main") }
        var targetDirectory by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { installDialog = false },
            title = { Text(stringResource(R.string.wp_repo_install)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = provider, onValueChange = { provider = it },
                        label = { Text(stringResource(R.string.wp_repo_provider_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.wp_repo_name_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = branch, onValueChange = { branch = it },
                        label = { Text(stringResource(R.string.wp_repo_branch_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetDirectory, onValueChange = { targetDirectory = it },
                        label = { Text(stringResource(R.string.wp_repo_target_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            installDialog = false
                            runAction(doneMessage) {
                                PloiApi.installWpRepository(
                                    token, serverId, siteId, provider, name, branch, targetDirectory
                                )
                            }
                        } catch (invalidInput: IllegalArgumentException) {
                            invalid = true
                        }
                    },
                    enabled = !busy && provider.isNotBlank() && name.isNotBlank() &&
                        branch.isNotBlank() && targetDirectory.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { installDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    editDialog?.let { repository ->
        var user by remember(repository.id) { mutableStateOf(repository.user) }
        var name by remember(repository.id) { mutableStateOf(repository.name) }
        var branch by remember(repository.id) { mutableStateOf(repository.branch) }
        AlertDialog(
            onDismissRequest = { editDialog = null },
            title = { Text(stringResource(R.string.wp_repo_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text(stringResource(R.string.wp_repo_user_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.wp_repo_name_only_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = branch, onValueChange = { branch = it },
                        label = { Text(stringResource(R.string.wp_repo_branch_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = repository
                        editDialog = null
                        runAction(doneMessage) {
                            PloiApi.updateWpRepository(token, serverId, siteId, target.id, user, name, branch)
                        }
                    },
                    enabled = !busy && user.isNotBlank() && name.isNotBlank() && branch.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { editDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    scriptDialog?.let { repository ->
        var script by remember(repository.id) { mutableStateOf(repository.deployScript) }
        AlertDialog(
            onDismissRequest = { scriptDialog = null },
            title = { Text(stringResource(R.string.wp_repo_script)) },
            text = {
                OutlinedTextField(
                    value = script, onValueChange = { script = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = repository
                        scriptDialog = null
                        runAction(doneMessage) {
                            PloiApi.updateWpRepositoryDeployScript(token, serverId, siteId, target.id, script)
                        }
                    },
                    enabled = !busy && script.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { scriptDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    confirmDeploy?.let { repository ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_wp_repo_deploy, "${repository.user}/${repository.name}"),
            confirmLabel = R.string.wp_repo_deploy,
            onConfirmed = {
                confirmDeploy = null
                runAction(doneMessage) { PloiApi.deployWpRepository(token, serverId, siteId, repository.id) }
            },
            onDismiss = { confirmDeploy = null }
        )
    }

    if (confirmDeployAll) {
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_wp_repo_deploy_all),
            confirmLabel = R.string.wp_repo_deploy_all,
            onConfirmed = {
                confirmDeployAll = false
                runAction(doneMessage) { PloiApi.deployAllWpRepositories(token, serverId, siteId) }
            },
            onDismiss = { confirmDeployAll = false }
        )
    }

    confirmDelete?.let { repository ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_wp_repo_delete, "${repository.user}/${repository.name}"),
            confirmLabel = R.string.wp_repo_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteWpRepository(token, serverId, siteId, repository.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

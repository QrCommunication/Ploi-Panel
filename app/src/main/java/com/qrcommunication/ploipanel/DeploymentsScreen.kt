package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository dialog (repositories domain): shows the installed repository, installs one
 * when missing (404), toggles quick deploy, enables custom deployments and deletes the
 * repository after PIN/biometric re-authentication.
 */
@Composable
internal fun RepositoryDialog(
    token: String, serverId: Long, siteId: Long, siteDomain: String,
    lock: AppLock, activity: FragmentActivity,
    onChanged: () -> Unit, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var repository by remember { mutableStateOf<SiteRepository?>(null) }
    var missing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.repository_deleted)
    val installedMessage = stringResource(R.string.repository_installed)
    val customEnabledMessage = stringResource(R.string.custom_deployments_enabled)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loading = true
        error = null
        try {
            repository = withContext(Dispatchers.IO) { PloiApi.repository(token, serverId, siteId) }
            missing = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repository = null
            if (failure is PloiHttpException && failure.status == 404) {
                missing = true
            } else {
                error = failure
            }
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
                onChanged()
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
        title = { Text(stringResource(R.string.repository_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) Text(feedback)
                repository?.let { repo ->
                    if (repo.provider.isNotBlank() && repo.provider != "none") {
                        Text(stringResource(R.string.repository_provider, repo.provider))
                        if (repo.repositoryName.isNotBlank()) {
                            Text(stringResource(R.string.repository_name, repo.repositoryName))
                        }
                        if (repo.branch.isNotBlank()) Text(stringResource(R.string.repository_branch, repo.branch))
                    } else {
                        Text(stringResource(R.string.repository_custom))
                    }
                    val yesNo = stringResource(if (repo.quickDeploy) R.string.flag_yes else R.string.flag_no)
                    Text(stringResource(R.string.repository_quick_deploy, yesNo))
                    if (repo.lastDeployAt.isNotBlank()) {
                        Text(stringResource(R.string.site_last_deploy, repo.lastDeployAt))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runAction("") { PloiApi.toggleQuickDeploy(token, serverId, siteId) }
                        }, enabled = !busy) { Text(stringResource(R.string.quick_deploy_toggle)) }
                        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                            Text(stringResource(R.string.repository_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    CustomDeploymentForm(busy = busy, onEnable = { script ->
                        runAction(customEnabledMessage) {
                            PloiApi.enableCustomDeployments(token, serverId, siteId, script)
                        }
                    })
                }
                if (!loading && missing) {
                    Text(stringResource(R.string.repository_none))
                    InstallRepositoryForm(busy = busy, onInstall = { request ->
                        runAction(installedMessage) { PloiApi.installRepository(token, serverId, siteId, request) }
                    })
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } }
    )

    if (confirmDelete) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_delete_repository, siteDomain),
            confirmLabel = R.string.repository_delete,
            onConfirmed = {
                confirmDelete = false
                runAction(deletedMessage) { PloiApi.deleteRepository(token, serverId, siteId) }
            },
            onDismiss = { confirmDelete = false })
    }
}

/** Install form for POST /sites/{site}/repository with documented validation. */
@Composable
private fun InstallRepositoryForm(busy: Boolean, onInstall: (InstallRepositoryRequest) -> Unit) {
    var provider by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sourceProviderId by remember { mutableStateOf("") }
    var installComposer by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.repository_install), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = provider, onValueChange = { provider = it },
            label = { Text(stringResource(R.string.repository_provider_label)) },
            supportingText = { Text(stringResource(R.string.repository_provider_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = branch, onValueChange = { branch = it },
            label = { Text(stringResource(R.string.repository_branch_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.repository_name_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = sourceProviderId, onValueChange = { sourceProviderId = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.repository_source_provider_label)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.repository_install_composer), Modifier.padding(top = 12.dp))
            Switch(checked = installComposer, onCheckedChange = { installComposer = it })
        }
        if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            val request = try {
                InstallRepositoryRequest(
                    provider = provider.trim(), branch = branch.trim(), name = name.trim(),
                    sourceProviderId = sourceProviderId.toLongOrNull(), installComposer = installComposer
                )
            } catch (invalidInput: IllegalArgumentException) {
                invalid = true
                null
            }
            if (request != null) {
                invalid = false
                onInstall(request)
            }
        }, enabled = !busy && provider.isNotBlank() && branch.isNotBlank() && name.isNotBlank()) {
            Text(stringResource(R.string.repository_install))
        }
    }
}

/** Optional initial-installation script for POST …/repository/custom-deployments. */
@Composable
private fun CustomDeploymentForm(busy: Boolean, onEnable: (String) -> Unit) {
    var showForm by remember { mutableStateOf(false) }
    var script by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showForm = !showForm }, enabled = !busy) {
            Text(stringResource(R.string.custom_deployments_enable))
        }
        if (showForm) {
            OutlinedTextField(value = script, onValueChange = { script = it },
                label = { Text(stringResource(R.string.custom_deployments_script)) },
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                showForm = false
                onEnable(script.trim())
            }, enabled = !busy) { Text(stringResource(R.string.custom_deployments_enable)) }
        }
    }
}

/** Deploy script viewer/editor; saving replaces the script after re-authentication. */
@Composable
internal fun DeployScriptDialog(
    token: String, serverId: Long, siteId: Long, siteDomain: String,
    lock: AppLock, activity: FragmentActivity, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var feedback by remember { mutableStateOf("") }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    val savedMessage = stringResource(R.string.deploy_script_saved)

    LaunchedEffect(token, serverId, siteId) {
        loading = true
        error = null
        try {
            val fetched = withContext(Dispatchers.IO) { PloiApi.deployScript(token, serverId, siteId) }
            content = fetched
            draft = fetched
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deploy_script)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) Text(feedback)
                content?.let { current ->
                    if (editing) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it },
                            label = { Text(stringResource(R.string.deploy_script_edit)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(current, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                Button(onClick = {
                    if (draft.isNotBlank() && draft.length <= DEPLOY_SCRIPT_MAX_LENGTH) pendingSave = draft
                }, enabled = !saving && draft.isNotBlank() && draft.length <= DEPLOY_SCRIPT_MAX_LENGTH) {
                    Text(stringResource(R.string.save))
                }
            } else {
                Button(onClick = { editing = true }, enabled = content != null && !saving) {
                    Text(stringResource(R.string.edit_site))
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    val pending = pendingSave
    if (pending != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_deploy_script_save, siteDomain),
            confirmLabel = R.string.save,
            onConfirmed = {
                pendingSave = null
                saving = true
                error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PloiApi.updateDeployScript(token, serverId, siteId, pending) }
                        content = pending
                        editing = false
                        feedback = savedMessage
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure
                    } finally {
                        saving = false
                    }
                }
            },
            onDismiss = { pendingSave = null })
    }
}

/** .env viewer/editor; saving replaces the file after re-authentication. */
@Composable
internal fun EnvDialog(
    token: String, serverId: Long, siteId: Long, siteDomain: String,
    lock: AppLock, activity: FragmentActivity, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf("") }
    val savedMessage = stringResource(R.string.env_saved)

    LaunchedEffect(token, serverId, siteId) {
        loading = true
        error = null
        try {
            val fetched = withContext(Dispatchers.IO) { PloiApi.environmentFile(token, serverId, siteId) }
            content = fetched
            draft = fetched
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.env_file)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) Text(feedback)
                content?.let { current ->
                    if (editing) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it },
                            label = { Text(stringResource(R.string.env_edit)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(current, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                Button(onClick = {
                    if (draft.length >= ENV_CONTENT_MIN_LENGTH) pendingSave = draft
                }, enabled = !saving && draft.length >= ENV_CONTENT_MIN_LENGTH) {
                    Text(stringResource(R.string.save))
                }
            } else {
                Button(onClick = { editing = true }, enabled = content != null && !saving) {
                    Text(stringResource(R.string.edit_site))
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    val pending = pendingSave
    if (pending != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_env_save, siteDomain),
            confirmLabel = R.string.save,
            onConfirmed = {
                pendingSave = null
                saving = true
                error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PloiApi.updateEnvironmentFile(token, serverId, siteId, pending) }
                        content = pending
                        editing = false
                        feedback = savedMessage
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure
                    } finally {
                        saving = false
                    }
                }
            },
            onDismiss = { pendingSave = null })
    }
}

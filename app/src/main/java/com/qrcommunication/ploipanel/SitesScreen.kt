package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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

@Composable
internal fun SitesScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var selectedId by remember(token, serverId) { mutableStateOf<Long?>(null) }
    var creating by remember(token, serverId) { mutableStateOf(false) }
    var result by remember(token, serverId) { mutableStateOf<SitePage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(false) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.sites(token, serverId, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        val createdMessage = stringResource(R.string.site_created)
        var feedback by remember(token, serverId) { mutableStateOf("") }
        val detail: @Composable (Long) -> Unit = { id ->
            SiteDetail(token, serverId, id, lock, activity, onDeleted = {
                selectedId = null
                feedback = ""
                refresh++
            }, onChanged = { refresh++ })
        }
        Column {
        if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(bottom = 8.dp))
        if (creating) {
            CreateSiteForm(token, serverId,
                onDone = { createdId -> creating = false; selectedId = createdId; feedback = createdMessage; refresh++ },
                onCancel = { creating = false })
        } else if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    SiteList(result, loading, error, page,
                        onPage = { page = it; selectedId = null }, onRefresh = { refresh++ },
                        onSelect = { selectedId = it }, onCreate = { creating = true })
                }
                Column(Modifier.weight(1f)) {
                    val id = selectedId
                    if (id == null) Text(stringResource(R.string.select_site)) else detail(id)
                }
            }
        } else if (selectedId != null) {
            Column {
                OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.back_sites)) }
                detail(selectedId!!)
            }
        } else {
            SiteList(result, loading, error, page,
                onPage = { page = it; selectedId = null }, onRefresh = { refresh++ },
                onSelect = { selectedId = it }, onCreate = { creating = true })
        }
        }
    }
}

@Composable
private fun SiteList(
    data: SitePage?, loading: Boolean, error: Throwable?, page: Int,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Long) -> Unit, onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
            OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.new_site)) }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error)
        if (data != null) {
            if (data.sites.isEmpty()) Text(stringResource(R.string.empty_sites))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { onPage(page + 1) }, enabled = data.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.sites, key = { it.id }) { site ->
                    Card(onClick = { onSelect(site.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(site.domain, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.server_status, site.status))
                        }
                    }
                }
            }
        }
    }
}

/** Creation form for POST /servers/{server}/sites with documented validation sets. */
@Composable
private fun CreateSiteForm(token: String, serverId: Long, onDone: (Long) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf("") }
    var webDirectory by remember { mutableStateOf("/public") }
    var projectRoot by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("") }
    var systemUser by remember { mutableStateOf("") }
    var webhook by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.new_site), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = domain, onValueChange = { domain = it },
            label = { Text(stringResource(R.string.root_domain_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = webDirectory, onValueChange = { webDirectory = it },
            label = { Text(stringResource(R.string.web_directory_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = projectRoot, onValueChange = { projectRoot = it },
            label = { Text(stringResource(R.string.project_root_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = projectType, onValueChange = { projectType = it },
            label = { Text(stringResource(R.string.project_type_label)) },
            supportingText = { Text(stringResource(R.string.project_type_hint)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = systemUser, onValueChange = { systemUser = it },
            label = { Text(stringResource(R.string.system_user_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = webhook, onValueChange = { webhook = it },
            label = { Text(stringResource(R.string.webhook_optional_label)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth())
        if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
        if (error != null) ApiErrorText(error!!)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            Button(onClick = {
                val request = try {
                    CreateSiteRequest(
                        rootDomain = domain.trim(), webDirectory = webDirectory.trim(),
                        projectRoot = projectRoot.trim(), projectType = projectType.trim(),
                        systemUser = systemUser.trim(), webhookUrl = webhook.trim()
                    )
                } catch (invalidInput: IllegalArgumentException) {
                    invalid = true
                    null
                }
                if (request != null) {
                    invalid = false
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val created = withContext(Dispatchers.IO) { PloiApi.createSite(token, serverId, request) }
                            onDone(created.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure
                            busy = false
                        }
                    }
                }
            }, enabled = !busy && domain.isNotBlank() && webDirectory.isNotBlank()) {
                Text(stringResource(R.string.create_site_submit))
            }
        }
    }
}

/** Full site detail with every documented site action; destructive ones re-authenticate. */
@Composable
private fun SiteDetail(
    token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity,
    onDeleted: () -> Unit, onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var site by remember(token, serverId, siteId) { mutableStateOf<Site?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<Throwable?>(null) }
    var actionFeedback by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var phpDialog by remember { mutableStateOf(false) }
    var cloneDialog by remember { mutableStateOf(false) }
    var suspendDialog by remember { mutableStateOf(false) }
    var logsDialog by remember { mutableStateOf(false) }
    var horizonDialog by remember { mutableStateOf(false) }
    var nginxDialog by remember { mutableStateOf(false) }
    var repositoryDialog by remember { mutableStateOf(false) }
    var deployScriptDialog by remember { mutableStateOf(false) }
    var envDialog by remember { mutableStateOf(false) }
    var confirmDeploy by remember { mutableStateOf(false) }
    var confirmDeployProduction by remember { mutableStateOf(false) }
    var pendingSuspendReason by remember { mutableStateOf("") }
    var confirmSuspend by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmResetPermissions by remember { mutableStateOf(false) }
    var pendingNginxContent by remember { mutableStateOf<String?>(null) }

    val updatedMessage = stringResource(R.string.site_updated)
    val suspendedMessage = stringResource(R.string.site_suspended)
    val resumedMessage = stringResource(R.string.site_resumed)
    val clonedMessage = stringResource(R.string.site_cloned)
    val permissionsMessage = stringResource(R.string.permissions_reset)
    val nginxSavedMessage = stringResource(R.string.nginx_saved)
    val deployStartedMessage = stringResource(R.string.deploy_started)
    val deployProductionStartedMessage = stringResource(R.string.deploy_production_started)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loading = true
        error = null
        try {
            site = withContext(Dispatchers.IO) { PloiApi.site(token, serverId, siteId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            site = null
            error = failure
        } finally {
            loading = false
        }
    }

    fun runAction(feedback: String, refreshAfter: Boolean = true, block: suspend () -> Unit) {
        busy = true
        actionError = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                actionFeedback = feedback
                if (refreshAfter) {
                    refresh++
                    onChanged()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                actionError = failure
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        site?.let { details ->
            val yes = stringResource(R.string.flag_yes)
            val no = stringResource(R.string.flag_no)
            Text(details.domain, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.server_status, details.status))
            if (details.phpVersion.isNotBlank()) Text(stringResource(R.string.site_php, details.phpVersion))
            if (details.webDirectory.isNotBlank()) Text(stringResource(R.string.site_directory, details.webDirectory))
            if (details.projectType.isNotBlank()) Text(stringResource(R.string.site_project_type, details.projectType))
            if (details.systemUser.isNotBlank()) Text(stringResource(R.string.site_system_user, details.systemUser))
            if (details.diskUsage.isNotBlank()) Text(stringResource(R.string.site_disk, details.diskUsage))
            if (details.healthUrl.isNotBlank()) Text(stringResource(R.string.site_health, details.healthUrl))
            if (details.testDomain.isNotBlank()) Text(stringResource(R.string.test_domain, details.testDomain))
            if (details.lastDeployAt.isNotBlank()) Text(stringResource(R.string.site_last_deploy, details.lastDeployAt))
            if (details.createdAt.isNotBlank()) Text(stringResource(R.string.site_created_at, details.createdAt))
            Text(stringResource(R.string.site_repository, if (details.hasRepository) yes else no))
            Text(stringResource(R.string.site_zero_downtime, if (details.zeroDowntimeDeployment) yes else no))
            Text(stringResource(R.string.site_robots, if (details.disableRobots) yes else no))
            Text(stringResource(R.string.site_fastcgi, if (details.fastcgiCache) yes else no))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }, enabled = !busy) {
                    Text(stringResource(R.string.edit_site))
                }
                if (details.status.equals("suspended", ignoreCase = true)) {
                    OutlinedButton(onClick = {
                        runAction(resumedMessage) { PloiApi.resumeSite(token, serverId, siteId) }
                    }, enabled = !busy) { Text(stringResource(R.string.resume_site)) }
                } else {
                    OutlinedButton(onClick = { suspendDialog = true }, enabled = !busy) {
                        Text(stringResource(R.string.suspend_site))
                    }
                }
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text(stringResource(R.string.delete_site), color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { phpDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.php_version_change))
                }
                OutlinedButton(onClick = { cloneDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.clone_site))
                }
                OutlinedButton(onClick = { confirmResetPermissions = true }, enabled = !busy) {
                    Text(stringResource(R.string.reset_permissions))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nginxDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.nginx_configuration))
                }
                OutlinedButton(onClick = { logsDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.site_logs))
                }
                OutlinedButton(onClick = { horizonDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.horizon_statistics))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repositoryDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.repository_title))
                }
                OutlinedButton(onClick = { confirmDeploy = true }, enabled = !busy) {
                    Text(stringResource(R.string.deploy_site))
                }
                OutlinedButton(onClick = { confirmDeployProduction = true }, enabled = !busy) {
                    Text(stringResource(R.string.deploy_production))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { deployScriptDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.deploy_script))
                }
                OutlinedButton(onClick = { envDialog = true }, enabled = !busy) {
                    Text(stringResource(R.string.env_file))
                }
            }
            TestDomainSection(token, serverId, siteId, busy,
                onBusy = { busy = it }, onError = { actionError = it },
                onChanged = { refresh++; onChanged() })
        }
        if (actionError != null) ApiErrorText(actionError!!)
        if (actionFeedback.isNotEmpty()) Text(actionFeedback)
    }

    if (editing && site != null) {
        EditSiteDialog(current = site!!, busy = busy,
            onSave = { domain, zeroDowntime, robots ->
                editing = false
                runAction(updatedMessage) {
                    PloiApi.updateSite(
                        token, serverId, siteId,
                        rootDomain = domain,
                        zeroDowntimeDeployment = zeroDowntime.takeIf { it != site!!.zeroDowntimeDeployment },
                        disableRobots = robots.takeIf { it != site!!.disableRobots }
                    )
                }
            },
            onDismiss = { editing = false })
    }
    if (phpDialog) {
        PhpVersionDialog(busy = busy, onPick = { version ->
            phpDialog = false
            runAction(updatedMessage) { PloiApi.changeSitePhpVersion(token, serverId, siteId, version) }
        }, onDismiss = { phpDialog = false })
    }
    if (cloneDialog) {
        CloneSiteDialog(busy = busy, onClone = { target, domain ->
            cloneDialog = false
            runAction(clonedMessage, refreshAfter = false) {
                PloiApi.cloneSite(token, serverId, siteId, target, domain)
            }
        }, onDismiss = { cloneDialog = false })
    }
    if (suspendDialog) {
        SuspendSiteDialog(busy = busy, onConfirm = { reason ->
            suspendDialog = false
            pendingSuspendReason = reason
            confirmSuspend = true
        }, onDismiss = { suspendDialog = false })
    }
    if (confirmSuspend && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_suspend_site, site!!.domain),
            confirmLabel = R.string.suspend_site,
            onConfirmed = {
                confirmSuspend = false
                val reason = pendingSuspendReason
                runAction(suspendedMessage) { PloiApi.suspendSite(token, serverId, siteId, reason) }
            },
            onDismiss = { confirmSuspend = false })
    }
    if (confirmDelete && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_delete_site, site!!.domain),
            confirmLabel = R.string.delete_site,
            onConfirmed = {
                confirmDelete = false
                busy = true
                actionError = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PloiApi.deleteSite(token, serverId, siteId) }
                        onDeleted()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        actionError = failure
                        busy = false
                    }
                }
            },
            onDismiss = { confirmDelete = false })
    }
    if (confirmResetPermissions && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_reset_permissions, site!!.domain),
            confirmLabel = R.string.reset_permissions,
            onConfirmed = {
                confirmResetPermissions = false
                runAction(permissionsMessage) { PloiApi.resetSitePermissions(token, serverId, siteId) }
            },
            onDismiss = { confirmResetPermissions = false })
    }
    val nginxContent = pendingNginxContent
    if (nginxContent != null && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_nginx_save, site!!.domain),
            confirmLabel = R.string.save,
            onConfirmed = {
                pendingNginxContent = null
                runAction(nginxSavedMessage, refreshAfter = false) {
                    PloiApi.updateNginxConfiguration(token, serverId, siteId, nginxContent)
                }
            },
            onDismiss = { pendingNginxContent = null })
    }
    if (logsDialog) SiteLogsDialog(token, serverId, siteId, onDismiss = { logsDialog = false })
    if (horizonDialog) HorizonDialog(token, serverId, onDismiss = { horizonDialog = false })
    if (confirmDeploy && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_deploy_site, site!!.domain),
            confirmLabel = R.string.deploy_site,
            onConfirmed = {
                confirmDeploy = false
                runAction(deployStartedMessage, refreshAfter = false) {
                    PloiApi.deploySite(token, serverId, siteId)
                }
            },
            onDismiss = { confirmDeploy = false })
    }
    if (confirmDeployProduction && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.confirm_deploy_production, site!!.domain),
            confirmLabel = R.string.deploy_production,
            onConfirmed = {
                confirmDeployProduction = false
                runAction(deployProductionStartedMessage, refreshAfter = false) {
                    PloiApi.deployToProduction(token, serverId, siteId)
                }
            },
            onDismiss = { confirmDeployProduction = false })
    }
    if (repositoryDialog && site != null) {
        RepositoryDialog(token, serverId, siteId, site!!.domain, lock, activity,
            onChanged = { refresh++; onChanged() },
            onDismiss = { repositoryDialog = false })
    }
    if (deployScriptDialog && site != null) {
        DeployScriptDialog(token, serverId, siteId, site!!.domain, lock, activity,
            onDismiss = { deployScriptDialog = false })
    }
    if (envDialog && site != null) {
        EnvDialog(token, serverId, siteId, site!!.domain, lock, activity,
            onDismiss = { envDialog = false })
    }
    if (nginxDialog) {
        NginxDialog(token, serverId, siteId, onSave = { content ->
            nginxDialog = false
            pendingNginxContent = content
        }, onDismiss = { nginxDialog = false })
    }
}

/** Test domain status + enable/disable (GET 404 means none is active). */
@Composable
private fun TestDomainSection(
    token: String, serverId: Long, siteId: Long, busy: Boolean,
    onBusy: (Boolean) -> Unit, onError: (Throwable?) -> Unit, onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var info by remember(token, serverId, siteId) { mutableStateOf<TestDomain?>(null) }
    var loaded by remember(token, serverId, siteId) { mutableStateOf(false) }
    val dnsHint = stringResource(R.string.test_domain_dns_hint)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loaded = false
        try {
            info = withContext(Dispatchers.IO) { PloiApi.testDomain(token, serverId, siteId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            info = null
            if (failure !is PloiHttpException || failure.status != 404) onError(failure)
        } finally {
            loaded = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!loaded) {
            CircularProgressIndicator()
        } else if (info == null || info!!.testDomain.isBlank()) {
            Text(stringResource(R.string.test_domain_none))
            OutlinedButton(onClick = {
                onBusy(true)
                onError(null)
                scope.launch {
                    try {
                        info = withContext(Dispatchers.IO) { PloiApi.enableTestDomain(token, serverId, siteId) }
                        refresh++
                        onChanged()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        onError(failure)
                    } finally {
                        onBusy(false)
                    }
                }
            }, enabled = !busy) { Text(stringResource(R.string.enable_test_domain)) }
        } else {
            Text(stringResource(R.string.test_domain, info!!.fullTestDomain.ifBlank { info!!.testDomain }))
            Text(dnsHint, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                onBusy(true)
                onError(null)
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PloiApi.disableTestDomain(token, serverId, siteId) }
                        info = null
                        refresh++
                        onChanged()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        onError(failure)
                    } finally {
                        onBusy(false)
                    }
                }
            }, enabled = !busy) { Text(stringResource(R.string.disable_test_domain)) }
        }
    }
}

/** Edit dialog for PATCH /sites/{site}: optional new root domain + both toggleable flags. */
@Composable
private fun EditSiteDialog(
    current: Site, busy: Boolean,
    onSave: (domain: String, zeroDowntime: Boolean, robots: Boolean) -> Unit, onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var zeroDowntime by remember { mutableStateOf(current.zeroDowntimeDeployment) }
    var robots by remember { mutableStateOf(current.disableRobots) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_site)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.root_domain_label)) },
                    placeholder = { Text(current.domain) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.zero_downtime_label), Modifier.padding(top = 12.dp))
                    Switch(checked = zeroDowntime, onCheckedChange = { zeroDowntime = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.robots_block_label), Modifier.padding(top = 12.dp))
                    Switch(checked = robots, onCheckedChange = { robots = it })
                }
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = domain.trim()
                invalid = trimmed.isNotEmpty() && try {
                    validateRootDomain(trimmed)
                    false
                } catch (invalidInput: IllegalArgumentException) {
                    true
                }
                if (!invalid) {
                    val changedDomain = trimmed != current.domain
                    val flagsChanged = zeroDowntime != current.zeroDowntimeDeployment || robots != current.disableRobots
                    if (changedDomain || flagsChanged) {
                        onSave(if (changedDomain) trimmed else "", zeroDowntime, robots)
                    } else {
                        onDismiss()
                    }
                }
            }, enabled = !busy) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Dialog for POST /sites/{site}/php-version with the documented version set. */
@Composable
private fun PhpVersionDialog(busy: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var version by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.php_version_change)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = version, onValueChange = { version = it },
                    label = { Text(stringResource(R.string.php_label)) },
                    supportingText = { Text(stringResource(R.string.php_version_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = version.trim()
                invalid = trimmed !in SITE_PHP_VERSIONS
                if (!invalid) onPick(trimmed)
            }, enabled = !busy && version.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Dialog for POST /sites/{site}/clone: target server ID + optional new domain. */
@Composable
private fun CloneSiteDialog(busy: Boolean, onClone: (Long, String) -> Unit, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clone_site)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = target, onValueChange = { target = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.clone_target_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.clone_domain_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val targetId = target.toLongOrNull()
                val trimmedDomain = domain.trim()
                val domainValid = trimmedDomain.isEmpty() || try {
                    validateRootDomain(trimmedDomain)
                    true
                } catch (invalidInput: IllegalArgumentException) {
                    false
                }
                invalid = targetId == null || targetId <= 0 || !domainValid
                if (!invalid) onClone(targetId!!, trimmedDomain)
            }, enabled = !busy && target.isNotBlank()) { Text(stringResource(R.string.clone_site)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Reason prompt before the PIN/biometric-gated suspend call. */
@Composable
private fun SuspendSiteDialog(busy: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.suspend_site)) },
        text = {
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                label = { Text(stringResource(R.string.suspend_reason_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason.trim()) }, enabled = !busy) {
                Text(stringResource(R.string.suspend_site))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Paginated site logs; tapping an entry fetches its full content. */
@Composable
private fun SiteLogsDialog(token: String, serverId: Long, siteId: Long, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf<SiteLogPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var selected by remember { mutableStateOf<SiteLogEntry?>(null) }
    var detailLoading by remember { mutableStateOf(false) }

    LaunchedEffect(token, serverId, siteId, page) {
        loading = true
        error = null
        selected = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.siteLogs(token, serverId, siteId, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_logs)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                result?.let { data ->
                    if (data.logs.isEmpty()) Text(stringResource(R.string.empty_site_logs))
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(data.logs, key = { it.id }) { entry ->
                            Card(onClick = {
                                detailLoading = true
                                scope.launch {
                                    try {
                                        selected = withContext(Dispatchers.IO) {
                                            PloiApi.siteLog(token, serverId, siteId, entry.id)
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        error = failure
                                    } finally {
                                        detailLoading = false
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(entry.description, style = MaterialTheme.typography.bodyLarge)
                                    val whenText = entry.createdAtHuman.ifBlank { entry.createdAt }
                                    if (whenText.isNotBlank()) Text(whenText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (detailLoading) CircularProgressIndicator()
                    selected?.let { entry ->
                        if (entry.content.isNotBlank()) {
                            Text(entry.content, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** Laravel Horizon statistics with the four documented types. */
@Composable
private fun HorizonDialog(token: String, serverId: Long, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("stats") }
    var result by remember { mutableStateOf<HorizonStatistics?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, serverId, type) {
        loading = true
        error = null
        result = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.horizonStatistics(token, serverId, type) }
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
        title = { Text(stringResource(R.string.horizon_statistics)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(
                        "stats" to R.string.horizon_type_stats, "workload" to R.string.horizon_type_workload,
                        "masters" to R.string.horizon_type_masters, "failed" to R.string.horizon_type_failed
                    )
                    labels.forEach { (value, label) ->
                        OutlinedButton(onClick = { type = value }, enabled = type != value) {
                            Text(stringResource(label))
                        }
                    }
                }
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                when (val stats = result) {
                    is HorizonStatistics.Stats -> {
                        Text(stringResource(R.string.server_status, stats.status))
                        Text(stringResource(R.string.horizon_failed_jobs) + ": ${stats.failedJobs}")
                        Text(stringResource(R.string.horizon_jobs_per_minute) + ": ${stats.jobsPerMinute}")
                        Text(stringResource(R.string.horizon_recent_jobs) + ": ${stats.recentJobs}")
                        Text(stringResource(R.string.horizon_processes) + ": ${stats.processes}")
                        Text(stringResource(R.string.horizon_paused) + ": ${stats.pausedMasters}")
                        stats.wait.forEach { (queue, wait) -> Text("$queue: ${wait}s") }
                    }
                    is HorizonStatistics.Workload -> stats.queues.forEach { queue ->
                        Text("${queue.name} — ${queue.length} jobs, ${queue.wait}s, ${queue.processes} proc.")
                    }
                    is HorizonStatistics.Masters -> stats.masters.forEach { master ->
                        Text("${master.name} (${master.status}) — ${master.supervisors} " +
                            stringResource(R.string.horizon_supervisors))
                    }
                    is HorizonStatistics.Failed -> {
                        Text(stringResource(R.string.horizon_total) + ": ${stats.total}")
                        stats.jobs.forEach { job -> Text("${job.name} [${job.queue}] — ${job.exception}") }
                    }
                    null -> Unit
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** NGINX configuration viewer/editor; saving is gated by re-authentication upstream. */
@Composable
private fun NginxDialog(token: String, serverId: Long, siteId: Long, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var content by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, serverId, siteId) {
        loading = true
        error = null
        try {
            val fetched = withContext(Dispatchers.IO) { PloiApi.nginxConfiguration(token, serverId, siteId) }
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
        title = { Text(stringResource(R.string.nginx_configuration)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                content?.let { current ->
                    if (editing) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it },
                            label = { Text(stringResource(R.string.nginx_edit)) },
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
                Button(onClick = { if (draft.isNotBlank()) onSave(draft) }, enabled = draft.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            } else {
                Button(onClick = { editing = true }, enabled = content != null) {
                    Text(stringResource(R.string.edit_site))
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

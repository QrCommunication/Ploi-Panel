package com.qrcommunication.ploipanel

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.key
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

internal enum class SiteCategory(@param:StringRes val title: Int, @param:StringRes val description: Int) {
    OVERVIEW(R.string.site_category_overview, R.string.site_category_overview_description),
    DEPLOYMENT(R.string.site_category_deployment, R.string.site_category_deployment_description),
    CONFIGURATION(R.string.site_category_configuration, R.string.site_category_configuration_description),
    SECURITY(R.string.site_category_security, R.string.site_category_security_description),
    OPERATIONS(R.string.site_category_operations, R.string.site_category_operations_description)
}

internal fun siteSubsectionCategory(section: Int): SiteCategory = when (section) {
    3, 4 -> SiteCategory.SECURITY
    in 1..2, in 5..9 -> SiteCategory.OPERATIONS
    else -> throw IllegalArgumentException("Unknown site subsection: $section")
}

@Composable
private fun SiteNavigationCard(
    @StringRes title: Int, @StringRes description: Int? = null,
    enabled: Boolean, destructive: Boolean = false, onClick: () -> Unit
) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (description != null) Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
    }
}

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
        result = null
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
            key(token, serverId, id) {
                SiteDetail(token, serverId, id, lock, activity, onDeleted = {
                    selectedId = null
                    feedback = ""
                    refresh++
                }, onChanged = { refresh++ })
            }
        }
        Column(Modifier.fillMaxSize()) {
        if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(bottom = 8.dp))
        if (creating) {
            CreateSiteForm(token, serverId,
                onDone = { createdId -> creating = false; selectedId = createdId; feedback = createdMessage; refresh++ },
                onCancel = { creating = false })
        } else if (expanded) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    SiteList(result, loading, error, page,
                        onPage = { page = it; selectedId = null }, onRefresh = { refresh++ },
                        onSelect = { selectedId = it }, onCreate = { creating = true })
                }
                Column(Modifier.weight(1f).fillMaxSize()) {
                    val id = selectedId
                    if (id == null) Text(stringResource(R.string.select_site)) else detail(id)
                }
            }
        } else if (selectedId != null) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.back_sites)) }
                Box(Modifier.weight(1f).fillMaxWidth()) { detail(selectedId!!) }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SiteList(result, loading, error, page,
                    onPage = { page = it; selectedId = null }, onRefresh = { refresh++ },
                    onSelect = { selectedId = it }, onCreate = { creating = true })
            }
        }
        }
    }
}

@Composable
private fun SiteList(
    data: SitePage?, loading: Boolean, error: Throwable?, page: Int,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Long) -> Unit, onCreate: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
            OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.new_site)) }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error)
        if (data != null) {
            if (data.sites.isEmpty()) Text(stringResource(R.string.empty_sites))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { onPage(page + 1) }, enabled = data.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    var siteSection by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var siteCategory by remember(token, serverId, siteId) { mutableStateOf<SiteCategory?>(null) }
    var confirmResume by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<Triple<String, Boolean, Boolean>?>(null) }
    var pendingPhpVersion by remember { mutableStateOf<String?>(null) }
    var pendingClone by remember { mutableStateOf<Pair<Long, String>?>(null) }

    BackHandler(enabled = siteSection != 0 || siteCategory != null) {
        if (siteSection != 0) {
            siteCategory = siteSubsectionCategory(siteSection)
            siteSection = 0
        } else {
            siteCategory = null
        }
    }

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
        site = null
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
        actionFeedback = ""
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

    if (siteSection != 0) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            OutlinedButton(onClick = {
                siteCategory = siteSubsectionCategory(siteSection)
                siteSection = 0
            }) { Text(stringResource(R.string.back)) }
            Box(Modifier.weight(1f).fillMaxWidth()) { when (siteSection) {
                1 -> QueueWorkersScreen(token, serverId, siteId, lock, activity)
                2 -> RedirectsScreen(token, serverId, siteId, lock, activity)
                3 -> CertificatesScreen(token, serverId, siteId, lock, activity)
                4 -> AuthUsersScreen(token, serverId, siteId, lock, activity)
                5 -> AliasesScreen(token, serverId, siteId, lock, activity)
                6 -> TenantsScreen(token, serverId, siteId, lock, activity)
                7 -> SiteMonitorsScreen(token, serverId, siteId, lock, activity)
                8 -> site?.let { details ->
                    AppsScreen(token, serverId, siteId, details, lock, activity, onChanged = { refresh++; onChanged() })
                }
                else -> WordPressScreen(token, serverId, siteId, lock, activity)
            } }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (siteCategory == null) {
            if (loading) CircularProgressIndicator()
            if (error != null) ApiErrorText(error!!)
            site?.let { details ->
                Text(details.domain, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SiteCategory.entries.forEach { category ->
                        SiteNavigationCard(category.title, category.description, enabled = !busy) {
                            siteCategory = category
                        }
                    }
                }
            }
        } else {
            OutlinedButton(onClick = { siteCategory = null }, enabled = !busy) {
                Text(stringResource(R.string.site_category_back))
            }
            Text(stringResource(siteCategory!!.title), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                site?.let { details ->
                    when (siteCategory ?: return@let) {
                        SiteCategory.OVERVIEW -> {
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
                            SiteNavigationCard(R.string.clone_site, enabled = !busy) { cloneDialog = true }
                            if (details.status.equals("suspended", ignoreCase = true)) {
                                SiteNavigationCard(R.string.resume_site, enabled = !busy) { confirmResume = true }
                            } else {
                                SiteNavigationCard(R.string.suspend_site, enabled = !busy) { suspendDialog = true }
                            }
                            SiteNavigationCard(R.string.delete_site, enabled = !busy, destructive = true) { confirmDelete = true }
                        }
                        SiteCategory.DEPLOYMENT -> {
                            SiteNavigationCard(R.string.repository_title, enabled = !busy) { repositoryDialog = true }
                            SiteNavigationCard(R.string.deploy_site, enabled = !busy) { confirmDeploy = true }
                            SiteNavigationCard(R.string.deploy_production, enabled = !busy) { confirmDeployProduction = true }
                            SiteNavigationCard(R.string.deploy_script, enabled = !busy) { deployScriptDialog = true }
                        }
                        SiteCategory.CONFIGURATION -> {
                            SiteNavigationCard(R.string.edit_site, enabled = !busy) { editing = true }
                            SiteNavigationCard(R.string.php_version_change, enabled = !busy) { phpDialog = true }
                            SiteNavigationCard(R.string.nginx_configuration, enabled = !busy) { nginxDialog = true }
                            SiteNavigationCard(R.string.env_file, enabled = !busy) { envDialog = true }
                            SiteNavigationCard(R.string.reset_permissions, enabled = !busy) { confirmResetPermissions = true }
                            TestDomainSection(token, serverId, siteId, busy, lock, activity,
                                onBusy = { busy = it }, onError = { actionError = it },
                                onChanged = { refresh++; onChanged() })
                        }
                        SiteCategory.SECURITY -> {
                            SiteNavigationCard(R.string.certificates_tab, enabled = !busy) { siteSection = 3 }
                            SiteNavigationCard(R.string.auth_users_tab, enabled = !busy) { siteSection = 4 }
                        }
                        SiteCategory.OPERATIONS -> {
                            SiteNavigationCard(R.string.queues_tab, enabled = !busy) { siteSection = 1 }
                            SiteNavigationCard(R.string.site_logs, enabled = !busy) { logsDialog = true }
                            SiteNavigationCard(R.string.horizon_statistics, enabled = !busy) { horizonDialog = true }
                            SiteNavigationCard(R.string.redirects_tab, enabled = !busy) { siteSection = 2 }
                            SiteNavigationCard(R.string.aliases_tab, enabled = !busy) { siteSection = 5 }
                            SiteNavigationCard(R.string.tenants_tab, enabled = !busy) { siteSection = 6 }
                            SiteNavigationCard(R.string.site_monitors_tab, enabled = !busy) { siteSection = 7 }
                            SiteNavigationCard(R.string.apps_tab, enabled = !busy) { siteSection = 8 }
                            SiteNavigationCard(R.string.wordpress_tab, enabled = !busy) { siteSection = 9 }
                        }
                    }
                }
            }
        }
        if (actionError != null) ApiErrorText(actionError!!)
        if (actionFeedback.isNotEmpty()) Text(actionFeedback)
    }

    if (editing && site != null) {
        EditSiteDialog(current = site!!, busy = busy,
            onSave = { domain, zeroDowntime, robots ->
                editing = false
                pendingEdit = Triple(domain, zeroDowntime, robots)
            },
            onDismiss = { editing = false })
    }
    if (phpDialog) {
        PhpVersionDialog(busy = busy, onPick = { version ->
            phpDialog = false
            pendingPhpVersion = version
        }, onDismiss = { phpDialog = false })
    }
    if (cloneDialog) {
        CloneSiteDialog(busy = busy, onClone = { target, domain ->
            cloneDialog = false
            pendingClone = target to domain
        }, onDismiss = { cloneDialog = false })
    }
    pendingEdit?.let { (domain, zeroDowntime, robots) ->
        if (site != null) SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.site_category_edit_confirmation, site!!.domain),
            confirmLabel = R.string.save,
            onConfirmed = {
                pendingEdit = null
                val current = site!!
                runAction(updatedMessage) {
                    PloiApi.updateSite(token, serverId, siteId, rootDomain = domain,
                        zeroDowntimeDeployment = zeroDowntime.takeIf { it != current.zeroDowntimeDeployment },
                        disableRobots = robots.takeIf { it != current.disableRobots })
                }
            }, onDismiss = { pendingEdit = null })
    }
    pendingPhpVersion?.let { version ->
        if (site != null) SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.site_category_php_confirmation, site!!.domain),
            confirmLabel = R.string.save,
            onConfirmed = {
                pendingPhpVersion = null
                runAction(updatedMessage) { PloiApi.changeSitePhpVersion(token, serverId, siteId, version) }
            }, onDismiss = { pendingPhpVersion = null })
    }
    pendingClone?.let { (target, domain) ->
        if (site != null) SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.site_category_clone_confirmation, site!!.domain),
            confirmLabel = R.string.clone_site,
            onConfirmed = {
                pendingClone = null
                runAction(clonedMessage, refreshAfter = false) {
                    PloiApi.cloneSite(token, serverId, siteId, target, domain)
                }
            }, onDismiss = { pendingClone = null })
    }
    if (confirmResume && site != null) {
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(R.string.site_category_resume_confirmation, site!!.domain),
            confirmLabel = R.string.resume_site,
            onConfirmed = {
                confirmResume = false
                runAction(resumedMessage) { PloiApi.resumeSite(token, serverId, siteId) }
            }, onDismiss = { confirmResume = false })
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
                actionFeedback = ""
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
    token: String, serverId: Long, siteId: Long, busy: Boolean, lock: AppLock, activity: FragmentActivity,
    onBusy: (Boolean) -> Unit, onError: (Throwable?) -> Unit, onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var info by remember(token, serverId, siteId) { mutableStateOf<TestDomain?>(null) }
    var loaded by remember(token, serverId, siteId) { mutableStateOf(false) }
    var pendingEnable by remember(token, serverId, siteId) { mutableStateOf<Boolean?>(null) }
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
            OutlinedButton(onClick = { pendingEnable = true }, enabled = !busy) {
                Text(stringResource(R.string.enable_test_domain))
            }
        } else {
            Text(stringResource(R.string.test_domain, info!!.fullTestDomain.ifBlank { info!!.testDomain }))
            Text(dnsHint, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { pendingEnable = false }, enabled = !busy) {
                Text(stringResource(R.string.disable_test_domain))
            }
        }
    }
    pendingEnable?.let { enable ->
        SensitiveConfirmDialog(lock = lock, activity = activity,
            message = stringResource(if (enable) R.string.enable_test_domain else R.string.disable_test_domain),
            confirmLabel = if (enable) R.string.enable_test_domain else R.string.disable_test_domain,
            onConfirmed = {
                pendingEnable = null
                onBusy(true)
                onError(null)
                scope.launch {
                    try {
                        if (enable) {
                            info = withContext(Dispatchers.IO) { PloiApi.enableTestDomain(token, serverId, siteId) }
                        } else {
                            withContext(Dispatchers.IO) { PloiApi.disableTestDomain(token, serverId, siteId) }
                            info = null
                        }
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
            }, onDismiss = { pendingEnable = null })
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        content = null
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

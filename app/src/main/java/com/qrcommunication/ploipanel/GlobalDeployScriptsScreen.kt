package com.qrcommunication.ploipanel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Locally stored templates; Ploi has no account-wide deploy-script mutation endpoint. */
@Composable
internal fun GlobalDeployScriptsScreen(token: String, profileId: String, lock: AppLock,
    activity: FragmentActivity, onRunningChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val store = remember(context, profileId) {
        DeployScriptTemplateStore(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher("ploi-panel.deploy-templates"))
    }
    val scope = rememberCoroutineScope()
    var templates by remember(profileId) { mutableStateOf<List<DeployScriptTemplate>?>(null) }
    var templateVersion by remember(profileId) { mutableIntStateOf(0) }
    var selectedTemplate by remember(profileId) { mutableStateOf<String?>(null) }
    var name by remember(profileId) { mutableStateOf("") }
    var script by remember(profileId) { mutableStateOf("") }
    var selected by remember(token, profileId) { mutableStateOf<Map<Pair<Long, Long>, DeployTarget>>(emptyMap()) }
    var browsingServer by remember(token, profileId) { mutableStateOf<Pair<Long, String>?>(null) }
    var pendingApply by remember(token, profileId) { mutableStateOf<Pair<String, List<DeployTarget>>?>(null) }
    var pendingDelete by remember(profileId) { mutableStateOf<String?>(null) }
    var busy by remember(profileId) { mutableStateOf(false) }
    BackHandler(enabled = busy) { /* Keep progress and the per-site report visible. */ }
    DisposableEffect(Unit) { onDispose { onRunningChange(false) } }
    var error by remember(profileId) { mutableStateOf<Throwable?>(null) }
    var validation by remember(profileId) { mutableStateOf(false) }
    var progress by remember(profileId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var outcome by remember(profileId) { mutableStateOf<DeployBatchOutcome?>(null) }

    LaunchedEffect(profileId, templateVersion) {
        templates = null
        try {
            templates = withContext(Dispatchers.IO) { store.list(profileId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        }
    }

    val editor: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            SectionCard(
                title = stringResource(R.string.deploy_global_templates),
                description = stringResource(R.string.deploy_global_local_hint),
                icon = Icons.Outlined.Description
            ) {
                if (templates == null && error == null) BusyIndicator()
                val currentMark: @Composable () -> Unit = {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                templates?.forEach { item ->
                    PanelListItem(
                        icon = Icons.Outlined.Description, title = item.name, enabled = !busy,
                        trailing = if (selectedTemplate == item.id) currentMark else null,
                        onClick = {
                            selectedTemplate = item.id
                            name = item.name
                            script = item.content
                            validation = false
                            outcome = null
                        }
                    )
                }
                OutlinedButton(onClick = {
                    selectedTemplate = null
                    name = ""
                    script = ""
                    validation = false
                    outcome = null
                }, enabled = !busy) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.deploy_global_new), Modifier.padding(start = PanelSpacing.sm))
                }
            }
            SectionCard(title = stringResource(R.string.deploy_global_text), icon = Icons.Outlined.Code) {
                OutlinedTextField(name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.deploy_global_name)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(script, onValueChange = { script = it },
                    label = { Text(stringResource(R.string.deploy_global_text)) },
                    textStyle = panelMonoStyle,
                    minLines = 8, maxLines = 20, enabled = !busy,
                    supportingText = {
                        Text(stringResource(R.string.deploy_global_length, script.length, DEPLOY_SCRIPT_MAX_LENGTH))
                    },
                    isError = script.length > DEPLOY_SCRIPT_MAX_LENGTH,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                    Button(onClick = {
                        error = null; validation = false
                        busy = true
                        scope.launch {
                            try {
                                val saved = withContext(Dispatchers.IO) {
                                    store.save(profileId, selectedTemplate, name, script)
                                }
                                selectedTemplate = saved.id
                                name = saved.name
                                templateVersion++
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (invalid: IllegalArgumentException) {
                                validation = true
                            } catch (failure: Exception) {
                                error = failure
                            } finally { busy = false }
                        }
                    }, enabled = !busy && templates != null && name.isNotBlank() && script.isNotBlank() &&
                        script.length <= DEPLOY_SCRIPT_MAX_LENGTH) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.deploy_global_save_local), Modifier.padding(start = PanelSpacing.sm))
                    }
                    if (selectedTemplate != null) DangerAction(stringResource(R.string.deploy_global_delete), onClick = {
                        pendingDelete = selectedTemplate
                    }, enabled = !busy)
                }
                if (validation) Text(stringResource(R.string.deploy_global_invalid), color = MaterialTheme.colorScheme.error)
                if (error != null) ApiErrorText(error!!)
                Text(stringResource(R.string.deploy_global_placeholders), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val targets: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            SectionCard(
                title = stringResource(R.string.deploy_global_select_sites),
                description = stringResource(R.string.deploy_global_replace_warning),
                icon = Icons.Outlined.Language
            ) {
                val browsingMark: @Composable () -> Unit = {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                PagedOptionPicker(
                    key = token to profileId, label = stringResource(R.string.deploy_global_servers),
                    emptyLabel = stringResource(R.string.deploy_global_empty_servers),
                    load = { page -> PloiApi.servers(token, page = page, perPage = 50).let {
                        PickerOptions(it.servers, it.currentPage, it.lastPage)
                    } }
                ) { server ->
                    PanelListItem(
                        icon = Icons.Outlined.Dns, title = server.name, enabled = !busy,
                        trailing = if (browsingServer?.first == server.id) browsingMark else null,
                        onClick = { browsingServer = server.id to server.name }
                    )
                }
                browsingServer?.let { (serverId, serverName) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PagedOptionPicker(
                        key = token to serverId, label = stringResource(R.string.deploy_global_sites_on, serverName),
                        emptyLabel = stringResource(R.string.empty_sites),
                        load = { page -> PloiApi.sites(token, serverId, page = page, perPage = 50).let {
                            PickerOptions(it.sites, it.currentPage, it.lastPage)
                        } }
                    ) { site ->
                        val key = serverId to site.id
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = key in selected, enabled = !busy, onCheckedChange = { checked ->
                                selected = if (checked) selected + (key to DeployTarget(serverId, site.id, site.domain, serverName))
                                    else selected - key
                            })
                            Text(site.domain, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            SectionCard(
                title = pluralStringResource(R.plurals.deploy_global_count, selected.size, selected.size),
                icon = Icons.Outlined.RocketLaunch
            ) {
                selected.values.forEach { target ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(target.domain, style = MaterialTheme.typography.bodyMedium)
                            Text(target.serverName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selected = selected - (target.serverId to target.siteId) }, enabled = !busy) {
                            Icon(Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.deploy_global_remove_site, target.domain, target.serverName))
                        }
                    }
                }
                if (selected.isNotEmpty() && script.isNotBlank()) {
                    val preview = runCatching { DeployScriptBatch.render(script, selected.values.first()) }.getOrNull()
                    if (preview != null) {
                        Text(stringResource(R.string.deploy_global_preview, selected.values.first().domain),
                            style = MaterialTheme.typography.labelLarge)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Text(preview.take(800), style = panelMonoStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                    modifier = Modifier.padding(PanelSpacing.sm))
                            }
                        }
                    }
                }
                Button(onClick = {
                    val targetsSnapshot = selected.values.toList()
                    validation = try {
                        targetsSnapshot.forEach { DeployScriptBatch.render(script, it) }
                        false
                    } catch (_: IllegalArgumentException) { true }
                    if (!validation && targetsSnapshot.isNotEmpty()) pendingApply = script to targetsSnapshot
                }, enabled = !busy && selected.isNotEmpty() && script.isNotBlank() &&
                    script.length <= DEPLOY_SCRIPT_MAX_LENGTH && templates != null) {
                    Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.deploy_global_apply), Modifier.padding(start = PanelSpacing.sm))
                }
                if (validation) Text(stringResource(R.string.deploy_global_invalid), color = MaterialTheme.colorScheme.error)
                progress?.let { (done, total) ->
                    Text(stringResource(R.string.deploy_global_progress, done, total), style = MaterialTheme.typography.bodyMedium)
                    if (total > 0) LinearProgressIndicator(
                        progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth()
                    )
                }
                if (busy) BusyIndicator()
            }
            outcome?.let { batch ->
                SectionCard(
                    title = stringResource(if (batch.abortedBeforeWrite) R.string.deploy_global_preflight_abort
                        else R.string.deploy_global_report),
                    icon = Icons.AutoMirrored.Outlined.FactCheck
                ) {
                    batch.targets.forEach { row ->
                        val status = when (row.status) {
                            DeployApplyStatus.VERIFIED -> R.string.deploy_global_verified
                            DeployApplyStatus.UNVERIFIED -> R.string.deploy_global_unverified
                            DeployApplyStatus.FAILED -> R.string.deploy_global_failed
                            DeployApplyStatus.PREFLIGHT_FAILED -> R.string.deploy_global_preflight_failed
                            DeployApplyStatus.SKIPPED -> R.string.deploy_global_skipped
                        }
                        val tone = when (row.status) {
                            DeployApplyStatus.VERIFIED -> PanelTheme.status.success
                            DeployApplyStatus.FAILED, DeployApplyStatus.PREFLIGHT_FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val glyph = when (row.status) {
                            DeployApplyStatus.VERIFIED -> Icons.Outlined.CheckCircle
                            DeployApplyStatus.FAILED, DeployApplyStatus.PREFLIGHT_FAILED -> Icons.Outlined.ErrorOutline
                            else -> Icons.Outlined.HourglassTop
                        }
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                            Icon(glyph, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                                Text(stringResource(R.string.deploy_global_result, row.target.domain, stringResource(status)),
                                    style = MaterialTheme.typography.bodyMedium)
                                row.failure?.let { ApiErrorText(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = PanelSpacing.sm)) {
        if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { editor() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { targets() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) { editor(); targets() }
    }

    pendingDelete?.let { id ->
        SensitiveConfirmDialog(lock, activity, stringResource(R.string.deploy_global_delete_confirm),
            R.string.deploy_global_delete, onConfirmed = {
                pendingDelete = null
                error = null
                try {
                    store.delete(profileId, id)
                    if (selectedTemplate == id) { selectedTemplate = null; name = ""; script = "" }
                    templateVersion++
                } catch (failure: Exception) { error = failure }
            }, onDismiss = { pendingDelete = null })
    }
    pendingApply?.let { (contents, targetsSnapshot) ->
        SensitiveConfirmDialog(lock, activity,
            pluralStringResource(R.plurals.deploy_global_confirm, targetsSnapshot.size, targetsSnapshot.size),
            R.string.deploy_global_apply,
            onConfirmed = {
                pendingApply = null
                busy = true
                onRunningChange(true)
                error = null
                outcome = null
                progress = 0 to targetsSnapshot.size
                scope.launch {
                    try {
                        val gateway = object : DeployScriptGateway {
                            override suspend fun read(serverId: Long, siteId: Long) = PloiApi.deployScript(token, serverId, siteId)
                            override suspend fun write(serverId: Long, siteId: Long, script: String) {
                                PloiApi.updateDeployScript(token, serverId, siteId, script)
                            }
                        }
                        outcome = withContext(Dispatchers.IO) {
                            DeployScriptBatch(gateway).apply(contents, targetsSnapshot) { done, total ->
                                withContext(Dispatchers.Main) { progress = done to total }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure
                    } finally { busy = false; onRunningChange(false) }
                }
            }, onDismiss = { pendingApply = null })
    }
}

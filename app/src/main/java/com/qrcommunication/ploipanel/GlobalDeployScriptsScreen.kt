package com.qrcommunication.ploipanel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.deploy_global_templates), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.deploy_global_local_hint), style = MaterialTheme.typography.bodySmall)
            if (templates == null && error == null) CircularProgressIndicator()
            templates?.forEach { item ->
                Card(onClick = {
                    selectedTemplate = item.id
                    name = item.name
                    script = item.content
                    validation = false
                    outcome = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(item.name, Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            OutlinedButton(onClick = {
                selectedTemplate = null
                name = ""
                script = ""
                validation = false
                outcome = null
            }, enabled = !busy) { Text(stringResource(R.string.deploy_global_new)) }
            OutlinedTextField(name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.deploy_global_name)) },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(script, onValueChange = { script = it },
                label = { Text(stringResource(R.string.deploy_global_text)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 8, maxLines = 20, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp))
            Text(stringResource(R.string.deploy_global_length, script.length, DEPLOY_SCRIPT_MAX_LENGTH),
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    script.length <= DEPLOY_SCRIPT_MAX_LENGTH) { Text(stringResource(R.string.deploy_global_save_local)) }
                if (selectedTemplate != null) OutlinedButton(onClick = {
                    pendingDelete = selectedTemplate
                }, enabled = !busy) { Text(stringResource(R.string.deploy_global_delete)) }
            }
            if (validation) Text(stringResource(R.string.deploy_global_invalid), color = MaterialTheme.colorScheme.error)
            if (error != null) ApiErrorText(error!!)
            Text(stringResource(R.string.deploy_global_placeholders), style = MaterialTheme.typography.bodySmall)
        }
    }

    val targets: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.deploy_global_select_sites), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.deploy_global_replace_warning), style = MaterialTheme.typography.bodySmall)
            PagedOptionPicker(
                key = token to profileId, label = stringResource(R.string.deploy_global_servers),
                emptyLabel = stringResource(R.string.deploy_global_empty_servers),
                load = { page -> PloiApi.servers(token, page = page, perPage = 50).let {
                    PickerOptions(it.servers, it.currentPage, it.lastPage)
                } }
            ) { server ->
                OutlinedButton(onClick = { browsingServer = server.id to server.name }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(server.name) }
            }
            browsingServer?.let { (serverId, serverName) ->
                PagedOptionPicker(
                    key = token to serverId, label = stringResource(R.string.deploy_global_sites_on, serverName),
                    emptyLabel = stringResource(R.string.empty_sites),
                    load = { page -> PloiApi.sites(token, serverId, page = page, perPage = 50).let {
                        PickerOptions(it.sites, it.currentPage, it.lastPage)
                    } }
                ) { site ->
                    val key = serverId to site.id
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(checked = key in selected, enabled = !busy, onCheckedChange = { checked ->
                            selected = if (checked) selected + (key to DeployTarget(serverId, site.id, site.domain, serverName))
                                else selected - key
                        })
                        Text(site.domain, Modifier.padding(top = 12.dp))
                    }
                }
            }
            Text(pluralStringResource(R.plurals.deploy_global_count, selected.size, selected.size))
            selected.values.forEach { target ->
                OutlinedButton(onClick = { selected = selected - (target.serverId to target.siteId) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.deploy_global_remove_site, target.domain, target.serverName))
                }
            }
            if (selected.isNotEmpty() && script.isNotBlank()) {
                val preview = runCatching { DeployScriptBatch.render(script, selected.values.first()) }.getOrNull()
                if (preview != null) {
                    Text(stringResource(R.string.deploy_global_preview, selected.values.first().domain))
                    SelectionContainer { Text(preview.take(800), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
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
                Text(stringResource(R.string.deploy_global_apply))
            }
            if (validation) Text(stringResource(R.string.deploy_global_invalid), color = MaterialTheme.colorScheme.error)
            progress?.let { (done, total) -> Text(stringResource(R.string.deploy_global_progress, done, total)) }
            if (busy) CircularProgressIndicator()
            outcome?.let { batch ->
                Text(stringResource(if (batch.abortedBeforeWrite) R.string.deploy_global_preflight_abort
                    else R.string.deploy_global_report), style = MaterialTheme.typography.titleMedium)
                batch.targets.forEach { row ->
                    val status = when (row.status) {
                        DeployApplyStatus.VERIFIED -> R.string.deploy_global_verified
                        DeployApplyStatus.UNVERIFIED -> R.string.deploy_global_unverified
                        DeployApplyStatus.FAILED -> R.string.deploy_global_failed
                        DeployApplyStatus.PREFLIGHT_FAILED -> R.string.deploy_global_preflight_failed
                        DeployApplyStatus.SKIPPED -> R.string.deploy_global_skipped
                    }
                    Text(stringResource(R.string.deploy_global_result, row.target.domain, stringResource(status)))
                    row.failure?.let { ApiErrorText(it) }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { editor() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { targets() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)) { editor(); targets() }
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

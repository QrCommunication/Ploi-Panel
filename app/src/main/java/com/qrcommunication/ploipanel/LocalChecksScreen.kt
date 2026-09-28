package com.qrcommunication.ploipanel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qrcommunication.ploipanel.widget.SiteChecksWidgetRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Local site watchdog UI: targets are probed by the device itself, never through Ploi.
 * Background checks are scheduled best effort via [LocalCheckRefresh]; manual checks give
 * immediate feedback while the app is open. No URL or result leaves the device.
 */
@Composable
internal fun LocalChecksScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalCheckStore(SharedPreferencesProfilePrefs(context)) }
    val engine = remember { SiteCheckEngine(store, UrlConnectionSiteProber()) }
    var version by remember { mutableIntStateOf(0) }
    val targets = remember(version) { store.targets() }
    var alerts by remember(version) { mutableStateOf(store.alertsEnabled()) }
    var alertsDenied by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<MonitoredTarget?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<MonitoredTarget?>(null) }

    // Keep the periodic worker aligned with the configured targets whenever the screen shows.
    LaunchedEffect(targets.size) { LocalCheckRefresh.sync(context) }

    fun runChecks(ids: List<String>) {
        if (checking) return
        checking = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { ids.forEach { engine.check(it) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Probe failures are recorded per target; the list simply refreshes.
            } finally {
                checking = false
                version++
                SiteChecksWidgetRefresh.refreshAll(context)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            store.setAlertsEnabled(true)
            alertsDenied = false
        } else {
            store.setAlertsEnabled(false)
            alertsDenied = true
        }
        version++
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.local_checks_hint),
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.local_checks_alerts), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = alerts,
                    onCheckedChange = { wanted ->
                        if (!wanted) {
                            store.setAlertsEnabled(false)
                            alertsDenied = false
                            version++
                        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            store.setAlertsEnabled(true)
                            alertsDenied = false
                            version++
                        }
                    }
                )
            }
        }
        if (alertsDenied) item {
            Text(stringResource(R.string.local_checks_alerts_denied), color = MaterialTheme.colorScheme.error)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { editor = null; editorOpen = true }, enabled = targets.size < LocalCheckRules.MAX_TARGETS) {
                    Text(stringResource(R.string.local_checks_add))
                }
                OutlinedButton(onClick = { runChecks(targets.map { it.id }) }, enabled = targets.isNotEmpty() && !checking) {
                    Text(stringResource(if (checking) R.string.local_checks_checking else R.string.local_checks_check_all))
                }
            }
        }
        if (checking) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
        if (targets.isEmpty()) item {
            Text(stringResource(R.string.local_checks_empty), style = MaterialTheme.typography.bodyLarge)
        }
        items(targets, key = { it.id }) { target ->
            val status = remember(version) { store.statusOf(target.id) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(target.url, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(
                            when (status.state) {
                                CheckState.UP -> R.string.local_checks_state_up
                                CheckState.DOWN -> R.string.local_checks_state_down
                                CheckState.UNKNOWN -> R.string.local_checks_state_unknown
                            }
                        ),
                        color = when (status.state) {
                            CheckState.UP -> MaterialTheme.colorScheme.primary
                            CheckState.DOWN -> MaterialTheme.colorScheme.error
                            CheckState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                    )
                    val detail = buildList {
                        status.lastHttpStatus?.let { add(stringResource(R.string.local_checks_http_status, it)) }
                        status.lastLatencyMs?.let { add(stringResource(R.string.local_checks_latency, it)) }
                        add(
                            status.lastCheckedAt?.let {
                                stringResource(
                                    R.string.local_checks_last_checked,
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                )
                            } ?: stringResource(R.string.local_checks_never_checked)
                        )
                        if (status.consecutiveFailures > 0) {
                            add(pluralStringResource(R.plurals.local_checks_failures, status.consecutiveFailures, status.consecutiveFailures))
                        }
                    }.joinToString(" · ")
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                    val stale = isLocalCheckStale(status.lastCheckedAt, System.currentTimeMillis())
                    if (stale) Text(
                        stringResource(R.string.local_checks_stale),
                        color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { runChecks(listOf(target.id)) }, enabled = !checking) {
                            Text(stringResource(R.string.local_checks_check))
                        }
                        OutlinedButton(onClick = { editor = target; editorOpen = true }) {
                            Text(stringResource(R.string.local_checks_edit))
                        }
                        OutlinedButton(onClick = { pendingRemoval = target }) {
                            Text(stringResource(R.string.local_checks_delete))
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) TargetEditorDialog(
        initial = editor,
        onDismiss = { editorOpen = false },
        onSubmit = { label, url, method, timeoutMs, statusesText, debounce ->
            val statuses = parseExpectedStatuses(statusesText)
            val editing = editor
            if (editing == null) {
                store.add(label, url, method, timeoutMs, statuses, debounce)
            } else {
                store.update(
                    editing.copy(
                        label = label.trim(), url = url.trim(), method = method,
                        timeoutMs = timeoutMs, expectedStatuses = statuses, debounce = debounce
                    )
                )
            }
            editorOpen = false
            version++
            SiteChecksWidgetRefresh.refreshAll(context)
        }
    )

    pendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.local_checks_delete)) },
            text = { Text(stringResource(R.string.local_checks_delete_confirm, target.label)) },
            confirmButton = {
                Button(onClick = {
                    store.remove(target.id)
                    pendingRemoval = null
                    LocalCheckRefresh.sync(context)
                    version++
                    SiteChecksWidgetRefresh.refreshAll(context)
                }) { Text(stringResource(R.string.local_checks_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Add/edit form; stays open on validation errors so no input is lost. */
@Composable
private fun TargetEditorDialog(
    initial: MonitoredTarget?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, CheckMethod, Int, String, Int) -> Unit
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var method by remember { mutableStateOf(initial?.method ?: CheckMethod.HEAD) }
    var timeout by remember { mutableStateOf((initial?.timeoutMs ?: LocalCheckRules.DEFAULT_TIMEOUT_MS).toString()) }
    var statuses by remember {
        mutableStateOf(initial?.expectedStatuses?.sorted()?.joinToString(", ") ?: LocalCheckRules.DEFAULT_EXPECTED_STATUS.toString())
    }
    var debounce by remember { mutableStateOf((initial?.debounce ?: LocalCheckRules.DEFAULT_DEBOUNCE).toString()) }
    var error by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.local_checks_add else R.string.local_checks_edit)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.local_checks_label_field)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.local_checks_url_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.local_checks_method_field), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckMethod.entries.forEach { option ->
                        FilterChip(selected = method == option, onClick = { method = option }, label = { Text(option.name) })
                    }
                }
                OutlinedTextField(timeout, onValueChange = { timeout = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.local_checks_timeout_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(statuses, onValueChange = { statuses = it },
                    label = { Text(stringResource(R.string.local_checks_statuses_field)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(debounce, onValueChange = { debounce = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.local_checks_debounce_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                error = 0
                val timeoutMs = timeout.toIntOrNull()
                val debounceCount = debounce.toIntOrNull()
                if (timeoutMs == null || debounceCount == null) {
                    error = R.string.local_checks_error_invalid
                    return@Button
                }
                try {
                    onSubmit(label, url, method, timeoutMs, statuses, debounceCount)
                } catch (invalid: IllegalArgumentException) {
                    error = when (invalid.message) {
                        "Duplicate check label" -> R.string.local_checks_error_duplicate
                        "Too many check targets" -> R.string.local_checks_error_limit
                        "Invalid accepted status" -> R.string.local_checks_error_statuses
                        else -> R.string.local_checks_error_invalid
                    }
                }
            }, enabled = label.isNotBlank() && url.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

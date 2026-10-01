package com.qrcommunication.ploipanel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.g5_local_checks_title),
                description = stringResource(R.string.local_checks_hint),
                icon = Icons.Outlined.MonitorHeart
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.NotificationsActive, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.local_checks_alerts), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
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
                if (alertsDenied) InlineNotice(stringResource(R.string.local_checks_alerts_denied))
            }
        }
        item {
            // Toolbar: count on the left, "check all" and the primary "add" action on the right.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.items_count, targets.size), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { runChecks(targets.map { it.id }) }, enabled = targets.isNotEmpty() && !checking) {
                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (checking) R.string.local_checks_checking else R.string.local_checks_check_all),
                        Modifier.padding(start = PanelSpacing.sm)
                    )
                }
                FilledTonalButton(onClick = { editor = null; editorOpen = true }, enabled = targets.size < LocalCheckRules.MAX_TARGETS) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.local_checks_add), Modifier.padding(start = PanelSpacing.sm))
                }
            }
        }
        if (checking) item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BusyIndicator(Modifier.padding(PanelSpacing.sm)) }
        }
        if (targets.isEmpty()) item {
            // The toolbar above already has "Add"; the empty state only explains.
            EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.local_checks_empty))
        }
        items(targets, key = { it.id }) { target ->
            val status = remember(version) { store.statusOf(target.id) }
            val stale = isLocalCheckStale(status.lastCheckedAt, System.currentTimeMillis())
            TargetCard(
                target = target,
                state = status.state,
                facts = listOfNotNull(
                    status.lastHttpStatus?.let {
                        ResourceFact(stringResource(R.string.g5_local_checks_fact_http), it.toString(), mono = true)
                    },
                    status.lastLatencyMs?.let {
                        ResourceFact(stringResource(R.string.g5_local_checks_fact_latency), stringResource(R.string.local_checks_latency, it), mono = true)
                    },
                    ResourceFact(
                        stringResource(R.string.g5_local_checks_fact_last),
                        status.lastCheckedAt?.let {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                        } ?: stringResource(R.string.local_checks_never_checked)
                    ),
                    ResourceFact(stringResource(R.string.local_checks_method_field), target.method.name, mono = true)
                ),
                failures = status.consecutiveFailures.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.local_checks_failures, it, it)
                },
                stale = stale
            ) {
                CardAction(
                    stringResource(R.string.local_checks_check), icon = Icons.Outlined.NetworkCheck,
                    enabled = !checking, onClick = { runChecks(listOf(target.id)) }
                )
                CardAction(
                    stringResource(R.string.local_checks_edit), icon = Icons.Outlined.Edit,
                    onClick = { editor = target; editorOpen = true }
                )
                DangerAction(stringResource(R.string.local_checks_delete), onClick = { pendingRemoval = target })
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
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.local_checks_delete)) },
            text = { Text(stringResource(R.string.local_checks_delete_confirm, target.label)) },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
                ), onClick = {
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
        icon = { Icon(Icons.Outlined.MonitorHeart, contentDescription = null) },
        title = { Text(stringResource(if (initial == null) R.string.local_checks_add else R.string.local_checks_edit)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
            ) {
                OutlinedTextField(label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.local_checks_label_field)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.local_checks_url_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = panelMonoStyle,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.local_checks_method_field), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                    CheckMethod.entries.forEach { option ->
                        FilterChip(
                            selected = method == option, onClick = { method = option }, label = { Text(option.name) },
                            leadingIcon = if (method == option) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
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
                if (error != 0) InlineNotice(stringResource(error))
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

/**
 * One watched URL: state as a coloured dot plus a word (never colour alone), the URL in
 * monospace, measured facts, and the stale / failure warnings in words.
 */
@Composable
private fun TargetCard(
    target: MonitoredTarget,
    state: CheckState,
    facts: List<ResourceFact>,
    failures: String?,
    stale: Boolean,
    actions: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(PanelSpacing.lg), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                IconBadge(Icons.Outlined.Language, size = 36)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                    Text(target.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        target.url, style = panelMonoStyle, color = colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            CheckStateLabel(state)
            if (facts.isNotEmpty()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) {
                facts.forEach { fact ->
                    Column {
                        Text(fact.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Text(fact.value, style = if (fact.mono) panelMonoStyle else MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (failures != null) CardNote(Icons.Outlined.ErrorOutline, failures, colors.error)
            if (stale) CardNote(Icons.Outlined.Schedule, stringResource(R.string.local_checks_stale), PanelTheme.status.warning)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) { actions() }
        }
    }
}

/** Dot + word; the word carries the meaning, the dot only reinforces it. */
@Composable
private fun CheckStateLabel(state: CheckState) {
    val color = when (state) {
        CheckState.UP -> PanelTheme.status.success
        CheckState.DOWN -> MaterialTheme.colorScheme.error
        CheckState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            stringResource(
                when (state) {
                    CheckState.UP -> R.string.local_checks_state_up
                    CheckState.DOWN -> R.string.local_checks_state_down
                    CheckState.UNKNOWN -> R.string.local_checks_state_unknown
                }
            ),
            color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CardNote(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs + PanelSpacing.xxs)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

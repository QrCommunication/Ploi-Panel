package com.qrcommunication.ploipanel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Per-server CPU/RAM/disk alert thresholds, stored on the device and evaluated by
 * [ThresholdAlertWorker] (best effort, 15-minute floor).
 */
@Composable
internal fun ThresholdAlertCard(profileId: String, server: Server) {
    val context = LocalContext.current
    val store = remember(context) { ThresholdAlertStore(SharedPreferencesProfilePrefs(context)) }
    var version by remember { mutableIntStateOf(0) }
    val existing = remember(profileId, server.id, version) { store.rule(profileId, server.id) }
    var cpu by remember(existing) { mutableStateOf(existing?.cpu?.toString().orEmpty()) }
    var ram by remember(existing) { mutableStateOf(existing?.ram?.toString().orEmpty()) }
    var disk by remember(existing) { mutableStateOf(existing?.disk?.toString().orEmpty()) }
    var sustained by remember(existing) { mutableIntStateOf(existing?.sustained ?: 2) }
    var feedback by remember { mutableIntStateOf(0) }
    var error by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) feedback = R.string.threshold_permission_denied
    }

    SectionCard(
        title = stringResource(R.string.threshold_title),
        description = stringResource(R.string.threshold_hint),
        icon = Icons.Outlined.NotificationsActive
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            listOf(
                Triple(R.string.metric_cpu, cpu) { v: String -> cpu = v },
                Triple(R.string.metric_ram, ram) { v: String -> ram = v },
                Triple(R.string.metric_disk, disk) { v: String -> disk = v },
            ).forEach { (label, value, update) ->
                OutlinedTextField(
                    value, { update(it.filter(Char::isDigit).take(3)) },
                    label = { Text(stringResource(label) + " %") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        Text(stringResource(R.string.threshold_sustained), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
            (1..4).forEach { n ->
                FilterChip(
                    selected = sustained == n, onClick = { sustained = n }, label = { Text(n.toString()) },
                    leadingIcon = if (sustained == n) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
        if (feedback != 0) {
            // The permission-denied note is a warning, not a confirmation.
            if (feedback == R.string.threshold_permission_denied) InlineNotice(stringResource(feedback))
            else SuccessBanner(stringResource(feedback))
        }
        if (error != 0) InlineNotice(stringResource(error))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
        ) {
            Button(onClick = {
                error = 0
                feedback = 0
                val rule = try {
                    ThresholdRule(
                        profileId, server.id, server.name.ifBlank { "#${server.id}" }.take(120),
                        cpu.toIntOrNull(), ram.toIntOrNull(), disk.toIntOrNull(), sustained
                    )
                } catch (invalid: IllegalArgumentException) {
                    error = R.string.threshold_invalid
                    return@Button
                }
                try {
                    store.save(rule)
                    ThresholdAlerts.sync(context)
                    feedback = R.string.threshold_saved
                    version++
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (full: IllegalArgumentException) {
                    error = R.string.threshold_limit
                }
            }) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.threshold_save), Modifier.padding(start = PanelSpacing.sm))
            }
            if (existing != null) DangerAction(stringResource(R.string.threshold_remove), onClick = {
                store.remove(profileId, server.id)
                ThresholdAlerts.sync(context)
                feedback = R.string.threshold_removed
                version++
            })
        }
    }
}

/** Auto-lock grace delay picker for the security settings. */
@Composable
internal fun AutoLockSetting() {
    val context = LocalContext.current
    val prefs = remember(context) { SharedPreferencesProfilePrefs(context) }
    var grace by remember { mutableIntStateOf(AutoLockPolicy.graceSeconds(prefs)) }
    Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            Icon(Icons.Outlined.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.autolock_title), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            AutoLockPolicy.CHOICES.forEach { seconds ->
                FilterChip(
                    selected = grace == seconds,
                    onClick = { AutoLockPolicy.setGraceSeconds(prefs, seconds); grace = seconds },
                    leadingIcon = if (grace == seconds) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    label = {
                        Text(
                            when (seconds) {
                                0 -> stringResource(R.string.autolock_immediate)
                                in 1..59 -> stringResource(R.string.autolock_seconds, seconds)
                                else -> stringResource(R.string.autolock_minutes, seconds / 60)
                            }
                        )
                    }
                )
            }
        }
        Text(stringResource(R.string.autolock_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

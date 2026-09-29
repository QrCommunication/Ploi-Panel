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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.threshold_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.threshold_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(stringResource(R.string.threshold_sustained), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                (1..4).forEach { n ->
                    FilterChip(selected = sustained == n, onClick = { sustained = n }, label = { Text(n.toString()) })
                }
            }
            if (feedback != 0) Text(stringResource(feedback), color = MaterialTheme.colorScheme.primary)
            if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }) { Text(stringResource(R.string.threshold_save)) }
                if (existing != null) OutlinedButton(onClick = {
                    store.remove(profileId, server.id)
                    ThresholdAlerts.sync(context)
                    feedback = R.string.threshold_removed
                    version++
                }) { Text(stringResource(R.string.threshold_remove)) }
            }
        }
    }
}

/** Auto-lock grace delay picker for the security settings. */
@Composable
internal fun AutoLockSetting() {
    val context = LocalContext.current
    val prefs = remember(context) { SharedPreferencesProfilePrefs(context) }
    var grace by remember { mutableIntStateOf(AutoLockPolicy.graceSeconds(prefs)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.autolock_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoLockPolicy.CHOICES.forEach { seconds ->
                FilterChip(
                    selected = grace == seconds,
                    onClick = { AutoLockPolicy.setGraceSeconds(prefs, seconds); grace = seconds },
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
        Text(stringResource(R.string.autolock_hint), style = MaterialTheme.typography.bodySmall)
    }
}

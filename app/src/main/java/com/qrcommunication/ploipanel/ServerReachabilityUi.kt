package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Words for a device probe; the port is always named so the scope of the test is explicit. */
@Composable
internal fun deviceProbeText(probe: DeviceProbe): String = when (probe) {
    is DeviceProbe.Reachable -> stringResource(R.string.recheck_device_ok, probe.port, probe.latencyMs.toInt())
    is DeviceProbe.Refused -> stringResource(R.string.recheck_device_refused, probe.port)
    is DeviceProbe.TimedOut -> stringResource(R.string.recheck_device_timeout, probe.port)
    is DeviceProbe.Failed -> stringResource(R.string.recheck_device_failed, probe.port)
}

/**
 * Result block for a recheck: Ploi's re-read status and the phone's TCP test on two separate
 * lines, the absolute time of the test, and a Retest button. Announced politely when it changes.
 */
@Composable
internal fun RecheckPanel(recheck: Recheck?, running: Boolean, onRetest: () -> Unit, modifier: Modifier = Modifier) {
    val status = PanelTheme.status
    val colors = MaterialTheme.colorScheme
    val reachable = recheck?.probe is DeviceProbe.Reachable
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = when {
            recheck == null || running -> colors.surfaceContainerHigh
            reachable -> status.successContainer
            else -> colors.errorContainer
        },
        contentColor = when {
            recheck == null || running -> colors.onSurface
            reachable -> status.onSuccessContainer
            else -> colors.onErrorContainer
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(PanelSpacing.md).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                Icon(
                    when {
                        running || recheck == null -> Icons.Outlined.WifiTethering
                        reachable -> Icons.Outlined.CloudDone
                        else -> Icons.Outlined.CloudOff
                    },
                    contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Text(stringResource(R.string.recheck_title), style = MaterialTheme.typography.titleSmall)
            }
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                    BusyIndicator(Modifier.size(18.dp))
                    Text(stringResource(R.string.recheck_running), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (recheck != null) {
                val ploi = recheck.ploiStatus
                Text(
                    when {
                        ploi != null -> stringResource(R.string.recheck_ploi_status, ploi.ifBlank { "—" })
                        recheck.ploiError != null -> stringResource(R.string.recheck_ploi_failed, apiErrorMessage(recheck.ploiError))
                        else -> stringResource(R.string.recheck_ploi_failed, "—")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    recheck.probe?.let { deviceProbeText(it) } ?: stringResource(R.string.recheck_device_no_ip),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(
                        R.string.recheck_checked_at,
                        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(recheck.checkedAt))
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(onClick = onRetest, enabled = !running) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.recheck_retest), Modifier.padding(start = PanelSpacing.sm))
            }
        }
    }
}

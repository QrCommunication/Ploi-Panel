package com.qrcommunication.ploipanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.qrcommunication.ploipanel.ssh.SshHostKey
import com.qrcommunication.ploipanel.ssh.SshHostKeyTransport
import com.qrcommunication.ploipanel.ssh.SshHostTrustStore
import com.qrcommunication.ploipanel.ssh.SshProbeAssessment
import com.qrcommunication.ploipanel.ssh.assessPresentedKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connection-time host verification: a handshake-only probe confronts the presented host key
 * with the TOFU trust store. This dialog is the ONLY UI allowed to call [SshHostTrustStore.trust]
 * (first contact, explicit confirmation) and [SshHostTrustStore.repin] (mismatch, hard block
 * lifted only behind a fresh PIN/biometric gate). No authentication and no command is performed.
 */
@Composable
internal fun SshHostProbeDialog(
    trustStore: SshHostTrustStore,
    transport: SshHostKeyTransport,
    lock: AppLock,
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onPinnedChanged: () -> Unit,
    initialHost: String = "",
    initialPort: Int = SshHostTrustStore.DEFAULT_PORT
) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(initialPort.toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableIntStateOf(0) }
    var assessment by remember { mutableStateOf<SshProbeAssessment?>(null) }
    var pendingRepin by remember { mutableStateOf<SshProbeAssessment.KeyMismatch?>(null) }

    fun probe() {
        error = 0
        feedback = 0
        assessment = null
        val targetHost = host.trim()
        val targetPort = port.trim().toIntOrNull()
        if (targetHost.isEmpty() || targetPort == null) {
            error = R.string.ssh_probe_invalid
            return
        }
        busy = true
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val (keyType, keyBlob) = transport.fetchHostKey(targetHost, targetPort)
                    assessPresentedKey(trustStore, targetHost, targetPort, keyType, keyBlob)
                }
                assessment = outcome
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = when (failure) {
                    is IllegalArgumentException -> R.string.ssh_probe_invalid
                    else -> R.string.ssh_probe_error
                }
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
        title = { Text(stringResource(R.string.ssh_probe_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
            ) {
                Text(stringResource(R.string.ssh_probe_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                    OutlinedTextField(
                        host, onValueChange = { host = it; assessment = null; feedback = 0 },
                        label = { Text(stringResource(R.string.ssh_probe_host)) },
                        singleLine = true, enabled = !busy, modifier = Modifier.weight(2f), textStyle = panelMonoStyle
                    )
                    OutlinedTextField(
                        port, onValueChange = { port = it.filter(Char::isDigit).take(5); assessment = null; feedback = 0 },
                        label = { Text(stringResource(R.string.ssh_probe_port)) },
                        singleLine = true, enabled = !busy, modifier = Modifier.weight(1f), textStyle = panelMonoStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (error != 0) InlineNotice(stringResource(error))
                if (feedback != 0) SuccessBanner(stringResource(feedback))
                if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BusyIndicator() }
                when (val outcome = assessment) {
                    is SshProbeAssessment.Trusted -> {
                        ProbeVerdict(ProbeTone.OK, Icons.Outlined.VerifiedUser, stringResource(R.string.ssh_probe_trusted))
                        ProbeFingerprint(outcome.presented)
                        if (outcome.presented.isWeakSignature) ProbeVerdict(
                            ProbeTone.DANGER, Icons.Outlined.WarningAmber, stringResource(R.string.ssh_weak_rsa)
                        )
                    }
                    is SshProbeAssessment.FirstContact -> {
                        ProbeVerdict(ProbeTone.CAUTION, Icons.Outlined.GppMaybe, stringResource(R.string.ssh_probe_first))
                        ProbeFingerprint(outcome.presented)
                        FilledTonalButton(onClick = {
                            error = 0
                            try {
                                trustStore.trust(
                                    outcome.presented.host, outcome.presented.port,
                                    outcome.presented.keyType, outcome.presented.keyBlob
                                )
                                feedback = R.string.ssh_probe_pinned
                                assessment = null
                                onPinnedChanged()
                            } catch (failure: Exception) {
                                error = R.string.ssh_error_vault
                            }
                        }) {
                            Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.ssh_probe_pin), Modifier.padding(start = PanelSpacing.sm))
                        }
                    }
                    is SshProbeAssessment.KeyMismatch -> {
                        ProbeVerdict(ProbeTone.DANGER, Icons.Outlined.GppBad, stringResource(R.string.ssh_probe_mismatch))
                        Text(
                            stringResource(R.string.ssh_probe_mismatch_old),
                            style = MaterialTheme.typography.titleSmall
                        )
                        ProbeFingerprint(outcome.pinned)
                        Text(
                            stringResource(R.string.ssh_probe_mismatch_new),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        ProbeFingerprint(outcome.presented, mismatch = true)
                        // Re-pin stays a secondary, error-outlined action behind a fresh PIN/biometric gate.
                        OutlinedButton(
                            onClick = { pendingRepin = outcome },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.ssh_probe_repin))
                        }
                    }
                    null -> Unit
                }
            }
        },
        confirmButton = {
            Button(onClick = { probe() }, enabled = !busy && host.isNotBlank() && port.isNotBlank()) {
                Text(stringResource(R.string.ssh_probe_run))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.ssh_close))
            }
        }
    )

    pendingRepin?.let { mismatch ->
        SensitiveConfirmDialog(
            lock, activity,
            stringResource(
                R.string.ssh_probe_repin_confirm,
                "${mismatch.presented.host}:${mismatch.presented.port}"
            ),
            R.string.ssh_probe_repin,
            onConfirmed = {
                pendingRepin = null
                error = 0
                try {
                    trustStore.repin(
                        mismatch.presented.host, mismatch.presented.port,
                        mismatch.presented.keyType, mismatch.presented.keyBlob
                    )
                    feedback = R.string.ssh_probe_repinned
                    assessment = null
                    onPinnedChanged()
                } catch (failure: Exception) {
                    error = R.string.ssh_error_vault
                }
            },
            onDismiss = { pendingRepin = null }
        )
    }
}

private enum class ProbeTone { OK, CAUTION, DANGER }

/** Verdict banner: icon + sentence on a tinted surface; the words carry the meaning. */
@Composable
private fun ProbeVerdict(tone: ProbeTone, icon: ImageVector, message: String) {
    val status = PanelTheme.status
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ProbeTone.OK -> status.successContainer to status.onSuccessContainer
        ProbeTone.CAUTION -> status.warningContainer to status.onWarningContainer
        ProbeTone.DANGER -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(
        color = container, contentColor = content, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(PanelSpacing.md), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                message, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (tone == ProbeTone.DANGER) FontWeight.SemiBold else null
            )
        }
    }
}

/** Key type and SHA-256 fingerprint in monospace; a mismatching key gets an error outline. */
@Composable
private fun ProbeFingerprint(key: SshHostKey, mismatch: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.small,
        border = if (mismatch) BorderStroke(1.dp, colors.error) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
            Text(key.keyType, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                key.fingerprint(), style = panelMonoStyle,
                color = if (mismatch) colors.error else colors.onSurface
            )
        }
    }
}

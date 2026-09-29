package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
        title = { Text(stringResource(R.string.ssh_probe_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.ssh_probe_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    host, onValueChange = { host = it; assessment = null; feedback = 0 },
                    label = { Text(stringResource(R.string.ssh_probe_host)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    port, onValueChange = { port = it.filter(Char::isDigit).take(5); assessment = null; feedback = 0 },
                    label = { Text(stringResource(R.string.ssh_probe_port)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                if (feedback != 0) Text(stringResource(feedback), color = MaterialTheme.colorScheme.primary)
                if (busy) BusyIndicator()
                when (val outcome = assessment) {
                    is SshProbeAssessment.Trusted -> {
                        Text(
                            stringResource(R.string.ssh_probe_trusted),
                            color = MaterialTheme.colorScheme.primary
                        )
                        ProbeFingerprint(outcome.presented)
                        if (outcome.presented.isWeakSignature) Text(
                            stringResource(R.string.ssh_weak_rsa),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is SshProbeAssessment.FirstContact -> {
                        Text(
                            stringResource(R.string.ssh_probe_first),
                            color = MaterialTheme.colorScheme.error
                        )
                        ProbeFingerprint(outcome.presented)
                        OutlinedButton(onClick = {
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
                        }) { Text(stringResource(R.string.ssh_probe_pin)) }
                    }
                    is SshProbeAssessment.KeyMismatch -> {
                        Text(
                            stringResource(R.string.ssh_probe_mismatch),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(R.string.ssh_probe_mismatch_old),
                            style = MaterialTheme.typography.titleSmall
                        )
                        ProbeFingerprint(outcome.pinned)
                        Text(
                            stringResource(R.string.ssh_probe_mismatch_new),
                            style = MaterialTheme.typography.titleSmall
                        )
                        ProbeFingerprint(outcome.presented)
                        OutlinedButton(onClick = { pendingRepin = outcome }) {
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

@Composable
private fun ProbeFingerprint(key: SshHostKey) {
    Text(key.keyType, style = MaterialTheme.typography.bodySmall)
    Text(
        key.fingerprint(), style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}

package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Https
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Load balancers domain: attach/detach backend servers and request/revoke a
 * certificate for a domain of this load balancer.
 */
@Composable
internal fun LoadBalancerScreen(token: String, server: Server, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var error by remember(token, server.id) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, server.id) { mutableStateOf("") }
    var busy by remember(token, server.id) { mutableStateOf(false) }
    var attachDialog by remember { mutableStateOf(false) }
    var detachDialog by remember { mutableStateOf(false) }
    var certDialog by remember { mutableStateOf(false) }
    var revokeDialog by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf<String?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    fun runAction(block: suspend () -> Unit) {
        busy = true
        error = null
        feedback = ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                feedback = doneMessage
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
    ) {
        Text(
            stringResource(R.string.load_balancer_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (busy) BusyIndicator()
        error?.let { failure -> ErrorState(failure) }
        if (feedback.isNotEmpty()) SuccessBanner(feedback)
        SectionCard(
            title = stringResource(R.string.g1_lb_backends_title),
            description = stringResource(R.string.g1_lb_backends_description),
            icon = Icons.Outlined.Dns
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) {
                CardAction(stringResource(R.string.lb_attach), icon = Icons.Outlined.AddLink, enabled = !busy, onClick = { attachDialog = true })
                CardAction(stringResource(R.string.lb_detach), icon = Icons.Outlined.LinkOff, enabled = !busy, onClick = { detachDialog = true })
            }
        }
        SectionCard(
            title = stringResource(R.string.g1_lb_certificates_title),
            description = stringResource(R.string.g1_lb_certificates_description),
            icon = Icons.Outlined.VerifiedUser
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) {
                CardAction(stringResource(R.string.lb_request_certificate), icon = Icons.Outlined.Https, enabled = !busy, onClick = { certDialog = true })
                // Revocation is destructive: styled as danger and still gated by SensitiveConfirmDialog below.
                DangerAction(stringResource(R.string.lb_revoke_certificate), onClick = { revokeDialog = true }, enabled = !busy)
            }
        }
    }

    if (attachDialog || detachDialog) {
        val isAttach = attachDialog
        ServerIdDialog(
            title = if (isAttach) R.string.lb_attach else R.string.lb_detach,
            busy = busy,
            onSubmit = { targetId ->
                attachDialog = false
                detachDialog = false
                runAction {
                    if (isAttach) PloiApi.attachLoadBalancerServer(token, server.id, targetId)
                    else PloiApi.detachLoadBalancerServer(token, server.id, targetId)
                }
            },
            onDismiss = { attachDialog = false; detachDialog = false }
        )
    }

    if (certDialog) {
        DomainDialog(
            title = R.string.lb_request_certificate,
            busy = busy,
            onSubmit = { domain ->
                certDialog = false
                runAction { PloiApi.requestLoadBalancerCertificate(token, server.id, domain) }
            },
            onDismiss = { certDialog = false }
        )
    }

    if (revokeDialog) {
        DomainDialog(
            title = R.string.lb_revoke_certificate,
            busy = busy,
            onSubmit = { domain ->
                revokeDialog = false
                confirmRevoke = domain
            },
            onDismiss = { revokeDialog = false }
        )
    }

    confirmRevoke?.let { domain ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_lb_revoke, domain),
            confirmLabel = R.string.lb_revoke_certificate,
            onConfirmed = {
                confirmRevoke = null
                runAction { PloiApi.revokeLoadBalancerCertificate(token, server.id, domain) }
            },
            onDismiss = { confirmRevoke = null }
        )
    }
}

@Composable
private fun ServerIdDialog(title: Int, busy: Boolean, onSubmit: (Long) -> Unit, onDismiss: () -> Unit) {
    var serverId by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                OutlinedTextField(
                    value = serverId, onValueChange = { serverId = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.lb_server_id_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = serverId.toLongOrNull()
                    if (id == null || id <= 0) invalid = true else onSubmit(id)
                },
                enabled = !busy && serverId.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun DomainDialog(title: Int, busy: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var domain by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.lb_domain_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = try {
                        validateRootDomain(domain.trim())
                    } catch (invalidDomain: IllegalArgumentException) {
                        null
                    }
                    if (target == null) invalid = true else onSubmit(target)
                },
                enabled = !busy && domain.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

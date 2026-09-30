package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Network rules domain: paginated firewall rules of a server with create and delete
 * (PIN/biometric) actions — the four documented /servers/{server}/network-rules routes.
 */
@Composable
internal fun NetworkRulesScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<NetworkRulePage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<NetworkRule?>(null) }

    val createdMessage = stringResource(R.string.network_rule_created)
    val deletedMessage = stringResource(R.string.network_rule_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.networkRules(token, serverId, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    fun runAction(message: String, block: suspend () -> Unit) {
        busy = true
        error = null
        feedback = ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                feedback = message
                refresh++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                primaryLabel = stringResource(R.string.new_network_rule), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.rules.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.rules.isEmpty()) item { EmptyState(Icons.Outlined.Shield, stringResource(R.string.empty_network_rules)) }
            items(data.rules, key = { it.id }) { rule ->
                ResourceCard(
                    title = rule.name, icon = Icons.Outlined.Shield,
                    status = rule.status,
                    facts = listOfNotNull(
                        ResourceFact(stringResource(R.string.g1_network_rule_port), rule.port, mono = true),
                        rule.protocol.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.network_rule_protocol_label), it.uppercase()) },
                        ResourceFact(stringResource(R.string.g1_network_rule_action), when (rule.ruleType.trim().lowercase()) {
                            "allow" -> stringResource(R.string.firewall_allow)
                            "deny" -> stringResource(R.string.firewall_deny)
                            else -> rule.ruleType
                        }),
                        rule.fromIpAddress.takeIf { it.isNotBlank() }?.let {
                            ResourceFact(stringResource(R.string.g1_network_rule_from), it, mono = true)
                        }
                    )
                ) {
                    DangerAction(stringResource(R.string.delete_network_rule), onClick = { confirmDelete = rule }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
            }
        }
    }

    if (creating) {
        CreateNetworkRuleDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createNetworkRule(token, serverId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { rule ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_network_rule, rule.name),
            confirmLabel = R.string.delete_network_rule,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteNetworkRule(token, serverId, rule.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create dialog for POST /servers/{server}/network-rules: name, port, type, rule_type (+ optional from_ip_address). */
@Composable
private fun CreateNetworkRuleDialog(
    busy: Boolean,
    onCreate: (CreateNetworkRuleRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("tcp") }
    var ruleType by remember { mutableStateOf("allow") }
    var fromIp by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_network_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.network_rule_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() || c == ':' } },
                    label = { Text(stringResource(R.string.network_rule_port_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.network_rule_protocol_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                    NETWORK_RULE_PROTOCOLS.forEach { option ->
                        OutlinedButton(onClick = { protocol = option }, enabled = protocol != option) {
                            Text(option.uppercase())
                        }
                    }
                }
                Text(stringResource(R.string.network_rule_type_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                    NETWORK_RULE_TYPES.forEach { option ->
                        OutlinedButton(onClick = { ruleType = option }, enabled = ruleType != option) {
                            Text(option.uppercase())
                        }
                    }
                }
                OutlinedTextField(
                    value = fromIp, onValueChange = { fromIp = it },
                    label = { Text(stringResource(R.string.network_rule_from_ip_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateNetworkRuleRequest(
                            name = name.trim(),
                            port = port.trim(),
                            protocol = protocol,
                            ruleType = ruleType,
                            fromIpAddress = fromIp.replace(" ", "")
                        )
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onCreate(request)
                    }
                },
                enabled = !busy && name.isNotBlank() && port.isNotBlank()
            ) { Text(stringResource(R.string.create_network_rule_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

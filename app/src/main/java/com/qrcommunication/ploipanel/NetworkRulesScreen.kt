package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
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

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
            OutlinedButton(onClick = { creating = true }, enabled = !busy) {
                Text(stringResource(R.string.new_network_rule))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.rules.isEmpty()) Text(stringResource(R.string.empty_network_rules))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 1) {
                    Text(stringResource(R.string.previous))
                }
                Text(
                    stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()),
                    Modifier.padding(top = 12.dp)
                )
                OutlinedButton(onClick = { page++ }, enabled = data.hasNext) {
                    Text(stringResource(R.string.next))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.rules, key = { it.id }) { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.network_rule_port,
                                    rule.port,
                                    rule.protocol.uppercase().ifBlank { "?" }
                                )
                            )
                            Text(stringResource(R.string.network_rule_type, rule.ruleType))
                            if (rule.fromIpAddress.isNotBlank()) {
                                Text(stringResource(R.string.network_rule_from_ip, rule.fromIpAddress))
                            }
                            if (rule.status.isNotBlank()) {
                                Text(stringResource(R.string.network_rule_status, rule.status))
                            }
                            OutlinedButton(onClick = { confirmDelete = rule }, enabled = !busy) {
                                Text(
                                    stringResource(R.string.delete_network_rule),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NETWORK_RULE_PROTOCOLS.forEach { option ->
                        OutlinedButton(onClick = { protocol = option }, enabled = protocol != option) {
                            Text(option.uppercase())
                        }
                    }
                }
                Text(stringResource(R.string.network_rule_type_label))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

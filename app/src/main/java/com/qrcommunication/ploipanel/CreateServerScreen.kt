package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Server creation: provider-based provisioning or custom (bring-your-own) server flow. */
@Composable
internal fun CreateServerScreen(token: String, onDone: () -> Unit, onCancel: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.back)) }
            OutlinedButton(onClick = { mode = 0 }, enabled = mode != 0) {
                Text(stringResource(R.string.create_via_provider))
            }
            OutlinedButton(onClick = { mode = 1 }, enabled = mode != 1) {
                Text(stringResource(R.string.create_custom))
            }
        }
        if (mode == 0) ProviderCreateForm(token, onDone) else CustomCreateForm(token, onDone)
    }
}

/** Simple single-choice picker rendered as a button opening a dialog list. */
@Composable
private fun OptionPicker(
    label: String, selected: String, options: List<String>, enabled: Boolean = true, onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(if (selected.isEmpty()) label else "$label : $selected")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options) { option ->
                        TextButton(onClick = { onSelect(option); open = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** POST /servers using a provider credential, plan and region already linked to the Ploi account. */
@Composable
private fun ProviderCreateForm(token: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var providers by remember(token) { mutableStateOf<ProviderPage?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }
    var credential by remember { mutableStateOf<ProviderCredential?>(null) }
    var plan by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("server") }
    var databaseType by remember { mutableStateOf("mysql") }
    var webserverType by remember { mutableStateOf("nginx") }
    var phpVersion by remember { mutableStateOf("8.4") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<Throwable?>(null) }
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        loading = true
        error = null
        try {
            providers = withContext(Dispatchers.IO) { PloiApi.providers(token, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            providers = null
            error = failure
        } finally {
            loading = false
        }
    }

    if (loading) CircularProgressIndicator()
    if (error != null) ApiErrorText(error!!)
    providers?.let { page ->
        if (page.providers.isEmpty()) {
            Text(stringResource(R.string.empty_providers))
        } else {
            OptionPicker(
                label = stringResource(R.string.credential_label),
                selected = credential?.displayName.orEmpty(),
                options = page.providers.map { it.displayName }
            ) { chosen -> credential = page.providers.first { it.displayName == chosen }; plan = ""; region = "" }
            credential?.let { chosen ->
                OptionPicker(
                    label = stringResource(R.string.plan_label),
                    selected = plan,
                    options = chosen.plans.map { option -> option.id + " — " + option.name }
                ) { picked -> plan = chosen.plans.first { picked.startsWith(it.id + " — ") }.id }
                OptionPicker(
                    label = stringResource(R.string.region_label),
                    selected = region,
                    options = chosen.regions.map { option -> option.id + " — " + option.name }
                ) { picked -> region = chosen.regions.first { picked.startsWith(it.id + " — ") }.id }
            }
        }
    }
    OptionPicker(stringResource(R.string.server_type_label), type, SERVER_TYPES.toList()) { type = it }
    OptionPicker(stringResource(R.string.database_label), databaseType, DATABASE_TYPES.toList()) { databaseType = it }
    OptionPicker(stringResource(R.string.webserver_label), webserverType, WEBSERVER_TYPES.toList()) { webserverType = it }
    OptionPicker(stringResource(R.string.php_label), phpVersion, PHP_VERSIONS.toList()) { phpVersion = it }
    OutlinedTextField(
        value = name, onValueChange = { name = it },
        label = { Text(stringResource(R.string.name_optional_label)) },
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
    if (submitError != null) ApiErrorText(submitError!!)
    Button(
        onClick = {
            val chosen = credential ?: return@Button
            busy = true
            invalid = false
            submitError = null
            scope.launch {
                try {
                    val request = CreateServerRequest(
                        plan = plan, region = region, credential = chosen.id, type = type,
                        databaseType = databaseType, webserverType = webserverType,
                        phpVersion = phpVersion, name = name.trim()
                    )
                    val created = withContext(Dispatchers.IO) { PloiApi.createServer(token, request) }
                    if (created.id > 0) onDone()
                } catch (invalidRequest: IllegalArgumentException) {
                    invalid = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    submitError = failure
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && credential != null && plan.isNotEmpty() && region.isNotEmpty()
    ) { Text(stringResource(R.string.create_server_submit)) }
}

/** POST /servers/custom then optional POST /servers/custom/{id}/start. */
@Composable
private fun CustomCreateForm(token: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("server") }
    var ip by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var databaseType by remember { mutableStateOf("mysql") }
    var phpVersion by remember { mutableStateOf("8.4") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<Throwable?>(null) }
    var creation by remember { mutableStateOf<CustomServerCreation?>(null) }
    var startMessage by remember { mutableStateOf("") }

    val created = creation
    if (created == null) {
        OptionPicker(stringResource(R.string.server_type_label), type, CUSTOM_SERVER_TYPES.toList()) { type = it }
        OutlinedTextField(
            value = ip, onValueChange = { ip = it },
            label = { Text(stringResource(R.string.ip_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = sshPort, onValueChange = { sshPort = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.ssh_port_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OptionPicker(stringResource(R.string.database_label), databaseType, DATABASE_TYPES.toList()) { databaseType = it }
        OptionPicker(stringResource(R.string.php_label), phpVersion, PHP_VERSIONS.toList()) { phpVersion = it }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.name_optional_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
        if (submitError != null) ApiErrorText(submitError!!)
        Button(
            onClick = {
                val port = sshPort.toIntOrNull()
                if (port == null) {
                    invalid = true
                    return@Button
                }
                busy = true
                invalid = false
                submitError = null
                scope.launch {
                    try {
                        val request = CreateCustomServerRequest(
                            type = type, ip = ip.trim(), sshPort = port,
                            databaseType = databaseType, phpVersion = phpVersion, name = name.trim()
                        )
                        creation = withContext(Dispatchers.IO) { PloiApi.createCustomServer(token, request) }
                    } catch (invalidRequest: IllegalArgumentException) {
                        invalid = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        submitError = failure
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && ip.isNotBlank()
        ) { Text(stringResource(R.string.create_custom_submit)) }
    } else {
        Text(stringResource(R.string.custom_server_ready, created.name), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.custom_key_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer { Text(created.publicKey, Modifier.padding(12.dp)) }
        }
        Text(stringResource(R.string.custom_command_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer { Text(created.sshCommand, Modifier.padding(12.dp)) }
        }
        Text(stringResource(R.string.custom_next_step))
        if (submitError != null) ApiErrorText(submitError!!)
        if (startMessage.isNotEmpty()) Text(startMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    busy = true
                    submitError = null
                    scope.launch {
                        try {
                            startMessage = withContext(Dispatchers.IO) {
                                PloiApi.startCustomServerInstallation(token, created.id)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            submitError = failure
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && startMessage.isEmpty()
            ) { Text(stringResource(R.string.start_installation)) }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
    }
}

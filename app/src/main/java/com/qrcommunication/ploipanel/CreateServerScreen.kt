package com.qrcommunication.ploipanel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    var customResultReady by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { if (customResultReady) confirmExit = true else onCancel() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = PanelSpacing.maxContentWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                IconButton(onClick = { if (customResultReady) confirmExit = true else onCancel() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.new_server), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                FilterChip(
                    selected = mode == 0, onClick = { mode = 0 }, enabled = mode == 0 || !customResultReady,
                    label = { Text(stringResource(R.string.create_via_provider)) },
                    leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = mode == 1, onClick = { mode = 1 },
                    label = { Text(stringResource(R.string.create_custom)) },
                    leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            if (mode == 0) ProviderCreateForm(token, onDone)
            else CustomCreateForm(token, onDone, onCreated = { customResultReady = true })
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.custom_leave_title)) },
            text = { Text(stringResource(R.string.custom_leave_warning)) },
            confirmButton = {
                Button(onClick = { confirmExit = false; onCancel() }) { Text(stringResource(R.string.custom_leave_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Simple single-choice picker rendered as a button opening a dialog list. */
@Composable
private fun OptionPicker(
    label: String, selected: String, options: List<String>, enabled: Boolean = true, onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = PanelSpacing.lg, vertical = PanelSpacing.sm)
    ) {
        // Field-like chooser: the label above, the chosen machine value below in monospace.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
            Text(
                label, style = if (selected.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected.isNotEmpty()) Text(selected, style = panelMonoStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options) { option ->
                        TextButton(onClick = { onSelect(option); open = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                option, Modifier.fillMaxWidth(),
                                fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                            )
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
    var providerPage by remember(token) { mutableIntStateOf(1) }
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

    LaunchedEffect(token, providerPage) {
        loading = true
        providers = null
        error = null
        try {
            providers = withContext(Dispatchers.IO) { PloiApi.providers(token, page = providerPage, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            providers = null
            error = failure
        } finally {
            loading = false
        }
    }

    SectionCard(title = stringResource(R.string.credential_label), icon = Icons.Outlined.Cloud) {
        if (loading) BusyIndicator()
        if (error != null) ApiErrorText(error!!)
        providers?.let { page ->
            if (page.providers.isEmpty()) {
                Text(
                    stringResource(R.string.empty_providers), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OptionPicker(
                    label = stringResource(R.string.credential_label),
                    selected = credential?.let { "${it.displayName} (#${it.id})" }.orEmpty(),
                    options = page.providers.map { "${it.displayName} (#${it.id})" }
                ) { chosen ->
                    credential = page.providers.first { "${it.displayName} (#${it.id})" == chosen }
                    plan = ""
                    region = ""
                }
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
            if (page.currentPage > 1 || page.hasNext) {
                PageBar(
                    page.currentPage, page.lastPage, page.hasNext,
                    onPrevious = { credential = null; plan = ""; region = ""; providerPage = page.currentPage - 1 },
                    onNext = { credential = null; plan = ""; region = ""; providerPage = page.currentPage + 1 }
                )
            }
        }
    }
    SectionCard(title = stringResource(R.string.g4_server_setup), icon = Icons.Outlined.Tune) {
        OptionPicker(stringResource(R.string.server_type_label), type, SERVER_TYPES.toList()) { type = it }
        OptionPicker(stringResource(R.string.database_label), databaseType, DATABASE_TYPES.toList()) { databaseType = it }
        OptionPicker(stringResource(R.string.webserver_label), webserverType, WEBSERVER_TYPES.toList()) { webserverType = it }
        OptionPicker(stringResource(R.string.php_label), phpVersion, PHP_VERSIONS.toList()) { phpVersion = it }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.name_optional_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
    }
    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
    if (submitError != null) ErrorState(submitError!!)
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
                    if (created.id <= 0L) throw PloiMalformedPayloadException(IllegalStateException("Missing created server ID"))
                    onDone()
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
        enabled = !busy && credential != null && plan.isNotEmpty() && region.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.create_server_submit), Modifier.padding(start = PanelSpacing.sm))
    }
}

/** POST /servers/custom then optional POST /servers/custom/{id}/start. */
@Composable
private fun CustomCreateForm(token: String, onDone: () -> Unit, onCreated: () -> Unit) {
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
        SectionCard(title = stringResource(R.string.g4_server_setup), icon = Icons.Outlined.Dns) {
            OptionPicker(stringResource(R.string.server_type_label), type, CUSTOM_SERVER_TYPES.toList()) { type = it }
            OutlinedTextField(
                value = ip, onValueChange = { ip = it },
                label = { Text(stringResource(R.string.ip_label)) },
                singleLine = true,
                textStyle = panelMonoStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sshPort, onValueChange = { sshPort = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.ssh_port_label)) },
                singleLine = true,
                textStyle = panelMonoStyle,
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
        }
        if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
        if (submitError != null) ErrorState(submitError!!)
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
                        val created = withContext(Dispatchers.IO) { PloiApi.createCustomServer(token, request) }
                        if (created.id <= 0L) throw PloiMalformedPayloadException(IllegalStateException("Missing created server ID"))
                        creation = created
                        onCreated()
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
            enabled = !busy && ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.create_custom_submit), Modifier.padding(start = PanelSpacing.sm))
        }
    } else {
        SuccessBanner(stringResource(R.string.custom_server_ready, created.name))
        SectionCard(title = stringResource(R.string.custom_key_title), icon = Icons.Outlined.Key) {
            MonoBlock(created.publicKey)
        }
        SectionCard(title = stringResource(R.string.custom_command_title), icon = Icons.Outlined.Terminal) {
            MonoBlock(created.sshCommand)
        }
        Text(stringResource(R.string.custom_next_step), style = MaterialTheme.typography.bodyMedium)
        if (submitError != null) ErrorState(submitError!!)
        if (startMessage.isNotEmpty()) SuccessBanner(startMessage)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
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
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.start_installation), Modifier.padding(start = PanelSpacing.sm))
            }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
    }
}

/** One-time setup value (public key, root command): selectable monospace on a tinted block. */
@Composable
private fun MonoBlock(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer { Text(text, style = panelMonoStyle, modifier = Modifier.padding(PanelSpacing.md)) }
    }
}

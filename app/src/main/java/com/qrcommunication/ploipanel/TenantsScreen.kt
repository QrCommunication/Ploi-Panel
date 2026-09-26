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

/** Tenants domain: tenant domains of a multi-tenant site with certificates and NGINX config. */
@Composable
internal fun TenantsScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<SiteTenants?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var confirmRevoke by remember { mutableStateOf<String?>(null) }
    var nginxTenant by remember { mutableStateOf<String?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.tenants(token, serverId, siteId) }
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
                Text(stringResource(R.string.new_tenant))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.main.isNotBlank()) Text(stringResource(R.string.tenant_main, data.main))
            if (data.tenants.isEmpty()) Text(stringResource(R.string.empty_tenants))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.tenants, key = { it }) { tenant ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tenant, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        runAction(doneMessage) {
                                            PloiApi.requestTenantCertificate(token, serverId, siteId, tenant)
                                        }
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.tenant_request_certificate)) }
                                OutlinedButton(onClick = { confirmRevoke = tenant }, enabled = !busy) {
                                    Text(stringResource(R.string.tenant_revoke_certificate))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { nginxTenant = tenant }, enabled = !busy) {
                                    Text(stringResource(R.string.tenant_nginx))
                                }
                                OutlinedButton(onClick = { confirmDelete = tenant }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.delete_tenant),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var domains by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.new_tenant)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = domains, onValueChange = { domains = it },
                        label = { Text(stringResource(R.string.tenant_domains_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val list = domains.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
                        val valid = try {
                            validateDomainList(list)
                        } catch (invalidDomains: IllegalArgumentException) {
                            null
                        }
                        if (valid == null) {
                            invalid = true
                        } else {
                            creating = false
                            runAction(doneMessage) { PloiApi.createTenants(token, serverId, siteId, valid) }
                        }
                    },
                    enabled = !busy && domains.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    confirmDelete?.let { tenant ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_tenant, tenant),
            confirmLabel = R.string.delete_tenant,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteTenant(token, serverId, siteId, tenant) }
            },
            onDismiss = { confirmDelete = null }
        )
    }

    confirmRevoke?.let { tenant ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_tenant_revoke, tenant),
            confirmLabel = R.string.tenant_revoke_certificate,
            onConfirmed = {
                confirmRevoke = null
                runAction(doneMessage) { PloiApi.revokeTenantCertificate(token, serverId, siteId, tenant) }
            },
            onDismiss = { confirmRevoke = null }
        )
    }

    nginxTenant?.let { tenant ->
        TenantNginxDialog(
            token = token, serverId = serverId, siteId = siteId, tenant = tenant,
            onSaved = { message -> feedback = message },
            onDismiss = { nginxTenant = null }
        )
    }
}

/** View/edit dialog of a tenant NGINX configuration (GET then PATCH). */
@Composable
private fun TenantNginxDialog(
    token: String, serverId: Long, siteId: Long, tenant: String,
    onSaved: (String) -> Unit, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var content by remember(tenant) { mutableStateOf<String?>(null) }
    var error by remember(tenant) { mutableStateOf<Throwable?>(null) }
    var saving by remember(tenant) { mutableStateOf(false) }
    var draft by remember(tenant) { mutableStateOf("") }
    LaunchedEffect(tenant) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                PloiApi.tenantNginxConfiguration(token, serverId, siteId, tenant)
            }
            content = loaded
            draft = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tenant_nginx_title, tenant)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (content == null && error == null) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                if (content != null) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val ack = withContext(Dispatchers.IO) {
                                PloiApi.updateTenantNginxConfiguration(token, serverId, siteId, tenant, draft)
                            }
                            onSaved(ack.message)
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = content != null && !saving && draft.isNotBlank()
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

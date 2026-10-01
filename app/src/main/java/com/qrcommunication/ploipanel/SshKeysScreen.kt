package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
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

/** SSH keys domain: paginated public keys of a server with create and delete actions. */
@Composable
internal fun SshKeysScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<SshKeyPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SshKey?>(null) }

    val createdMessage = stringResource(R.string.ssh_key_created)
    val deletedMessage = stringResource(R.string.ssh_key_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.sshKeys(token, serverId, page) }
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
                primaryLabel = stringResource(R.string.new_ssh_key), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.keys.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.keys.isEmpty()) item { EmptyState(Icons.Outlined.Key, stringResource(R.string.empty_ssh_keys)) }
            items(data.keys, key = { it.id }) { key ->
                ResourceCard(
                    title = key.name, icon = Icons.Outlined.Key,
                    status = key.status,
                    facts = listOfNotNull(
                        key.systemUser.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g1_fact_user), it, mono = true) },
                        key.key.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g1_ssh_key_public), it.take(48) + "…", mono = true) }
                    )
                ) {
                    DangerAction(stringResource(R.string.delete_ssh_key), onClick = { confirmDelete = key }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
            }
        }
    }

    if (creating) {
        CreateSshKeyDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createSshKey(token, serverId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { key ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_ssh_key, key.name),
            confirmLabel = R.string.delete_ssh_key,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteSshKey(token, serverId, key.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create dialog for POST /servers/{server}/ssh-keys: name, public key and system user. */
@Composable
private fun CreateSshKeyDialog(
    busy: Boolean,
    onCreate: (CreateSshKeyRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var systemUser by remember { mutableStateOf("ploi") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_ssh_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ssh_key_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(stringResource(R.string.ssh_key_key_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemUser, onValueChange = { systemUser = it },
                    label = { Text(stringResource(R.string.ssh_key_user_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateSshKeyRequest(name.trim(), key.trim(), systemUser.trim())
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) invalid = true else onCreate(request)
                },
                enabled = !busy && name.isNotBlank() && key.isNotBlank()
            ) { Text(stringResource(R.string.create_ssh_key_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

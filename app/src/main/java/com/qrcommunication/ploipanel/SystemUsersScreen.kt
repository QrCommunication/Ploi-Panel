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
import androidx.compose.material3.Switch
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
 * System users domain: paginated Linux accounts of a server with create and delete
 * (PIN/biometric) actions — the four documented /servers/{server}/system-users routes.
 */
@Composable
internal fun SystemUsersScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<SystemUserPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SystemUser?>(null) }
    // Sudo password returned once by the API when receive_password was requested; never persisted.
    var revealedPassword by remember { mutableStateOf<Pair<String, String>?>(null) }

    val createdMessage = stringResource(R.string.system_user_created)
    val deletedMessage = stringResource(R.string.system_user_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.systemUsers(token, serverId, page) }
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
                Text(stringResource(R.string.new_system_user))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.users.isEmpty()) Text(stringResource(R.string.empty_system_users))
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
                items(data.users, key = { it.id }) { user ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium)
                            if (user.root.isNotBlank()) {
                                Text(stringResource(R.string.system_user_root, user.root))
                            }
                            if (user.createdAt.isNotBlank()) {
                                Text(stringResource(R.string.system_user_created_at, user.createdAt))
                            }
                            OutlinedButton(onClick = { confirmDelete = user }, enabled = !busy) {
                                Text(
                                    stringResource(R.string.delete_system_user),
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
        CreateSystemUserDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                busy = true
                error = null
                scope.launch {
                    try {
                        val created = withContext(Dispatchers.IO) { PloiApi.createSystemUser(token, serverId, request) }
                        feedback = createdMessage
                        if (created.password.isNotBlank()) {
                            revealedPassword = created.user.name to created.password
                        }
                        refresh++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { user ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_system_user, user.name),
            confirmLabel = R.string.delete_system_user,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteSystemUser(token, serverId, user.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
    revealedPassword?.let { (name, password) ->
        AlertDialog(
            onDismissRequest = { revealedPassword = null },
            title = { Text(stringResource(R.string.system_user_password_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(password, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.system_user_password_warning))
                }
            },
            confirmButton = {
                Button(onClick = { revealedPassword = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

/** Create dialog for POST /servers/{server}/system-users: name (+ optional sudo and receive_password flags). */
@Composable
private fun CreateSystemUserDialog(
    busy: Boolean,
    onCreate: (CreateSystemUserRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sudo by remember { mutableStateOf(false) }
    var receivePassword by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_system_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.system_user_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.system_user_sudo_label), Modifier.padding(top = 12.dp))
                    Switch(checked = sudo, onCheckedChange = { sudo = it })
                }
                if (sudo) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.system_user_receive_password_label), Modifier.padding(top = 12.dp))
                        Switch(checked = receivePassword, onCheckedChange = { receivePassword = it })
                    }
                }
                if (invalid) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateSystemUserRequest(
                            name = name.trim(),
                            sudo = sudo,
                            receivePassword = sudo && receivePassword
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
                enabled = !busy && name.isNotBlank()
            ) { Text(stringResource(R.string.create_system_user_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

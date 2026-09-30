package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Databases + database-users domains: paginated databases of a server with create, acknowledge,
 * duplicate, forget (PIN/biometric), delete (PIN/biometric) and a per-database users manager.
 */
@Composable
internal fun DatabasesScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<DatabasePage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var acknowledging by remember { mutableStateOf(false) }
    var duplicating by remember { mutableStateOf<PloiDatabase?>(null) }
    var confirmForget by remember { mutableStateOf<PloiDatabase?>(null) }
    var confirmDelete by remember { mutableStateOf<PloiDatabase?>(null) }
    var usersFor by remember { mutableStateOf<PloiDatabase?>(null) }

    val createdMessage = stringResource(R.string.database_created)
    val acknowledgedMessage = stringResource(R.string.database_acknowledged)
    val forgottenMessage = stringResource(R.string.database_forgotten)
    val deletedMessage = stringResource(R.string.database_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        result = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.databases(token, serverId, page) }
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
                primaryLabel = stringResource(R.string.new_database), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.databases.size) },
                extra = {
                    TextButton(onClick = { acknowledging = true }, enabled = !busy) {
                        Text(stringResource(R.string.acknowledge_database), maxLines = 1)
                    }
                }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(plainFromMarkdown(feedback)) }
        result?.let { data ->
            if (data.databases.isEmpty()) item { EmptyState(Icons.Outlined.Storage, stringResource(R.string.empty_databases)) }
            items(data.databases, key = { it.id }) { database ->
                ResourceCard(
                    title = database.name, monoTitle = true, icon = Icons.Outlined.Storage,
                    status = database.status,
                    facts = listOfNotNull(
                        database.type.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.server_type_label), it) },
                        database.siteDomain.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_linked_site_label), it, mono = true) },
                        database.createdAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_created_label), it) }
                    )
                ) {
                    CardAction(stringResource(R.string.database_users), icon = Icons.Outlined.Group, enabled = !busy, onClick = { usersFor = database })
                    CardAction(stringResource(R.string.duplicate_database), icon = Icons.Outlined.ContentCopy, enabled = !busy, onClick = { duplicating = database })
                    // Forget only unlinks the database from Ploi (data kept); still behind SensitiveConfirmDialog.
                    CardAction(stringResource(R.string.forget_database), icon = Icons.Outlined.LinkOff, enabled = !busy, onClick = { confirmForget = database })
                    DangerAction(stringResource(R.string.delete_database), onClick = { confirmDelete = database }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
            }
        }
    }

    if (creating) {
        CreateDatabaseDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createDatabase(token, serverId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    if (acknowledging) {
        AcknowledgeDatabaseDialog(
            busy = busy,
            onAcknowledge = { name ->
                acknowledging = false
                runAction(acknowledgedMessage) { PloiApi.acknowledgeDatabase(token, serverId, name) }
            },
            onDismiss = { acknowledging = false }
        )
    }
    duplicating?.let { database ->
        DuplicateDatabaseDialog(
            database = database,
            busy = busy,
            onDuplicate = { name, user, password ->
                duplicating = null
                feedback = ""
                runAction("") {
                    feedback = PloiApi.duplicateDatabase(token, serverId, database.id, name, user, password).message
                }
            },
            onDismiss = { duplicating = null }
        )
    }
    confirmForget?.let { database ->
        SensitiveConfirmDialog(
            lock = lock, activity = activity,
            message = stringResource(R.string.confirm_forget_database, database.name),
            confirmLabel = R.string.forget_database,
            onConfirmed = {
                confirmForget = null
                runAction(forgottenMessage) { PloiApi.forgetDatabase(token, serverId, database.id) }
            },
            onDismiss = { confirmForget = null }
        )
    }
    confirmDelete?.let { database ->
        SensitiveConfirmDialog(
            lock = lock, activity = activity,
            message = stringResource(R.string.confirm_delete_database, database.name),
            confirmLabel = R.string.delete_database,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteDatabase(token, serverId, database.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
    usersFor?.let { database ->
        DatabaseUsersDialog(
            token = token, serverId = serverId, database = database,
            lock = lock, activity = activity,
            onDismiss = { usersFor = null }
        )
    }
}

/** Create form for POST /servers/{server}/databases with documented client-side validation. */
@Composable
private fun CreateDatabaseDialog(busy: Boolean, onCreate: (CreateDatabaseRequest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var siteId by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_database)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.database_name_label)) },
                    supportingText = { Text(stringResource(R.string.database_name_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.database_user_optional_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.database_password_optional_label)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.database_description_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = siteId, onValueChange = { siteId = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.database_site_id_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val request = try {
                    CreateDatabaseRequest(
                        name = name.trim(), user = user.trim(), password = password,
                        description = description.trim(), siteId = siteId.toLongOrNull()
                    )
                } catch (invalidInput: IllegalArgumentException) {
                    invalid = true
                    null
                }
                if (request != null) {
                    invalid = false
                    onCreate(request)
                }
            }, enabled = !busy && name.isNotBlank()) { Text(stringResource(R.string.create_database_submit)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Acknowledge form for POST /servers/{server}/databases/acknowledge. */
@Composable
private fun AcknowledgeDatabaseDialog(busy: Boolean, onAcknowledge: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.acknowledge_database)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.acknowledge_database_hint))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.database_name_label)) },
                    supportingText = { Text(stringResource(R.string.database_name_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    onAcknowledge(validateDatabaseName(name.trim()))
                    invalid = false
                } catch (invalidInput: IllegalArgumentException) {
                    invalid = true
                }
            }, enabled = !busy && name.isNotBlank()) { Text(stringResource(R.string.acknowledge_database)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Duplicate form for POST /databases/{database}/duplicate (name max 255, password max 50). */
@Composable
private fun DuplicateDatabaseDialog(
    database: PloiDatabase, busy: Boolean,
    onDuplicate: (name: String, user: String, password: String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duplicate_database)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.duplicate_database_hint, database.name))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.duplicate_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.database_user_optional_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.database_password_optional_label)) },
                    supportingText = { Text(stringResource(R.string.duplicate_password_hint)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onDuplicate(name.trim(), user.trim(), password) },
                enabled = !busy && name.isNotBlank() && name.length <= DUPLICATE_NAME_MAX_LENGTH &&
                    password.length <= DUPLICATE_PASSWORD_MAX_LENGTH
            ) { Text(stringResource(R.string.duplicate_database)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Paginated users of one database: list, create, attach existing, delete (re-authenticated). */
@Composable
private fun DatabaseUsersDialog(
    token: String, serverId: Long, database: PloiDatabase,
    lock: AppLock, activity: FragmentActivity, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<DatabaseUserPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DatabaseUser?>(null) }

    val createdMessage = stringResource(R.string.database_user_created)
    val attachedMessage = stringResource(R.string.database_user_attached)
    val deletedMessage = stringResource(R.string.database_user_deleted)

    LaunchedEffect(token, serverId, database.id, page, refresh) {
        loading = true
        error = null
        result = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.databaseUsers(token, serverId, database.id, page) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.database_users_title, database.name)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                    CardAction(stringResource(R.string.new_database_user), icon = Icons.Outlined.PersonAdd, enabled = !busy, onClick = { creating = true })
                    CardAction(stringResource(R.string.attach_database_user), icon = Icons.Outlined.AddLink, enabled = !busy, onClick = { attaching = true })
                }
                if (loading) LoadingState(rows = 1)
                if (error != null) ErrorState(error!!)
                if (feedback.isNotEmpty()) SuccessBanner(feedback)
                result?.let { data ->
                    if (data.users.isEmpty()) EmptyState(Icons.Outlined.Group, stringResource(R.string.empty_database_users))
                    PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
                    data.users.forEach { user ->
                        val yesNo =
                            stringResource(if (user.remote) R.string.flag_yes else R.string.flag_no)
                        val readonly =
                            stringResource(if (user.readonly) R.string.flag_yes else R.string.flag_no)
                        ResourceCard(
                            title = user.user, monoTitle = true, icon = Icons.Outlined.Person,
                            facts = listOfNotNull(
                                ResourceFact(stringResource(R.string.database_user_remote), yesNo),
                                user.remoteIp.takeIf { user.remote && it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_remote_ip_label), it, mono = true) },
                                ResourceFact(stringResource(R.string.database_user_readonly), readonly),
                                user.createdAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_created_label), it) }
                            )
                        ) {
                            DangerAction(stringResource(R.string.delete_database_user), onClick = { confirmDelete = user }, enabled = !busy)
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } }
    )

    if (creating) {
        CreateDatabaseUserDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createDatabaseUser(token, serverId, database.id, request) }
            },
            onDismiss = { creating = false }
        )
    }
    if (attaching) {
        AttachDatabaseUserDialog(
            busy = busy,
            onAttach = { userId ->
                attaching = false
                runAction(attachedMessage) { PloiApi.attachDatabaseUser(token, serverId, database.id, userId) }
            },
            onDismiss = { attaching = false }
        )
    }
    confirmDelete?.let { user ->
        SensitiveConfirmDialog(
            lock = lock, activity = activity,
            message = stringResource(R.string.confirm_delete_database_user, user.user),
            confirmLabel = R.string.delete_database_user,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteDatabaseUser(token, serverId, database.id, user.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create form for POST …/databases/{database}/users (remote requires remote_ip, documented). */
@Composable
private fun CreateDatabaseUserDialog(
    busy: Boolean, onCreate: (CreateDatabaseUserRequest) -> Unit, onDismiss: () -> Unit
) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf(false) }
    var remoteIp by remember { mutableStateOf("") }
    var readonly by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_database_user)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.database_user_label)) },
                    supportingText = { Text(stringResource(R.string.database_user_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.database_password_label)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.database_user_remote), Modifier.padding(top = 12.dp))
                    Switch(checked = remote, onCheckedChange = { remote = it })
                }
                if (remote) {
                    OutlinedTextField(value = remoteIp, onValueChange = { remoteIp = it },
                        label = { Text(stringResource(R.string.database_remote_ip_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.database_user_readonly), Modifier.padding(top = 12.dp))
                    Switch(checked = readonly, onCheckedChange = { readonly = it })
                }
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val request = try {
                    CreateDatabaseUserRequest(
                        user = user.trim(), password = password,
                        remote = remote, remoteIp = remoteIp.trim(), readonly = readonly
                    )
                } catch (invalidInput: IllegalArgumentException) {
                    invalid = true
                    null
                }
                if (request != null) {
                    invalid = false
                    onCreate(request)
                }
            }, enabled = !busy && user.isNotBlank() && password.isNotBlank()) {
                Text(stringResource(R.string.create_database_user_submit))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Attach form for POST …/users/attach: numeric ID of an existing server-level user. */
@Composable
private fun AttachDatabaseUserDialog(busy: Boolean, onAttach: (Long) -> Unit, onDismiss: () -> Unit) {
    var userId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_database_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.attach_database_user_hint))
                OutlinedTextField(value = userId, onValueChange = { userId = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.attach_user_id_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { userId.toLongOrNull()?.let(onAttach) },
                enabled = !busy && (userId.toLongOrNull() ?: 0) > 0
            ) { Text(stringResource(R.string.attach_user_submit)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
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
 * Crontabs domain: paginated cron jobs of a server with create and delete
 * (PIN/biometric) actions — the four documented /servers/{server}/crontabs routes.
 */
@Composable
internal fun CrontabsScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<CrontabPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Crontab?>(null) }

    val createdMessage = stringResource(R.string.crontab_created)
    val deletedMessage = stringResource(R.string.crontab_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.crontabs(token, serverId, page) }
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
                primaryLabel = stringResource(R.string.new_crontab), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.crontabs.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.crontabs.isEmpty()) item { EmptyState(Icons.Outlined.Schedule, stringResource(R.string.empty_crontabs)) }
            items(data.crontabs, key = { it.id }) { crontab ->
                ResourceCard(
                    title = crontab.command, monoTitle = true, icon = Icons.Outlined.Schedule,
                    facts = listOfNotNull(
                        ResourceFact(stringResource(R.string.g1_crontab_frequency), crontab.frequency, mono = true),
                        crontab.user.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g1_fact_user), it, mono = true) },
                        crontab.createdAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g1_fact_created), it) }
                    )
                ) {
                    DangerAction(stringResource(R.string.delete_crontab), onClick = { confirmDelete = crontab }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
            }
        }
    }

    if (creating) {
        CreateCrontabDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(createdMessage) { PloiApi.createCrontab(token, serverId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    confirmDelete?.let { crontab ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_crontab, crontab.command),
            confirmLabel = R.string.delete_crontab,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteCrontab(token, serverId, crontab.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create dialog for POST /servers/{server}/crontabs: user, command and frequency (documented). */
@Composable
private fun CreateCrontabDialog(
    busy: Boolean,
    onCreate: (CreateCrontabRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var user by remember { mutableStateOf("ploi") }
    var command by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("* * * * *") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_crontab)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.crontab_user_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text(stringResource(R.string.crontab_command_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = frequency, onValueChange = { frequency = it },
                    label = { Text(stringResource(R.string.crontab_frequency_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
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
                        CreateCrontabRequest(user.trim(), command.trim(), frequency.trim())
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) {
                        invalid = true
                    } else {
                        onCreate(request)
                    }
                },
                enabled = !busy && command.isNotBlank()
            ) { Text(stringResource(R.string.create_crontab_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

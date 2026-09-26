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

/** Aliases domain: additional domains answering on the same site. */
@Composable
internal fun AliasesScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<SiteAliases?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.aliases(token, serverId, siteId) }
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
                Text(stringResource(R.string.new_alias))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.main.isNotBlank()) Text(stringResource(R.string.alias_main, data.main))
            if (data.aliases.isEmpty()) Text(stringResource(R.string.empty_aliases))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.aliases, key = { it }) { alias ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(alias, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                            OutlinedButton(onClick = { confirmDelete = alias }, enabled = !busy) {
                                Text(
                                    stringResource(R.string.delete_alias),
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
        var domains by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.new_alias)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = domains, onValueChange = { domains = it },
                        label = { Text(stringResource(R.string.alias_domains_label)) },
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
                            runAction(doneMessage) { PloiApi.createAliases(token, serverId, siteId, valid) }
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
    confirmDelete?.let { alias ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_alias, alias),
            confirmLabel = R.string.delete_alias,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteAlias(token, serverId, siteId, alias) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

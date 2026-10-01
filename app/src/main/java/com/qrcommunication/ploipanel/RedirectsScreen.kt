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
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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

/** Redirects domain: paginated redirects of a site with create and delete actions. */
@Composable
internal fun RedirectsScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId, siteId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<RedirectPage?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SiteRedirect?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, page, refresh) {
        loading = true
        error = null
        result = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.redirects(token, serverId, siteId, page) }
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
                primaryLabel = stringResource(R.string.new_redirect), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.redirects.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading && !busy) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.redirects.isEmpty()) item {
                EmptyState(Icons.AutoMirrored.Outlined.AltRoute, stringResource(R.string.empty_redirects))
            }
            items(data.redirects, key = { it.id }) { redirect ->
                ResourceCard(
                    title = redirect.redirectFrom, monoTitle = true, icon = Icons.AutoMirrored.Outlined.AltRoute,
                    status = redirect.status,
                    facts = listOfNotNull(
                        ResourceFact(stringResource(R.string.redirect_to_label), redirect.redirectTo, mono = true),
                        redirect.type.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_type), it) }
                    )
                ) {
                    DangerAction(stringResource(R.string.delete_redirect), onClick = { confirmDelete = redirect }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ }, enabled = !loading)
            }
        }
    }

    if (creating) {
        var redirectFrom by remember { mutableStateOf("/") }
        var redirectTo by remember { mutableStateOf("") }
        var permanent by remember { mutableStateOf(true) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.new_redirect)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                    OutlinedTextField(
                        value = redirectFrom, onValueChange = { redirectFrom = it },
                        label = { Text(stringResource(R.string.redirect_from_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = redirectTo, onValueChange = { redirectTo = it },
                        label = { Text(stringResource(R.string.redirect_to_label)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                        FilterChip(selected = permanent, onClick = { permanent = true },
                            label = { Text(stringResource(R.string.redirect_permanent)) })
                        FilterChip(selected = !permanent, onClick = { permanent = false },
                            label = { Text(stringResource(R.string.redirect_temporary)) })
                    }
                    if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val request = try {
                            CreateRedirectRequest(
                                redirectFrom.trim(), redirectTo.trim(),
                                if (permanent) "permanent" else "temporary"
                            )
                        } catch (invalidRequest: IllegalArgumentException) {
                            null
                        }
                        if (request == null) {
                            invalid = true
                        } else {
                            creating = false
                            runAction(doneMessage) { PloiApi.createRedirect(token, serverId, siteId, request) }
                        }
                    },
                    enabled = !busy && redirectFrom.isNotBlank() && redirectTo.isNotBlank()
                ) { Text(stringResource(R.string.submit_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    confirmDelete?.let { redirect ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_delete_redirect, redirect.redirectFrom),
            confirmLabel = R.string.delete_redirect,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteRedirect(token, serverId, siteId, redirect.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

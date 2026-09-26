package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/** Insights domain: server optimization suggestions with detail, fix, ignore and delete. */
@Composable
internal fun InsightsScreen(token: String, serverId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var result by remember(token, serverId) { mutableStateOf<InsightPage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(true) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId) { mutableStateOf("") }
    var busy by remember(token, serverId) { mutableStateOf(false) }
    var detail by remember { mutableStateOf<InsightDetail?>(null) }
    var confirmFix by remember { mutableStateOf<Insight?>(null) }
    var confirmDelete by remember { mutableStateOf<Insight?>(null) }

    val ignoredMessage = stringResource(R.string.insight_ignored)
    val fixedMessage = stringResource(R.string.insight_fix_started)
    val deletedMessage = stringResource(R.string.insight_deleted)

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.insights(token, serverId, page) }
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
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.insights.isEmpty()) Text(stringResource(R.string.empty_insights))
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
                items(data.insights, key = { it.id }) { insight ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(insight.type, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.insight_status, insight.status, insight.priority))
                            if (insight.description.isNotBlank()) Text(insight.description)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        error = null
                                        scope.launch {
                                            try {
                                                detail = withContext(Dispatchers.IO) {
                                                    PloiApi.insightDetail(token, serverId, insight.id)
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                error = failure
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.insight_detail)) }
                                if (insight.fixable) {
                                    OutlinedButton(onClick = { confirmFix = insight }, enabled = !busy) {
                                        Text(stringResource(R.string.insight_fix))
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        runAction(ignoredMessage) { PloiApi.ignoreInsight(token, serverId, insight.id) }
                                    },
                                    enabled = !busy && insight.status != "ignored"
                                ) { Text(stringResource(R.string.insight_ignore)) }
                                OutlinedButton(onClick = { confirmDelete = insight }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.insight_delete),
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

    detail?.let { insightDetail ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(stringResource(R.string.insight_detail)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (insightDetail.description.isNotBlank()) Text(insightDetail.description)
                    if (insightDetail.html.isNotBlank()) {
                        Text(
                            insightDetail.html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { detail = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    confirmFix?.let { insight ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_insight_fix, insight.type),
            confirmLabel = R.string.insight_fix,
            onConfirmed = {
                confirmFix = null
                runAction(fixedMessage) { PloiApi.automaticallyFixInsight(token, serverId, insight.id) }
            },
            onDismiss = { confirmFix = null }
        )
    }

    confirmDelete?.let { insight ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_insight_delete, insight.type),
            confirmLabel = R.string.insight_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(deletedMessage) { PloiApi.deleteInsight(token, serverId, insight.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

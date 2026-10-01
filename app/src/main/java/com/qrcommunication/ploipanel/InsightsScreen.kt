package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
        result = null
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
                summary = result?.let { stringResource(R.string.items_count, it.insights.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.insights.isEmpty()) item { EmptyState(Icons.Outlined.Lightbulb, stringResource(R.string.empty_insights)) }
            items(data.insights, key = { it.id }) { insight ->
                ResourceCard(
                    title = insight.type, icon = Icons.Outlined.Lightbulb,
                    subtitle = plainFromMarkdown(insight.description).takeIf { it.isNotBlank() },
                    status = insight.status,
                    facts = listOfNotNull(
                        insight.priority.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g1_insight_priority), priorityLabel(it)) }
                    )
                ) {
                    CardAction(
                        stringResource(R.string.insight_detail), icon = Icons.Outlined.Info, enabled = !busy,
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
                        }
                    )
                    if (insight.fixable) {
                        CardAction(stringResource(R.string.insight_fix), icon = Icons.Outlined.AutoFixHigh, enabled = !busy, onClick = { confirmFix = insight })
                    }
                    CardAction(
                        stringResource(R.string.insight_ignore), icon = Icons.Outlined.VisibilityOff,
                        enabled = !busy && insight.status != "ignored",
                        onClick = {
                            runAction(ignoredMessage) { PloiApi.ignoreInsight(token, serverId, insight.id) }
                        }
                    )
                    DangerAction(stringResource(R.string.insight_delete), onClick = { confirmDelete = insight }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
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
                    verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
                ) {
                    if (insightDetail.description.isNotBlank()) Text(plainFromMarkdown(insightDetail.description))
                    if (insightDetail.html.isNotBlank()) {
                        Text(
                            insightDetail.html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { detail = null }) { Text(stringResource(R.string.close)) }
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

/** Ploi priority keywords in the user's language; unknown values are shown as sent. */
@Composable
private fun priorityLabel(raw: String): String = when (raw.trim().lowercase()) {
    "low" -> stringResource(R.string.priority_low)
    "medium", "normal" -> stringResource(R.string.priority_medium)
    "high" -> stringResource(R.string.priority_high)
    "critical", "urgent" -> stringResource(R.string.priority_critical)
    else -> raw
}

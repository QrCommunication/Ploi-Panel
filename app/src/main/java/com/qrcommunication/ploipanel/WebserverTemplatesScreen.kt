package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Webserver templates domain: read-only — the two documented
 * /api/webserver-templates routes (paginated list and detail by id). Templates
 * are consumed by server creation (webserver_template), so this tab only lists
 * and displays them; no mutation route exists in the documented inventory.
 */
@Composable
internal fun WebserverTemplatesScreen(token: String) {
    var page by remember(token) { mutableIntStateOf(1) }
    var refresh by remember(token) { mutableIntStateOf(0) }
    var result by remember(token) { mutableStateOf<WebserverTemplatePage?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }
    var viewing by remember { mutableStateOf<WebserverTemplate?>(null) }

    LaunchedEffect(token, page, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.webserverTemplates(token, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading,
                summary = result?.let { stringResource(R.string.items_count, it.templates.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading) } }
        result?.let { data ->
            if (data.templates.isEmpty()) item { EmptyState(Icons.Outlined.Description, stringResource(R.string.empty_webserver_templates)) }
            items(data.templates, key = { it.id }) { template ->
                ResourceCard(
                    title = template.label, icon = Icons.Outlined.Description,
                    facts = listOfNotNull(
                        template.createdAt.takeIf { it.isNotBlank() }?.let {
                            ResourceFact(stringResource(R.string.g4_created_label), it)
                        }
                    )
                ) {
                    CardAction(stringResource(R.string.webserver_template_content), icon = Icons.Outlined.Visibility,
                        onClick = { viewing = template })
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ })
            }
        }
    }

    viewing?.let { template ->
        WebserverTemplateDetailDialog(
            token = token,
            template = template,
            onDismiss = { viewing = null }
        )
    }
}

/** Detail dialog: fetches GET /api/webserver-templates/{id} for the full content. */
@Composable
private fun WebserverTemplateDetailDialog(
    token: String,
    template: WebserverTemplate,
    onDismiss: () -> Unit
) {
    var detail by remember { mutableStateOf<WebserverTemplate?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, template.id) {
        loading = true
        error = null
        try {
            detail = withContext(Dispatchers.IO) { PloiApi.webserverTemplate(token, template.id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webserver_template_title, template.label)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
            ) {
                if (loading) BusyIndicator()
                if (error != null) ApiErrorText(error!!)
                detail?.let { data ->
                    if (data.createdAt.isNotBlank()) {
                        Text(
                            stringResource(R.string.webserver_template_created_at, data.createdAt),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (data.content.isBlank()) Text(
                        stringResource(R.string.empty_webserver_template_content),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Full config file: monospace in a tinted, selectable block (the dialog already scrolls).
                    else Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                data.content, style = panelMonoStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                modifier = Modifier.padding(PanelSpacing.sm)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

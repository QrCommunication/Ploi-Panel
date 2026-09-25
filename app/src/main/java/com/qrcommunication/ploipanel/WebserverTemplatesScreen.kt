package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading) {
                Text(stringResource(R.string.reload))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        result?.let { data ->
            if (data.templates.isEmpty()) Text(stringResource(R.string.empty_webserver_templates))
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
                items(data.templates, key = { it.id }) { template ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(template.label, style = MaterialTheme.typography.titleMedium)
                            if (template.createdAt.isNotBlank()) {
                                Text(stringResource(R.string.webserver_template_created_at, template.createdAt))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewing = template }) {
                                    Text(stringResource(R.string.webserver_template_content))
                                }
                            }
                        }
                    }
                }
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                detail?.let { data ->
                    if (data.createdAt.isNotBlank()) {
                        Text(stringResource(R.string.webserver_template_created_at, data.createdAt))
                    }
                    Text(
                        data.content.ifBlank { stringResource(R.string.empty_webserver_template_content) },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

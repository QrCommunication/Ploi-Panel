package com.qrcommunication.ploipanel

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProvidersScreen(token: String) {
    var page by remember(token) { mutableIntStateOf(1) }
    var refresh by remember(token) { mutableIntStateOf(0) }
    var selectedId by remember(token) { mutableStateOf<Long?>(null) }
    var result by remember(token) { mutableStateOf<ProviderPage?>(null) }
    var loading by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, page, refresh) {
        loading = true
        result = null
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.providers(token, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg)) {
                Column(Modifier.weight(1f)) {
                    ProviderList(result, loading, error, page,
                        onPage = { page = it; selectedId = null }, onRefresh = { refresh++ }, onSelect = { selectedId = it })
                }
                Column(Modifier.weight(1f)) {
                    val id = selectedId
                    if (id == null) EmptyState(Icons.Outlined.Cloud, stringResource(R.string.select_provider))
                    else ProviderDetail(token, id)
                }
            }
        } else if (selectedId != null) {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedId = null }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.back_providers), Modifier.padding(start = PanelSpacing.sm))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { ProviderDetail(token, selectedId!!) }
            }
        } else {
            ProviderList(result, loading, error, page,
                onPage = { page = it; selectedId = null }, onRefresh = { refresh++ }, onSelect = { selectedId = it })
        }
    }
}

@Composable
private fun ProviderList(
    data: ProviderPage?, loading: Boolean, error: Throwable?, page: Int,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Long) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = onRefresh, refreshEnabled = !loading,
                primaryLabel = stringResource(R.string.add_provider),
                onPrimary = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://ploi.io/profile/server-providers".toUri()))
                },
                primaryIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                summary = data?.let { stringResource(R.string.items_count, it.providers.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        if (error != null) item { ErrorState(error, onRetry = onRefresh, retryEnabled = !loading) }
        if (data != null) {
            if (data.providers.isEmpty()) item { EmptyState(Icons.Outlined.Cloud, stringResource(R.string.empty_providers)) }
            items(data.providers, key = { it.id }) { provider ->
                ResourceCard(
                    title = provider.displayName, icon = Icons.Outlined.Cloud,
                    subtitle = provider.name.takeIf { it != provider.displayName },
                    onClick = { onSelect(provider.id) }
                )
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext,
                    onPrevious = { onPage(page - 1) }, onNext = { onPage(page + 1) })
            }
        }
    }
}

@Composable
private fun ProviderDetail(token: String, id: Long) {
    var provider by remember(token, id) { mutableStateOf<ProviderCredential?>(null) }
    var loading by remember(token, id) { mutableStateOf(true) }
    var error by remember(token, id) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, id) {
        loading = true
        provider = null
        error = null
        try {
            provider = withContext(Dispatchers.IO) { PloiApi.provider(token, id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            provider = null
            error = failure
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        SectionHeader(stringResource(R.string.provider_detail))
        if (loading) LoadingState(rows = 2)
        if (error != null) ErrorState(error!!)
        provider?.let { details ->
            Text(details.displayName, style = MaterialTheme.typography.headlineSmall)
            SectionCard(title = stringResource(R.string.plans), icon = Icons.Outlined.Memory) {
                details.plans.forEachIndexed { index, plan ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProviderOptionRow(plan)
                }
            }
            SectionCard(title = stringResource(R.string.regions), icon = Icons.Outlined.Public) {
                details.regions.forEachIndexed { index, region ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProviderOptionRow(region)
                }
            }
        }
    }
}

/** One plan or region: human name (+ description) with the machine ID Ploi expects, in monospace. */
@Composable
private fun ProviderOptionRow(option: ProviderOption) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
            Text(option.name, style = MaterialTheme.typography.bodyLarge)
            if (option.description.isNotBlank()) Text(
                option.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(option.id, style = panelMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

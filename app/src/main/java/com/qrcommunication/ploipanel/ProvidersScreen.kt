package com.qrcommunication.ploipanel

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    ProviderList(result, loading, error, page,
                        onPage = { page = it; selectedId = null }, onRefresh = { refresh++ }, onSelect = { selectedId = it })
                }
                Column(Modifier.weight(1f)) {
                    val id = selectedId
                    if (id == null) Text(stringResource(R.string.select_provider))
                    else ProviderDetail(token, id)
                }
            }
        } else if (selectedId != null) {
            OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.back_providers)) }
            ProviderDetail(token, selectedId!!)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://ploi.io/profile/server-providers".toUri()))
            }) { Text(stringResource(R.string.add_provider)) }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error)
        if (data != null) {
            if (data.providers.isEmpty()) Text(stringResource(R.string.empty_providers))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { onPage(page + 1) }, enabled = data.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.providers, key = { it.id }) { provider ->
                    Card(onClick = { onSelect(provider.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(provider.name)
                        }
                    }
                }
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
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.provider_detail), style = MaterialTheme.typography.titleLarge)
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        provider?.let { details ->
            Text(details.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.plans), style = MaterialTheme.typography.titleMedium)
            details.plans.forEach { plan -> Text("${plan.name} (${plan.id}) — ${plan.description}") }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.regions), style = MaterialTheme.typography.titleMedium)
            details.regions.forEach { region -> Text("${region.name} (${region.id})") }
        }
    }
}

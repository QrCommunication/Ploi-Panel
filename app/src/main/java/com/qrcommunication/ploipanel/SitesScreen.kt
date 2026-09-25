package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
internal fun SitesScreen(token: String, serverId: Long) {
    var page by remember(token, serverId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId) { mutableIntStateOf(0) }
    var selectedId by remember(token, serverId) { mutableStateOf<Long?>(null) }
    var result by remember(token, serverId) { mutableStateOf<SitePage?>(null) }
    var loading by remember(token, serverId) { mutableStateOf(false) }
    var error by remember(token, serverId) { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(token, serverId, page, refresh) {
        loading = true
        error = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.sites(token, serverId, page) }
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
                    SiteList(result, loading, error, page,
                        onPage = { page = it; selectedId = null }, onRefresh = { refresh++ }, onSelect = { selectedId = it })
                }
                Column(Modifier.weight(1f)) {
                    val id = selectedId
                    if (id == null) Text(stringResource(R.string.select_site))
                    else SiteDetail(token, serverId, id)
                }
            }
        } else if (selectedId != null) {
            OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.back_sites)) }
            SiteDetail(token, serverId, selectedId!!)
        } else {
            SiteList(result, loading, error, page,
                onPage = { page = it; selectedId = null }, onRefresh = { refresh++ }, onSelect = { selectedId = it })
        }
    }
}

@Composable
private fun SiteList(
    data: SitePage?, loading: Boolean, error: Throwable?, page: Int,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error)
        if (data != null) {
            if (data.sites.isEmpty()) Text(stringResource(R.string.empty_sites))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { onPage(page + 1) }, enabled = data.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.sites, key = { it.id }) { site ->
                    Card(onClick = { onSelect(site.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(site.domain, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.server_status, site.status))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteDetail(token: String, serverId: Long, siteId: Long) {
    var site by remember(token, serverId, siteId) { mutableStateOf<Site?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, serverId, siteId) {
        loading = true
        error = null
        try {
            site = withContext(Dispatchers.IO) { PloiApi.site(token, serverId, siteId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            site = null
            error = failure
        } finally {
            loading = false
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        site?.let { details ->
            Text(details.domain, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.server_status, details.status))
            if (details.phpVersion.isNotBlank()) Text(stringResource(R.string.site_php, details.phpVersion))
            if (details.webDirectory.isNotBlank()) Text(stringResource(R.string.site_directory, details.webDirectory))
            if (details.diskUsage.isNotBlank()) Text(stringResource(R.string.site_disk, details.diskUsage))
            if (details.healthUrl.isNotBlank()) Text(stringResource(R.string.site_health, details.healthUrl))
        }
    }
}

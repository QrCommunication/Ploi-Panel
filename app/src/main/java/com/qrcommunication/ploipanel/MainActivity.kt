package com.qrcommunication.ploipanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { PloiPanel() }
    }
}

private val accent = Color(0xFF137A69)

@Composable
private fun PloiPanel() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF82DAC3)) else lightColorScheme(primary = accent)
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var draftToken by remember { mutableStateOf("") }
            var token by remember { mutableStateOf<String?>(null) }
            var page by remember { mutableIntStateOf(1) }
            var selected by remember { mutableStateOf<Server?>(null) }
            var showProviders by remember { mutableStateOf(false) }
            var refresh by remember { mutableIntStateOf(0) }
            var servers by remember { mutableStateOf<ServerPage?>(null) }
            var error by remember { mutableStateOf<Throwable?>(null) }
            var loading by remember { mutableStateOf(false) }

            LaunchedEffect(token, page, refresh, showProviders) {
                val active = token ?: return@LaunchedEffect
                if (showProviders) return@LaunchedEffect
                loading = true
                error = null
                try {
                    servers = withContext(Dispatchers.IO) { PloiApi.servers(active, page) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    servers = null
                    error = failure
                } finally {
                    loading = false
                }
            }

            if (token == null) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = draftToken, onValueChange = { draftToken = it },
                        label = { Text(stringResource(R.string.token)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        token = draftToken.trim()
                        draftToken = ""
                    }, enabled = draftToken.isNotBlank()) { Text(stringResource(R.string.connect)) }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (showProviders) stringResource(R.string.providers) else stringResource(R.string.servers), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            token = null
                            draftToken = ""
                            selected = null
                            showProviders = false
                            servers = null
                            error = null
                            page = 1
                        }) { Text(stringResource(R.string.disconnect)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showProviders = false }, enabled = showProviders) { Text(stringResource(R.string.servers)) }
                        OutlinedButton(onClick = { showProviders = true }, enabled = !showProviders) { Text(stringResource(R.string.providers)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (showProviders) {
                        ProvidersScreen(token!!)
                    } else BoxWithConstraints(Modifier.fillMaxSize()) {
                        val expanded = maxWidth >= 720.dp
                        if (expanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    ServerList(servers, loading, error, page, onPage = { page = it }, onRefresh = { refresh++ }, onSelect = { selected = it })
                                }
                                Column(Modifier.weight(1f)) {
                                    val server = selected
                                    if (server == null) Text(stringResource(R.string.select_server))
                                    else ServerDetail(token!!, server, refresh)
                                }
                            }
                        } else if (selected != null) {
                            OutlinedButton(onClick = { selected = null }) { Text(stringResource(R.string.back)) }
                            ServerDetail(token!!, selected!!, refresh)
                        } else {
                            ServerList(servers, loading, error, page, onPage = { page = it }, onRefresh = { refresh++ }, onSelect = { selected = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ApiErrorText(failure: Throwable) {
    val message = when (failure) {
        is PloiHttpException -> when (failure.status) {
            401 -> stringResource(R.string.error_auth)
            403 -> stringResource(R.string.error_permission)
            429 -> stringResource(R.string.error_rate, failure.retryAfterSeconds ?: "?")
            else -> stringResource(R.string.error_other, failure.status)
        }
        else -> stringResource(R.string.error_network)
    }
    Text(message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ServerList(
    pageData: ServerPage?, loading: Boolean, error: Throwable?, page: Int,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Server) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error)
        if (pageData != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page, pageData.currentPage.toString(), pageData.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { onPage(page + 1) }, enabled = pageData.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pageData.servers, key = { it.id }) { server ->
                    Card(onClick = { onSelect(server) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(server.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.server_status, server.status))
                            Text(server.ipAddress)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerDetail(token: String, server: Server, refresh: Int) {
    var tab by remember(server.id, token) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tab = 0 }, enabled = tab != 0) { Text(stringResource(R.string.monitoring)) }
            OutlinedButton(onClick = { tab = 1 }, enabled = tab != 1) { Text(stringResource(R.string.sites)) }
        }
        if (tab == 0) MonitoringView(token, server, refresh) else SitesScreen(token, server.id)
    }
}

@Composable
private fun MonitoringView(token: String, server: Server, refresh: Int) {
    var sample by remember(server.id, token) { mutableStateOf<MonitorSample?>(null) }
    var loading by remember(server.id, token) { mutableStateOf(true) }
    var error by remember(server.id, token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(server.id, token, refresh) {
        loading = true
        error = null
        try {
            sample = withContext(Dispatchers.IO) { PloiApi.monitoring(token, server.id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            sample = null
            error = failure
        } finally {
            loading = false
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(server.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.monitoring), style = MaterialTheme.typography.titleLarge)
        if (loading) CircularProgressIndicator()
        if (error is PloiHttpException && (error as PloiHttpException).status == 422) {
            Text(stringResource(R.string.monitoring_unavailable))
        } else if (error != null) ApiErrorText(error!!)
        else if (!loading && sample == null) Text(stringResource(R.string.monitoring_unavailable))
        if (sample != null) {
            Text(stringResource(R.string.stale_warning))
            Text(stringResource(R.string.updated, sample!!.date))
            Metric(stringResource(R.string.metric_cpu), "${sample!!.cpu} %")
            Metric(stringResource(R.string.metric_ram), "${sample!!.ram} %")
            Metric(stringResource(R.string.metric_disk), "${sample!!.disk} %")
            Metric(stringResource(R.string.metric_load), sample!!.load)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

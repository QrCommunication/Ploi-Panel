package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Account overview: user info, backup configurations, notification channels and source control. */
@Composable
internal fun AccountScreen(token: String) {
    var refresh by remember(token) { mutableIntStateOf(0) }

    var info by remember(token) { mutableStateOf<UserInfo?>(null) }
    var infoError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, refresh) {
        infoError = null
        try {
            info = withContext(Dispatchers.IO) { PloiApi.user(token) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            info = null
            infoError = failure
        }
    }

    var backups by remember(token) { mutableStateOf<BackupConfigurationPage?>(null) }
    var backupsError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, refresh) {
        backupsError = null
        try {
            backups = withContext(Dispatchers.IO) { PloiApi.backupConfigurations(token, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            backups = null
            backupsError = failure
        }
    }

    var channels by remember(token) { mutableStateOf<NotificationChannelPage?>(null) }
    var channelsError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, refresh) {
        channelsError = null
        try {
            channels = withContext(Dispatchers.IO) { PloiApi.notificationChannels(token, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            channels = null
            channelsError = failure
        }
    }

    var sourceControl by remember(token) { mutableStateOf<SourceControlPage?>(null) }
    var sourceControlError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, refresh) {
        sourceControlError = null
        try {
            sourceControl = withContext(Dispatchers.IO) { PloiApi.sourceControlProviders(token, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            sourceControl = null
            sourceControlError = failure
        }
    }

    var expandedProvider by remember(token) { mutableStateOf<Long?>(null) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.account), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { refresh++ }) { Text(stringResource(R.string.reload)) }
            }
        }
        item {
            when {
                infoError != null -> ApiErrorText(infoError!!)
                info == null -> CircularProgressIndicator()
                else -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(info!!.name, style = MaterialTheme.typography.titleMedium)
                        Text(info!!.email)
                        Text(stringResource(R.string.account_plan, info!!.plan))
                        if (info!!.planExpiresAt.isNotBlank()) {
                            Text(stringResource(R.string.account_plan_expiry, info!!.planExpiresAt))
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.backup_configurations), style = MaterialTheme.typography.titleMedium) }
        when {
            backupsError != null -> item { ApiErrorText(backupsError!!) }
            backups == null -> item { CircularProgressIndicator() }
            backups!!.configurations.isEmpty() -> item { Text(stringResource(R.string.empty_backup_configurations)) }
            else -> items(backups!!.configurations.size) { index ->
                val configuration = backups!!.configurations[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val label = configuration.label.ifBlank { configuration.humanType.ifBlank { configuration.type } }
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(configuration.type, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.notification_channels), style = MaterialTheme.typography.titleMedium) }
        when {
            channelsError != null -> item { ApiErrorText(channelsError!!) }
            channels == null -> item { CircularProgressIndicator() }
            channels!!.channels.isEmpty() -> item { Text(stringResource(R.string.empty_notification_channels)) }
            else -> items(channels!!.channels.size) { index ->
                val channel = channels!!.channels[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(channel.label.ifBlank { channel.type }, style = MaterialTheme.typography.titleSmall)
                        Text(channel.type, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.source_control), style = MaterialTheme.typography.titleMedium) }
        when {
            sourceControlError != null -> item { ApiErrorText(sourceControlError!!) }
            sourceControl == null -> item { CircularProgressIndicator() }
            sourceControl!!.providers.isEmpty() -> item { Text(stringResource(R.string.empty_source_control)) }
            else -> items(sourceControl!!.providers.size) { index ->
                val provider = sourceControl!!.providers[index]
                SourceControlCard(
                    token = token,
                    provider = provider,
                    expanded = expandedProvider == provider.id,
                    onToggle = { expandedProvider = if (expandedProvider == provider.id) null else provider.id }
                )
            }
        }
    }
}

@Composable
private fun SourceControlCard(token: String, provider: SourceControlProvider, expanded: Boolean, onToggle: () -> Unit) {
    var repositories by remember(provider.id, token) { mutableStateOf<List<SourceControlRepository>?>(null) }
    var error by remember(provider.id, token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(provider.id, token, expanded) {
        if (!expanded) return@LaunchedEffect
        error = null
        try {
            repositories = withContext(Dispatchers.IO) { PloiApi.sourceControlRepositories(token, provider.id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            repositories = null
            error = failure
        }
    }
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
            Text(provider.provider, style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                Text(stringResource(R.string.repositories), style = MaterialTheme.typography.titleSmall)
                when {
                    error != null -> ApiErrorText(error!!)
                    repositories == null -> CircularProgressIndicator()
                    repositories!!.isEmpty() -> Text(stringResource(R.string.empty_repositories))
                    else -> repositories!!.forEach { repository -> Text(repository.name) }
                }
            }
        }
    }
}

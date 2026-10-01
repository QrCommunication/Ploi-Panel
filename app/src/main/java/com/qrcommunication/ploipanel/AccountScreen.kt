package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
        info = null
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
    var backupPage by remember(token) { mutableIntStateOf(1) }
    var backupsError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, backupPage, refresh) {
        backups = null
        backupsError = null
        try {
            backups = withContext(Dispatchers.IO) { PloiApi.backupConfigurations(token, page = backupPage, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            backups = null
            backupsError = failure
        }
    }

    var channels by remember(token) { mutableStateOf<NotificationChannelPage?>(null) }
    var channelsPage by remember(token) { mutableIntStateOf(1) }
    var channelsError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, channelsPage, refresh) {
        channels = null
        channelsError = null
        try {
            channels = withContext(Dispatchers.IO) { PloiApi.notificationChannels(token, page = channelsPage, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            channels = null
            channelsError = failure
        }
    }

    var sourceControl by remember(token) { mutableStateOf<SourceControlPage?>(null) }
    var sourcePage by remember(token) { mutableIntStateOf(1) }
    var sourceControlError by remember(token) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(token, sourcePage, refresh) {
        sourceControl = null
        sourceControlError = null
        try {
            sourceControl = withContext(Dispatchers.IO) { PloiApi.sourceControlProviders(token, page = sourcePage, perPage = 50) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            sourceControl = null
            sourceControlError = failure
        }
    }

    var expandedProvider by remember(token) { mutableStateOf<Long?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item { ListToolbar(onRefresh = { refresh++ }) }
        item {
            when {
                infoError != null -> ErrorState(infoError!!, onRetry = { refresh++ })
                info == null -> LoadingState(rows = 1)
                else -> {
                    val user = info!!
                    ResourceCard(
                        title = user.name, subtitle = user.email, icon = Icons.Outlined.AccountCircle,
                        facts = listOfNotNull(
                            ResourceFact(stringResource(R.string.g4_account_plan_label), user.plan),
                            user.planExpiresAt.takeIf { it.isNotBlank() }?.let {
                                ResourceFact(stringResource(R.string.g4_account_expiry_label), it)
                            }
                        )
                    )
                }
            }
        }
        item { SectionHeader(stringResource(R.string.backup_configurations)) }
        when {
            backupsError != null -> item { ErrorState(backupsError!!, onRetry = { refresh++ }) }
            backups == null -> item { LoadingState(rows = 1) }
            backups!!.configurations.isEmpty() -> item {
                EmptyState(Icons.Outlined.Backup, stringResource(R.string.empty_backup_configurations))
            }
            else -> items(backups!!.configurations) { configuration ->
                val label = configuration.label.ifBlank { configuration.humanType.ifBlank { configuration.type } }
                ResourceCard(
                    title = label, icon = Icons.Outlined.Backup,
                    facts = listOf(ResourceFact(stringResource(R.string.g4_type_label), configuration.type, mono = true))
                )
            }
        }
        backups?.let { data ->
            if (data.currentPage > 1 || data.hasNext) item {
                AccountPager(data.currentPage, data.lastPage, data.hasNext) { backupPage = it }
            }
        }
        item { SectionHeader(stringResource(R.string.notification_channels)) }
        when {
            channelsError != null -> item { ErrorState(channelsError!!, onRetry = { refresh++ }) }
            channels == null -> item { LoadingState(rows = 1) }
            channels!!.channels.isEmpty() -> item {
                EmptyState(Icons.Outlined.NotificationsNone, stringResource(R.string.empty_notification_channels))
            }
            else -> items(channels!!.channels) { channel ->
                ResourceCard(
                    title = channel.label.ifBlank { channel.type }, icon = Icons.Outlined.NotificationsNone,
                    facts = listOf(ResourceFact(stringResource(R.string.g4_type_label), channel.type, mono = true))
                )
            }
        }
        channels?.let { data ->
            if (data.currentPage > 1 || data.hasNext) item {
                AccountPager(data.currentPage, data.lastPage, data.hasNext) { channelsPage = it }
            }
        }
        item { SectionHeader(stringResource(R.string.source_control)) }
        when {
            sourceControlError != null -> item { ErrorState(sourceControlError!!, onRetry = { refresh++ }) }
            sourceControl == null -> item { LoadingState(rows = 1) }
            sourceControl!!.providers.isEmpty() -> item {
                EmptyState(Icons.Outlined.Source, stringResource(R.string.empty_source_control))
            }
            else -> items(sourceControl!!.providers) { provider ->
                SourceControlCard(
                    token = token,
                    provider = provider,
                    expanded = expandedProvider == provider.id,
                    onToggle = { expandedProvider = if (expandedProvider == provider.id) null else provider.id }
                )
            }
        }
        sourceControl?.let { data ->
            if (data.currentPage > 1 || data.hasNext) item {
                AccountPager(data.currentPage, data.lastPage, data.hasNext) {
                    sourcePage = it
                    expandedProvider = null
                }
            }
        }
    }
}

/** Per-section pager: each account section pages independently through the shared [PageBar]. */
@Composable
private fun AccountPager(current: Int, last: Int, hasNext: Boolean, onPage: (Int) -> Unit) {
    PageBar(current, last, hasNext, onPrevious = { onPage(current - 1) }, onNext = { onPage(current + 1) })
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
        ResourceCard(
            title = provider.displayName, icon = Icons.Outlined.Source,
            facts = listOf(ResourceFact(stringResource(R.string.g4_type_label), provider.provider, mono = true))
        ) {
            CardAction(
                stringResource(if (expanded) R.string.g4_repositories_hide else R.string.g4_repositories_show),
                onClick = onToggle,
                icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore
            )
        }
        if (expanded) SectionCard(title = stringResource(R.string.repositories), icon = Icons.Outlined.FolderOpen) {
            when {
                error != null -> ErrorState(error!!)
                repositories == null -> BusyIndicator()
                repositories!!.isEmpty() -> Text(
                    stringResource(R.string.empty_repositories), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> repositories!!.forEachIndexed { index, repository ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(repository.name, style = panelMonoStyle)
                }
            }
        }
    }
}

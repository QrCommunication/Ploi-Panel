package com.qrcommunication.ploipanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Dashboard numbers describe only the loaded page; never imply a fleet-wide total. Each count is
 * a filter chip (all / healthy / needs attention) so the numbers are also the way to act on them.
 * Healthy uses the freshest known status (a recheck result replaces Ploi's listed status).
 */
@Composable
internal fun ServerPageHero(
    page: ServerPage, rechecks: Map<Long, Recheck> = emptyMap(),
    filter: ServerFilter = ServerFilter.ALL, onFilter: (ServerFilter) -> Unit = {},
    refreshing: Boolean = false, offline: Boolean = false,
    onRefresh: () -> Unit = {}, onMonitored: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val healthy = filterByStatus(page.servers, ServerFilter.HEALTHY, rechecks).size
    val attention = page.servers.size - healthy
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(PanelSpacing.lg), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                    Text(
                        pluralStringResource(R.plurals.dashboard_page_count, page.servers.size, page.servers.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.dashboard_page_scope, page.currentPage.toString(), page.lastPage.toString()),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMonitored, enabled = !offline) {
                    Icon(Icons.Outlined.MonitorHeart, contentDescription = stringResource(R.string.monitored_overview))
                }
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.reload))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                StatusFilterChip(ServerFilter.ALL, filter, R.string.servers_filter_all, page.servers.size, onFilter)
                StatusFilterChip(ServerFilter.HEALTHY, filter, R.string.servers_filter_healthy, healthy, onFilter)
                StatusFilterChip(ServerFilter.ATTENTION, filter, R.string.servers_filter_attention, attention, onFilter)
            }
        }
    }
}

@Composable
private fun StatusFilterChip(
    value: ServerFilter, current: ServerFilter, label: Int, count: Int, onFilter: (ServerFilter) -> Unit
) {
    val status = PanelTheme.status
    val colors = MaterialTheme.colorScheme
    val (dot, container) = when (value) {
        ServerFilter.ALL -> colors.primary to colors.primaryContainer
        ServerFilter.HEALTHY -> status.success to status.successContainer
        ServerFilter.ATTENTION -> colors.error to colors.errorContainer
    }
    FilterChip(
        selected = current == value,
        onClick = { onFilter(value) },
        label = { Text(stringResource(label, count)) },
        leadingIcon = {
            Icon(
                when (value) {
                    ServerFilter.ALL -> Icons.Outlined.Dns
                    ServerFilter.HEALTHY -> Icons.Outlined.CheckCircle
                    ServerFilter.ATTENTION -> Icons.Outlined.ErrorOutline
                },
                contentDescription = null, tint = dot, modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = container)
    )
}

/**
 * Says, in words, that the rows below were read from the device cache at a given time and that the
 * account may have changed since. The colour alone would say nothing to a screen reader, so the
 * whole banner is announced as one sentence.
 */
@Composable
internal fun OfflineCacheBanner(fetchedAt: Long) {
    val colors = MaterialTheme.colorScheme
    val timestamp = formatCacheTimestamp(fetchedAt)
    val message = stringResource(R.string.offline_cache_banner, timestamp)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = message }
    ) {
        Row(Modifier.padding(PanelSpacing.lg), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                Text(
                    stringResource(R.string.offline_cache_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onTertiaryContainer
                )
                Text(
                    stringResource(R.string.offline_cache_body, timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * One server row: name, IP in monospace, a worded status pill and a one-tap SSH terminal.
 * [enabled] = false (offline cache) disables opening the server; the terminal shortcut follows it.
 * For a server Ploi reports as unreachable, the row shows the latest recheck (fresh Ploi status and
 * this phone's TCP test, labelled separately) and a Retest button.
 */
@Composable
internal fun ServerItemCard(
    server: Server, enabled: Boolean = true,
    recheck: Recheck? = null, rechecking: Boolean = false, onRecheck: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shownStatus = recheck?.ploiStatus ?: server.status
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerLowest,
            disabledContainerColor = colors.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = PanelSpacing.lg, top = PanelSpacing.md, bottom = PanelSpacing.md, end = PanelSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)
            ) {
                IconBadge(Icons.Outlined.Dns, container = colors.primaryContainer, content = colors.onPrimaryContainer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        server.ipAddress.ifBlank { stringResource(R.string.server_card_no_ip) },
                        style = if (server.ipAddress.isBlank()) MaterialTheme.typography.bodySmall else panelMonoStyle,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(shownStatus)
                }
                if (server.ipAddress.isNotBlank()) IconButton(
                    onClick = { TerminalNavigator.open(SshTarget(server.ipAddress, SSH_DEFAULT_PORT, server.id, server.name)) },
                    enabled = enabled
                ) {
                    Icon(Icons.Outlined.Terminal, contentDescription = stringResource(R.string.server_card_terminal, server.name))
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            if (onRecheck != null && enabled && (needsRecheck(server.status) || recheck != null || rechecking)) {
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = PanelSpacing.lg))
                RecheckLine(
                    recheck, rechecking, onRetest = onRecheck,
                    modifier = Modifier.padding(start = PanelSpacing.lg, end = PanelSpacing.xs)
                )
            }
        }
    }
}

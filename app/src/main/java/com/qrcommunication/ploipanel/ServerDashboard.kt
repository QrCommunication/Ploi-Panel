package com.qrcommunication.ploipanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Dashboard numbers describe only the loaded page; never imply a fleet-wide total. */
@Composable
internal fun ServerPageHero(page: ServerPage) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.dashboard_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer
                )
                Text(
                    pluralStringResource(R.plurals.dashboard_page_count, page.servers.size, page.servers.size),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.dashboard_page_scope, page.currentPage.toString(), page.lastPage.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer
                )
            }
            Box(
                Modifier.size(62.dp).background(colors.surface.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    page.servers.size.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
internal fun ServerItemCard(server: Server, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(40.dp).background(colors.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(server.name.take(1).uppercase(), color = colors.primary, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(server.ipAddress, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (server.status.isNotBlank()) Text(
                    stringResource(R.string.server_status, server.status),
                    style = MaterialTheme.typography.labelLarge, color = colors.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = colors.onSurfaceVariant)
        }
    }
}

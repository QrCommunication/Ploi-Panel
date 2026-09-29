package com.qrcommunication.ploipanel

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Shared building blocks of the redesign. Screens compose these instead of hand-rolling cards,
 * spinners and red text so that states look and read the same everywhere.
 */

/** Coarse, honest grouping of Ploi's free-form server status strings. */
internal enum class ServerStatusKind { HEALTHY, PENDING, ERROR, UNKNOWN }

private val healthyStatuses = setOf("active", "running", "online", "ok", "up", "ready", "enabled")
private val errorStatuses = setOf(
    "failed", "failure", "error", "errored", "offline", "down", "unreachable", "stopped",
    "crashed", "suspended", "disabled", "deleted", "unhealthy"
)

/** Unknown or blank values stay [ServerStatusKind.UNKNOWN]; nothing is guessed as healthy. */
internal fun serverStatusKind(raw: String): ServerStatusKind {
    val value = raw.trim().lowercase()
    return when {
        value.isEmpty() -> ServerStatusKind.UNKNOWN
        value in healthyStatuses -> ServerStatusKind.HEALTHY
        value in errorStatuses || value.contains("fail") || value.contains("error") -> ServerStatusKind.ERROR
        value.endsWith("ing") || value in setOf("pending", "queued", "new", "provision") -> ServerStatusKind.PENDING
        else -> ServerStatusKind.UNKNOWN
    }
}

@StringRes
internal fun statusKindLabel(kind: ServerStatusKind): Int = when (kind) {
    ServerStatusKind.HEALTHY -> R.string.status_kind_healthy
    ServerStatusKind.PENDING -> R.string.status_kind_pending
    ServerStatusKind.ERROR -> R.string.status_kind_error
    ServerStatusKind.UNKNOWN -> R.string.status_kind_unknown
}

/**
 * Status badge: an icon and words, never colour alone. The raw Ploi value is kept next to the
 * grouped label whenever they differ so nothing reported by the API is hidden.
 */
@Composable
internal fun StatusPill(raw: String, modifier: Modifier = Modifier) {
    val kind = serverStatusKind(raw)
    val status = PanelTheme.status
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        ServerStatusKind.HEALTHY -> status.successContainer to status.onSuccessContainer
        ServerStatusKind.PENDING -> status.infoContainer to status.onInfoContainer
        ServerStatusKind.ERROR -> colors.errorContainer to colors.onErrorContainer
        ServerStatusKind.UNKNOWN -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    val icon = when (kind) {
        ServerStatusKind.HEALTHY -> Icons.Outlined.CheckCircle
        ServerStatusKind.PENDING -> Icons.Outlined.HourglassTop
        ServerStatusKind.ERROR -> Icons.Outlined.ErrorOutline
        ServerStatusKind.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
    }
    val grouped = stringResource(statusKindLabel(kind))
    val trimmed = raw.trim()
    val label = if (trimmed.isEmpty() || trimmed.equals(grouped, ignoreCase = true)) grouped
    else stringResource(R.string.status_with_raw, grouped, trimmed)
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = PanelSpacing.sm + PanelSpacing.xxs, vertical = PanelSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs + PanelSpacing.xxs)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Rounded, tinted square that holds a leading icon in list rows and cards. */
@Composable
internal fun IconBadge(
    icon: ImageVector, modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Int = 40
) {
    Box(
        modifier.size(size.dp).background(container, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Section heading announced as a heading to screen readers. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
        Text(
            text, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        if (supporting != null) Text(
            supporting, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Grouping surface: optional icon + title + description header above arbitrary content. */
@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(PanelSpacing.lg), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            if (title != null) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)
            ) {
                if (icon != null) IconBadge(icon, size = 36)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (description != null) Text(
                        description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/**
 * Clickable row with a leading icon, title, optional description and a chevron. At least 56 dp
 * tall so the whole row is a comfortable touch target.
 */
@Composable
internal fun PanelListItem(
    icon: ImageVector, title: String, modifier: Modifier = Modifier,
    description: String? = null, enabled: Boolean = true, destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Row(
            Modifier.padding(horizontal = PanelSpacing.lg, vertical = PanelSpacing.md).alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg)
        ) {
            IconBadge(
                icon,
                container = if (destructive) colors.errorContainer else colors.secondaryContainer,
                content = if (destructive) colors.onErrorContainer else colors.onSecondaryContainer
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = if (destructive) colors.error else colors.onSurface)
                if (description != null) Text(
                    description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                )
            }
            if (trailing != null) trailing()
            else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/** A labelled figure. Only real values are passed in; the caller says which scope they cover. */
@Composable
internal fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label, $value" }
    ) {
        Column(Modifier.padding(PanelSpacing.md), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs + PanelSpacing.xxs)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
        }
    }
}

/** Empty list or screen: icon, a title that says what is missing, and what to do next. */
@Composable
internal fun EmptyState(
    icon: ImageVector, title: String, modifier: Modifier = Modifier, body: String? = null,
    actionLabel: String? = null, actionEnabled: Boolean = true, onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = PanelSpacing.xl, horizontal = PanelSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
    ) {
        IconBadge(icon, size = 56,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(
            body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp)
        )
        if (actionLabel != null && onAction != null) FilledTonalButton(onClick = onAction, enabled = actionEnabled) {
            Text(actionLabel)
        }
    }
}

/** Human message for an API failure; shared by [ApiErrorText] and [ErrorState]. */
@Composable
internal fun apiErrorMessage(failure: Throwable): String = when (failure) {
    is PloiHttpException -> when (failure.status) {
        401 -> stringResource(R.string.error_auth)
        403 -> stringResource(R.string.error_permission)
        429 -> stringResource(R.string.error_rate, failure.retryAfterSeconds ?: "?")
        else -> stringResource(R.string.error_other, failure.status)
    }
    is PloiMalformedPayloadException -> stringResource(R.string.error_malformed)
    else -> stringResource(R.string.error_network)
}

/**
 * Failure block: says what failed in words (icon + title + message) and offers a retry when the
 * caller can reload. Error container colours are paired with text, never used alone.
 */
@Composable
internal fun ErrorState(failure: Throwable, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null, retryEnabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.errorContainer, contentColor = colors.onErrorContainer,
        shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(PanelSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                Text(stringResource(R.string.state_error_title), style = MaterialTheme.typography.titleSmall)
                Text(apiErrorMessage(failure), style = MaterialTheme.typography.bodyMedium)
                if (onRetry != null) OutlinedButton(onClick = onRetry, enabled = retryEnabled) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.state_retry), Modifier.padding(start = PanelSpacing.sm))
                }
            }
        }
    }
}

/** Loading placeholder: the announced spinner plus decorative skeleton rows hidden from TalkBack. */
@Composable
internal fun LoadingState(modifier: Modifier = Modifier, rows: Int = 3) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BusyIndicator(Modifier.padding(PanelSpacing.sm)) }
        Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            repeat(rows) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                ) { }
            }
        }
    }
}

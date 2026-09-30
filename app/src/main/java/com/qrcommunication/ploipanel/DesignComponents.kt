package com.qrcommunication.ploipanel

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

private val healthyStatuses = setOf("active", "running", "online", "ok", "up", "ready", "enabled", "resolved", "fixed")
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
        value.endsWith("ing") || value in setOf("pending", "queued", "new", "provision", "open", "paused") -> ServerStatusKind.PENDING
        else -> ServerStatusKind.UNKNOWN
    }
}

/** Precise wording for status values Ploi is known to send (seen on the live API and in its docs). */
@StringRes
internal fun knownStatusLabel(raw: String): Int? = when (raw.trim().lowercase()) {
    "active", "server active" -> R.string.status_kind_healthy
    "unreachable", "server unreachable" -> R.string.status_unreachable
    "installing", "server installing" -> R.string.status_installing
    "creating", "building", "provisioning" -> R.string.status_kind_pending
    "open" -> R.string.status_open
    "resolved", "fixed" -> R.string.status_resolved
    "ignored" -> R.string.status_ignored
    "paused" -> R.string.status_paused
    "failed" -> R.string.status_failed
    "running" -> R.string.status_running
    else -> null
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
    // Known Ploi values get a precise word ("Injoignable"); unknown ones keep the raw text visible.
    val known = knownStatusLabel(trimmed)
    val label = when {
        known != null -> stringResource(known)
        trimmed.isEmpty() || trimmed.equals(grouped, ignoreCase = true) -> grouped
        else -> stringResource(R.string.status_with_raw, grouped, trimmed)
    }
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


// ---------------------------------------------------------------------------------------------
// Resource list chrome shared by every server/site sub-screen (daemons, firewall, crons, sites…).
// ---------------------------------------------------------------------------------------------

/**
 * Top row of a resource list: an optional count on the left, then a refresh icon button and the
 * screen's primary action as a tonal button. Replaces rows of equal-weight outlined buttons.
 */
@Composable
internal fun ListToolbar(
    onRefresh: () -> Unit, refreshEnabled: Boolean = true,
    primaryLabel: String? = null, onPrimary: (() -> Unit)? = null, primaryEnabled: Boolean = true,
    primaryIcon: ImageVector = Icons.Outlined.Add,
    summary: String? = null,
    extra: (@Composable () -> Unit)? = null
) {
    // Summary on its own line when there are extra actions, so it is never squeezed letter by letter.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
        if (extra != null && !summary.isNullOrBlank()) Text(
            summary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
        ) {
            if (extra == null) Text(
                summary.orEmpty(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            ) else {
                extra()
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.reload))
            }
            if (primaryLabel != null && onPrimary != null) FilledTonalButton(onClick = onPrimary, enabled = primaryEnabled) {
                Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(primaryLabel, Modifier.padding(start = PanelSpacing.sm), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Previous / "Page x of y" / Next. Renders nothing when everything fits on one page. */
@Composable
internal fun PageBar(current: Int, last: Int, hasNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, enabled: Boolean = true) {
    if (last <= 1 && current <= 1) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPrevious, enabled = enabled && current > 1) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.previous))
        }
        Text(
            stringResource(R.string.servers_page_label, current.toString(), last.toString()),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = PanelSpacing.sm)
        )
        OutlinedButton(onClick = onNext, enabled = enabled && hasNext) {
            Text(stringResource(R.string.next))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** One label/value line of a [ResourceCard]; values that are machine data use monospace. */
internal data class ResourceFact(val label: String, val value: String, val mono: Boolean = false)

/**
 * The card every resource row uses: leading icon, title (+ optional subtitle), a worded status
 * pill, compact label/value facts and a wrapping row of actions. Destructive actions go last.
 */
@Composable
internal fun ResourceCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    status: String? = null,
    facts: List<ResourceFact> = emptyList(),
    monoTitle: Boolean = false,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(PanelSpacing.lg), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                if (icon != null) IconBadge(icon, size = 36)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                    Text(
                        title, style = if (monoTitle) panelMonoStyle.copy(fontWeight = FontWeight.Medium)
                        else MaterialTheme.typography.titleMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) Text(
                        subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            if (!status.isNullOrBlank()) StatusPill(status)
            if (facts.isNotEmpty()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) {
                facts.forEach { fact ->
                    Column {
                        Text(fact.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Text(fact.value, style = if (fact.mono) panelMonoStyle else MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (actions != null) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) { actions() }
        }
    }
    if (onClick != null) Card(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) { content() }
    else Card(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) { content() }
}

/** Secondary action inside a card. */
@Composable
internal fun CardAction(label: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = PanelSpacing.md)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, Modifier.padding(start = if (icon != null) PanelSpacing.xs else 0.dp))
    }
}

/** Destructive action inside a card: error-coloured text and outline, always placed last. */
@Composable
internal fun DangerAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(horizontal = PanelSpacing.md),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.6f else 0.2f))
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, Modifier.padding(start = PanelSpacing.xs))
    }
}

/** Confirmation of a completed action (green, with icon), announced to screen readers. */
@Composable
internal fun SuccessBanner(message: String, modifier: Modifier = Modifier) {
    if (message.isBlank()) return
    val status = PanelTheme.status
    Surface(
        color = status.successContainer, contentColor = status.onSuccessContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(PanelSpacing.md), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Ploi sends some texts (insights, notifications) with light Markdown. Show them as plain prose:
 * bold/italic/code markers and link syntax are removed, the words are kept.
 */
internal fun plainFromMarkdown(text: String): String = text
    .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
    .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
    .replace(Regex("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace("**", "")
    .trim()

/** Long machine output (logs, command output): first lines only, tap to expand. */
@Composable
internal fun ExpandableMono(text: String, modifier: Modifier = Modifier, collapsedLines: Int = 4) {
    var expanded by remember(text) { mutableStateOf(false) }
    val lines = remember(text) { text.lines().size }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(
                text, style = panelMonoStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(PanelSpacing.sm)
            )
        }
        if (lines > collapsedLines) TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
        }
    }
}

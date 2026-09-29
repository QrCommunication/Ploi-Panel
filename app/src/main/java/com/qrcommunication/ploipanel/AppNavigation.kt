package com.qrcommunication.ploipanel

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Primary navigation. `panelTab` stays the single source of truth (widget routes, config import,
 * TerminalNavigator and the global deploy lock all write it); destinations are a view over it.
 *
 *   panelTab 0 Servers · 10 Terminal · 9 Monitoring (local checks) · 8 Deploy (global scripts)
 *   panelTab 11 = "More" hub, whose entries open 1..7 (providers … settings)
 */

/** Tab index of the "More" hub screen. Sub-pages 1..7 return to it. */
internal const val MORE_HUB_TAB = 11

/** Width from which the bottom bar becomes a side rail (unfolded foldables, tablets, landscape). */
internal val NAVIGATION_RAIL_MIN_WIDTH: Dp = 600.dp

internal fun usesNavigationRail(width: Dp): Boolean = width >= NAVIGATION_RAIL_MIN_WIDTH

internal enum class PanelDestination(val tab: Int, @param:StringRes val label: Int) {
    SERVERS(0, R.string.nav_servers),
    TERMINAL(10, R.string.nav_terminal),
    MONITORING(9, R.string.nav_monitoring),
    DEPLOY(8, R.string.nav_deploy),
    MORE(MORE_HUB_TAB, R.string.nav_more)
}

/** Entries of the "More" hub, in display order. Every secondary panelTab must appear here. */
internal enum class MoreEntry(val tab: Int, @param:StringRes val title: Int, @param:StringRes val description: Int) {
    PROVIDERS(1, R.string.providers, R.string.more_providers_description),
    ACCOUNT(2, R.string.account, R.string.more_account_description),
    SCRIPTS(3, R.string.scripts_tab, R.string.more_scripts_description),
    STATUS_PAGES(4, R.string.status_pages_tab, R.string.more_status_pages_description),
    WEBSERVER_TEMPLATES(5, R.string.webserver_templates_tab, R.string.more_templates_description),
    PROJECTS(6, R.string.projects_tab, R.string.more_projects_description),
    SETTINGS(7, R.string.settings_tab, R.string.more_settings_description)
}

/** Which destination is highlighted for a given tab; hub sub-pages keep "More" selected. */
internal fun destinationForTab(tab: Int): PanelDestination =
    PanelDestination.entries.firstOrNull { it.tab == tab } ?: PanelDestination.MORE

/** Where system/top-bar Back goes from a tab, or null when the tab is a top-level destination. */
internal fun parentTab(tab: Int): Int? = if (MoreEntry.entries.any { it.tab == tab }) MORE_HUB_TAB else null

@StringRes
internal fun panelTabTitle(tab: Int, creating: Boolean): Int = when (tab) {
    MORE_HUB_TAB -> R.string.more_title
    8 -> R.string.deploy_global_tab
    9 -> R.string.local_checks_tab
    10 -> R.string.ssh_term_tab
    0 -> if (creating) R.string.new_server else R.string.servers
    else -> MoreEntry.entries.firstOrNull { it.tab == tab }?.title ?: R.string.servers
}

internal fun destinationIcon(destination: PanelDestination): ImageVector = when (destination) {
    PanelDestination.SERVERS -> Icons.Outlined.Dns
    PanelDestination.TERMINAL -> Icons.Outlined.Terminal
    PanelDestination.MONITORING -> Icons.Outlined.MonitorHeart
    PanelDestination.DEPLOY -> Icons.Outlined.RocketLaunch
    PanelDestination.MORE -> Icons.Outlined.Apps
}

internal fun moreEntryIcon(entry: MoreEntry): ImageVector = when (entry) {
    MoreEntry.PROVIDERS -> Icons.Outlined.Cloud
    MoreEntry.ACCOUNT -> Icons.Outlined.ManageAccounts
    MoreEntry.SCRIPTS -> Icons.Outlined.Code
    MoreEntry.STATUS_PAGES -> Icons.Outlined.Public
    MoreEntry.WEBSERVER_TEMPLATES -> Icons.Outlined.Description
    MoreEntry.PROJECTS -> Icons.Outlined.Workspaces
    MoreEntry.SETTINGS -> Icons.Outlined.Settings
}

/**
 * Compact widths: bottom bar. While a global deploy batch runs only the current destination stays
 * enabled, exactly like the former chip strip.
 */
@Composable
internal fun PanelNavigationBar(panelTab: Int, locked: Boolean, onNavigate: (Int) -> Unit) {
    val current = destinationForTab(panelTab)
    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        PanelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = current == destination,
                enabled = !locked || current == destination,
                onClick = { onNavigate(destination.tab) },
                icon = { Icon(destinationIcon(destination), contentDescription = null) },
                label = { Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** Medium/expanded widths (>= 600 dp): side rail with the app mark as header. */
@Composable
internal fun PanelNavigationRail(panelTab: Int, locked: Boolean, onNavigate: (Int) -> Unit) {
    val current = destinationForTab(panelTab)
    NavigationRail(
        windowInsets = WindowInsets(0, 0, 0, 0),
        header = {
            Icon(
                painterResource(R.drawable.ic_app), contentDescription = stringResource(R.string.app_name),
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.padding(top = PanelSpacing.md).size(40.dp)
            )
        }
    ) {
        Spacer(Modifier.height(PanelSpacing.sm))
        PanelDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = current == destination,
                enabled = !locked || current == destination,
                onClick = { onNavigate(destination.tab) },
                icon = { Icon(destinationIcon(destination), contentDescription = null) },
                label = { Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** "More" hub: every secondary area as a list row with icon, title and description. */
@Composable
internal fun MoreHubScreen(onOpen: (Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PanelSpacing.lg, vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(MoreEntry.entries, key = { it.tab }) { entry ->
            PanelListItem(
                icon = moreEntryIcon(entry),
                title = stringResource(entry.title),
                description = stringResource(entry.description),
                modifier = Modifier.widthIn(max = PanelSpacing.maxContentWidth)
            ) { onOpen(entry.tab) }
        }
    }
}

/**
 * Section title, profile switcher and lock action. The switcher activates a profile through the
 * same callback as Settings; "Manage" opens Settings; disconnect stays one tap away in the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PanelTopBar(
    @StringRes title: Int,
    profiles: List<PloiProfile>,
    active: PloiProfile?,
    locked: Boolean,
    showDisconnect: Boolean,
    onBack: (() -> Unit)?,
    onSwitchProfile: (PloiProfile) -> Unit,
    onManageProfiles: () -> Unit,
    onDisconnect: () -> Unit,
    onLock: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        title = { Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            val label = active?.label.orEmpty()
            val description = stringResource(R.string.profile_menu_open, label)
            Column {
                TextButton(
                    onClick = { menuOpen = true }, enabled = !locked,
                    contentPadding = PaddingValues(horizontal = PanelSpacing.sm),
                    modifier = Modifier.semantics { contentDescription = description }
                ) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = PanelSpacing.xs).widthIn(max = 120.dp)
                    )
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    profiles.forEach { profile ->
                        val isActive = profile.id == active?.id
                        DropdownMenuItem(
                            text = { Text(profile.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            trailingIcon = if (isActive) {
                                { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.settings_profile_active)) }
                            } else null,
                            onClick = {
                                menuOpen = false
                                if (!isActive) onSwitchProfile(profile)
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_menu_manage)) },
                        leadingIcon = { Icon(Icons.Outlined.ManageAccounts, contentDescription = null) },
                        onClick = { menuOpen = false; onManageProfiles() }
                    )
                    if (showDisconnect) DropdownMenuItem(
                        text = { Text(stringResource(R.string.disconnect)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                        onClick = { menuOpen = false; onDisconnect() }
                    )
                }
            }
            IconButton(onClick = onLock) {
                Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.lock_now))
            }
        }
    )
}

/** Small row shown under the top bar while navigation is frozen by a running deploy batch. */
@Composable
internal fun NavigationLockedNotice() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PanelSpacing.lg, vertical = PanelSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.nav_locked_during_batch), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

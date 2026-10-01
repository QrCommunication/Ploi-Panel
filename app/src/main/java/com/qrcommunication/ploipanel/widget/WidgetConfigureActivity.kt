package com.qrcommunication.ploipanel.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.qrcommunication.ploipanel.AppLanguage
import com.qrcommunication.ploipanel.AppLock
import com.qrcommunication.ploipanel.AppTheme
import com.qrcommunication.ploipanel.EmptyState
import com.qrcommunication.ploipanel.LoadingState
import com.qrcommunication.ploipanel.LocalCheckStore
import com.qrcommunication.ploipanel.LocalizedActivityScope
import com.qrcommunication.ploipanel.PanelSpacing
import com.qrcommunication.ploipanel.PinEntry
import com.qrcommunication.ploipanel.PloiApi
import com.qrcommunication.ploipanel.PloiPanelTheme
import com.qrcommunication.ploipanel.PloiProfile
import com.qrcommunication.ploipanel.R
import com.qrcommunication.ploipanel.SectionCard
import com.qrcommunication.ploipanel.SectionHeader
import com.qrcommunication.ploipanel.SharedPreferencesProfilePrefs
import com.qrcommunication.ploipanel.UiPreferences
import com.qrcommunication.ploipanel.panelDarkColors
import com.qrcommunication.ploipanel.panelLightColors
import com.qrcommunication.ploipanel.panelMonoStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** Launcher-owned configuration surface. PIN required before profile enumeration or API access. */
class WidgetConfigureActivity : ComponentActivity() {
    private var authorized by mutableStateOf(false)
    private var pin by mutableStateOf("")

    override fun onStop() {
        authorized = false
        pin = ""
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(id)?.provider
        val single = provider?.className == SingleServerWidget::class.java.name
        val multi = provider?.className == MultiServerWidget::class.java.name
        val checks = provider?.className == SiteChecksWidget::class.java.name
        if (provider?.packageName != packageName || (!single && !multi && !checks)) { finish(); return }
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val preferences = remember { UiPreferences(SharedPreferencesProfilePrefs(this)) }
            val systemConfiguration = LocalConfiguration.current
            val language = preferences.language()
            val configuration = remember(systemConfiguration, language) {
                Configuration(systemConfiguration).apply {
                    when (language) {
                        AppLanguage.FRENCH -> setLocale(Locale.FRENCH)
                        AppLanguage.ENGLISH -> setLocale(Locale.ENGLISH)
                        AppLanguage.SYSTEM -> Unit
                    }
                }
            }
            val localizedContext = remember(configuration) { createConfigurationContext(configuration) }
            val dark = when (preferences.theme()) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            val colors = if (dark) panelDarkColors else panelLightColors
            SideEffect {
                window.decorView.setBackgroundColor(colors.background.toArgb())
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            LocalizedActivityScope(this, localizedContext, configuration) {
                PloiPanelTheme(dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (checks) {
                            ConfigureSiteChecks(id) {
                                SiteChecksData(this).save(id, it)
                                SiteChecksWidgetRefresh.render(this, id)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        } else {
                            ConfigureWidget(id, single) {
                                WidgetData(this).save(id, it)
                                WidgetRefresh.render(this, id)
                                WidgetRefresh.request(this)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    /** Title bar shared by the three widget configuration screens. */
    @Composable
    private fun ConfigHeader(title: Int, subtitle: String) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
            Icon(painterResource(R.drawable.ic_app), contentDescription = null, tint = Color.Unspecified,
                modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    /** One selectable row: radio (single) or checkbox (multi), disabled once the cap is reached. */
    @Composable
    private fun ChoiceRow(
        title: String, subtitle: String?, checked: Boolean, radio: Boolean, enabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (checked) colors.primaryContainer else colors.surfaceContainerLowest,
            border = BorderStroke(1.dp, if (checked) colors.primary else colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .then(
                        if (radio) Modifier.selectable(checked, enabled = enabled, role = Role.RadioButton, onClick = { onToggle(true) })
                        else Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
                    )
                    .padding(horizontal = PanelSpacing.md, vertical = PanelSpacing.sm)
                    .alpha(if (enabled) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (radio) RadioButton(checked, onClick = null, enabled = enabled)
                else Checkbox(checked, onCheckedChange = null, enabled = enabled)
                Column(Modifier.padding(start = PanelSpacing.md).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = panelMonoStyle, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    private fun SaveBar(enabled: Boolean, onSave: () -> Unit) {
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().padding(horizontal = PanelSpacing.xl, vertical = PanelSpacing.md),
            contentAlignment = Alignment.Center) {
            Button(onClick = onSave, enabled = enabled,
                modifier = Modifier.widthIn(max = PanelSpacing.maxContentWidth).fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.widget_save), Modifier.padding(start = PanelSpacing.sm))
            }
        }
    }

    @Composable
    private fun ConfigureWidget(id: Int, single: Boolean, onSave: (WidgetConfig) -> Unit) {
        val data = remember { WidgetData(this) }
        val existing = remember(id) { data.config(id) }
        var profile by remember { mutableStateOf(existing?.profileId) }
        // Single widget: exactly one server. Multi widget: at most MAX_MULTI_SERVERS (3).
        var selected by remember {
            mutableStateOf(existing?.serverIds?.take(if (single) 1 else WidgetConfig.MAX_MULTI_SERVERS)?.toSet() ?: emptySet())
        }
        var selectedNames by remember(id) {
            mutableStateOf<Map<Long, String>>(existing?.let { saved ->
                data.readings(id, saved).mapValues { (_, reading) -> reading.name }
            } ?: emptyMap())
        }
        var metrics by remember { mutableStateOf(existing?.metrics ?: setOf("cpu", "ram", "disk")) }
        var page by remember(profile) { mutableIntStateOf(1) }
        var retry by remember { mutableIntStateOf(0) }
        var serverPage by remember { mutableStateOf<com.qrcommunication.ploipanel.ServerPage?>(null) }
        var loadedProfile by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var fetchError by remember { mutableStateOf(false) }
        val profiles = if (authorized) data.profiles.profiles() else emptyList()
        LaunchedEffect(authorized, profile, page, retry) {
            serverPage = null // Never present old-page rows as the new page, including after a failed request.
            fetchError = false
            val idForFetch = profile ?: return@LaunchedEffect
            if (!authorized || profiles.none { it.id == idForFetch }) return@LaunchedEffect
            loading = true
            try {
                val token = withContext(Dispatchers.IO) { data.profiles.tokenFor(idForFetch) }
                if (token == null) {
                    fetchError = true
                } else {
                    val loaded = withContext(Dispatchers.IO) { PloiApi.servers(token, page, 50) }
                    serverPage = loaded
                    loadedProfile = idForFetch
                    selectedNames = selectedNames + loaded.servers.filter { it.id in selected }
                        .associate { it.id to it.name }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fetchError = true }
            finally { loading = false }
        }
        val maxServers = widgetServerLimit(single)
        val full = selected.size >= maxServers
        val canSave = authorized && profile != null && profiles.any { it.id == profile } &&
            selected.isNotEmpty() && selected.size <= maxServers &&
            loadedProfile == profile && metrics.isNotEmpty()
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            LazyColumn(Modifier.widthIn(max = PanelSpacing.maxContentWidth).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = PanelSpacing.xl, vertical = PanelSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                item {
                    ConfigHeader(
                        if (single) R.string.widget_single else R.string.widget_multi,
                        if (single) stringResource(R.string.widget_config_single_rule)
                        else stringResource(R.string.widget_config_multi_rule, WidgetConfig.MAX_MULTI_SERVERS)
                    )
                }
                if (!authorized) {
                    item { PinGate() }
                } else {
                    item { Text(stringResource(R.string.widget_public_notice),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { SectionHeader(stringResource(R.string.widget_config_step_profile)) }
                    if (profiles.isEmpty()) item { EmptyState(Icons.Outlined.Person, stringResource(R.string.widget_config_no_profiles)) }
                    items(profiles, key = { "profile.${it.id}" }) { item: PloiProfile ->
                        ChoiceRow(item.label, null, checked = profile == item.id, radio = true, enabled = true) {
                            if (profile != item.id) {
                                profile = item.id; selected = emptySet(); selectedNames = emptyMap()
                            }
                        }
                    }
                    if (profile != null && profiles.any { it.id == profile }) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(
                                    stringResource(if (single) R.string.widget_config_step_server else R.string.widget_config_step_servers),
                                    Modifier.weight(1f)
                                )
                                Surface(shape = CircleShape, color = if (full) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (full) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSecondaryContainer) {
                                    Text(stringResource(R.string.widget_config_count, selected.size, maxServers),
                                        Modifier.padding(horizontal = PanelSpacing.md, vertical = PanelSpacing.xs),
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (selected.isNotEmpty()) item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                                selected.forEach { serverId ->
                                    val name = selectedNames[serverId] ?: stringResource(R.string.widget_unknown_server, serverId)
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            selected = selected - serverId
                                            selectedNames = selectedNames - serverId
                                        },
                                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = {
                                            Icon(Icons.Outlined.Close, modifier = Modifier.size(18.dp),
                                                contentDescription = stringResource(R.string.widget_config_remove_named, name))
                                        }
                                    )
                                }
                            }
                        }
                        if (!single && full) item {
                            Text(stringResource(R.string.widget_config_limit, maxServers),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (loading) item { LoadingState(rows = 2) }
                        if (fetchError) item {
                            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                                Text(stringResource(R.string.widget_config_page_error), color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.widget_config_retry)) }
                            }
                        }
                        if (serverPage?.servers?.isEmpty() == true) item {
                            EmptyState(Icons.Outlined.Dns, stringResource(R.string.widget_config_empty_page))
                        }
                        items(serverPage?.servers ?: emptyList(), key = { "server.${it.id}" }) { server ->
                            val checked = server.id in selected
                            ChoiceRow(
                                server.name, server.ipAddress.ifBlank { null }, checked, radio = single,
                                // Multi: once three are chosen, the others are disabled until one is removed.
                                enabled = canPickWidgetServer(selected, server.id, single)
                            ) { wanted ->
                                selected = toggleWidgetServer(selected, server.id, wanted, single)
                                selectedNames = if (single) mapOf(server.id to server.name)
                                else (selectedNames + (server.id to server.name)).filterKeys { it in selected }
                            }
                        }
                        if (serverPage != null) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { page-- }, enabled = page > 1) {
                                    Text(stringResource(R.string.widget_config_previous))
                                }
                                Text(stringResource(R.string.widget_config_page, page), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                                OutlinedButton(onClick = { page++ }, enabled = serverPage?.hasNext == true) {
                                    Text(stringResource(R.string.widget_config_next))
                                }
                            }
                        }
                    }
                    item { SectionHeader(stringResource(R.string.widget_config_step_metrics)) }
                    items(listOf("cpu", "ram", "disk", "load"), key = { "metric.$it" }) { metric ->
                        val label = when (metric) {
                            "cpu" -> R.string.widget_config_cpu
                            "ram" -> R.string.widget_config_ram
                            "disk" -> R.string.widget_config_disk
                            else -> R.string.widget_config_load
                        }
                        ChoiceRow(stringResource(label), null, checked = metric in metrics, radio = false, enabled = true) { wanted ->
                            metrics = if (wanted) metrics + metric else metrics - metric
                        }
                    }
                }
            }
            if (authorized) SaveBar(canSave) {
                profile?.let { onSave(WidgetConfig(it, selected.toList(), metrics)) }
            }
        }
    }

    /** Shared launcher PIN gate: same keypad and 4–12 digit rule as the app lock screen. */
    @Composable
    private fun PinGate() {
        val lock = remember { AppLock(SharedPreferencesProfilePrefs(this)) }
        var pinError by remember { mutableStateOf(false) }
        var lockedMs by remember { mutableLongStateOf(lock.remainingLockMs()) }
        LaunchedEffect(lockedMs > 0L) {
            while (lock.remainingLockMs() > 0L) {
                lockedMs = lock.remainingLockMs()
                delay(1_000L)
            }
            lockedMs = 0L
        }
        SectionCard(title = stringResource(R.string.widget_pin), icon = Icons.Outlined.Lock) {
            Text(stringResource(R.string.widget_pin_prompt), style = MaterialTheme.typography.bodyMedium)
            if (!lock.hasPin()) {
                Text(stringResource(R.string.widget_no_pin), color = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PinEntry(pin, { pin = it.take(AppLock.MAX_PIN_LENGTH); pinError = false },
                    enabled = lockedMs <= 0L && lock.hasPin(), error = pinError && pin.isEmpty())
            }
            if (pinError) Text(stringResource(R.string.widget_pin_error), color = MaterialTheme.colorScheme.error)
            if (lockedMs > 0L) {
                val seconds = ((lockedMs + 999L) / 1_000L).toInt()
                Text(pluralStringResource(R.plurals.widget_pin_wait, seconds, seconds),
                    color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = {
                when (val result = lock.verify(pin)) {
                    AppLock.UnlockResult.Unlocked -> { authorized = true; pinError = false }
                    is AppLock.UnlockResult.Locked -> { lockedMs = result.remainingMs; pinError = true }
                    is AppLock.UnlockResult.WrongPin -> pinError = true
                }
                pin = ""
            }, enabled = pin.isNotBlank() && lockedMs <= 0L && lock.hasPin(),
                modifier = Modifier.fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)) {
                Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.widget_unlock), Modifier.padding(start = PanelSpacing.sm))
            }
        }
    }

    /** Watchdog widget configuration: local targets only, no profile or API access involved. */
    @Composable
    private fun ConfigureSiteChecks(id: Int, onSave: (SiteCheckWidgetConfig) -> Unit) {
        val data = remember { SiteChecksData(this) }
        val store = remember { LocalCheckStore(SharedPreferencesProfilePrefs(this)) }
        val existing = remember(id) { data.config(id) }
        val targets = remember { store.targets() }
        val maxChecks = SiteCheckWidgetConfig.MAX_WIDGET_CHECKS
        var selected by remember {
            mutableStateOf(existing?.targetIds?.filter { targetId -> targets.any { it.id == targetId } }?.toSet()
                ?: targets.map { it.id }.take(maxChecks).toSet())
        }
        val canSave = authorized && selected.isNotEmpty() && selected.size <= maxChecks
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            LazyColumn(Modifier.widthIn(max = PanelSpacing.maxContentWidth).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = PanelSpacing.xl, vertical = PanelSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                item { ConfigHeader(R.string.widget_checks, stringResource(R.string.widget_config_checks_rule, maxChecks)) }
                if (!authorized) {
                    item { PinGate() }
                } else {
                    item { Text(stringResource(R.string.widget_checks_notice),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.widget_config_checks), Modifier.weight(1f))
                            Text(stringResource(R.string.widget_config_count, selected.size, maxChecks),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (targets.isEmpty()) item { EmptyState(Icons.Outlined.MonitorHeart, stringResource(R.string.widget_config_no_checks)) }
                    items(targets, key = { "check.${it.id}" }) { target ->
                        val checked = target.id in selected
                        ChoiceRow(target.label, target.url, checked, radio = false,
                            enabled = checked || selected.size < maxChecks) { wanted ->
                            selected = if (!wanted) selected - target.id
                            else if (selected.size < maxChecks) selected + target.id else selected
                        }
                    }
                    if (selected.size >= maxChecks) item {
                        Text(stringResource(R.string.widget_config_checks_limit, maxChecks),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (authorized) SaveBar(canSave) {
                onSave(SiteCheckWidgetConfig(targets.map { it.id }.filter { it in selected }))
            }
        }
    }
}

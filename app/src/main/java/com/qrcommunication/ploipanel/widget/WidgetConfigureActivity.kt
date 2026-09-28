package com.qrcommunication.ploipanel.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qrcommunication.ploipanel.AppLanguage
import com.qrcommunication.ploipanel.AppLock
import com.qrcommunication.ploipanel.AppTheme
import com.qrcommunication.ploipanel.PloiApi
import com.qrcommunication.ploipanel.PloiProfile
import com.qrcommunication.ploipanel.R
import com.qrcommunication.ploipanel.SharedPreferencesProfilePrefs
import com.qrcommunication.ploipanel.UiPreferences
import com.qrcommunication.ploipanel.panelDarkColors
import com.qrcommunication.ploipanel.panelLightColors
import com.qrcommunication.ploipanel.panelShapes
import com.qrcommunication.ploipanel.panelTypography
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
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
        if (provider?.packageName != packageName || (!single && !multi)) { finish(); return }
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
            CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = colors, typography = panelTypography, shapes = panelShapes) {
                    Surface(Modifier.fillMaxSize(), color = colors.background) {
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

    @Composable
    private fun ConfigureWidget(id: Int, single: Boolean, onSave: (WidgetConfig) -> Unit) {
        val data = remember { WidgetData(this) }
        val existing = remember(id) { data.config(id) }
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
        var profile by remember { mutableStateOf(existing?.profileId) }
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
        var limitReached by remember { mutableStateOf(false) }
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
        val maxServers = WidgetConfig.MAX_MULTI_SERVERS
        val canSave = authorized && profile != null && profiles.any { it.id == profile } &&
            selected.isNotEmpty() && selected.size <= (if (single) 1 else maxServers) &&
            loadedProfile == profile && metrics.isNotEmpty()
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(stringResource(if (single) R.string.widget_single else R.string.widget_multi),
                    style = MaterialTheme.typography.headlineMedium) }
                if (!authorized) {
                    item { Text(stringResource(R.string.widget_pin_prompt)) }
                    if (!lock.hasPin()) item {
                        Text(stringResource(R.string.widget_no_pin), color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(AppLock.MAX_PIN_LENGTH) },
                            label = { Text(stringResource(R.string.widget_pin)) },
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    }
                    if (pinError) item { Text(stringResource(R.string.widget_pin_error), color = MaterialTheme.colorScheme.error) }
                    if (lockedMs > 0L) item {
                        val seconds = ((lockedMs + 999L) / 1_000L).toInt()
                        Text(pluralStringResource(R.plurals.widget_pin_wait, seconds, seconds),
                            color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        Button(onClick = {
                            when (val result = lock.verify(pin)) {
                                AppLock.UnlockResult.Unlocked -> { authorized = true; pinError = false }
                                is AppLock.UnlockResult.Locked -> { lockedMs = result.remainingMs; pinError = true }
                                is AppLock.UnlockResult.WrongPin -> pinError = true
                            }
                            pin = ""
                        }, enabled = pin.isNotBlank() && lockedMs <= 0L && lock.hasPin()) {
                            Text(stringResource(R.string.widget_unlock))
                        }
                    }
                } else {
                    item { Text(stringResource(R.string.widget_public_notice),
                        style = MaterialTheme.typography.bodySmall) }
                    item { Text(stringResource(R.string.widget_config_profile), style = MaterialTheme.typography.titleMedium) }
                    if (profiles.isEmpty()) item { Text(stringResource(R.string.widget_config_no_profiles)) }
                    items(profiles, key = { "profile.${it.id}" }) { item: PloiProfile ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = profile == item.id, role = Role.RadioButton,
                                    onClick = { if (profile != item.id) {
                                        profile = item.id; selected = emptySet(); selectedNames = emptyMap()
                                        limitReached = false
                                    } })
                                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = profile == item.id, onClick = null)
                                Text(item.label, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                    if (profile != null && profiles.any { it.id == profile }) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.widget_config_servers), style = MaterialTheme.typography.titleMedium)
                                if (!single) Text(stringResource(R.string.widget_config_count, selected.size, maxServers))
                            }
                        }
                        if (selected.isNotEmpty()) item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.widget_config_selected),
                                        style = MaterialTheme.typography.titleSmall)
                                    selected.forEach { serverId ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(selectedNames[serverId]
                                                ?: stringResource(R.string.widget_unknown_server, serverId),
                                                modifier = Modifier.weight(1f))
                                            OutlinedButton(onClick = {
                                                selected = selected - serverId
                                                selectedNames = selectedNames - serverId
                                                limitReached = false
                                            }) { Text(stringResource(R.string.widget_config_remove)) }
                                        }
                                    }
                                }
                            }
                        }
                        if (loading) item { Text(stringResource(R.string.widget_loading)) }
                        if (fetchError) item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.widget_config_page_error), color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.widget_config_retry)) }
                            }
                        }
                        if (serverPage?.servers?.isEmpty() == true) item {
                            Text(stringResource(R.string.widget_config_empty_page))
                        }
                        items(serverPage?.servers ?: emptyList(), key = { "server.${it.id}" }) { server ->
                            val checked = server.id in selected
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .then(if (single) Modifier.selectable(checked, role = Role.RadioButton,
                                        onClick = {
                                            selected = setOf(server.id)
                                            selectedNames = mapOf(server.id to server.name)
                                            limitReached = false
                                        })
                                    else Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = { wanted ->
                                        if (!wanted) {
                                            selected = selected - server.id
                                            selectedNames = selectedNames - server.id
                                            limitReached = false
                                        } else if (selected.size < maxServers) {
                                            selected = selected + server.id
                                            selectedNames = selectedNames + (server.id to server.name)
                                            limitReached = false
                                        } else limitReached = true
                                    }))
                                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (single) RadioButton(checked, onClick = null)
                                    else Checkbox(checked, onCheckedChange = null)
                                    Text(server.name, Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                        if (limitReached && !single) item {
                            Text(stringResource(R.string.widget_config_limit, maxServers),
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (serverPage != null) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { page--; limitReached = false }, enabled = page > 1) {
                                    Text(stringResource(R.string.widget_config_previous))
                                }
                                Text(stringResource(R.string.widget_config_page, page), Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { page++; limitReached = false }, enabled = serverPage?.hasNext == true) {
                                    Text(stringResource(R.string.widget_config_next))
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.widget_metrics), style = MaterialTheme.typography.titleMedium) }
                    items(listOf("cpu", "ram", "disk", "load"), key = { "metric.$it" }) { metric ->
                        val checked = metric in metrics
                        val label = when (metric) {
                            "cpu" -> R.string.widget_config_cpu
                            "ram" -> R.string.widget_config_ram
                            "disk" -> R.string.widget_config_disk
                            else -> R.string.widget_config_load
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { wanted ->
                                    metrics = if (wanted) metrics + metric else metrics - metric
                                }).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(stringResource(label), Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
            if (authorized) {
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center) {
                    Button(onClick = { profile?.let { onSave(WidgetConfig(it, selected.toList(), metrics)) } },
                        enabled = canSave, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.widget_save))
                    }
                }
            }
        }
    }
}

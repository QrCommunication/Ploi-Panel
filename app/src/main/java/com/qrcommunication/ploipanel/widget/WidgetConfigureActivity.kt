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
import androidx.compose.foundation.layout.Row
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
import com.qrcommunication.ploipanel.Server
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
        var error by remember { mutableStateOf(false) }
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
        var selected by remember { mutableStateOf(existing?.serverIds?.toSet() ?: emptySet()) }
        var metrics by remember { mutableStateOf(existing?.metrics ?: setOf("cpu", "ram", "disk")) }
        var servers by remember { mutableStateOf<List<Server>?>(null) }
        var loading by remember { mutableStateOf(false) }
        val profiles = if (authorized) data.profiles.profiles() else emptyList()
        LaunchedEffect(authorized, profile) {
            servers = null
            val idForFetch = profile ?: return@LaunchedEffect
            if (!authorized) return@LaunchedEffect
            loading = true
            try {
                val token = withContext(Dispatchers.IO) { data.profiles.tokenFor(idForFetch) }
                if (token == null) { error = true; return@LaunchedEffect }
                servers = withContext(Dispatchers.IO) {
                    val items = mutableListOf<Server>()
                    var page = 1
                    do {
                        val response = PloiApi.servers(token, page, 50)
                        items += response.servers
                        page++
                    } while (response.hasNext && page <= 20)
                    items
                }
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true } finally { loading = false }
        }
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(stringResource(if (single) R.string.widget_single else R.string.widget_multi),
                style = MaterialTheme.typography.headlineSmall) }
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
                item { Text(stringResource(R.string.widget_public_notice)) }
                items(profiles, key = { "profile.${it.id}" }) { item: PloiProfile ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = profile == item.id, role = Role.RadioButton,
                            onClick = { profile = item.id; selected = emptySet() })) {
                        RadioButton(selected = profile == item.id, onClick = null)
                        Text(item.label, Modifier.padding(top = 12.dp))
                    }
                }
                if (loading) item { Text(stringResource(R.string.widget_loading)) }
                items(servers ?: emptyList(), key = { "server.${it.id}" }) { server ->
                    val checked = server.id in selected
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { wanted ->
                            selected = if (wanted) {
                                if (single) setOf(server.id) else (selected + server.id).take(6).toSet()
                            } else selected - server.id
                        })) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(server.name, Modifier.padding(top = 12.dp))
                    }
                }
                if (servers?.size == 1000) item { Text(stringResource(R.string.widget_list_limit)) }
                item { Text(stringResource(R.string.widget_metrics)) }
                items(WidgetConfig.METRICS.sorted(), key = { "metric.$it" }) { metric ->
                    val checked = metric in metrics
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { wanted ->
                            metrics = if (wanted) metrics + metric else metrics - metric
                        })) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(metric.uppercase(), Modifier.padding(top = 12.dp))
                    }
                }
                item {
                    Button(onClick = { profile?.let { onSave(WidgetConfig(it, selected.toList(), metrics)) } },
                        enabled = profile != null && servers != null &&
                            selected.isNotEmpty() && (!single || selected.size == 1) &&
                            selected.all { id -> servers?.any { it.id == id } == true } && metrics.isNotEmpty()) {
                        Text(stringResource(R.string.widget_save))
                    }
                }
            }
            if (error) item { Text(stringResource(R.string.widget_fetch_error), color = MaterialTheme.colorScheme.error) }
        }
    }
}

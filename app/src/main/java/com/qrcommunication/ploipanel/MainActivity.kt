package com.qrcommunication.ploipanel

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { PloiPanel() }
    }
}

private val accent = Color(0xFF137A69)

@Composable
private fun PloiPanel() {
    val activity = LocalActivity.current as FragmentActivity
    val systemConfiguration = LocalConfiguration.current
    val preferences = remember(activity) { UiPreferences(SharedPreferencesProfilePrefs(activity)) }
    var theme by remember { mutableStateOf(preferences.theme()) }
    var language by remember { mutableStateOf(preferences.language()) }
    val configuration = remember(systemConfiguration, language) {
        Configuration(systemConfiguration).apply {
            when (language) {
                AppLanguage.FRENCH -> setLocale(Locale.FRENCH)
                AppLanguage.ENGLISH -> setLocale(Locale.ENGLISH)
                AppLanguage.SYSTEM -> Unit
            }
        }
    }
    val localizedContext = remember(activity, configuration) { activity.createConfigurationContext(configuration) }
    val dark = when (theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xFF82DAC3)) else lightColorScheme(primary = accent)
    SideEffect {
        activity.window.decorView.setBackgroundColor(colors.background.toArgb())
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
    MaterialTheme(colorScheme = colors) {
        // API 35+ draws edge-to-edge even without an explicit enableEdgeToEdge call.
        // Consume system bars once at the root; the IME then reduces the usable viewport.
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val context = activity
            val lock = remember { AppLock(SharedPreferencesProfilePrefs(context)) }
            var lockVersion by remember { mutableIntStateOf(0) }
            val hasPin = remember(lockVersion) { lock.hasPin() }
            var unlocked by remember { mutableStateOf(!lock.hasPin()) }

            // Auto-lock as soon as the app leaves the foreground; unlock state never survives
            // backgrounding or process death, so secrets are re-gated by PIN/biometrics.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && lock.hasPin()) unlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            when {
                !hasPin -> PinSetupScreen(lock) {
                    lockVersion++
                    unlocked = true
                }
                !unlocked -> PinUnlockScreen(lock, activity) { unlocked = true }
                else -> PanelHome(
                    lock, activity, theme, language,
                    onThemeChanged = { preferences.setTheme(it); theme = it },
                    onLanguageChanged = { preferences.setLanguage(it); language = it },
                    onLock = { unlocked = false }
                )
            }
        }
    }
    }
}

/** Authenticated content. Leaves composition entirely while locked, dropping in-memory tokens. */
@Composable
private fun PanelHome(
    lock: AppLock, activity: FragmentActivity, theme: AppTheme, language: AppLanguage,
    onThemeChanged: (AppTheme) -> Unit, onLanguageChanged: (AppLanguage) -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher()) }
    var profilesVersion by remember { mutableIntStateOf(0) }
    val profiles = remember(profilesVersion) { store.profiles() }
    var draftLabel by remember { mutableStateOf("") }
    var draftToken by remember { mutableStateOf("") }
    var activeProfile by remember {
        mutableStateOf(store.activeProfileId()?.let { id ->
            profiles.firstOrNull { it.id == id }?.let { it to store.tokenFor(id) }
        }?.takeIf { it.second != null })
    }
    val token = activeProfile?.second
    var profileError by remember { mutableIntStateOf(0) }
    var pendingProfileRemoval by remember { mutableStateOf<PloiProfile?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var selected by remember { mutableStateOf<Server?>(null) }
    var panelTab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var showMonitored by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<ServerPage?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(token, page, refresh, panelTab) {
        val active = token ?: return@LaunchedEffect
        if (panelTab != 0) return@LaunchedEffect
        loading = true
        error = null
        try {
            servers = withContext(Dispatchers.IO) { PloiApi.servers(active, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            servers = null
            error = failure
        } finally {
            loading = false
        }
    }

    if (token == null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onLock) { Text(stringResource(R.string.lock_now)) }
            }
            Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)
            if (profiles.isNotEmpty()) {
                Text(stringResource(R.string.profiles), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                profiles.forEach { profile ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(profile.label, style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { pendingProfileRemoval = profile }) {
                                    Text(stringResource(R.string.delete_profile))
                                }
                                Button(onClick = {
                                    val profileToken = store.tokenFor(profile.id)
                                    if (profileToken != null) {
                                        store.activate(profile.id)
                                        activeProfile = profile to profileToken
                                    } else {
                                        profileError = R.string.settings_profile_token_invalid
                                    }
                                }) { Text(stringResource(R.string.use_profile)) }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = draftLabel, onValueChange = { draftLabel = it },
                label = { Text(stringResource(R.string.profile_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draftToken, onValueChange = { draftToken = it },
                label = { Text(stringResource(R.string.token)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (profileError != 0) Text(stringResource(profileError), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                try {
                    val profile = store.add(draftLabel, draftToken.trim())
                    store.activate(profile.id)
                    profilesVersion++
                    profileError = 0
                    draftLabel = ""
                    draftToken = ""
                    activeProfile = profile to store.tokenFor(profile.id)
                } catch (invalid: IllegalArgumentException) {
                    profileError = when (invalid.message) {
                        "Duplicate profile label" -> R.string.profile_error_duplicate
                        "Too many profiles" -> R.string.profile_error_limit
                        else -> R.string.profile_error_invalid
                    }
                }
            }, enabled = draftLabel.isNotBlank() && draftToken.isNotBlank()) { Text(stringResource(R.string.add_profile)) }
        }
        pendingProfileRemoval?.let { profile ->
            SensitiveConfirmDialog(
                lock, activity,
                stringResource(R.string.settings_profile_delete_confirm, profile.label),
                R.string.delete_profile,
                onConfirmed = {
                    store.remove(profile.id)
                    pendingProfileRemoval = null
                    profilesVersion++
                },
                onDismiss = { pendingProfileRemoval = null }
            )
        }
    } else {
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Give focused fields the whole available height in landscape and on small phones.
            if (!keyboardVisible) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column {
                        Text(
                            when (panelTab) {
                                1 -> stringResource(R.string.providers)
                                2 -> stringResource(R.string.account)
                                3 -> stringResource(R.string.scripts_tab)
                                4 -> stringResource(R.string.status_pages_tab)
                                5 -> stringResource(R.string.webserver_templates_tab)
                                6 -> stringResource(R.string.projects_tab)
                                7 -> stringResource(R.string.settings_tab)
                                else -> stringResource(R.string.servers)
                            },
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                        )
                        Text(stringResource(R.string.current_profile, activeProfile?.first?.label.orEmpty()), style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = onLock) { Text(stringResource(R.string.lock_now)) }
                        OutlinedButton(onClick = {
                            store.deactivate()
                            activeProfile = null
                            draftLabel = ""
                            draftToken = ""
                            selected = null
                            panelTab = 0
                            servers = null
                            error = null
                            page = 1
                        }) { Text(stringResource(R.string.disconnect)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val sections = listOf(
                    R.string.servers, R.string.providers, R.string.account, R.string.scripts_tab,
                    R.string.status_pages_tab, R.string.webserver_templates_tab, R.string.projects_tab,
                    R.string.settings_tab
                )
                val navigationState = rememberLazyListState()
                LaunchedEffect(panelTab) { navigationState.animateScrollToItem(panelTab) }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = navigationState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sections.size) { index ->
                        FilterChip(
                            selected = panelTab == index,
                            onClick = { panelTab = index },
                            label = { Text(stringResource(sections[index])) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                when (panelTab) {
                    1 -> ProvidersScreen(token)
                    2 -> AccountScreen(token)
                    3 -> ScriptsScreen(token, lock, activity)
                    4 -> StatusPagesScreen(token, lock, activity)
                    5 -> WebserverTemplatesScreen(token)
                    6 -> ProjectsScreen(token, lock, activity)
                    7 -> SettingsScreen(
                        store = store, profiles = profiles, active = activeProfile?.first,
                        lock = lock, activity = activity, theme = theme, language = language,
                        onThemeChanged = onThemeChanged, onLanguageChanged = onLanguageChanged,
                        onProfilesChanged = { profilesVersion++ },
                        onActiveChanged = { profile ->
                            activeProfile = profile?.let { it to store.tokenFor(it.id) }
                            selected = null
                            servers = null
                            page = 1
                            refresh++
                        },
                        onLock = onLock
                    )
                    else -> {
                        val expanded = maxWidth >= 720.dp
                        when {
                            creating -> CreateServerScreen(
                                token,
                                onDone = { creating = false; selected = null; refresh++ },
                                onCancel = { creating = false }
                            )
                            showMonitored -> Column {
                                OutlinedButton(onClick = { showMonitored = false }) { Text(stringResource(R.string.back)) }
                                MonitoredServersScreen(token)
                            }
                            expanded -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    ServerList(servers, loading, error, page, onPage = { page = it }, onRefresh = { refresh++ },
                                        onSelect = { selected = it }, onCreate = { creating = true }, onMonitored = { showMonitored = true })
                                }
                                Column(Modifier.weight(1f)) {
                                    val server = selected
                                    if (server == null) Text(stringResource(R.string.select_server))
                                    else ServerDetailScreen(
                                        token, server, lock, activity, refresh,
                                        onChanged = { refresh++ },
                                        onDeleted = { selected = null; refresh++ }
                                    )
                                }
                            }
                            selected != null -> Column {
                                OutlinedButton(onClick = { selected = null }) { Text(stringResource(R.string.back)) }
                                ServerDetailScreen(
                                    token, selected!!, lock, activity, refresh,
                                    onChanged = { refresh++ },
                                    onDeleted = { selected = null; refresh++ }
                                )
                            }
                            else -> ServerList(servers, loading, error, page, onPage = { page = it }, onRefresh = { refresh++ },
                                onSelect = { selected = it }, onCreate = { creating = true }, onMonitored = { showMonitored = true })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Opt-in biometric shortcut. Enabling asks the system prompt to confirm identity first;
 * disabling is immediate. Hidden entirely when no strong biometric hardware is present.
 */
@Composable
internal fun BiometricToggle(lock: AppLock, activity: FragmentActivity) {
    if (!biometricAvailable(activity)) {
        Text(stringResource(R.string.biometric_unavailable), style = MaterialTheme.typography.bodySmall)
        return
    }
    var enabled by remember { mutableStateOf(lock.biometricEnabled()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.biometric_toggle), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = enabled,
            onCheckedChange = { wanted ->
                if (wanted) {
                    showBiometricPrompt(activity, onSuccess = {
                        lock.setBiometricEnabled(true)
                        enabled = true
                    })
                } else {
                    lock.setBiometricEnabled(false)
                    enabled = false
                }
            }
        )
    }
}

@Composable
internal fun ApiErrorText(failure: Throwable) {
    val message = when (failure) {
        is PloiHttpException -> when (failure.status) {
            401 -> stringResource(R.string.error_auth)
            403 -> stringResource(R.string.error_permission)
            429 -> stringResource(R.string.error_rate, failure.retryAfterSeconds ?: "?")
            else -> stringResource(R.string.error_other, failure.status)
        }
        is PloiMalformedPayloadException -> stringResource(R.string.error_malformed)
        else -> stringResource(R.string.error_network)
    }
    Text(message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ServerList(
    pageData: ServerPage?, loading: Boolean, error: Throwable?, page: Int,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Server) -> Unit,
    onCreate: () -> Unit, onMonitored: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
            OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.new_server)) }
            OutlinedButton(onClick = onMonitored) { Text(stringResource(R.string.monitored_overview)) }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error)
        if (pageData != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.page, pageData.currentPage.toString(), pageData.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { onPage(page + 1) }, enabled = pageData.hasNext) { Text(stringResource(R.string.next)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pageData.servers, key = { it.id }) { server ->
                    Card(onClick = { onSelect(server) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(server.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.server_status, server.status))
                            Text(server.ipAddress)
                        }
                    }
                }
            }
        }
    }
}

package com.qrcommunication.ploipanel

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.qrcommunication.ploipanel.widget.WidgetRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

internal data class WidgetRoute(val profileId: String, val serverId: Long?)

internal fun widgetRouteFor(profileId: String?, serverId: Long): WidgetRoute? =
    profileId?.takeIf { it.isNotBlank() }?.let { WidgetRoute(it, serverId.takeIf { id -> id > 0L }) }

internal fun widgetRouteFrom(intent: Intent?): WidgetRoute? = widgetRouteFor(
    intent?.getStringExtra("widget_profile_id"), intent?.getLongExtra("widget_server_id", -1L) ?: -1L
)

class MainActivity : FragmentActivity() {
    private var widgetRoute by mutableStateOf<WidgetRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetRoute = widgetRouteFrom(intent)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { PloiPanel(widgetRoute) { widgetRoute = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetRoute = widgetRouteFrom(intent)
    }
}

@Composable
private fun PloiPanel(widgetRoute: WidgetRoute?, onWidgetRouteConsumed: () -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var pendingArchive by remember { mutableStateOf<ByteArray?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var transferStatus by remember { mutableIntStateOf(0) }
    val saveArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val encrypted = pendingArchive
        pendingArchive = null
        if (uri == null || encrypted == null) {
            encrypted?.fill(0)
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
                            ?: error("Could not open archive destination")
                        val saved = activity.contentResolver.openInputStream(uri)?.use(::readLimitedArchive)
                            ?: error("Could not verify saved archive")
                        check(MessageDigest.isEqual(encrypted, saved)) { "Archive verification failed" }
                    }
                    transferStatus = R.string.config_export_saved
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    transferStatus = R.string.config_export_error
                } finally { encrypted.fill(0) }
            }
        }
    }
    val openArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri
    }
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
    val colors = if (dark) panelDarkColors else panelLightColors
    SideEffect {
        activity.window.decorView.setBackgroundColor(colors.background.toArgb())
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
    MaterialTheme(colorScheme = colors, typography = panelTypography, shapes = panelShapes) {
        // API 35+ draws edge-to-edge even without an explicit enableEdgeToEdge call.
        // Consume system bars once at the root; the IME then reduces the usable viewport.
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val context = activity
            val lock = remember { AppLock(SharedPreferencesProfilePrefs(context)) }
            var lockVersion by remember { mutableIntStateOf(0) }
            val hasPin = remember(lockVersion) { lock.hasPin() }
            var unlocked by remember { mutableStateOf(!lock.hasPin()) }

            // Auto-lock when the app leaves the foreground, after the optional grace delay chosen in
            // settings (monotonic clock). Unlock state never survives process death, so secrets are
            // re-gated by PIN/biometrics.
            val lifecycleOwner = LocalLifecycleOwner.current
            val lockPrefs = remember { SharedPreferencesProfilePrefs(context) }
            var stoppedAt by remember { mutableStateOf<Long?>(null) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (!lock.hasPin()) return@LifecycleEventObserver
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            val grace = AutoLockPolicy.graceSeconds(lockPrefs)
                            if (grace == 0) unlocked = false else stoppedAt = SystemClock.elapsedRealtime()
                        }
                        Lifecycle.Event.ON_START -> {
                            if (AutoLockPolicy.mustRelock(stoppedAt, SystemClock.elapsedRealtime(), AutoLockPolicy.graceSeconds(lockPrefs))) {
                                unlocked = false
                            }
                            stoppedAt = null
                        }
                        else -> Unit
                    }
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
                    widgetRoute = widgetRoute,
                    onWidgetRouteConsumed = onWidgetRouteConsumed,
                    importUri = importUri,
                    onImportUriConsumed = { importUri = null },
                    onChooseImport = {
                        transferStatus = 0
                        try { openArchive.launch(arrayOf("*/*")) }
                        catch (_: Exception) { transferStatus = R.string.config_import_error }
                    },
                    onSaveEncryptedArchive = { encrypted ->
                        pendingArchive?.fill(0)
                        pendingArchive = encrypted
                        transferStatus = 0
                        try { saveArchive.launch("Ploi-Panel-config.ploi") }
                        catch (_: Exception) { pendingArchive = null; encrypted.fill(0); transferStatus = R.string.config_export_error }
                    },
                    transferStatus = transferStatus,
                    onImportSuccess = { transferStatus = R.string.config_import_saved },
                    onThemeChanged = { preferences.setTheme(it); theme = it },
                    onLanguageChanged = {
                        preferences.setLanguage(it)
                        language = it
                        WidgetRefresh.widgetIds(activity).forEach { id -> WidgetRefresh.render(activity, id) }
                    },
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
    widgetRoute: WidgetRoute?, onWidgetRouteConsumed: () -> Unit,
    importUri: Uri?, onImportUriConsumed: () -> Unit,
    onChooseImport: () -> Unit, onSaveEncryptedArchive: (ByteArray) -> Unit,
    transferStatus: Int, onImportSuccess: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit, onLanguageChanged: (AppLanguage) -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher()) }
    val configurationManager = remember {
        PortableConfigurationManager(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher(),
            KeystoreTokenCipher("ploi-panel.deploy-templates"))
    }
    val offlineCache = remember {
        OfflineCache(SharedPreferencesProfilePrefs(context), KeystoreTokenCipher(OFFLINE_CACHE_ALIAS))
    }
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
    var globalBatchRunning by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var showMonitored by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<ServerPage?>(null) }
    var serversCachedAt by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(false) }
    var deepLinkError by remember { mutableStateOf(false) }

    fun onConfigurationImported() {
        profilesVersion++
        activeProfile = store.activeProfileId()?.let { id ->
            store.profiles().firstOrNull { it.id == id }?.let { it to store.tokenFor(id) }
        }?.takeIf { it.second != null }
        selected = null
        servers = null
        creating = false
        showMonitored = false
        page = 1
        refresh++
        val restored = UiPreferences(SharedPreferencesProfilePrefs(context))
        onThemeChanged(restored.theme())
        onLanguageChanged(restored.language())
    }

    val terminalRequest by TerminalNavigator.requests.collectAsState()
    LaunchedEffect(terminalRequest, globalBatchRunning) {
        if (terminalRequest != null && token != null && !globalBatchRunning) panelTab = 10
    }

    LaunchedEffect(importUri, token, transferStatus, globalBatchRunning) {
        if ((importUri != null || transferStatus != 0) && token != null && !globalBatchRunning) panelTab = 7
    }

    // Route only after the application lock has been passed. Opaque widget IDs are never
    // trusted as credentials; resolve the saved profile and server with the normal API client.
    LaunchedEffect(widgetRoute, globalBatchRunning) {
        val route = widgetRoute ?: return@LaunchedEffect
        if (globalBatchRunning) return@LaunchedEffect
        deepLinkError = false
        try {
            val profile = store.profiles().firstOrNull { it.id == route.profileId }
            val routeToken = profile?.let { store.tokenFor(it.id) }
            if (profile == null || routeToken == null) {
                deepLinkError = true
            } else {
                store.activate(profile.id)
                activeProfile = profile to routeToken
                selected = null
                page = 1
                panelTab = 0
                creating = false
                showMonitored = false
                servers = null
                if (route.serverId != null) {
                    val detail = withContext(Dispatchers.IO) { PloiApi.server(routeToken, route.serverId) }
                    selected = Server(detail.id, detail.name, detail.status, detail.ipAddress)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            deepLinkError = true
        }
        onWidgetRouteConsumed()
    }

    LaunchedEffect(token, page, refresh, panelTab) {
        val active = token ?: return@LaunchedEffect
        if (panelTab != 0) return@LaunchedEffect
        val profileId = activeProfile?.first?.id
        loading = true
        servers = null
        serversCachedAt = null
        error = null
        try {
            val fresh = withContext(Dispatchers.IO) { PloiApi.servers(active, page) }
            servers = fresh
            if (profileId != null) {
                // Best effort: a cache write failure must never break a successful read.
                withContext(Dispatchers.IO) {
                    runCatching { offlineCache.saveServers(profileId, page, fresh) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            servers = null
            // Offline only: an HTTP answer is a real account signal and must stay visible.
            val cached = if (profileId != null && shouldServeCache(failure)) {
                withContext(Dispatchers.IO) { runCatching { offlineCache.servers(profileId, page) }.getOrNull() }
            } else null
            servers = cached?.page
            serversCachedAt = cached?.fetchedAt
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
            if (deepLinkError) Text(stringResource(R.string.widget_route_unavailable), color = MaterialTheme.colorScheme.error)
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
            SettingsSectionHeading(R.string.config_transfer_title)
            ConfigurationTransferSection(
                configurationManager, lock, activity, importUri, onImportUriConsumed,
                onChooseImport, onSaveEncryptedArchive, transferStatus,
                onImported = { onImportSuccess(); onConfigurationImported() }
            )
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    8 -> stringResource(R.string.deploy_global_tab)
                                    9 -> stringResource(R.string.local_checks_tab)
                                    10 -> stringResource(R.string.ssh_term_tab)
                                    else -> stringResource(if (creating) R.string.new_server else R.string.servers)
                                },
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                            )
                            Text(stringResource(R.string.current_profile, activeProfile?.first?.label.orEmpty()),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = onLock) { Text(stringResource(R.string.lock_now)) }
                            if (!creating) OutlinedButton(onClick = {
                                store.deactivate()
                                activeProfile = null
                                draftLabel = ""
                                draftToken = ""
                                selected = null
                                creating = false
                                showMonitored = false
                                panelTab = 0
                                deepLinkError = false
                                servers = null
                                error = null
                                page = 1
                            }, enabled = !globalBatchRunning) { Text(stringResource(R.string.disconnect)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (deepLinkError) {
                    Text(stringResource(R.string.widget_route_unavailable), color = MaterialTheme.colorScheme.error)
                }
                val sections = listOf(
                    0 to R.string.servers, 8 to R.string.deploy_global_tab,
                    9 to R.string.local_checks_tab, 10 to R.string.ssh_term_tab,
                    1 to R.string.providers, 2 to R.string.account, 3 to R.string.scripts_tab,
                    4 to R.string.status_pages_tab, 5 to R.string.webserver_templates_tab,
                    6 to R.string.projects_tab, 7 to R.string.settings_tab
                )
                val navigationState = rememberLazyListState()
                LaunchedEffect(panelTab) { navigationState.animateScrollToItem(sections.indexOfFirst { it.first == panelTab }.coerceAtLeast(0)) }
                if (!creating) LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = navigationState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sections.size) { index ->
                        val (tab, label) = sections[index]
                        FilterChip(
                            selected = panelTab == tab,
                            enabled = !globalBatchRunning || panelTab == tab,
                            onClick = { panelTab = tab },
                            label = { Text(stringResource(label)) }
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
                    8 -> activeProfile?.first?.id?.let { profileId ->
                        GlobalDeployScriptsScreen(token, profileId, lock, activity,
                            onRunningChange = { globalBatchRunning = it })
                    }
                    9 -> LocalChecksScreen()
                    10 -> activeProfile?.first?.id?.let { profileId ->
                        SshTerminalScreen(profileId, token, lock, activity)
                    }
                    7 -> SettingsScreen(
                        store = store, profiles = profiles, active = activeProfile?.first,
                        lock = lock, activity = activity, theme = theme, language = language,
                        onThemeChanged = onThemeChanged, onLanguageChanged = onLanguageChanged,
                        onProfilesChanged = { profilesVersion++ },
                        onActiveChanged = { profile ->
                            activeProfile = profile?.let { it to store.tokenFor(it.id) }
                            selected = null
                            creating = false
                            showMonitored = false
                            deepLinkError = false
                            servers = null
                            page = 1
                            refresh++
                        },
                        configurationManager = configurationManager,
                        importUri = importUri,
                        onImportUriConsumed = onImportUriConsumed,
                        onChooseImport = onChooseImport,
                        onSaveEncryptedArchive = onSaveEncryptedArchive,
                        transferStatus = transferStatus,
                        onImported = { onImportSuccess(); onConfigurationImported() },
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
                                Box(Modifier.weight(1f).fillMaxWidth()) { MonitoredServersScreen(token) }
                            }
                            expanded -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    ServerList(servers, loading, error, page, serversCachedAt, onPage = { page = it; selected = null }, onRefresh = { refresh++ },
                                        onSelect = { selected = it }, onCreate = { creating = true }, onMonitored = { showMonitored = true })
                                }
                                Column(Modifier.weight(1f)) {
                                    val server = selected
                                    if (server == null) Text(stringResource(R.string.select_server))
                                    else ServerDetailScreen(
                                        token, server, lock, activity, refresh,
                                        profileId = activeProfile?.first?.id,
                                        onChanged = { refresh++ },
                                        onDeleted = { selected = null; refresh++ }
                                    )
                                }
                            }
                            selected != null -> Column {
                                OutlinedButton(onClick = { selected = null }) { Text(stringResource(R.string.back)) }
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    ServerDetailScreen(
                                        token, selected!!, lock, activity, refresh,
                                        profileId = activeProfile?.first?.id,
                                        onChanged = { refresh++ },
                                        onDeleted = { selected = null; refresh++ }
                                    )
                                }
                            }
                            else -> ServerList(servers, loading, error, page, serversCachedAt, onPage = { page = it; selected = null }, onRefresh = { refresh++ },
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
    pageData: ServerPage?, loading: Boolean, error: Throwable?, page: Int, cachedAt: Long?,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Server) -> Unit,
    onCreate: () -> Unit, onMonitored: () -> Unit
) {
    // Cached rows are a past observation: opening a server would need live calls we cannot make,
    // and creating one would act on an account state we have not read. Reload stays available.
    val offline = cachedAt != null
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onCreate, enabled = !offline) { Text(stringResource(R.string.new_server)) }
                OutlinedButton(onClick = onMonitored, enabled = !offline) { Text(stringResource(R.string.monitored_overview)) }
                OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(stringResource(R.string.reload)) }
            }
        }
        if (loading) item { BusyIndicator(Modifier.padding(24.dp)) }
        if (error != null) item { ApiErrorText(error) }
        if (cachedAt != null) item { OfflineCacheBanner(cachedAt) }
        if (pageData != null) {
            item { ServerPageHero(pageData) }
            if (pageData.servers.isEmpty()) item {
                Text(stringResource(R.string.dashboard_empty), style = MaterialTheme.typography.bodyLarge)
            }
            items(pageData.servers, key = { it.id }) { server ->
                ServerItemCard(server, enabled = !offline) { onSelect(server) }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) {
                        Text(stringResource(R.string.previous))
                    }
                    Text(
                        stringResource(R.string.page, pageData.currentPage.toString(), pageData.lastPage.toString()),
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { onPage(page + 1) }, enabled = pageData.hasNext) {
                        Text(stringResource(R.string.next))
                    }
                }
            }
        }
    }
}

package com.qrcommunication.ploipanel

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
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
    LocalizedActivityScope(activity, localizedContext, configuration) {
    PloiPanelTheme(dark) {
        // API 35+ draws edge-to-edge even without an explicit enableEdgeToEdge call.
        // Consume system bars once at the root; the IME then reduces the usable viewport.
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
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

/**
 * Supplies the in-app language to `stringResource` (context + configuration) while keeping the
 * Activity reachable for Compose APIs that look it up through LocalContext. A configuration
 * context is a bare ContextImpl, not a wrapper of the Activity: without the explicit owners below,
 * `rememberLauncherForActivityResult` (notification permission in Monitoring alerts and in
 * Surveillance) throws "No ActivityResultRegistryOwner was provided" and closes the app.
 */
@Composable
internal fun LocalizedActivityScope(
    activity: ComponentActivity, localizedContext: Context, configuration: Configuration,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalActivityResultRegistryOwner provides activity,
        LocalOnBackPressedDispatcherOwner provides activity,
        LocalActivity provides activity,
        content = content
    )
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
    var serverQuery by remember { mutableStateOf("") }
    // Rechecks of servers Ploi reports as unreachable: fresh Ploi status + device TCP test.
    var rechecks by remember { mutableStateOf<Map<Long, Recheck>>(emptyMap()) }
    var rechecking by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val recheckScope = rememberCoroutineScope()

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
            rechecks = emptyMap()
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

    // Opening the list re-tests every server Ploi reports as unreachable (bounded per page), so
    // a stale "unreachable" is replaced by Ploi's current status and this phone's own TCP test.
    fun recheck(server: Server) {
        val active = token ?: return
        if (server.id in rechecking) return
        rechecking = rechecking + server.id
        recheckScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { recheckServer(active, server) }
                if (token == active) rechecks = rechecks + (server.id to result)
            } finally {
                rechecking = rechecking - server.id
            }
        }
    }
    LaunchedEffect(servers, serversCachedAt) {
        val loaded = servers ?: return@LaunchedEffect
        if (serversCachedAt != null) return@LaunchedEffect // cached rows: no live calls
        loaded.servers.filter { needsRecheck(it.status) }.take(MAX_AUTO_RECHECKS_PER_PAGE).forEach { recheck(it) }
    }

    // Shared by Settings and the top-bar profile switcher: one code path to change the active profile.
    fun applyActiveProfile(profile: PloiProfile?) {
        activeProfile = profile?.let { it to store.tokenFor(it.id) }
        selected = null
        serverQuery = ""
        creating = false
        showMonitored = false
        deepLinkError = false
        servers = null
        page = 1
        refresh++
    }

    if (token == null) {
        WelcomeScreen(
            profiles = profiles, deepLinkError = deepLinkError, profileError = profileError,
            draftLabel = draftLabel, onDraftLabel = { draftLabel = it },
            draftToken = draftToken, onDraftToken = { draftToken = it },
            onLock = onLock,
            onUseProfile = { profile ->
                val profileToken = store.tokenFor(profile.id)
                if (profileToken != null) {
                    store.activate(profile.id)
                    activeProfile = profile to profileToken
                } else {
                    profileError = R.string.settings_profile_token_invalid
                }
            },
            onDeleteProfile = { pendingProfileRemoval = it },
            onAddProfile = {
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
            }
        ) {
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
        // Hub sub-pages return to "More"; an open server returns to the list. A running global
        // deploy batch freezes navigation entirely (its own BackHandler keeps the report visible).
        val backAction: (() -> Unit)? = when {
            globalBatchRunning || creating -> null
            parentTab(panelTab) != null -> { { panelTab = MORE_HUB_TAB } }
            panelTab == 0 && showMonitored -> { { showMonitored = false } }
            panelTab == 0 && selected != null -> { { selected = null } }
            else -> null
        }
        BackHandler(enabled = backAction != null) { backAction?.invoke() }
        // Server creation keeps its own back/exit guard, so primary navigation steps aside.
        val showNavigation = !creating && !keyboardVisible
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rail = usesNavigationRail(maxWidth)
            Row(Modifier.fillMaxSize()) {
                if (rail && !creating) PanelNavigationRail(panelTab, locked = globalBatchRunning) { panelTab = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Give focused fields the whole available height in landscape and on small phones.
                    if (!keyboardVisible) PanelTopBar(
                        title = panelTabTitle(panelTab, creating),
                        profiles = profiles, active = activeProfile?.first,
                        // Creation owns its exit guard (one-time setup secrets): no profile switch meanwhile.
                        locked = globalBatchRunning || creating, showDisconnect = !creating,
                        onBack = backAction,
                        onSwitchProfile = { profile ->
                            if (store.tokenFor(profile.id) == null) {
                                profileError = R.string.profile_menu_switch_failed
                            } else {
                                store.activate(profile.id)
                                profileError = 0
                                applyActiveProfile(profile)
                            }
                        },
                        onManageProfiles = { panelTab = 7 },
                        onDisconnect = {
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
                        },
                        onLock = onLock
                    )
                    if (globalBatchRunning) NavigationLockedNotice()
                    if (deepLinkError || profileError != 0) Column(
                        Modifier.padding(horizontal = PanelSpacing.lg, vertical = PanelSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
                    ) {
                        if (deepLinkError) InlineNotice(stringResource(R.string.widget_route_unavailable))
                        if (profileError != 0) InlineNotice(stringResource(profileError))
                    }
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = PanelSpacing.lg).padding(top = PanelSpacing.xs)
                    ) {
                        when (panelTab) {
                            MORE_HUB_TAB -> MoreHubScreen(onOpen = { panelTab = it })
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
                                onActiveChanged = { profile -> applyActiveProfile(profile) },
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
                                        Box(Modifier.weight(1f).fillMaxWidth()) { MonitoredServersScreen(token) }
                                    }
                                    expanded -> Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg)) {
                                        Column(Modifier.weight(1f)) {
                                            ServerList(servers, loading, error, page, serversCachedAt, serverQuery, { serverQuery = it },
                                                rechecks, rechecking, ::recheck,
                                                onPage = { page = it; selected = null }, onRefresh = { refresh++ },
                                                onSelect = { selected = it }, onCreate = { creating = true }, onMonitored = { showMonitored = true })
                                        }
                                        Column(Modifier.weight(1f)) {
                                            val server = selected
                                            if (server == null) EmptyState(
                                                Icons.Outlined.Dns, stringResource(R.string.select_server),
                                                modifier = Modifier.padding(top = PanelSpacing.xxl)
                                            )
                                            else ServerDetailScreen(
                                                token, server, lock, activity, refresh,
                                                profileId = activeProfile?.first?.id,
                                                onChanged = { refresh++ },
                                                onDeleted = { selected = null; refresh++ }
                                            )
                                        }
                                    }
                                    selected != null -> Column {
                                        Box(Modifier.weight(1f).fillMaxWidth()) {
                                            ServerDetailScreen(
                                                token, selected!!, lock, activity, refresh,
                                                profileId = activeProfile?.first?.id,
                                                onChanged = { refresh++ },
                                                onDeleted = { selected = null; refresh++ }
                                            )
                                        }
                                    }
                                    else -> ServerList(servers, loading, error, page, serversCachedAt, serverQuery, { serverQuery = it },
                                        rechecks, rechecking, ::recheck,
                                        onPage = { page = it; selected = null }, onRefresh = { refresh++ },
                                        onSelect = { selected = it }, onCreate = { creating = true }, onMonitored = { showMonitored = true })
                                }
                            }
                        }
                    }
                    if (!rail && showNavigation) PanelNavigationBar(panelTab, locked = globalBatchRunning) { panelTab = it }
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
    val message = apiErrorMessage(failure)
    Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp))
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One-line warning with an icon, for route/profile problems shown above the current screen. */
@Composable
internal fun InlineNotice(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(PanelSpacing.md), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Local, case-insensitive filter over the rows of the page already loaded (name, IP or status). */
internal fun filterLoadedServers(servers: List<Server>, query: String): List<Server> {
    val needle = query.trim()
    if (needle.isEmpty()) return servers
    return servers.filter {
        it.name.contains(needle, ignoreCase = true) || it.ipAddress.contains(needle, ignoreCase = true) ||
            it.status.contains(needle, ignoreCase = true)
    }
}

@Composable
private fun ServerList(
    pageData: ServerPage?, loading: Boolean, error: Throwable?, page: Int, cachedAt: Long?,
    query: String, onQuery: (String) -> Unit,
    rechecks: Map<Long, Recheck>, rechecking: Set<Long>, onRecheck: (Server) -> Unit,
    onPage: (Int) -> Unit, onRefresh: () -> Unit, onSelect: (Server) -> Unit,
    onCreate: () -> Unit, onMonitored: () -> Unit
) {
    // Cached rows are a past observation: opening a server would need live calls we cannot make,
    // and creating one would act on an account state we have not read. Reload stays available.
    val offline = cachedAt != null
    val visible = pageData?.let { filterLoadedServers(it.servers, query) }.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(bottom = PanelSpacing.xl)
    ) {
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
            ) {
                Button(onClick = onCreate, enabled = !offline) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.new_server), Modifier.padding(start = PanelSpacing.sm))
                }
                OutlinedButton(onClick = onMonitored, enabled = !offline) {
                    Icon(Icons.Outlined.MonitorHeart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.monitored_overview), Modifier.padding(start = PanelSpacing.sm))
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.reload), Modifier.padding(start = PanelSpacing.sm))
                }
            }
        }
        if (loading) item { LoadingState() }
        if (error != null) item { ErrorState(error, onRetry = onRefresh, retryEnabled = !loading) }
        if (cachedAt != null) item { OfflineCacheBanner(cachedAt) }
        if (pageData != null) {
            item { ServerPageHero(pageData) }
            if (pageData.servers.isEmpty()) item {
                EmptyState(
                    Icons.Outlined.Dns, stringResource(R.string.servers_empty_title),
                    body = stringResource(R.string.dashboard_empty),
                    actionLabel = stringResource(R.string.new_server), actionEnabled = !offline, onAction = onCreate
                )
            } else item {
                OutlinedTextField(
                    value = query, onValueChange = onQuery,
                    label = { Text(stringResource(R.string.servers_search_label)) },
                    supportingText = { Text(stringResource(R.string.servers_search_scope, pageData.servers.size)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.servers_search_clear))
                            }
                        }
                    } else null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            if (pageData.servers.isNotEmpty() && visible.isEmpty()) item {
                EmptyState(
                    Icons.Outlined.SearchOff, stringResource(R.string.servers_search_empty_title),
                    body = stringResource(R.string.servers_search_empty_body),
                    actionLabel = stringResource(R.string.servers_search_clear), onAction = { onQuery("") }
                )
            }
            items(visible, key = { it.id }) { server ->
                ServerItemCard(server, enabled = !offline,
                    recheck = rechecks[server.id], rechecking = server.id in rechecking,
                    onRecheck = { onRecheck(server) }) { onSelect(server) }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.previous))
                    }
                    Text(
                        stringResource(R.string.servers_page_label, pageData.currentPage.toString(), pageData.lastPage.toString()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(horizontal = PanelSpacing.sm),
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = { onPage(page + 1) }, enabled = pageData.hasNext) {
                        Text(stringResource(R.string.next))
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Shown when no profile is active: explains token storage, lists saved profiles and offers the
 * add-profile form plus configuration import. Forms scroll so the keyboard never hides a field.
 */
@Composable
private fun WelcomeScreen(
    profiles: List<PloiProfile>, deepLinkError: Boolean, profileError: Int,
    draftLabel: String, onDraftLabel: (String) -> Unit,
    draftToken: String, onDraftToken: (String) -> Unit,
    onLock: () -> Unit, onUseProfile: (PloiProfile) -> Unit, onDeleteProfile: (PloiProfile) -> Unit,
    onAddProfile: () -> Unit,
    transferSection: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = PanelSpacing.maxContentWidth)
                .verticalScroll(rememberScrollState()).padding(PanelSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                Icon(painterResource(R.drawable.ic_app), contentDescription = null, tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onLock) {
                    Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.lock_now))
                }
            }
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (deepLinkError) InlineNotice(stringResource(R.string.widget_route_unavailable))
            if (profiles.isNotEmpty()) {
                SectionHeader(stringResource(R.string.profiles))
                profiles.forEach { profile ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                            IconBadge(Icons.Outlined.Person)
                            Text(profile.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                            Button(onClick = { onUseProfile(profile) }) { Text(stringResource(R.string.use_profile)) }
                            OutlinedButton(onClick = { onDeleteProfile(profile) }) { Text(stringResource(R.string.delete_profile)) }
                        }
                    }
                }
            }
            SectionCard(title = stringResource(R.string.welcome_new_profile), icon = Icons.Outlined.PersonAdd) {
                OutlinedTextField(
                    value = draftLabel, onValueChange = onDraftLabel,
                    label = { Text(stringResource(R.string.profile_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftToken, onValueChange = onDraftToken,
                    label = { Text(stringResource(R.string.token)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (profileError != 0) ApiLikeError(stringResource(profileError))
                Button(onClick = onAddProfile, enabled = draftLabel.isNotBlank() && draftToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_profile)) }
            }
            SectionHeader(stringResource(R.string.config_transfer_title))
            transferSection()
        }
    }
}

@Composable
private fun ApiLikeError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

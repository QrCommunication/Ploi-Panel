package com.qrcommunication.ploipanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF82DAC3)) else lightColorScheme(primary = accent)
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val activity = context as FragmentActivity
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
                else -> PanelHome(lock, activity, onLock = { unlocked = false })
            }
        }
    }
}

/** Authenticated content. Leaves composition entirely while locked, dropping in-memory tokens. */
@Composable
private fun PanelHome(lock: AppLock, activity: FragmentActivity, onLock: () -> Unit) {
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
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onLock) { Text(stringResource(R.string.lock_now)) }
            }
            Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)
            if (profiles.isNotEmpty()) {
                Text(stringResource(R.string.profiles), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                profiles.forEach { profile ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(profile.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    store.remove(profile.id)
                                    profilesVersion++
                                }) { Text(stringResource(R.string.delete_profile)) }
                                Button(onClick = {
                                    val profileToken = store.tokenFor(profile.id)
                                    if (profileToken != null) {
                                        store.activate(profile.id)
                                        activeProfile = profile to profileToken
                                    } else {
                                        store.remove(profile.id)
                                        profilesVersion++
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
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        when (panelTab) {
                            1 -> stringResource(R.string.providers)
                            2 -> stringResource(R.string.account)
                            else -> stringResource(R.string.servers)
                        },
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.current_profile, activeProfile?.first?.label.orEmpty()), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(12.dp))
            BiometricToggle(lock, activity)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { panelTab = 0 }, enabled = panelTab != 0) { Text(stringResource(R.string.servers)) }
                OutlinedButton(onClick = { panelTab = 1 }, enabled = panelTab != 1) { Text(stringResource(R.string.providers)) }
                OutlinedButton(onClick = { panelTab = 2 }, enabled = panelTab != 2) { Text(stringResource(R.string.account)) }
                OutlinedButton(onClick = { panelTab = 3 }, enabled = panelTab != 3) { Text(stringResource(R.string.scripts_tab)) }
            }
            Spacer(Modifier.height(12.dp))
            when (panelTab) {
                1 -> ProvidersScreen(token)
                2 -> AccountScreen(token)
                3 -> ScriptsScreen(token, lock, activity)
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
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

/**
 * Opt-in biometric shortcut. Enabling asks the system prompt to confirm identity first;
 * disabling is immediate. Hidden entirely when no strong biometric hardware is present.
 */
@Composable
private fun BiometricToggle(lock: AppLock, activity: FragmentActivity) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

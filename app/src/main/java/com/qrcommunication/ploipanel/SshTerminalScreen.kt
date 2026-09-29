package com.qrcommunication.ploipanel

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.qrcommunication.ploipanel.ssh.CellStyle
import com.qrcommunication.ploipanel.ssh.JSchHostKeyTransport
import com.qrcommunication.ploipanel.ssh.JSchShellConnector
import com.qrcommunication.ploipanel.ssh.SshBookmark
import com.qrcommunication.ploipanel.ssh.SshBookmarkStore
import com.qrcommunication.ploipanel.ssh.SshConnectException
import com.qrcommunication.ploipanel.ssh.SshConnectRequest
import com.qrcommunication.ploipanel.ssh.SshCredential
import com.qrcommunication.ploipanel.ssh.SshHostTrustStore
import com.qrcommunication.ploipanel.ssh.SshKeyEntry
import com.qrcommunication.ploipanel.ssh.SshKeyVault
import com.qrcommunication.ploipanel.ssh.TermCell
import com.qrcommunication.ploipanel.ssh.TerminalInput
import com.qrcommunication.ploipanel.ssh.TerminalKey
import com.qrcommunication.ploipanel.ssh.TerminalPalette
import com.qrcommunication.ploipanel.ssh.TerminalSession
import com.qrcommunication.ploipanel.ssh.TerminalSessions
import com.qrcommunication.ploipanel.ssh.TerminalSnapshot
import com.qrcommunication.ploipanel.ssh.TerminalState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Destination prefilled from a Ploi server screen. */
internal data class SshTarget(val host: String, val port: Int, val serverId: Long?, val serverName: String)

/** Lets any screen ask the panel to open the terminal tab with a prefilled destination. */
internal object TerminalNavigator {
    private val pending = MutableStateFlow<SshTarget?>(null)
    val requests: StateFlow<SshTarget?> = pending.asStateFlow()
    fun open(target: SshTarget) { pending.value = target }
    fun consume(): SshTarget? = pending.value.also { pending.value = null }
}

internal const val SSH_VAULT_KEYSTORE_ALIAS = "ploi-panel.ssh-keys"
internal const val SSH_DEFAULT_PORT = 22
private const val MIN_FONT_SP = 8
private const val MAX_FONT_SP = 22
private const val FONT_PREF = "ssh.terminal.font_sp"

private fun connectErrorMessage(failure: Throwable): Int = when (failure) {
    is SshConnectException.UnknownHost -> R.string.ssh_term_error_unknown_host
    is SshConnectException.HostKeyMismatch -> R.string.ssh_term_error_mismatch
    is SshConnectException.AuthenticationFailed -> R.string.ssh_term_error_auth
    is SshConnectException.WrongPassphrase -> R.string.ssh_term_error_passphrase
    is SshConnectException.Unreachable -> R.string.ssh_term_error_unreachable
    is IllegalArgumentException -> R.string.ssh_term_error_invalid
    else -> R.string.ssh_term_error_protocol
}

/**
 * Terminal tab of the panel: open sessions of the active profile as tabs, saved destinations,
 * and a connection form. Sessions are process-wide ([TerminalSessions]) so locking the app or
 * switching tabs never drops a running command; they are closed on explicit disconnect or when
 * the profile is removed.
 */
@Composable
internal fun SshTerminalScreen(profileId: String, token: String, lock: AppLock, activity: FragmentActivity) {
    val context = LocalContext.current
    val prefs = remember(context) { SharedPreferencesProfilePrefs(context) }
    val bookmarks = remember(profileId) { SshBookmarkStore(prefs, profileId) }
    val all by TerminalSessions.all.collectAsState()
    val sessions = all.filter { it.profileId == profileId }
    var activeId by remember(profileId) { mutableStateOf(sessions.lastOrNull()?.id) }
    var connectTarget by remember { mutableStateOf<SshTarget?>(null) }
    var connectBookmark by remember { mutableStateOf<SshBookmark?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var bookmarksVersion by remember { mutableIntStateOf(0) }
    val saved = remember(profileId, bookmarksVersion) { bookmarks.list() }
    val pending by TerminalNavigator.requests.collectAsState()

    LaunchedEffect(pending) {
        TerminalNavigator.consume()?.let { target ->
            connectTarget = target
            connectBookmark = null
            connecting = true
        }
    }
    val active = sessions.firstOrNull { it.id == activeId } ?: sessions.lastOrNull()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(sessions, key = { it.id }) { session ->
                val state by session.state.collectAsState()
                FilterChip(
                    selected = active?.id == session.id,
                    onClick = { activeId = session.id },
                    label = {
                        Text(
                            if (state is TerminalState.Connected) session.label
                            else stringResource(R.string.ssh_term_tab_closed, session.label)
                        )
                    }
                )
            }
            item {
                OutlinedButton(onClick = { connectTarget = null; connectBookmark = null; connecting = true }) {
                    Text(stringResource(R.string.ssh_term_new))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (active == null) {
                SavedDestinations(
                    saved,
                    onOpen = { connectBookmark = it; connectTarget = null; connecting = true },
                    onRemove = { bookmarks.remove(it.id); bookmarksVersion++ }
                )
            } else {
                TerminalPane(active, onClose = {
                    TerminalSessions.remove(active.id)
                    activeId = null
                })
            }
        }
    }

    if (connecting) SshConnectDialog(
        profileId = profileId,
        token = token,
        target = connectTarget,
        bookmark = connectBookmark,
        lock = lock,
        activity = activity,
        onDismiss = { connecting = false },
        onConnected = { session, request, keyId, serverId ->
            TerminalSessions.add(session)
            activeId = session.id
            connecting = false
            runCatching {
                bookmarks.remember(session.label, request.host, request.port, request.username, keyId, serverId)
            }
            bookmarksVersion++
        }
    )
}

@Composable
private fun SavedDestinations(saved: List<SshBookmark>, onOpen: (SshBookmark) -> Unit, onRemove: (SshBookmark) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ssh_term_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ssh_term_saved), style = MaterialTheme.typography.titleMedium)
        if (saved.isEmpty()) Text(stringResource(R.string.ssh_term_saved_empty), style = MaterialTheme.typography.bodySmall)
        saved.forEach { bookmark ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(bookmark.label, style = MaterialTheme.typography.titleSmall)
                    Text("${bookmark.username}@${bookmark.host}:${bookmark.port}", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(if (bookmark.keyId != null) R.string.ssh_term_auth_key else R.string.ssh_term_auth_password),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(bookmark) }) { Text(stringResource(R.string.ssh_term_connect)) }
                        OutlinedButton(onClick = { onRemove(bookmark) }) { Text(stringResource(R.string.ssh_term_forget)) }
                    }
                }
            }
        }
    }
}

/**
 * Connection form. Host keys are verified against the profile's trust store *before*
 * authentication; an unknown or changed key opens the probe dialog (the only place allowed to
 * pin), never a silent bypass. Passwords and passphrases live only in this dialog's state and are
 * wiped once the attempt ends.
 */
@Composable
private fun SshConnectDialog(
    profileId: String,
    token: String,
    target: SshTarget?,
    bookmark: SshBookmark?,
    lock: AppLock,
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onConnected: (TerminalSession, SshConnectRequest, String?, Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { SharedPreferencesProfilePrefs(context) }
    val vault = remember(context) { SshKeyVault(prefs, KeystoreTokenCipher(SSH_VAULT_KEYSTORE_ALIAS)) }
    val trustStore = remember(profileId) { SshHostTrustStore(prefs, profileId) }
    var keys by remember { mutableStateOf<List<SshKeyEntry>>(emptyList()) }
    var keysVersion by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf(bookmark?.host ?: target?.host.orEmpty()) }
    var port by remember { mutableStateOf((bookmark?.port ?: target?.port ?: SshHostTrustStore.DEFAULT_PORT).toString()) }
    var username by remember { mutableStateOf(bookmark?.username ?: "ploi") }
    var label by remember { mutableStateOf(bookmark?.label ?: target?.serverName.orEmpty()) }
    var keyId by remember { mutableStateOf(bookmark?.keyId) }
    var usePassword by remember { mutableStateOf(bookmark != null && bookmark.keyId == null) }
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableIntStateOf(0) }
    var probing by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf<SshKeyEntry?>(null) }
    val serverId = bookmark?.serverId ?: target?.serverId

    LaunchedEffect(keysVersion) {
        keys = withContext(Dispatchers.IO) { runCatching { vault.list(profileId) }.getOrDefault(emptyList()) }
        if (keyId == null && !usePassword) keyId = keys.firstOrNull()?.id
        if (keys.isEmpty()) usePassword = true
    }
    val selectedKey = keys.firstOrNull { it.id == keyId }

    fun connect() {
        error = 0
        feedback = 0
        val targetPort = port.trim().toIntOrNull()
        val request = try {
            SshConnectRequest(host.trim(), targetPort ?: -1, username.trim(), 80, 24)
        } catch (invalid: IllegalArgumentException) {
            error = R.string.ssh_term_error_invalid
            return
        }
        val credential: SshCredential = if (usePassword || selectedKey == null) {
            SshCredential.Password(password.toCharArray())
        } else {
            SshCredential.PrivateKey(selectedKey.id, passphrase.takeIf { it.isNotEmpty() }?.toCharArray())
        }
        busy = true
        scope.launch {
            try {
                val shell = withContext(Dispatchers.IO) {
                    JSchShellConnector(trustStore, { id -> vault.privateKeyPem(profileId, id) }).open(request, credential)
                }
                val session = TerminalSession(
                    label.trim().ifEmpty { "${request.username}@${request.host}" }.take(SshBookmarkStore.MAX_LABEL_LENGTH),
                    profileId, shell, request.columns, request.rows
                )
                password = ""
                passphrase = ""
                onConnected(session, request, if (credential is SshCredential.PrivateKey) credential.keyId else null, serverId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = connectErrorMessage(failure)
            } finally {
                when (credential) {
                    is SshCredential.Password -> credential.password.fill('\u0000')
                    is SshCredential.PrivateKey -> credential.passphrase?.fill('\u0000')
                }
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ssh_term_connect_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.ssh_term_label)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it.trim() }, label = { Text(stringResource(R.string.ssh_probe_host)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(username, { username = it.trim() }, label = { Text(stringResource(R.string.ssh_term_user)) },
                        singleLine = true, enabled = !busy, modifier = Modifier.weight(2f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false))
                    OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.ssh_probe_port)) },
                        singleLine = true, enabled = !busy, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text(stringResource(R.string.ssh_term_auth), style = MaterialTheme.typography.titleSmall)
                keys.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !usePassword && keyId == entry.id, enabled = !busy,
                            onClick = { usePassword = false; keyId = entry.id })
                        Text("${entry.label} · ${entry.keyType}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = usePassword || keys.isEmpty(), enabled = !busy, onClick = { usePassword = true })
                    Text(stringResource(R.string.ssh_term_auth_password))
                }
                if (usePassword || selectedKey == null) {
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.ssh_term_password)) },
                        singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                } else if (selectedKey.passphraseProtected) {
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text(stringResource(R.string.ssh_term_passphrase)) },
                        singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { generating = true }, enabled = !busy) { Text(stringResource(R.string.ssh_term_generate)) }
                    if (serverId != null && selectedKey != null && !usePassword) {
                        OutlinedButton(onClick = { installing = selectedKey }, enabled = !busy) {
                            Text(stringResource(R.string.ssh_term_install_key))
                        }
                    }
                    OutlinedButton(onClick = { probing = true }, enabled = !busy && host.isNotBlank()) {
                        Text(stringResource(R.string.ssh_probe_title))
                    }
                }
                if (feedback != 0) Text(stringResource(feedback), color = MaterialTheme.colorScheme.primary)
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                if (busy) BusyIndicator()
                Text(stringResource(R.string.ssh_term_security_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { connect() }, enabled = !busy && host.isNotBlank() && username.isNotBlank() && port.isNotBlank()) {
                Text(stringResource(R.string.ssh_term_connect))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )

    if (probing) SshHostProbeDialog(
        trustStore = trustStore,
        transport = remember { JSchHostKeyTransport() },
        lock = lock,
        activity = activity,
        onDismiss = { probing = false },
        onPinnedChanged = { error = 0 },
        initialHost = host.trim(),
        initialPort = port.trim().toIntOrNull() ?: SshHostTrustStore.DEFAULT_PORT
    )
    if (generating) GenerateKeyDialog(
        onDismiss = { generating = false },
        onGenerate = { name ->
            val generated = com.qrcommunication.ploipanel.ssh.SshKeyMaterial.generateEd25519()
            vault.import(profileId, name, generated.keyType, generated.privatePem, generated.publicKeyBase64)
        },
        onGenerated = { entry ->
            generating = false
            keyId = entry.id
            usePassword = false
            feedback = R.string.ssh_term_generated
            keysVersion++
        }
    )
    installing?.let { entry ->
        InstallKeyDialog(
            token = token, serverId = serverId ?: return@let, key = entry, lock = lock, activity = activity,
            onDismiss = { installing = null },
            onInstalled = { installing = null; feedback = R.string.ssh_term_installed }
        )
    }
}

/** Generates an Ed25519 key on the phone; the private key never leaves the encrypted vault. */
@Composable
private fun GenerateKeyDialog(onDismiss: () -> Unit, onGenerate: (String) -> SshKeyEntry, onGenerated: (SshKeyEntry) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("ploi-panel-" + Build.MODEL.filter { it.isLetterOrDigit() }.take(20).lowercase()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ssh_term_generate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ssh_term_generate_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ssh_key_label)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                if (busy) BusyIndicator()
            }
        },
        confirmButton = {
            Button(onClick = {
                busy = true
                error = 0
                scope.launch {
                    try {
                        val entry = withContext(Dispatchers.Default) { onGenerate(name.trim()) }
                        onGenerated(entry)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = when (failure.message) {
                            "Duplicate key label" -> R.string.ssh_error_duplicate_label
                            "Too many SSH keys" -> R.string.ssh_error_limit
                            "Invalid key label" -> R.string.ssh_error_invalid_label
                            else -> R.string.ssh_error_vault
                        }
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy && name.isNotBlank()) { Text(stringResource(R.string.ssh_term_generate)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Authorizes a vault public key on a Ploi server through the documented
 * POST /servers/{server}/ssh-keys, behind a fresh PIN/biometric gate (it grants shell access).
 */
@Composable
private fun InstallKeyDialog(
    token: String, serverId: Long, key: SshKeyEntry, lock: AppLock, activity: FragmentActivity,
    onDismiss: () -> Unit, onInstalled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var systemUser by remember { mutableStateOf("ploi") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ssh_term_install_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ssh_term_install_hint, key.label), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(systemUser, { systemUser = it.trim() }, label = { Text(stringResource(R.string.ssh_term_system_user)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                error?.let { ApiErrorText(it) }
                if (busy) BusyIndicator()
            }
        },
        confirmButton = {
            Button(onClick = { confirm = true }, enabled = !busy && SshConnectRequest.isValidUsername(systemUser)) {
                Text(stringResource(R.string.ssh_term_install_key))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
    if (confirm) SensitiveConfirmDialog(
        lock, activity,
        stringResource(R.string.ssh_term_install_confirm, key.label, systemUser),
        R.string.ssh_term_install_key,
        onConfirmed = {
            confirm = false
            busy = true
            error = null
            scope.launch {
                try {
                    val line = com.qrcommunication.ploipanel.ssh.SshKeyMaterial.authorizedKeyLine(
                        key.keyType, key.publicKeyBase64, "ploi-panel"
                    )
                    withContext(Dispatchers.IO) {
                        PloiApi.createSshKey(token, serverId, CreateSshKeyRequest(key.label, line, systemUser))
                    }
                    onInstalled()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure
                } finally {
                    busy = false
                }
            }
        },
        onDismiss = { confirm = false }
    )
}

/** Extra keys missing from phone keyboards; Ctrl/Alt are sticky for the next key only. */
private val EXTRA_KEYS = listOf(
    "Esc" to TerminalKey.ESCAPE, "Tab" to TerminalKey.TAB, "↑" to TerminalKey.UP, "↓" to TerminalKey.DOWN,
    "←" to TerminalKey.LEFT, "→" to TerminalKey.RIGHT, "Home" to TerminalKey.HOME, "End" to TerminalKey.END,
    "PgUp" to TerminalKey.PAGE_UP, "PgDn" to TerminalKey.PAGE_DOWN, "Del" to TerminalKey.DELETE,
    "F1" to TerminalKey.F1, "F2" to TerminalKey.F2, "F3" to TerminalKey.F3, "F4" to TerminalKey.F4,
    "F5" to TerminalKey.F5, "F6" to TerminalKey.F6, "F7" to TerminalKey.F7, "F8" to TerminalKey.F8,
    "F9" to TerminalKey.F9, "F10" to TerminalKey.F10, "F11" to TerminalKey.F11, "F12" to TerminalKey.F12,
)
private val EXTRA_SYMBOLS = listOf("|", "-", "/", "~", "_", "*", "&", ">", "<", ":", "\"", "'", "`", "$")

/** Keeps a hidden two-character buffer so the IME always has something to delete (backspace). */
private const val IME_SENTINEL = "  "

@Composable
private fun TerminalPane(session: TerminalSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { SharedPreferencesProfilePrefs(context) }
    val snapshot by session.snapshot.collectAsState()
    val state by session.state.collectAsState()
    var fontSp by remember { mutableIntStateOf(prefs.read(FONT_PREF)?.toIntOrNull()?.coerceIn(MIN_FONT_SP, MAX_FONT_SP) ?: 12) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var ime by remember { mutableStateOf(TextFieldValue(IME_SENTINEL, TextRange(IME_SENTINEL.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val connected = state is TerminalState.Connected

    fun sendText(text: String) {
        if (text.isEmpty() || !connected) return
        session.send(TerminalInput.text(text, ctrl = ctrl, alt = alt))
        ctrl = false
        alt = false
    }

    fun sendKey(key: TerminalKey) {
        if (!connected) return
        session.send(TerminalInput.key(key, session.applicationCursorKeys(), alt = alt))
        ctrl = false
        alt = false
    }

    val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSp.sp, lineHeight = (fontSp * 1.25f).sp)
    val cell = remember(fontSp) { measurer.measure("M", textStyle).size }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                snapshot.title.ifBlank { session.label },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            TextButton(onClick = { fontSp = (fontSp - 1).coerceAtLeast(MIN_FONT_SP); prefs.write(FONT_PREF, fontSp.toString()) }) { Text("A−") }
            TextButton(onClick = { fontSp = (fontSp + 1).coerceAtMost(MAX_FONT_SP); prefs.write(FONT_PREF, fontSp.toString()) }) { Text("A+") }
            TextButton(onClick = {
                clipboardText(context)?.let { session.send(TerminalInput.paste(it, session.bracketedPaste())) }
            }, enabled = connected) { Text(stringResource(R.string.ssh_term_paste)) }
            TextButton(onClick = { copySensitive(context, session.transcript()) }) { Text(stringResource(R.string.ssh_term_copy)) }
            TextButton(onClick = onClose) {
                Text(stringResource(if (connected) R.string.ssh_term_disconnect else R.string.ssh_close))
            }
        }
        if (!connected) {
            val closed = state as TerminalState.Closed
            Text(
                stringResource(if (closed.byUser) R.string.ssh_term_closed_user else R.string.ssh_term_closed_remote),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
            )
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().background(Color(TerminalPalette.DEFAULT_BACKGROUND))
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val lineHeightPx = with(density) { (fontSp * 1.25f).sp.toPx() }
            val columns = (widthPx / cell.width.coerceAtLeast(1)).toInt().coerceAtLeast(20)
            val rows = (heightPx / lineHeightPx.coerceAtLeast(1f)).toInt().coerceAtLeast(5)
            LaunchedEffect(columns, rows) { session.resize(columns, rows) }
            val lines = snapshot.scrollback.size + snapshot.rows.size
            LaunchedEffect(snapshot) {
                // Follow output unless the operator scrolled back into history.
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lines > 0 && lastVisible >= lines - snapshot.rows.size - 2) listState.scrollToItem(lines - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
                    .semantics { contentDescription = session.label }
            ) {
                items(snapshot.scrollback.size) { index ->
                    TerminalLine(snapshot.scrollback[index], textStyle, cursorCol = -1)
                }
                items(snapshot.rows.size) { index ->
                    val cursor = if (snapshot.cursorVisible && index == snapshot.cursorRow && connected) snapshot.cursorCol else -1
                    TerminalLine(snapshot.rows[index], textStyle, cursor)
                }
            }
            // Invisible IME sink: receives soft-keyboard text and hardware keys.
            BasicTextField(
                value = ime,
                onValueChange = { next ->
                    val text = next.text
                    when {
                        text.startsWith(IME_SENTINEL) -> sendText(text.removePrefix(IME_SENTINEL))
                        text.length < IME_SENTINEL.length ->
                            repeat(IME_SENTINEL.length - text.length) { sendKey(TerminalKey.BACKSPACE) }
                        else -> sendText(text.trim())
                    }
                    ime = TextFieldValue(IME_SENTINEL, TextRange(IME_SENTINEL.length))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii
                ),
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                modifier = Modifier.size(1.dp).focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val special = when (event.key) {
                        Key.DirectionUp -> TerminalKey.UP
                        Key.DirectionDown -> TerminalKey.DOWN
                        Key.DirectionLeft -> TerminalKey.LEFT
                        Key.DirectionRight -> TerminalKey.RIGHT
                        Key.MoveHome -> TerminalKey.HOME
                        Key.MoveEnd -> TerminalKey.END
                        Key.PageUp -> TerminalKey.PAGE_UP
                        Key.PageDown -> TerminalKey.PAGE_DOWN
                        Key.Escape -> TerminalKey.ESCAPE
                        Key.Tab -> if (event.isShiftPressed) TerminalKey.BACK_TAB else TerminalKey.TAB
                        Key.Enter, Key.NumPadEnter -> TerminalKey.ENTER
                        Key.Backspace -> TerminalKey.BACKSPACE
                        Key.Delete -> TerminalKey.DELETE
                        Key.Insert -> TerminalKey.INSERT
                        Key.F1 -> TerminalKey.F1
                        Key.F2 -> TerminalKey.F2
                        Key.F3 -> TerminalKey.F3
                        Key.F4 -> TerminalKey.F4
                        Key.F5 -> TerminalKey.F5
                        Key.F6 -> TerminalKey.F6
                        Key.F7 -> TerminalKey.F7
                        Key.F8 -> TerminalKey.F8
                        Key.F9 -> TerminalKey.F9
                        Key.F10 -> TerminalKey.F10
                        Key.F11 -> TerminalKey.F11
                        Key.F12 -> TerminalKey.F12
                        else -> null
                    }
                    if (special != null) {
                        if (event.isAltPressed) alt = true
                        sendKey(special)
                        return@onPreviewKeyEvent true
                    }
                    if (event.isCtrlPressed || event.isAltPressed) {
                        val code = event.utf16CodePoint
                        if (code > 0) {
                            ctrl = event.isCtrlPressed
                            alt = event.isAltPressed
                            sendText(String(Character.toChars(code)))
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                FilterChip(selected = ctrl, onClick = { ctrl = !ctrl }, label = { Text("Ctrl") }, enabled = connected)
            }
            item {
                FilterChip(selected = alt, onClick = { alt = !alt }, label = { Text("Alt") }, enabled = connected)
            }
            items(EXTRA_KEYS) { (label, key) ->
                OutlinedButton(onClick = { sendKey(key) }, enabled = connected,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text(label) }
            }
            items(EXTRA_SYMBOLS) { symbol ->
                OutlinedButton(onClick = { sendText(symbol) }, enabled = connected,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text(symbol) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                focus.requestFocus()
                keyboard?.show()
            }, enabled = connected) { Text(stringResource(R.string.ssh_term_keyboard)) }
            OutlinedButton(onClick = { ctrl = true; sendText("c") }, enabled = connected) { Text("Ctrl+C") }
            OutlinedButton(onClick = { ctrl = true; sendText("d") }, enabled = connected) { Text("Ctrl+D") }
            OutlinedButton(onClick = { ctrl = true; sendText("z") }, enabled = connected) { Text("Ctrl+Z") }
            OutlinedButton(onClick = { ctrl = true; sendText("l") }, enabled = connected) { Text("Ctrl+L") }
        }
    }
    LaunchedEffect(session.id) { runCatching { focus.requestFocus() } }
}

@Composable
private fun TerminalLine(cells: List<TermCell>, style: TextStyle, cursorCol: Int) {
    Text(
        text = remember(cells, cursorCol) { annotate(cells, cursorCol) },
        style = style,
        softWrap = false,
        maxLines = 1,
        color = Color(TerminalPalette.DEFAULT_FOREGROUND)
    )
}

/** Builds one row, merging runs of identical style so long lines stay cheap to lay out. */
private fun annotate(cells: List<TermCell>, cursorCol: Int): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < cells.size) {
        val cellStyle = cells[index].style
        val atCursor = index == cursorCol
        val run = StringBuilder()
        var end = index
        while (end < cells.size && cells[end].style == cellStyle && (end == cursorCol) == atCursor &&
            (!atCursor || end == index)) {
            run.append(cells[end].ch)
            end++
        }
        withStyle(spanOf(if (atCursor) cellStyle.copy(inverse = !cellStyle.inverse) else cellStyle)) { append(run.toString()) }
        index = end
    }
}

private fun spanOf(style: CellStyle): SpanStyle {
    val (fg, bg) = TerminalPalette.resolve(style)
    return SpanStyle(
        color = Color(fg),
        background = if (bg == TerminalPalette.DEFAULT_BACKGROUND) Color.Unspecified else Color(bg),
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = if (style.underline) TextDecoration.Underline else null,
    )
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.take(64 * 1024)
}

/** Terminal output may contain secrets: flag the clip so Android hides it from previews (API 33+). */
private fun copySensitive(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("terminal", text.takeLast(256 * 1024))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
}

package com.qrcommunication.ploipanel

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

/** Local preferences and encrypted Ploi profile management; no token is ever rendered in clear. */
@Composable
internal fun SettingsScreen(
    store: ProfileStore, profiles: List<PloiProfile>, active: PloiProfile?,
    lock: AppLock, activity: FragmentActivity,
    theme: AppTheme, language: AppLanguage,
    onThemeChanged: (AppTheme) -> Unit, onLanguageChanged: (AppLanguage) -> Unit,
    onProfilesChanged: () -> Unit, onActiveChanged: (PloiProfile?) -> Unit,
    configurationManager: PortableConfigurationManager, importUri: Uri?,
    onImportUriConsumed: () -> Unit, onChooseImport: () -> Unit,
    onSaveEncryptedArchive: (ByteArray) -> Unit, transferStatus: Int,
    onImported: () -> Unit,
    onLock: () -> Unit
) {
    var addProfile by remember { mutableStateOf(false) }
    var renameProfile by remember { mutableStateOf<PloiProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<PloiProfile?>(null) }
    var changePin by remember { mutableStateOf(false) }
    var profileError by remember { mutableIntStateOf(0) }
    var pinChanged by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsSectionHeading(R.string.settings_profiles)
        if (profileError != 0) Text(stringResource(profileError), color = MaterialTheme.colorScheme.error)
        profiles.forEach { profile ->
            val isActive = profile.id == active?.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.label, style = MaterialTheme.typography.titleMedium)
                    if (isActive) Text(
                        stringResource(R.string.settings_profile_active), color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (profile.id != active?.id) OutlinedButton(onClick = {
                            val token = store.tokenFor(profile.id)
                            if (token == null) profileError = R.string.settings_profile_token_invalid
                            else {
                                store.activate(profile.id)
                                profileError = 0
                                onActiveChanged(profile)
                            }
                        }) { Text(stringResource(R.string.settings_profile_switch)) }
                        OutlinedButton(onClick = { renameProfile = profile }) {
                            Text(stringResource(R.string.settings_profile_rename))
                        }
                        OutlinedButton(onClick = { deleteProfile = profile }) {
                            Text(stringResource(R.string.settings_profile_delete))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { addProfile = true }, enabled = profiles.size < ProfileStore.MAX_PROFILES) {
            Text(stringResource(R.string.settings_profile_add))
        }

        SettingsSectionHeading(R.string.settings_appearance)
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AppTheme.SYSTEM, AppTheme.LIGHT, AppTheme.DARK).forEach { option ->
                val label = when (option) {
                    AppTheme.SYSTEM -> R.string.settings_theme_system
                    AppTheme.LIGHT -> R.string.settings_theme_light
                    AppTheme.DARK -> R.string.settings_theme_dark
                }
                FilterChip(selected = theme == option, onClick = { onThemeChanged(option) }, label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AppLanguage.SYSTEM, AppLanguage.FRENCH, AppLanguage.ENGLISH).forEach { option ->
                val label = when (option) {
                    AppLanguage.SYSTEM -> R.string.settings_language_system
                    AppLanguage.FRENCH -> R.string.settings_language_french
                    AppLanguage.ENGLISH -> R.string.settings_language_english
                }
                FilterChip(selected = language == option, onClick = { onLanguageChanged(option) }, label = { Text(stringResource(label)) })
            }
        }

        SettingsSectionHeading(R.string.settings_widgets)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.settings_widgets_hint),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        SettingsSectionHeading(R.string.config_transfer_title)
        ConfigurationTransferSection(
            configurationManager, lock, activity, importUri, onImportUriConsumed,
            onChooseImport, onSaveEncryptedArchive, transferStatus, onImported
        )

        SettingsSectionHeading(R.string.settings_security)
        BiometricToggle(lock, activity)
        Text(stringResource(R.string.settings_biometric_hint), style = MaterialTheme.typography.bodySmall)
        if (pinChanged) Text(stringResource(R.string.settings_pin_changed), color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { changePin = true }) { Text(stringResource(R.string.settings_change_pin)) }
            OutlinedButton(onClick = onLock) { Text(stringResource(R.string.lock_now)) }
        }

        SettingsSectionHeading(R.string.settings_about)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (addProfile) ProfileEditorDialog(
        title = R.string.settings_profile_add, initialLabel = "", isNew = true,
        onDismiss = { addProfile = false },
        onSubmit = { label, token ->
            val profile = store.add(label, token)
            store.activate(profile.id)
            onProfilesChanged()
            onActiveChanged(profile)
            addProfile = false
        }
    )
    renameProfile?.let { profile ->
        ProfileEditorDialog(
            title = R.string.settings_profile_rename, initialLabel = profile.label, isNew = false,
            onDismiss = { renameProfile = null },
            onSubmit = { label, _ ->
                val renamed = store.rename(profile.id, label)
                onProfilesChanged()
                if (profile.id == active?.id) onActiveChanged(renamed)
                renameProfile = null
            }
        )
    }
    deleteProfile?.let { profile ->
        SensitiveConfirmDialog(
            lock, activity,
            stringResource(R.string.settings_profile_delete_confirm, profile.label),
            R.string.settings_profile_delete,
            onConfirmed = {
                store.remove(profile.id)
                deleteProfile = null
                onProfilesChanged()
                if (profile.id == active?.id) onActiveChanged(null)
            },
            onDismiss = { deleteProfile = null }
        )
    }
    if (changePin) ChangePinDialog(lock, onDismiss = { changePin = false }) {
        changePin = false
        pinChanged = true
    }
}

@Composable
internal fun SettingsSectionHeading(title: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProfileEditorDialog(
    title: Int, initialLabel: String, isNew: Boolean,
    onDismiss: () -> Unit, onSubmit: (String, String) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.profile_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (isNew) OutlinedTextField(token, onValueChange = { token = it },
                    label = { Text(stringResource(R.string.token)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                try { onSubmit(label, token.trim()) }
                catch (invalid: IllegalArgumentException) {
                    error = when (invalid.message) {
                        "Duplicate profile label" -> R.string.profile_error_duplicate
                        "Too many profiles" -> R.string.profile_error_limit
                        else -> R.string.profile_error_invalid
                    }
                }
            }, enabled = label.isNotBlank() && (!isNew || token.isNotBlank())) {
                Text(stringResource(if (isNew) R.string.add_profile else R.string.settings_profile_rename))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ChangePinDialog(lock: AppLock, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableIntStateOf(0) }
    var attemptsLeft by remember { mutableIntStateOf(-1) }
    var lockedMs by remember { mutableLongStateOf(lock.remainingLockMs()) }
    LaunchedEffect(lockedMs > 0) {
        while (lock.remainingLockMs() > 0) {
            lockedMs = lock.remainingLockMs()
            delay(1_000)
        }
        lockedMs = 0
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_change_pin)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    R.string.settings_current_pin to current,
                    R.string.settings_new_pin to next,
                    R.string.pin_confirm_label to confirm
                ).forEachIndexed { index, (label, value) ->
                    OutlinedTextField(
                        value = value, onValueChange = { input ->
                            val clean = input.filter(Char::isDigit)
                            when (index) {
                                0 -> current = clean
                                1 -> next = clean
                                else -> confirm = clean
                            }
                        }, label = { Text(stringResource(label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                if (lockedMs > 0) Text(
                    stringResource(R.string.locked_wait, (lockedMs + 999) / 1_000),
                    color = MaterialTheme.colorScheme.error
                )
                if (attemptsLeft >= 0) Text(
                    pluralStringResource(R.plurals.wrong_pin, attemptsLeft, attemptsLeft),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                error = 0
                if (next != confirm) { error = R.string.pin_mismatch; return@Button }
                try { lock.validatePin(next) }
                catch (invalid: IllegalArgumentException) { error = R.string.pin_invalid; return@Button }
                when (val result = lock.verify(current)) {
                    is AppLock.UnlockResult.Unlocked -> {
                        lock.setPin(next)
                        current = ""; next = ""; confirm = ""
                        onChanged()
                    }
                    is AppLock.UnlockResult.WrongPin -> {
                        attemptsLeft = result.attemptsLeft
                        current = ""
                    }
                    is AppLock.UnlockResult.Locked -> {
                        lockedMs = result.remainingMs
                        current = ""
                    }
                }
            }, enabled = current.isNotBlank() && next.isNotBlank() && confirm.isNotBlank() && lockedMs <= 0 && !lock.isLocked()) {
                Text(stringResource(R.string.settings_change_pin))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

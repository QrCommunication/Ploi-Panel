package com.qrcommunication.ploipanel

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.fillMaxSize().widthIn(max = PanelSpacing.maxContentWidth)
            .verticalScroll(rememberScrollState()).padding(vertical = PanelSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.lg)
    ) {
        SectionCard(
            title = stringResource(R.string.settings_profiles),
            description = stringResource(R.string.settings_profiles_description),
            icon = Icons.Outlined.ManageAccounts
        ) {
            if (profileError != 0) ApiLikeMessage(stringResource(profileError))
            profiles.forEach { profile ->
                val isActive = profile.id == active?.id
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(
                        1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(PanelSpacing.md), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                            IconBadge(Icons.Outlined.Person, size = 36)
                            Column(Modifier.weight(1f)) {
                                Text(profile.label, style = MaterialTheme.typography.titleMedium)
                                if (isActive) Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.settings_profile_active), color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)
                        ) {
                            if (profile.id != active?.id) FilledTonalButton(onClick = {
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
                            OutlinedButton(
                                onClick = { deleteProfile = profile },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.settings_profile_delete))
                            }
                        }
                    }
                }
            }
            Button(onClick = { addProfile = true }, enabled = profiles.size < ProfileStore.MAX_PROFILES) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.settings_profile_add), Modifier.padding(start = PanelSpacing.sm))
            }
        }

        SectionCard(title = stringResource(R.string.settings_appearance), icon = Icons.Outlined.Palette) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                listOf(AppTheme.SYSTEM, AppTheme.LIGHT, AppTheme.DARK).forEach { option ->
                    val label = when (option) {
                        AppTheme.SYSTEM -> R.string.settings_theme_system
                        AppTheme.LIGHT -> R.string.settings_theme_light
                        AppTheme.DARK -> R.string.settings_theme_dark
                    }
                    FilterChip(
                        selected = theme == option, onClick = { onThemeChanged(option) },
                        label = { Text(stringResource(label)) },
                        leadingIcon = if (theme == option) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                listOf(AppLanguage.SYSTEM, AppLanguage.FRENCH, AppLanguage.ENGLISH).forEach { option ->
                    val label = when (option) {
                        AppLanguage.SYSTEM -> R.string.settings_language_system
                        AppLanguage.FRENCH -> R.string.settings_language_french
                        AppLanguage.ENGLISH -> R.string.settings_language_english
                    }
                    FilterChip(
                        selected = language == option, onClick = { onLanguageChanged(option) },
                        label = { Text(stringResource(label)) },
                        leadingIcon = if (language == option) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }

        SectionCard(
            title = stringResource(R.string.settings_security),
            description = stringResource(R.string.settings_security_description),
            icon = Icons.Outlined.Security
        ) {
            BiometricToggle(lock, activity)
            Text(stringResource(R.string.settings_biometric_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AutoLockSetting()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (pinChanged) Text(stringResource(R.string.settings_pin_changed), color = PanelTheme.status.success)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                OutlinedButton(onClick = { changePin = true }) {
                    Icon(Icons.Outlined.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.settings_change_pin), Modifier.padding(start = PanelSpacing.sm))
                }
                OutlinedButton(onClick = onLock) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.lock_now), Modifier.padding(start = PanelSpacing.sm))
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_widgets), icon = Icons.Outlined.Widgets) {
            Text(stringResource(R.string.settings_widgets_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = stringResource(R.string.ssh_device_title), icon = Icons.Outlined.Terminal) {
            SshDeviceSection(active?.id, lock, activity)
        }

        SectionCard(title = stringResource(R.string.config_transfer_title), icon = Icons.Outlined.CloudSync) {
            ConfigurationTransferSection(
                configurationManager, lock, activity, importUri, onImportUriConsumed,
                onChooseImport, onSaveEncryptedArchive, transferStatus, onImported
            )
        }

        SectionCard(title = stringResource(R.string.settings_about), icon = Icons.Outlined.Info) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                Icon(painterResource(R.drawable.ic_app), contentDescription = null, tint = Color.Unspecified,
                    modifier = Modifier.size(40.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = panelMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    SectionHeader(stringResource(title))
}

@Composable
private fun ApiLikeMessage(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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

/** Same keypad, rules and two-step confirmation as first-run setup (see [ChangePinFlow]). */
@Composable
private fun ChangePinDialog(lock: AppLock, onDismiss: () -> Unit, onChanged: () -> Unit) {
    ChangePinFlow(lock, onDismiss, onChanged)
}

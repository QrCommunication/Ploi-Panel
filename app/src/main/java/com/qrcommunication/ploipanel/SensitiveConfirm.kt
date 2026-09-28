package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Re-authentication gate for destructive or disruptive actions (delete, restart).
 * Biometrics are used only when the user opted in and strong hardware is present;
 * otherwise the PIN is verified through the same lockout-protected path as the lock screen.
 */
@Composable
internal fun SensitiveConfirmDialog(
    lock: AppLock,
    activity: FragmentActivity,
    message: String,
    confirmLabel: Int,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    val useBiometric = lock.biometricEnabled() && biometricAvailable(activity)
    var pin by remember { mutableStateOf("") }
    var pinFallback by remember { mutableStateOf(false) }
    var attemptsLeft by remember { mutableIntStateOf(-1) }
    var lockedMs by remember { mutableLongStateOf(lock.remainingLockMs()) }
    val active = remember { AtomicBoolean(true) }
    val submitted = remember { AtomicBoolean(false) }
    fun verified() {
        if (active.get() && !lock.isLocked() && submitted.compareAndSet(false, true)) {
            pin = ""
            onConfirmed()
        }
    }
    fun verifyPin() {
        if (!active.get() || submitted.get() || pin.isEmpty()) return
        when (val result = lock.verify(pin)) {
            is AppLock.UnlockResult.Unlocked -> verified()
            is AppLock.UnlockResult.WrongPin -> {
                attemptsLeft = result.attemptsLeft
                pin = ""
            }
            is AppLock.UnlockResult.Locked -> {
                lockedMs = result.remainingMs
                pin = ""
            }
        }
    }
    fun dismiss() { active.set(false); pin = ""; onDismiss() }
    DisposableEffect(Unit) { onDispose { active.set(false) } }
    LaunchedEffect(useBiometric, lockedMs > 0) {
        if (useBiometric && !pinFallback && !lock.isLocked() && active.get()) {
            try {
                showBiometricPrompt(activity, onSuccess = { verified() },
                    onDismissed = { if (active.get()) pinFallback = true })
            } catch (_: Exception) {
                pinFallback = true
            }
        }
    }
    LaunchedEffect(lockedMs > 0) {
        while (lock.remainingLockMs() > 0) {
            lockedMs = lock.remainingLockMs()
            delay(1_000)
        }
        lockedMs = 0
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(R.string.confirm_sensitive_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                if (useBiometric && !pinFallback) {
                    Text(stringResource(R.string.confirm_sensitive_biometric))
                    OutlinedButton(onClick = { pinFallback = true }) { Text(stringResource(R.string.biometric_cancel)) }
                    if (lockedMs > 0) Text(
                        stringResource(R.string.locked_wait, (lockedMs + 999) / 1_000),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(stringResource(R.string.confirm_sensitive_pin))
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.pin_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { verifyPin() }),
                        singleLine = true, enabled = lockedMs <= 0, modifier = Modifier.fillMaxWidth()
                    )
                    if (lockedMs > 0) {
                        Text(
                            stringResource(R.string.locked_wait, (lockedMs + 999) / 1_000),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (attemptsLeft >= 0) {
                        Text(
                            pluralStringResource(R.plurals.wrong_pin, attemptsLeft, attemptsLeft),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!useBiometric || pinFallback) Button(
                onClick = { verifyPin() },
                enabled = pin.isNotEmpty() && !lock.isLocked() && lockedMs <= 0 && !submitted.get()
            ) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            OutlinedButton(onClick = ::dismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

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
    var attemptsLeft by remember { mutableIntStateOf(-1) }
    var lockedMs by remember { mutableLongStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_sensitive_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                if (useBiometric) {
                    Text(stringResource(R.string.confirm_sensitive_biometric))
                } else {
                    Text(stringResource(R.string.confirm_sensitive_pin))
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.pin_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
            Button(
                onClick = {
                    if (useBiometric) {
                        showBiometricPrompt(activity, onSuccess = onConfirmed)
                    } else {
                        when (val result = lock.verify(pin)) {
                            is AppLock.UnlockResult.Unlocked -> onConfirmed()
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
                },
                enabled = useBiometric || (pin.isNotEmpty() && lockedMs <= 0)
            ) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

package com.qrcommunication.ploipanel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

/** Strong biometrics only; device credential fallback stays out so the PIN policy is never weakened. */
internal fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

internal fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onDismissed: () -> Unit = {}
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onDismissed()
            override fun onAuthenticationFailed() = Unit // Let the user retry inside the system prompt.
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.biometric_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    )
}

/** First-run onboarding: choosing the PIN is mandatory before any profile can be used. */
@Composable
internal fun PinSetupScreen(lock: AppLock, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.pin_setup_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.pin_setup_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.pin_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.pin_confirm_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                if (pin != confirm) {
                    error = R.string.pin_mismatch
                } else {
                    try {
                        lock.setPin(pin)
                        error = 0
                        onDone()
                    } catch (invalid: IllegalArgumentException) {
                        error = R.string.pin_invalid
                    }
                }
            },
            enabled = pin.isNotEmpty() && confirm.isNotEmpty()
        ) { Text(stringResource(R.string.set_pin)) }
    }
}

/** Lock screen: PIN always works; biometrics are an explicit opt-in shortcut, never a bypass. */
@Composable
internal fun PinUnlockScreen(lock: AppLock, activity: FragmentActivity, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var attemptsLeft by remember { mutableIntStateOf(-1) }
    var lockedMs by remember { mutableLongStateOf(lock.remainingLockMs()) }
    val biometric = lock.biometricEnabled() && biometricAvailable(activity)

    LaunchedEffect(lockedMs > 0) {
        while (lock.remainingLockMs() > 0) {
            lockedMs = lock.remainingLockMs()
            delay(1_000)
        }
        lockedMs = 0
    }
    LaunchedEffect(Unit) {
        if (biometric && lockedMs <= 0) {
            showBiometricPrompt(activity, onSuccess = onUnlocked)
        }
    }

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.pin_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true, enabled = lockedMs <= 0, modifier = Modifier.fillMaxWidth()
        )
        if (lockedMs > 0) {
            Text(stringResource(R.string.locked_wait, (lockedMs + 999) / 1_000), color = MaterialTheme.colorScheme.error)
        } else if (attemptsLeft >= 0) {
            Text(pluralStringResource(R.plurals.wrong_pin, attemptsLeft, attemptsLeft), color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                when (val result = lock.verify(pin)) {
                    is AppLock.UnlockResult.Unlocked -> {
                        pin = ""
                        onUnlocked()
                    }
                    is AppLock.UnlockResult.WrongPin -> {
                        attemptsLeft = result.attemptsLeft
                        pin = ""
                    }
                    is AppLock.UnlockResult.Locked -> {
                        lockedMs = result.remainingMs
                        pin = ""
                    }
                }
            },
            enabled = pin.isNotEmpty() && lockedMs <= 0
        ) { Text(stringResource(R.string.unlock)) }
        if (biometric) {
            OutlinedButton(
                onClick = { showBiometricPrompt(activity, onSuccess = onUnlocked) },
                enabled = lockedMs <= 0
            ) { Text(stringResource(R.string.use_biometric)) }
        }
    }
}

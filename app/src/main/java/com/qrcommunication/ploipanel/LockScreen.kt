package com.qrcommunication.ploipanel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/** Digits the user has typed, shown as dots; announced as a count, never as the digits. */
@Composable
private fun PinDots(length: Int, error: Boolean) {
    val colors = MaterialTheme.colorScheme
    val spoken = stringResource(R.string.pin_digits_entered, length)
    Row(
        Modifier.heightIn(min = 24.dp).clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Four hollow slots show the minimum; every further digit adds a dot, up to twelve.
        val shown = maxOf(length, AppLock.MIN_PIN_LENGTH)
        repeat(shown) { index ->
            val filled = index < length
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(if (filled) (if (error) colors.error else colors.primary) else Color.Transparent)
                    .border(2.dp, if (error) colors.error else colors.outline, CircleShape)
            )
        }
    }
}

/**
 * On-screen numeric keypad (no IME: it cannot cover the fields and it never learns the PIN).
 * Keys are 64 dp targets; backspace has a spoken label.
 */
@Composable
private fun PinKeypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg)) {
                row.forEach { digit -> PinKey(digit.toString(), enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.lg)) {
            Spacer(Modifier.size(PIN_KEY_SIZE))
            PinKey("0", enabled) { onDigit('0') }
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(PIN_KEY_SIZE)) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.pin_backspace))
            }
        }
    }
}

private val PIN_KEY_SIZE = 64.dp

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(PIN_KEY_SIZE)
    ) { Text(label, style = MaterialTheme.typography.headlineSmall) }
}

@StringRes
internal fun pinProblemMessage(problem: PinProblem): Int = when (problem) {
    PinProblem.LENGTH, PinProblem.NOT_DIGITS -> R.string.pin_problem_length
    PinProblem.REPEATED -> R.string.pin_problem_repeated
    PinProblem.SEQUENCE -> R.string.pin_problem_sequence
}

/**
 * Dots + live counter + keypad, shared by setup, change and unlock. [showPolicy] shows the
 * "n digits (4 to 12)" counter and the exact rule a new PIN breaks while it is typed, so a long
 * but trivial code (123456, 111111) is explained instead of looking like a length limit.
 */
@Composable
internal fun PinEntry(
    value: String, onValueChange: (String) -> Unit,
    enabled: Boolean = true, error: Boolean = false, showPolicy: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
        PinDots(value.length, error)
        if (showPolicy) {
            Text(
                stringResource(R.string.pin_length_counter, value.length, AppLock.MIN_PIN_LENGTH, AppLock.MAX_PIN_LENGTH),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val problem = pinProblem(value)?.takeIf { value.length >= AppLock.MIN_PIN_LENGTH }
            if (problem != null) LockMessage(stringResource(pinProblemMessage(problem)))
        }
        PinKeypad(
            enabled = enabled,
            onDigit = { digit -> if (value.length < AppLock.MAX_PIN_LENGTH) onValueChange(value + digit) },
            onBackspace = { onValueChange(value.dropLast(1)) }
        )
    }
}

/** Brand mark, title and supporting line shared by the setup and unlock screens. */
@Composable
private fun LockHeader(title: String, body: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
        Icon(painterResource(R.drawable.ic_app), contentDescription = stringResource(R.string.app_name),
            tint = Color.Unspecified, modifier = Modifier.size(64.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LockMessage(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp))
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/**
 * Two-step choice of a new PIN (enter, then confirm) used by first-run setup and by "Change PIN".
 * [onChosen] receives a PIN that already passed [pinProblem]; it returns an error string res or 0.
 */
@Composable
private fun NewPinSteps(
    title: Int, intro: Int, submitLabel: Int,
    onChosen: (String) -> Int,
    footer: @Composable () -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    fun restart(message: Int) { pin = ""; confirm = ""; confirming = false; error = message }
    LockHeader(
        stringResource(if (confirming) R.string.pin_step_confirm else title),
        stringResource(if (confirming) R.string.pin_step_confirm_intro else intro)
    )
    if (error != 0) LockMessage(stringResource(error))
    if (!confirming) {
        PinEntry(pin, { pin = it; error = 0 }, showPolicy = true)
        Button(
            onClick = {
                val problem = pinProblem(pin)
                if (problem == null) { confirming = true; error = 0 } else error = pinProblemMessage(problem)
            },
            enabled = pin.length >= AppLock.MIN_PIN_LENGTH && pinProblem(pin) == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)
        ) { Text(stringResource(R.string.pin_continue)) }
    } else {
        PinEntry(confirm, { confirm = it; error = 0 }, error = error != 0 && confirm.isEmpty())
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
            Button(
                onClick = {
                    if (pin != confirm) restart(R.string.pin_mismatch)
                    else {
                        val failure = onChosen(pin)
                        if (failure != 0) restart(failure)
                    }
                },
                enabled = confirm.length >= AppLock.MIN_PIN_LENGTH,
                modifier = Modifier.fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)
            ) { Text(stringResource(submitLabel)) }
            TextButton(onClick = { restart(0) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pin_restart))
            }
        }
    }
    footer()
}

@Composable
private fun LockColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 420.dp).padding(PanelSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xl)
        ) { content() }
    }
}

/** First-run onboarding: choosing the PIN is mandatory before any profile can be used. */
@Composable
internal fun PinSetupScreen(lock: AppLock, onDone: () -> Unit) {
    LockColumn {
        NewPinSteps(R.string.pin_setup_title, R.string.pin_setup_intro, R.string.set_pin, onChosen = { chosen ->
            try {
                lock.setPin(chosen)
                onDone()
                0
            } catch (_: IllegalArgumentException) {
                R.string.pin_invalid
            }
        })
    }
}

/**
 * Settings "Change PIN" as a full-screen flow: verify the current PIN (lockout-protected), then
 * choose and confirm a new one with the same keypad and rules as first-run setup.
 */
@Composable
internal fun ChangePinFlow(lock: AppLock, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var attemptsLeft by remember { mutableIntStateOf(-1) }
    var lockedMs by remember { mutableLongStateOf(lock.remainingLockMs()) }
    LaunchedEffect(lockedMs > 0) {
        while (lock.remainingLockMs() > 0) {
            lockedMs = lock.remainingLockMs()
            delay(1_000)
        }
        lockedMs = 0
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(PanelSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel))
                    }
                    Text(stringResource(R.string.settings_change_pin), style = MaterialTheme.typography.titleLarge)
                }
                LockColumn {
                    if (!verified) {
                        LockHeader(stringResource(R.string.settings_current_pin), stringResource(R.string.lock_enter_pin))
                        if (lockedMs > 0) LockMessage(stringResource(R.string.locked_wait, (lockedMs + 999) / 1_000))
                        else if (attemptsLeft >= 0) LockMessage(pluralStringResource(R.plurals.wrong_pin, attemptsLeft, attemptsLeft))
                        PinEntry(current, { current = it }, enabled = lockedMs <= 0, error = attemptsLeft >= 0 && current.isEmpty())
                        Button(
                            onClick = {
                                when (val result = lock.verify(current)) {
                                    is AppLock.UnlockResult.Unlocked -> { verified = true; attemptsLeft = -1 }
                                    is AppLock.UnlockResult.WrongPin -> attemptsLeft = result.attemptsLeft
                                    is AppLock.UnlockResult.Locked -> lockedMs = result.remainingMs
                                }
                                current = ""
                            },
                            enabled = current.isNotEmpty() && lockedMs <= 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)
                        ) { Text(stringResource(R.string.pin_continue)) }
                    } else {
                        NewPinSteps(R.string.settings_new_pin, R.string.pin_setup_intro, R.string.settings_change_pin, onChosen = { chosen ->
                            try {
                                lock.setPin(chosen)
                                onChanged()
                                0
                            } catch (_: IllegalArgumentException) {
                                R.string.pin_invalid
                            }
                        })
                    }
                }
            }
        }
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
            showBiometricPrompt(activity, onSuccess = {
                if (!lock.isLocked()) onUnlocked()
            })
        }
    }
    fun submit() {
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
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 420.dp).padding(PanelSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PanelSpacing.xl)
        ) {
            LockHeader(stringResource(R.string.unlock_title), stringResource(R.string.lock_enter_pin))
            if (lockedMs > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(PanelSpacing.lg), horizontalArrangement = Arrangement.spacedBy(PanelSpacing.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.xxs)) {
                            Text(stringResource(R.string.pin_locked_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.locked_wait, (lockedMs + 999) / 1_000),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else if (attemptsLeft >= 0) {
                LockMessage(pluralStringResource(R.plurals.wrong_pin, attemptsLeft, attemptsLeft))
            }
            PinEntry(pin, { pin = it }, enabled = lockedMs <= 0, error = attemptsLeft >= 0 && pin.isEmpty())
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                Button(
                    onClick = { submit() },
                    enabled = pin.isNotEmpty() && lockedMs <= 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)
                ) {
                    Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.unlock), Modifier.padding(start = PanelSpacing.sm))
                }
                if (biometric) {
                    OutlinedButton(
                        onClick = { showBiometricPrompt(activity, onSuccess = {
                            if (!lock.isLocked()) onUnlocked()
                        }) },
                        enabled = lockedMs <= 0,
                        modifier = Modifier.fillMaxWidth().heightIn(min = PanelSpacing.touchTarget)
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.use_biometric), Modifier.padding(start = PanelSpacing.sm))
                    }
                }
            }
        }
    }
}

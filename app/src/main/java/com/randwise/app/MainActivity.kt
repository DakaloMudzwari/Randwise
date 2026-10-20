package com.randwise.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.randwise.app.ads.AdsConsent
import com.randwise.app.security.VerifyResult
import com.randwise.app.ui.RandWiseRoot
import com.randwise.app.ui.screens.LockScreen
import com.randwise.app.ui.theme.RandWiseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single activity. Extends FragmentActivity because BiometricPrompt requires it.
 *
 * Lock rules: if the user set an app password it is always required (fingerprint/face is an optional
 * shortcut). Without a password, the device biometric/credential gate is used when enabled.
 * Re-locks after 60 s in the background.
 */
class MainActivity : FragmentActivity() {

    private var locked by mutableStateOf(true)
    private var lockError by mutableStateOf<String?>(null)
    private var lockedUntil by mutableLongStateOf(0L)
    private var checking by mutableStateOf(false)
    private var backgroundedAt = 0L

    private val container get() = (application as RandWiseApplication).container

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { backgroundedAt = SystemClock.elapsedRealtime() }
        override fun onStart(owner: LifecycleOwner) {
            if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_MS && lockRequired()) {
                lockError = null
                locked = true
                if (biometricShortcutAvailable()) authenticateBiometric()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Midnight theme: light status/nav icons on the dark canvas regardless of system setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        applySecureWindow(container.preferences.settings.value.hideFromScreenshots)
        lifecycleScope.launch { container.preferences.settings.collect { applySecureWindow(it.hideFromScreenshots) } }

        locked = lockRequired()
        lockedUntil = System.currentTimeMillis() + container.passcode.lockoutRemainingMillis()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        setContent {
            RandWiseTheme {
                if (locked) {
                    LockScreen(
                        kind = container.passcode.kind.value,
                        biometricAvailable = biometricShortcutAvailable(),
                        error = lockError,
                        lockedUntil = lockedUntil,
                        checking = checking,
                        onSubmit = ::verifyPasscode,
                        onBiometric = ::authenticateBiometric,
                        onForgot = ::eraseAndRestart,
                    )
                } else {
                    RandWiseRoot(container)
                }
            }
        }
        if (locked && biometricShortcutAvailable()) authenticateBiometric()
        AdsConsent.gather(this)
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }

    /** FLAG_SECURE keeps balances out of screenshots, screen recordings and the recents thumbnail. */
    private fun applySecureWindow(secure: Boolean) {
        if (secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun deviceAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** Authenticators used when an app password exists: biometrics only (the password is the fallback). */
    private fun shortcutAuthenticators(): Int = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    private fun canUse(authenticators: Int) =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun biometricShortcutAvailable(): Boolean {
        if (!container.preferences.settings.value.biometricLock) return false
        return if (container.passcode.isSet) canUse(shortcutAuthenticators()) else canUse(deviceAuthenticators())
    }

    private fun lockRequired(): Boolean =
        container.passcode.isSet || (container.preferences.settings.value.biometricLock && canUse(deviceAuthenticators()))

    private fun verifyPasscode(secret: CharArray) {
        checking = true
        lifecycleScope.launch {
            // PBKDF2 is deliberately slow; keep it off the main thread.
            val result = withContext(Dispatchers.Default) { container.passcode.verify(secret) }
            secret.fill('\u0000')
            checking = false
            when (result) {
                VerifyResult.Success -> { lockError = null; locked = false }
                is VerifyResult.Wrong -> lockError = "That's not right. ${result.attemptsBeforeLockout} attempt${if (result.attemptsBeforeLockout == 1) "" else "s"} before a short lockout."
                is VerifyResult.LockedOut -> { lockError = null; lockedUntil = result.untilMillis }
            }
        }
    }

    private fun authenticateBiometric() {
        if (!lockRequired()) { locked = false; return }
        if (!biometricShortcutAvailable()) return
        val usePasswordFallback = container.passcode.isSet
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { lockError = null; locked = false }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) lockError = errString.toString()
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
        if (usePasswordFallback) {
            builder.setAllowedAuthenticators(shortcutAuthenticators()).setNegativeButtonText(getString(R.string.use_app_password))
        } else {
            builder.setAllowedAuthenticators(deviceAuthenticators())
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) builder.setNegativeButtonText(getString(R.string.cancel))
        }
        prompt.authenticate(builder.build())
    }

    private fun eraseAndRestart() {
        lifecycleScope.launch {
            container.eraser.eraseEverything()
            container.eraser.restart()
        }
    }

    companion object {
        private const val RELOCK_MS = 60_000L
    }
}

package com.randwise.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.randwise.app.ui.RandWiseRoot
import com.randwise.app.ui.screens.LockScreen
import com.randwise.app.ui.theme.RandWiseTheme

/**
 * Single activity. Extends FragmentActivity because BiometricPrompt requires it.
 * The biometric gateway (FDS §7) locks on cold start and after 60 s in the background.
 */
class MainActivity : FragmentActivity() {

    private var locked by mutableStateOf(true)
    private var lockError by mutableStateOf<String?>(null)
    private var backgroundedAt = 0L

    private val container get() = (application as RandWiseApplication).container

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { backgroundedAt = SystemClock.elapsedRealtime() }
        override fun onStart(owner: LifecycleOwner) {
            if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_MS && lockRequired()) {
                locked = true
                authenticate()
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
        locked = lockRequired()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        setContent {
            RandWiseTheme {
                if (locked) {
                    LockScreen(error = lockError, onUnlock = ::authenticate)
                } else {
                    RandWiseRoot(container)
                }
            }
        }
        if (locked) authenticate()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }

    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    private fun lockRequired(): Boolean =
        container.preferences.settings.value.biometricLock &&
            BiometricManager.from(this).canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate() {
        if (!lockRequired()) { locked = false; return }
        lockError = null
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { lockError = errString.toString() }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
            .setAllowedAuthenticators(allowedAuthenticators())
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText(getString(R.string.cancel)) }
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val RELOCK_MS = 60_000L
    }
}

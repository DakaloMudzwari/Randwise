package com.randwise.app.data

import android.content.Context
import com.randwise.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val biometricLock: Boolean = true,
    /** POPIA: explicit opt-in before any statement is read. */
    val consentStatementUpload: Boolean = false,
    /** POPIA: explicit opt-in before anonymised facts are sent to the AI gateway. */
    val consentAiInterpretation: Boolean = false,
    val billReminders: Boolean = true,
    /** 80% / 100% budget notifications, once per category per pay cycle. */
    val budgetAlerts: Boolean = true,
    val gatewayUrl: String = BuildConfig.DEFAULT_GATEWAY_URL,
    val showAds: Boolean = true,
    /** FLAG_SECURE: blocks screenshots, screen recording and the recent-apps thumbnail. */
    val hideFromScreenshots: Boolean = true,
)

/** Non-financial app settings. Financial data lives only in the encrypted database. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("rw_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<Settings> = state.asStateFlow()

    private fun load() = Settings(
        biometricLock = prefs.getBoolean("biometric", true),
        consentStatementUpload = prefs.getBoolean("consent_upload", false),
        consentAiInterpretation = prefs.getBoolean("consent_ai", false),
        billReminders = prefs.getBoolean("reminders", true),
        budgetAlerts = prefs.getBoolean("budget_alerts", true),
        gatewayUrl = prefs.getString("gateway", BuildConfig.DEFAULT_GATEWAY_URL) ?: BuildConfig.DEFAULT_GATEWAY_URL,
        showAds = prefs.getBoolean("ads", true),
        hideFromScreenshots = prefs.getBoolean("secure_window", true),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(state.value)
        prefs.edit()
            .putBoolean("biometric", s.biometricLock)
            .putBoolean("consent_upload", s.consentStatementUpload)
            .putBoolean("consent_ai", s.consentAiInterpretation)
            .putBoolean("reminders", s.billReminders)
            .putBoolean("budget_alerts", s.budgetAlerts)
            .putString("gateway", s.gatewayUrl)
            .putBoolean("ads", s.showAds)
            .putBoolean("secure_window", s.hideFromScreenshots)
            .apply()
        state.value = s
    }

    fun reset() {
        prefs.edit().clear().apply()
        state.value = load()
    }
}

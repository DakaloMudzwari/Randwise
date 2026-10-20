package com.randwise.app.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google User Messaging Platform flow. The Ads SDK is only initialised — and banners only load —
 * once consent allows it; where no consent is required (e.g. South Africa today) that is immediate.
 */
object AdsConsent {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** True in regions (EEA/UK…) where users must be able to revisit their ad choices. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val adsInitialised = AtomicBoolean(false)

    fun gather(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { update(activity, info) } },
            { update(activity, info) },
        )
        // Consent from a previous session can be used straight away.
        update(activity, info)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { update(activity, UserMessagingPlatform.getConsentInformation(activity)) }
    }

    private fun update(activity: Activity, info: ConsentInformation) {
        _privacyOptionsRequired.value = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds()) {
            if (adsInitialised.compareAndSet(false, true)) {
                Thread { MobileAds.initialize(activity.applicationContext) }.start()
            }
            _canRequestAds.value = true
        }
    }
}

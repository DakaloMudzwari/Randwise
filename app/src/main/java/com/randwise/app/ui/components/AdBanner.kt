package com.randwise.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.randwise.app.BuildConfig
import com.randwise.app.ui.theme.RW

/**
 * AdMob anchored banner in a clearly separated, labelled container. It uses a neutral grey that is
 * not part of the navy/emerald app palette, sits away from interactive controls, and is never
 * placed inside financial cards or near the FAB, so it cannot be mistaken for app content.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp - 32
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = RW.colors.adSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("Advertisement", style = MaterialTheme.typography.labelSmall, color = RW.colors.adLabel)
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

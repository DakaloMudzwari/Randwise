plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.randwise.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.randwise.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // AdMob: Google's published TEST ids. Replace with your own before release.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        // Default AI gateway (Android emulator -> host machine). Editable in Settings.
        buildConfigField("String", "DEFAULT_GATEWAY_URL", "\"http://10.0.2.2:5080\"")
        // Gateway client key from the git-ignored local.properties (randwise.gatewayClientKey=...) or a
        // -P property in CI. Empty means the gateway is running without a key (local development).
        val localProps = java.util.Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val clientKey = (project.findProperty("randwise.gatewayClientKey") as String?) ?: localProps.getProperty("randwise.gatewayClientKey", "")
        buildConfigField("String", "GATEWAY_CLIENT_KEY", "\"$clientKey\"")
    }

    buildTypes {
        debug {
            // `./gradlew installDebug -PrwPreview` installs a side-by-side copy (fresh data) for UI testing.
            if (project.hasProperty("rwPreview")) applicationIdSuffix = ".preview"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

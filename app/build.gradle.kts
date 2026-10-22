import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release values come from the git-ignored local.properties or -P properties in CI — never from source.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun releaseProp(name: String): String = (project.findProperty(name) as String?) ?: localProps.getProperty(name, "")

// Google's published AdMob TEST ids. Debug builds always use them (real ids on a dev device risk invalid-traffic bans).
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdmobBannerId = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "com.randwise.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.randwise.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Gateway client key (randwise.gatewayClientKey). Empty means the gateway is running without a key (local development).
        buildConfigField("String", "GATEWAY_CLIENT_KEY", "\"${releaseProp("randwise.gatewayClientKey")}\"")
    }

    signingConfigs {
        // Upload key for Play App Signing: randwise.signing.storeFile / storePassword / keyAlias / keyPassword.
        if (releaseProp("randwise.signing.storeFile").isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseProp("randwise.signing.storeFile"))
                storePassword = releaseProp("randwise.signing.storePassword")
                keyAlias = releaseProp("randwise.signing.keyAlias")
                keyPassword = releaseProp("randwise.signing.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testAdmobBannerId\"")
            // Android emulator -> host machine. Editable in Settings in debug builds only.
            buildConfigField("String", "DEFAULT_GATEWAY_URL", "\"http://10.0.2.2:5080\"")
            // `./gradlew installDebug -PrwPreview` installs a side-by-side copy (fresh data) for UI testing.
            if (project.hasProperty("rwPreview")) applicationIdSuffix = ".preview"
            // `-PrwAbi=x86_64` packages one CPU architecture only (≈4× smaller) for emulators short on space.
            (project.findProperty("rwAbi") as String?)?.let { ndk { abiFilters += it.split(",") } }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Fall back to test ids / no gateway so CI can still assemble an unsigned release; verifyReleaseConfig
            // stops a store bundle being built that way.
            manifestPlaceholders["admobAppId"] = releaseProp("randwise.admobAppId").ifEmpty { testAdmobAppId }
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${releaseProp("randwise.admobBannerId").ifEmpty { testAdmobBannerId }}\"")
            // Fixed HTTPS gateway; empty means the coach answers on-device only.
            buildConfigField("String", "DEFAULT_GATEWAY_URL", "\"${releaseProp("randwise.gatewayUrl")}\"")
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
    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

// Room MigrationTestHelper reads the exported schemas as test assets.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The Play Store bundle must be signed, show real ads and talk to a real HTTPS gateway.
val verifyReleaseConfig by tasks.registering {
    val problems = buildList {
        if (releaseProp("randwise.signing.storeFile").isEmpty()) add("randwise.signing.* (upload key) is not set")
        if (releaseProp("randwise.admobAppId").isEmpty() || releaseProp("randwise.admobBannerId").isEmpty()) add("randwise.admobAppId / randwise.admobBannerId are not set")
        if (!releaseProp("randwise.gatewayUrl").startsWith("https://")) add("randwise.gatewayUrl must be an https:// URL")
        if (releaseProp("randwise.gatewayClientKey").isEmpty()) add("randwise.gatewayClientKey is not set")
    }
    doLast {
        if (problems.isNotEmpty()) throw GradleException("Release bundle not configured (local.properties or -P):\n - " + problems.joinToString("\n - "))
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(verifyReleaseConfig) }

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

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

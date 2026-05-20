plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // KSP plugin removed — YukiHookAPI KSP processor no longer needed with libxposed API 101
}

// Use JVM 17 for better Android/Xposed compatibility
kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "-Xwarning-level=DEPRECATION:disabled",
        )
    }
}

val appVersionName = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode = providers.gradleProperty("VERSION_CODE").get().toInt()

android {
    namespace = "com.astrixforge.devicemasker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.astrixforge.devicemasker"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ═══════════════════════════════════════════════════════════
    // SIGNING — Read keystore credentials from environment variables
    // Set these in your CI/CD environment or local.properties (gitignored)
    // Usage: KEYSTORE_PATH=... KEYSTORE_PASS=... KEY_ALIAS=... KEY_PASS=...
    // ═══════════════════════════════════════════════════════════
    signingConfigs {
        create("release") {
            storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASS")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASS")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Apply signing config only when env vars are set (safe fallback for dev)
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
        }
        create("ciRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
    }

    // CRITICAL: Prevent synthetic lambda classes that cause ClassNotFoundException in Xposed
    packaging {
        resources {
            // Standard open-source licence files
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Coroutines debug metadata (not needed in release)
            excludes += "/META-INF/com/android/build/gradle/**"
            // Duplicate service files from multiple deps
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            // Kotlin module files can duplicate across transitive deps
            excludes += "**/*.kotlin_module"
            // libxposed META-INF/xposed/ resources must NOT be excluded (handled in :xposed
            // sources)
        }
        // Ensure all module classes are in the primary dex for Xposed class loading
        dex { useLegacyPackaging = true }
        // Include native libs if any future deps need them
        jniLibs { keepDebugSymbols += "**/*.so" }
    }

    // ═══════════════════════════════════════════════════════════
    // LINT CONFIGURATION
    // ═══════════════════════════════════════════════════════════
    lint {
        // Enable cross-module analysis to prevent false positives
        // in library modules (:common, :xposed)
        checkDependencies = true

        abortOnError = true

        // Generate HTML report for review
        htmlReport = true

        // Global lint configuration
        lintConfig = rootProject.file("lint.xml")

        // Baseline file to track known issues (optional)
        // baseline = file("lint-baseline.xml")
    }
}

composeCompiler {
    val enableReports = providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()
    val enableMetrics = providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()

    if (enableReports) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler/reports")
    }
    if (enableMetrics) {
        metricsDestination = layout.buildDirectory.dir("compose_compiler/metrics")
    }
}

dependencies {
    // ═══════════════════════════════════════════════════════════
    // HMA-OSS ARCHITECTURE MODULES
    // ═══════════════════════════════════════════════════════════
    implementation(project(":common")) // Shared models and contracts
    implementation(project(":xposed")) // Hook logic - bundled in APK

    // ═══════════════════════════════════════════════════════════
    // CORE ANDROID
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ═══════════════════════════════════════════════════════════
    // JETPACK COMPOSE
    // ═══════════════════════════════════════════════════════════
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.material3.window.size)
    implementation(libs.graphics.shapes)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ═══════════════════════════════════════════════════════════
    // ACTIVITY & LIFECYCLE
    // ═══════════════════════════════════════════════════════════
    implementation(libs.activity.compose)
    implementation(libs.bundles.lifecycle)

    // ═══════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.material3.adaptive.navigation3)

    // ═══════════════════════════════════════════════════════════
    // libxposed SERVICE — Write RemotePreferences from the UI
    // interface: provides XposedServiceHelper, XposedService classes (compile + runtime)
    // service:   provides the ContentProvider implementation that LSPosed uses to deliver prefs
    // The hook-side reads via XposedInterface.getRemotePreferences() in :xposed.
    // ═══════════════════════════════════════════════════════════
    implementation(libs.libxposed.iface)
    implementation(libs.libxposed.service)
    implementation(libs.libsu.core)

    // ═══════════════════════════════════════════════════════════
    // DATA STORAGE
    // ═══════════════════════════════════════════════════════════
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    // ═══════════════════════════════════════════════════════════
    // COROUTINES
    // ═══════════════════════════════════════════════════════════
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // ═══════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════
    implementation(libs.timber)
    implementation(libs.coil.compose)

    // ═══════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

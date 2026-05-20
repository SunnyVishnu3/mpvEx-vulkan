plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Use JVM 17 for Android/Xposed compatibility
kotlin {
    jvmToolchain(17)
    compilerOptions { freeCompilerArgs.addAll("-Xwarning-level=DEPRECATION:disabled") }
}

android {
    namespace = "com.astrixforge.devicemasker.xposed"
    compileSdk = rootProject.ext.get("compileSdk") as Int

    defaultConfig {
        minSdk = rootProject.ext.get("minSdk") as Int
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
        // AIDL no longer needed in :xposed — config delivery via RemotePreferences
        aidl = false
    }

    // CRITICAL: libxposed reads META-INF/xposed/ files (java_init.list, module.prop, scope.list)
    // from Java resources — NOT from Android assets. This srcDirs addition makes Gradle
    // package src/main/resources/ into the AAR/DEX properly.
    sourceSets { getByName("main") { resources.srcDirs("src/main/resources") } }

    lint { lintConfig = rootProject.file("lint.xml") }
}

dependencies {
    // Common module for shared models and config contracts
    implementation(project(":common"))

    // ═══════════════════════════════════════════════════════════
    // libxposed API 101 — Modern Xposed hook engine
    // compileOnly: LSPosed provides the runtime implementation via its framework
    // Resolves from mavenCentral() — no local AARs needed
    // ═══════════════════════════════════════════════════════════
    compileOnly(libs.libxposed.api)

    // Coroutines for shared helpers/tests
    implementation(libs.kotlinx.coroutines.core)

    // Serialization for config parsing
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.libxposed.api)
}

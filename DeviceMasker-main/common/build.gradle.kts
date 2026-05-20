plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Use JVM 17 for Android/Xposed compatibility
kotlin { jvmToolchain(17) }

android {
    namespace = "com.astrixforge.devicemasker.common"
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

    buildFeatures { aidl = false }

    lint { lintConfig = rootProject.file("lint.xml") }
}

dependencies {
    // Kotlinx Serialization for JSON config
    implementation(libs.kotlinx.serialization.json)

    // Core Kotlin
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

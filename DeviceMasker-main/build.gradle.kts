import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    // ksp plugin removed — only used by YukiHookAPI KSP processor, now replaced by libxposed API
    // 100
    alias(libs.plugins.spotless)
    idea
}

// Shared configuration for all modules
ext {
    set("compileSdk", 37)
    set("minSdk", 26)
    set("targetSdk", 36)
}

fun Project.configureDeviceMaskerDetekt() {
    pluginManager.apply("dev.detekt")

    val libsCatalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies { add("detektPlugins", libsCatalog.findLibrary("detekt-compose-rules").get()) }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        allRules.set(true)
        parallel.set(true)
        ignoreFailures.set(false)
        failOnSeverity.set(FailOnSeverity.Error)
        basePath.set(rootProject.layout.projectDirectory)
        config.setFrom(rootProject.file("config/detekt.yml"))

        val moduleConfig = file("detekt.yml")
        if (moduleConfig.exists()) {
            config.from(moduleConfig)
        }

        baseline.set(file("detekt-baseline.xml"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget.set("17")
        reports {
            checkstyle.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            markdown.required.set(false)
        }
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/resources/**")
    }

    tasks.withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget.set("17")
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/resources/**")
    }
}

subprojects {
    plugins.withId("com.android.application") { configureDeviceMaskerDetekt() }
    plugins.withId("com.android.library") { configureDeviceMaskerDetekt() }
}

spotless {
    kotlin {
        target("app/**/*.kt", "common/**/*.kt", "xposed/**/*.kt", "verifier/**/*.kt")
        targetExclude("**/build/**/*.kt")
        targetExclude("**/memory-bank/**")
        targetExclude("**/openspec/**")
        targetExclude("**/scripts/**")
        targetExclude(".agents/**")
        targetExclude(".agents/**/*.kt")
        targetExclude("**/.agents/**")
        targetExclude(".claude/**")
        targetExclude(".claude/**/*.kt")
        targetExclude("**/.claude/**")
        targetExclude("**/docs/**")
        ktfmt("0.54").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/**/*.gradle.kts",
            "common/**/*.gradle.kts",
            "xposed/**/*.gradle.kts",
            "verifier/**/*.gradle.kts",
        )
        targetExclude("**/build/**/*.gradle.kts")
        targetExclude("**/memory-bank/**")
        targetExclude("**/openspec/**")
        targetExclude("**/scripts/**")
        targetExclude(".agents/**")
        targetExclude(".agents/**/*.gradle.kts")
        targetExclude("**/.agents/**")
        targetExclude(".claude/**")
        targetExclude(".claude/**/*.gradle.kts")
        targetExclude("**/.claude/**")
        targetExclude("**/docs/**")
        ktfmt("0.54").kotlinlangStyle()
    }
}

idea {
    module {
        excludeDirs.addAll(
            files("memory-bank", "openspec", "scripts", ".agents", ".claude", "docs")
        )
    }
}

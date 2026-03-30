import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Read a key from env var, falling back to .env.local at project root
fun envOrLocal(key: String, default: String = ""): String {
    return System.getenv(key) ?: run {
        val envFile = rootProject.file(".env.local")
        if (envFile.exists()) {
            envFile.readLines()
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")?.trim()
        } else null
    } ?: default
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            // Ktor Client - Android engine
            implementation(libs.ktor.client.okhttp)

            // Google Sign-In (Credential Manager)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.google.id)
        }
        iosMain.dependencies {
            // Ktor Client - iOS engine
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Ktor Client
            implementation(libs.bundles.ktor.common)

            // Logging
            implementation(libs.kermit)

            // Markdown rendering in feed
            implementation(libs.markdown.renderer.m3)

            // Auth (cross-platform Google Sign-In + Firebase Auth persistence)
            implementation(libs.kmpauth.google)
            implementation(libs.kmpauth.uihelper)
            implementation(libs.kmpauth.firebase)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.common)

            // Image loading (Coil 3 - Compose Multiplatform)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "org.bigboyapps.rngenerator"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "org.bigboyapps.rngenerator"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // BuildKonfig handles cross-platform config — no Android-only fields needed
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

buildkonfig {
    packageName = "org.bigboyapps.rngenerator"

    defaultConfigs {
        buildConfigField(STRING, "SERVER_URL", envOrLocal("SERVER_URL", "https://rpgenerator-yumkcfwfba-uc.a.run.app"))
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

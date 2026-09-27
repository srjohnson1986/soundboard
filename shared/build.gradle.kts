import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android app and the web version (#193): today the board model,
// eventually everything that isn't a platform adapter. See docs/ARCHITECTURE.md, "Modules".
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.example.soundboard.shared"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Node for now: the shared code has no browser APIs yet, and Node runs the tests in CI
    // without a headless browser. The web app itself adds browser() when it arrives.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // JetBrains' multiplatform build of androidx.lifecycle; on Android it resolves to
            // Google's own artifact, so BoardViewModel is an ordinary androidx ViewModel there.
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

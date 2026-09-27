import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android app and the web version (#193): the model, the repositories,
// the view model and the UI — everything that isn't a platform adapter. See
// docs/ARCHITECTURE.md, "Modules".
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.lint")
}

// The app's version, from gradle.properties, as a constant the shared UI can show (the
// menu's version link). The Android app reads the same properties for versionName/Code.
val appVersionName = providers.gradleProperty("appVersionName").get()
val generateAppVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appVersion/commonMain/kotlin")
    inputs.property("appVersionName", appVersionName)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/example/soundboard/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.example.soundboard
            |
            |/** Generated from gradle.properties' appVersionName; don't edit. */
            |const val APP_VERSION_NAME = "$appVersionName"
            |""".trimMargin()
        )
    }
}

kotlin {
    android {
        namespace = "com.example.soundboard.shared"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
        // Compose Resources (the bundled fonts) ship as Android resources on this target.
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Tests run in headless Chrome: the UI's graphics engine (Skia, as wasm) only loads in a
    // browser, not in Node, and only when bundled into an executable, which is also what
    // the web app will be.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
        wasmJsTest {
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
        commonMain {
            kotlin.srcDir(generateAppVersion)
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                // JetBrains' multiplatform builds of androidx.lifecycle; on Android they resolve
                // to Google's own artifacts, so BoardViewModel is an ordinary androidx ViewModel.
                api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                // Compose Multiplatform; likewise Google's Jetpack Compose on Android.
                api("org.jetbrains.compose.runtime:runtime:1.12.1")
                api("org.jetbrains.compose.foundation:foundation:1.12.1")
                api("org.jetbrains.compose.ui:ui:1.12.1")
                api("org.jetbrains.compose.material3:material3:1.9.0")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.components:components-resources:1.12.1")
            }
        }
        wasmJsMain.dependencies {
            implementation(npm("fflate", "0.8.3"))
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.19.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

compose.resources {
    packageOfResClass = "com.example.soundboard.resources"
}

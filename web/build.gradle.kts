import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The web version of the app: the shared UI and view model in a browser, with browser
// audio and speech behind them. Build it with `./gradlew :web:wasmJsBrowserDistribution`;
// run it locally with `./gradlew :web:wasmJsBrowserDevelopmentRun`.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("soundboard")
        browser {
            commonWebpackConfig {
                outputFileName = "soundboard.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
            dependencies {
                implementation(project(":shared"))
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            }
        }
    }
}

// Built-in boards are served next to the app as boards/<name>, straight from presets/ (no
// extra copy to keep in sync). Only the text-to-speech board ships on the web: it has no
// recorded voices in it. WebMain.kt lists the same names.
tasks.named<Copy>("wasmJsProcessResources") {
    from(rootProject.file("presets/tts-care-board.zip")) {
        into("boards")
    }
}

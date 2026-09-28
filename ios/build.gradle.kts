// The iOS version of the app (#266): the shared UI and view model with iOS's audio and speech
// behind them, built as the SoundboardKit framework that the Xcode project in app/ hosts.
// Only builds on macOS; the iOS workflow builds it, runs the app on the simulator and
// screenshots it. See docs/ARCHITECTURE.md, "Modules".
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SoundboardKit"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared"))
        }
    }
}

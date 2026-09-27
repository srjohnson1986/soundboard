plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
    id("com.android.lint") version "9.4.1" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

// Test coverage (#222), for the JVM tests of the app and the shared module together.
dependencies {
    kover(project(":app"))
    kover(project(":shared"))
}

// Two views of the same JVM tests (the app's debug unit tests and shared's Android host tests;
// Kover doesn't measure WebAssembly, so the web-only code is covered by its browser tests):
// "unit" is all the code, for the HTML report (./gradlew koverHtmlReportUnit); "core" is the
// data and model packages and BoardViewModel, which must each keep at least 90% of their lines
// covered (./gradlew koverVerifyCore). The UI and Android audio have no floor.
kover {
    currentProject {
        createVariant("unit") {}
        createVariant("core") {}
    }
    reports {
        filters {
            excludes { androidGeneratedClasses() }
        }
        variant("unit") {
            html { title = "Soundboard, JVM tests" }
        }
        variant("core") {
            filters {
                includes {
                    classes(
                        "com.example.soundboard.data.*",
                        "com.example.soundboard.model.*",
                        "com.example.soundboard.BoardViewModel",
                        "com.example.soundboard.BoardViewModel$*"
                    )
                }
            }
            verify {
                rule("Each core package keeps 90% line coverage") {
                    groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.PACKAGE
                    minBound(90)
                }
            }
        }
    }
}

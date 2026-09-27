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

// Coverage of the JVM tests: the app's debug unit tests and shared's Android host tests,
// together. Kover doesn't measure WebAssembly, so the web-only code is covered by its
// browser tests instead. ./gradlew :koverHtmlReportUnit writes the report for all the code.
kover {
    currentProject {
        createVariant("unit") {}
    }
    reports {
        filters {
            excludes { androidGeneratedClasses() }
        }
        variant("unit") {
            html { title = "Soundboard, JVM tests" }
        }
    }
}

// The well-tested core stays that way: the data and model packages and BoardViewModel must
// each keep at least 90% of their lines covered. Read from Kover's XML report rather than
// with a Kover rule, whose class filters didn't reliably reach the app module's classes.
// The UI and the Android audio (tested on the emulator, which Kover doesn't see) have no floor.
val verifyCoreCoverage = tasks.register("verifyCoreCoverage") {
    group = "verification"
    description = "Fails if data, model or BoardViewModel drops below 90% line coverage."
    val report = layout.buildDirectory.file("reports/kover/reportUnit.xml")
    dependsOn("koverXmlReportUnit")
    inputs.file(report)
    doLast {
        val floor = 90.0
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder().parse(report.get().asFile)
        val classes = document.getElementsByTagName("class")
        // Lines [missed, covered] per area, summed over its classes (lambdas included).
        val areas = linkedMapOf(
            "data" to longArrayOf(0, 0),
            "model" to longArrayOf(0, 0),
            "BoardViewModel" to longArrayOf(0, 0)
        )
        for (i in 0 until classes.length) {
            val element = classes.item(i) as org.w3c.dom.Element
            val name = element.getAttribute("name")
            val area = when {
                name.startsWith("com/example/soundboard/data/") -> "data"
                name.startsWith("com/example/soundboard/model/") -> "model"
                name == "com/example/soundboard/BoardViewModel" ||
                    name.startsWith("com/example/soundboard/BoardViewModel$") -> "BoardViewModel"
                else -> continue
            }
            val counters = element.getElementsByTagName("counter")
            for (j in 0 until counters.length) {
                val counter = counters.item(j) as org.w3c.dom.Element
                if (counter.parentNode != element || counter.getAttribute("type") != "LINE") continue
                areas.getValue(area)[0] += counter.getAttribute("missed").toLong()
                areas.getValue(area)[1] += counter.getAttribute("covered").toLong()
            }
        }
        val failures = areas.mapNotNull { (area, lines) ->
            val total = lines[0] + lines[1]
            val percent = if (total == 0L) 0.0 else 100.0 * lines[1] / total
            logger.lifecycle("%-15s %5.1f%% of %d lines covered".format(area, percent, total))
            if (percent < floor) "$area is at %.1f%%, under the %.0f%% floor".format(percent, floor) else null
        }
        if (failures.isNotEmpty()) throw GradleException(failures.joinToString("; "))
    }
}

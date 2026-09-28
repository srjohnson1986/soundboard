package com.example.soundboard.ui

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestResult

/**
 * The base class for the shared UI tests (#219). On the Android host JVM it runs them under
 * Robolectric, with its native graphics so text is measured for real; in the browser it's
 * nothing, since Compose runs there as it is.
 */
expect abstract class UiTest()

/** A phone's window, in dp: what the UI tests run in on both platforms. */
const val TEST_WINDOW_WIDTH_DP = 320
const val TEST_WINDOW_HEIGHT_DP = 640

/**
 * Runs [block] as a Compose UI test in a phone-sized window, [TEST_WINDOW_WIDTH_DP] by
 * [TEST_WINDOW_HEIGHT_DP], or the other way round when [landscape].
 */
@OptIn(ExperimentalTestApi::class)
expect fun runUiTest(landscape: Boolean = false, block: suspend ComposeUiTest.() -> Unit): TestResult

/**
 * Whether the board's dialogs keep the platform's default width in this test: yes, except in a
 * landscape window on the Android host JVM (see [LocalDialogUsesPlatformWidth], #234).
 */
expect fun dialogsUsePlatformWidth(): Boolean

/** Closes the open dropdown menu, the way a user would on the platform. */
@OptIn(ExperimentalTestApi::class)
expect fun ComposeUiTest.closeMenu()

/**
 * Animations that finish in their first frame, for the tests that draw with Skia (the browser
 * and iOS). Their idle wait steps the test clock a frame at a time until nothing wants
 * another frame, and a text field's animations sometimes re-arm one another for good, so
 * the wait never ended and headless Chrome stopped answering (#279).
 */
internal object NoMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
}

/**
 * The longest a Skia-drawn UI test may take. Its idle wait never yields to the browser, so
 * one that never ends stalls the whole run (#279); this makes it fail that test instead.
 */
val SKIA_TEST_TIMEOUT = 20.seconds

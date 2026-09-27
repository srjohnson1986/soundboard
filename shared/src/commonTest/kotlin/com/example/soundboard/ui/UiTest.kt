package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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

/** Closes the open dropdown menu, the way a user would on the platform. */
@OptIn(ExperimentalTestApi::class)
expect fun ComposeUiTest.closeMenu()

package com.example.soundboard.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.test.TestResult
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The window size must match TEST_WINDOW_WIDTH_DP/HEIGHT_DP.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h640dp-port-mdpi")
actual abstract class UiTest actual constructor()

@OptIn(ExperimentalTestApi::class)
actual fun runUiTest(landscape: Boolean, block: suspend ComposeUiTest.() -> Unit): TestResult {
    if (landscape) RuntimeEnvironment.setQualifiers("w640dp-h320dp-land")
    inLandscapeWindow = landscape
    try {
        runComposeUiTest(block = block)
    } finally {
        inLandscapeWindow = false
    }
}

private var inLandscapeWindow = false

// In a landscape window, Robolectric gives a platform-width dialog a width at which a text field
// in it never finishes measuring: its native line breaker doesn't return. Only then, dialogs
// size themselves (Material's own 280-560dp) instead. robolectric/robolectric#8460, #234.
actual fun dialogsUsePlatformWidth(): Boolean = !inLandscapeWindow

// Back. A tap outside doesn't reach the menu's window here: the test taps the node's own window.
@OptIn(ExperimentalTestApi::class)
actual fun ComposeUiTest.closeMenu() {
    Espresso.pressBackUnconditionally()
    waitForIdle()
}

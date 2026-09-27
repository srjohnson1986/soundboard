package com.example.soundboard.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.test.TestResult

actual abstract class UiTest actual constructor()

@OptIn(ExperimentalTestApi::class)
actual fun runUiTest(landscape: Boolean, block: suspend ComposeUiTest.() -> Unit): TestResult {
    val width = TEST_WINDOW_WIDTH_DP.toFloat()
    val height = TEST_WINDOW_HEIGHT_DP.toFloat()
    return runSkikoComposeUiTest(
        size = if (landscape) Size(height, width) else Size(width, height),
        density = Density(1f),
        block = block
    )
}

// The menu button again, which a click outside the menu reaches in the browser. The click
// after that one is lost, so it's spent on the title.
@OptIn(ExperimentalTestApi::class)
actual fun ComposeUiTest.closeMenu() {
    onNodeWithContentDescription("Menu").performClick()
    waitForIdle()
    onNodeWithText("Soundboard").performClick()
    waitForIdle()
}

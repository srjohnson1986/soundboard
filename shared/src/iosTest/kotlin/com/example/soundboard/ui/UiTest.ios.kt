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

// On iOS the shared UI tests draw with Skia, as in the browser (#265), so they're set up the same way.
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

// A click outside the menu closes it: the menu button, then the title in case that click
// reopened it, which leaves the menu closed either way.
@OptIn(ExperimentalTestApi::class)
actual fun ComposeUiTest.closeMenu() {
    onNodeWithContentDescription("Menu").performClick()
    waitForIdle()
    onNodeWithText("Soundboard").performClick()
    waitForIdle()
}

actual fun dialogsUsePlatformWidth(): Boolean = true

package com.example.soundboard.ui

import android.app.UiAutomation
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.soundboard.MainActivity
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rotating the phone keeps whatever's open (#237). This runs the real [MainActivity], since
 * it's the activity's manifest entry (android:configChanges) that stops Android recreating it,
 * and with it losing every open menu and dialog.
 */
@RunWith(AndroidJUnit4::class)
class RotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val uiAutomation: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @After
    fun backToPortrait() {
        uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    /** Rotates, waits for the new orientation, and checks the activity wasn't recreated to get there. */
    private fun rotate(landscape: Boolean) {
        val before = composeRule.activity
        uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        val wanted = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.activity.resources.configuration.orientation == wanted }
        composeRule.waitForIdle()
        assertSame("the activity was recreated", before, composeRule.activity)
    }

    @Test
    fun theMenuStaysOpenThroughRotation() {
        rotate(landscape = false)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        rotate(landscape = true)
        composeRule.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()

        rotate(landscape = false)
        composeRule.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aDialogAndWhatsTypedInItSurviveRotation() {
        rotate(landscape = false)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Speak...").performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Can I have some water")

        rotate(landscape = true)

        composeRule.onNode(hasSetTextAction() and hasText("Can I have some water")).assertIsDisplayed()

        rotate(landscape = false)

        composeRule.onNode(hasSetTextAction() and hasText("Can I have some water")).assertIsDisplayed()
    }

    @Test
    fun settingsStaysOpenThroughRotation() {
        rotate(landscape = false)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Tile labels").assertIsDisplayed()

        rotate(landscape = true)

        composeRule.onNodeWithText("Tile labels").performScrollTo().assertIsDisplayed()
    }
}

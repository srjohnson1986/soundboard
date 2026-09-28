package com.example.soundboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

/**
 * The caregiver lock's state and controls, for the top bar (#251): [locked] while editing is
 * hidden, [enabled] while the lock is on at all (locked, or unlocked for now).
 */
internal class EditingLock(
    val locked: Boolean,
    val enabled: Boolean,
    val onEnable: (Boolean) -> Unit,
    val onUnlock: () -> Unit,
    val onLockNow: () -> Unit
) {
    companion object {
        /** No lock, for a board without one. */
        val None = EditingLock(locked = false, enabled = false, onEnable = {}, onUnlock = {}, onLockNow = {})

        /** How long Unlock must be held. */
        val HOLD_TO_UNLOCK = 3.seconds

        /** How long an unlocked board sits unused before it locks itself again. */
        val RELOCK_AFTER_IDLE = 5.minutes
    }
}

/**
 * How long an unlocked board sits unused before it locks itself again: [EditingLock.RELOCK_AFTER_IDLE]
 * in the app. The UI tests shorten it, since Compose's test clock steps through a wait frame by frame.
 */
internal val LocalRelockAfterIdle = staticCompositionLocalOf { EditingLock.RELOCK_AFTER_IDLE }

/**
 * The locked menu's Unlock: holding it fills a ring over [EditingLock.HOLD_TO_UNLOCK], and
 * letting go early starts over, so a stray tap never unlocks. Screen readers get an Unlock
 * action instead, since holding is hard to do with one.
 */
@Composable
internal fun HoldToUnlockItem(onUnlocked: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    DropdownMenuItem(
        text = { Text("Hold to unlock") },
        leadingIcon = {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        },
        // A tap alone does nothing; the hold below is what unlocks.
        onClick = {},
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(EditingLock.HOLD_TO_UNLOCK.inWholeMilliseconds.toInt(), easing = LinearEasing))
                        onUnlocked()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        hold.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Unlock") { onUnlocked(); true })
            }
    )
}

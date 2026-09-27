package com.example.soundboard.ui

import com.example.soundboard.model.Tile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class LabelTextTest {

    @Test
    fun `largestFittingSize finds the biggest size that fits`() {
        assertEquals(23, largestFittingSize(14, 32) { it <= 23 })
    }

    @Test
    fun `largestFittingSize returns the max when everything fits`() {
        assertEquals(32, largestFittingSize(14, 32) { true })
    }

    @Test
    fun `largestFittingSize falls back to the min when nothing fits`() {
        assertEquals(14, largestFittingSize(14, 32) { false })
    }

    @Test
    fun `largestFittingSize handles an equal min and max`() {
        assertEquals(20, largestFittingSize(20, 20) { true })
        assertEquals(20, largestFittingSize(20, 20) { false })
    }

    @Test
    fun `a break between words is not a break inside one`() {
        // "Call the " | "doctor"
        assertFalse(breaksInsideWord("Call the doctor", listOf(9)))
    }

    @Test
    fun `a break mid-word is detected`() {
        // "Some" | "thing's wrong"
        assertTrue(breaksInsideWord("Something's wrong", listOf(4)))
    }

    @Test
    fun `a break right after a hyphen is fine`() {
        assertFalse(breaksInsideWord("Mm-mm", listOf(3)))
    }

    @Test
    fun `tile display text covers labeled, unnamed and blank tiles`() {
        assertEquals("Water", tileDisplayText(Tile(label = "Water"), allCaps = false))
        assertEquals("WATER", tileDisplayText(Tile(label = "Water"), allCaps = true))
        assertEquals("Unnamed", tileDisplayText(Tile(fileName = "a.mp3"), allCaps = false))
        assertEquals("+", tileDisplayText(Tile(), allCaps = true))
    }

    // A stand-in for text measurement: a label "fits" at a size when its longest word, at
    // 1 unit per character per sp, is no wider than 100 units.
    private fun fitsByLongestWord(text: String, sp: Int) = text.split(' ').maxOf { it.length } * sp <= 100

    @Test
    fun `labels that fit at the grid size get no size of their own`() {
        assertEquals(emptyMap(), labelSizeExceptions(listOf("Hey", "Call the doctor"), gridSp = 14, floorSp = 10, fits = ::fitsByLongestWord))
    }

    @Test
    fun `a word too wide for the grid size shrinks only its own label, as little as it can`() {
        // "Something's" is 11 characters: it needs 9 (11 x 9 = 99), below a floor of 10, so it
        // fits at no allowed size and keeps the grid's.
        assertEquals(emptyMap(), labelSizeExceptions(listOf("Something's wrong"), gridSp = 14, floorSp = 10, fits = ::fitsByLongestWord))
        // With a lower floor it gets the largest size that fits.
        assertEquals(mapOf("Something's wrong" to 9), labelSizeExceptions(listOf("Hey", "Something's wrong"), gridSp = 14, floorSp = 8, fits = ::fitsByLongestWord))
    }

    @Test
    fun `a label that only needs a little less than the grid size gets exactly that`() {
        // "Bathroom" is 8 characters: 8 x 14 = 112 doesn't fit, 8 x 13 = 104 doesn't, 8 x 12 = 96 does.
        assertEquals(mapOf("Bathroom" to 12), labelSizeExceptions(listOf("Bathroom", "Yes"), gridSp = 14, floorSp = 10, fits = ::fitsByLongestWord))
    }

    @Test
    fun `no exceptions when the grid size is already at or below the floor`() {
        assertEquals(emptyMap(), labelSizeExceptions(listOf("Bathroom"), gridSp = 10, floorSp = 10, fits = ::fitsByLongestWord))
    }
}

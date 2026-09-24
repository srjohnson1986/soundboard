package com.example.soundboard.ui

import com.example.soundboard.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

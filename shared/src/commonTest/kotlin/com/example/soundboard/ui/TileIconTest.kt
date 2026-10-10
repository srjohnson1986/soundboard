package com.example.soundboard.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TileIconTest : UiTest() {

    @Test
    fun `a large picture is shrunk to the limit keeping its shape`() {
        val scaled = ImageBitmap(1000, 500).scaledDownTo(200)

        assertEquals(200, scaled.width)
        assertEquals(100, scaled.height)
    }

    @Test
    fun `a tall picture is limited by its height`() {
        val scaled = ImageBitmap(300, 1200).scaledDownTo(400)

        assertEquals(100, scaled.width)
        assertEquals(400, scaled.height)
    }

    @Test
    fun `a picture already small enough is left alone`() {
        val small = ImageBitmap(120, 80)

        assertSame(small, small.scaledDownTo(200))
    }

    @Test
    fun `an extreme shape never shrinks a side to nothing`() {
        val scaled = ImageBitmap(4000, 2).scaledDownTo(100)

        assertEquals(100, scaled.width)
        assertEquals(1, scaled.height)
    }
}

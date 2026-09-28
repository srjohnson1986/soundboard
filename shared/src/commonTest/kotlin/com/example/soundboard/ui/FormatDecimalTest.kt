package com.example.soundboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDecimalTest {

    @Test
    fun `rounds to the requested places like the JVM's format did`() {
        assertEquals("2.1", formatDecimal(2.06, 1))
        assertEquals("1.0", formatDecimal(1.0, 1))
        assertEquals("0.5", formatDecimal(0.5, 1))
        assertEquals("340", formatDecimal(340.4, 0))
        assertEquals("341", formatDecimal(340.5, 0))
        assertEquals("12.05", formatDecimal(12.049, 2))
    }

    @Test
    fun `keeps the sign but not on a value that rounds to zero`() {
        assertEquals("-1.5", formatDecimal(-1.5, 1))
        assertEquals("0.0", formatDecimal(-0.01, 1))
    }
}

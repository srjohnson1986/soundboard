package com.example.soundboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmojiNamesTest {

    @Test
    fun `a listed emoji gets its plain spoken words`() {
        assertEquals("Bathroom", EmojiNames.suggest("🚽"))
        assertEquals("Water", EmojiNames.suggest("💧"))
    }

    @Test
    fun `the emoji presentation selector and surrounding spaces do not matter`() {
        assertEquals("I love you", EmojiNames.suggest("❤"))
        assertEquals("I love you", EmojiNames.suggest("❤️"))
        assertEquals("I love you", EmojiNames.suggest("  ❤️ "))
    }

    @Test
    fun `a skin tone does not change the suggestion`() {
        assertEquals("Yes", EmojiNames.suggest("👍"))
        assertEquals("Yes", EmojiNames.suggest("👍🏽"))
    }

    @Test
    fun `an emoji that is not listed or is not an emoji gets nothing`() {
        assertNull(EmojiNames.suggest("🦬"))
        assertNull(EmojiNames.suggest("hello"))
        assertNull(EmojiNames.suggest(""))
    }
}

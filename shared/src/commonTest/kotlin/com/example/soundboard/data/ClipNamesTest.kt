package com.example.soundboard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules for a clip's readable name, on every platform. */
class ClipNamesTest {

    private fun name(label: String, emoji: String? = null, page: String = "Home", original: String = "id.m4a", namer: ClipNamer = ClipNamer()) =
        namer.nameFor(label, emoji, page, original)

    @Test
    fun `a label becomes the name and the extension is kept`() {
        assertEquals("Yes.m4a", name("Yes"))
        assertEquals("Yes.wav", name("Yes", original = "3f9c.wav"))
        assertEquals("Yes", name("Yes", original = "noextension"))
    }

    @Test
    fun `characters Windows forbids are taken out and spaces collapse`() {
        assertEquals("What.m4a", name("What?"))
        assertEquals("ab.m4a", name("a\\/:*\"<>|b"))
        assertEquals("I want water.m4a", name("  I   want\twater \n"))
        assertEquals("ab.m4a", name("a\u0000\u0007b"))
    }

    @Test
    fun `trailing dots and spaces are trimmed`() {
        assertEquals("Hello.m4a", name("Hello... "))
        assertEquals("Sound.m4a", name("..."))
    }

    @Test
    fun `letters from any language are kept`() {
        assertEquals("Sí gracias.m4a", name("Sí gracias"))
        assertEquals("ありがとう.m4a", name("ありがとう"))
        assertEquals("Привет.m4a", name("Привет"))
    }

    @Test
    fun `a long label is cut and no character is split`() {
        val cut = name("a".repeat(100))
        assertEquals("a".repeat(60) + ".m4a", cut)
        // 59 letters then an emoji (two UTF-16 units) that would be cut in half.
        val emojiCut = ClipNames.clean("a".repeat(59) + "😀😀")
        assertEquals("a".repeat(59), emojiCut)
    }

    @Test
    fun `reserved Windows names are avoided with or without an extension`() {
        assertEquals("CON_.m4a", name("CON"))
        assertEquals("nul_.m4a", name("nul"))
        assertEquals("COM1_.m4a", name("COM1"))
        assertEquals("lpt9.txt_.m4a", name("lpt9.txt"))
        assertEquals("Console.m4a", name("Console"))
    }

    @Test
    fun `a tile with no label uses its emoji's name and then Sound`() {
        assertEquals("Happy.m4a", name("", emoji = "😀"))
        assertEquals("Sound.m4a", name("", emoji = "not an emoji we know"))
        assertEquals("Sound.m4a", name("   ", emoji = null))
        assertEquals("Yes.m4a", name("Yes", emoji = "😀"))
    }

    @Test
    fun `duplicates add the page name and then a number ignoring case`() {
        val namer = ClipNamer()
        assertEquals("Yes.m4a", name("Yes", page = "Home", namer = namer))
        assertEquals("yes (Food).m4a", name("yes", page = "Food", namer = namer))
        assertEquals("YES (Food) 2.m4a", name("YES", page = "Food", namer = namer))
        assertEquals("Yes (Food) 3.m4a", name("Yes", page = "Food", namer = namer))
        assertEquals("Yes (Home).m4a", name("Yes", page = "Home", namer = namer))
    }

    @Test
    fun `duplicates on a page with no usable name are numbered`() {
        val namer = ClipNamer()
        assertEquals("Yes.m4a", name("Yes", page = "???", namer = namer))
        assertEquals("Yes 2.m4a", name("Yes", page = "???", namer = namer))
        assertEquals("Yes 3.m4a", name("Yes", page = "", namer = namer))
    }

    @Test
    fun `the same name with a different extension is a different file`() {
        val namer = ClipNamer()
        assertEquals("Yes.m4a", name("Yes", original = "a.m4a", namer = namer))
        assertEquals("Yes.wav", name("Yes", original = "b.wav", namer = namer))
    }

    @Test
    fun `names already taken are steered clear of`() {
        val namer = ClipNamer(taken = listOf("YES.m4a"))
        assertEquals("Yes (Home).m4a", name("Yes", page = "Home", namer = namer))
    }

    @Test
    fun `extensions are only kept when they look like one`() {
        assertEquals(".m4a", ClipNames.extensionOf("a.m4a"))
        assertEquals("", ClipNames.extensionOf("abc"))
        assertEquals("", ClipNames.extensionOf("a.very long ext"))
        assertEquals("", ClipNames.extensionOf("a."))
        assertTrue(ClipNames.clean("a/b").none { it == '/' })
        assertFalse(ClipNames.clean("\u0001").isNotEmpty())
    }
}

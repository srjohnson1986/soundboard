package com.example.soundboard.ios

import com.example.soundboard.translate.UnavailableTranslator

/**
 * iOS's [com.example.soundboard.translate.Translator] for now: an honest "unavailable" (#332). Apple's
 * Translation framework is a SwiftUI feature (a `.translationTask` on a view, with a system sheet
 * for the first download) that's hard to drive from Kotlin, and nothing here can be tried without
 * a Mac, so a half-working version would be worse than a clear message. Tiles can still be given
 * their Spanish by hand. See docs/IOS.md.
 */
class IosTranslator : UnavailableTranslator(
    "Apple's Translation framework isn't connected to this app yet, so type the Spanish yourself."
)

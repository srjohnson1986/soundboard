package com.example.soundboard.model

/**
 * How speaking tiles sound on this device (#250): the voice, by the platform's id for it (null
 * for the device's default), and its speed and pitch as percentages of normal. Per device,
 * since each device offers its own voices.
 *
 * [spanishVoiceId] is the voice for Spanish (#331), chosen apart from [voiceId] because a
 * device's voices belong to a language: an English voice can't speak Spanish. Null means the
 * first Spanish voice the device has. Speed and pitch are shared by both languages.
 */
data class SpeechSettings(
    val voiceId: String? = null,
    val ratePercent: Int = 100,
    val pitchPercent: Int = 100,
    val spanishVoiceId: String? = null
) {
    companion object {
        /** The speeds Settings offers, from half to one and a half times normal. */
        val RATE_OPTIONS_PERCENT = listOf(50, 75, 100, 125, 150)

        /** The pitches Settings offers. */
        val PITCH_OPTIONS_PERCENT = listOf(75, 100, 125)

        /** How far a stored speed or pitch may go either way, in case one came from elsewhere. */
        val PERCENT_RANGE = 25..400
    }
}

/** The language tag for Spanish, the board's [Board.secondLanguage] unless it is turned off. */
const val SPANISH = "es"

/** A language tag in words for the screen: "es" is Spanish. An unknown tag shows as itself. */
fun languageName(tag: String): String = when (tag) {
    SPANISH -> "Spanish"
    else -> tag
}

package com.example.soundboard.model

/**
 * How speaking tiles sound on this device (#250): the voice, by the platform's id for it (null
 * for the device's default), and its speed and pitch as percentages of normal. Per device,
 * since each device offers its own voices.
 */
data class SpeechSettings(
    val voiceId: String? = null,
    val ratePercent: Int = 100,
    val pitchPercent: Int = 100
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

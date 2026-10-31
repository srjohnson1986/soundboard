package com.example.soundboard

import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.ThemeMode
import com.example.soundboard.model.TileBorder

/**
 * Every change the Settings dialog can make, so the dialog takes one object instead of a
 * callback per setting. [BoardViewModel] implements it; each call validates and persists
 * like any other board edit.
 */
interface BoardSettingsActions {
    fun setOpenOnHomePage(value: Boolean)
    fun setIdleTimeoutMinutes(value: Int)
    fun setLongPressDurationMillis(value: Int)
    fun setThemeMode(mode: ThemeMode)
    fun setKeepScreenAwake(value: Boolean)
    fun setHapticFeedbackEnabled(value: Boolean)
    fun setSpeakUnrecordedTilesEnabled(value: Boolean)
    fun setPerformanceModeEnabled(value: Boolean)
    fun setStickyHomeRowEnabled(value: Boolean)
    fun setHideBlankTilesEnabled(value: Boolean)
    fun setBackgroundColor(argb: Int?)
    fun clearBackground()
    fun setBoardTileOpacity(value: Float)
    fun setBoardTileBorder(value: TileBorder)
    fun setDefaultPageRows(value: Int)
    fun setDefaultPageColumns(value: Int)
    fun setLandscapeLayout(layout: LandscapeLayout)
    fun setRowHeight(rowHeight: RowHeight)
    fun setLabelStyle(style: LabelStyle)
    fun setShowModeEnabled(value: Boolean)
    fun setShowModeTimerSeconds(value: Int)
    fun setShowModeTapToClose(value: Boolean)
    fun setShowModeMuteSounds(value: Boolean)
    fun setShowModeFlipped(value: Boolean)
    fun setRecordingTrimEndMillis(value: Int)
    fun setSpeechVoice(voiceId: String?)
    fun setSpeechRatePercent(value: Int)
    fun setSpeechPitchPercent(value: Int)

    /** The voice for Spanish (#331); null is the device's first Spanish voice. */
    fun setSpanishVoice(voiceId: String?)

    /** Whether the board offers a second language (Spanish): the EN / ES switch and each tile's Spanish field. */
    fun setSecondLanguageEnabled(value: Boolean)

    /** Says a sample sentence in the current voice, speed and pitch. */
    fun previewSpeech()

    /** Says a sample sentence in the Spanish voice. */
    fun previewSpanishSpeech()

    /** Fills in the Spanish of every tile that has none, by translating its words on the device (#332). */
    fun translateAllTiles()

    /** Downloads the language model translating needs, if it isn't here yet. */
    fun prepareTranslator()
}

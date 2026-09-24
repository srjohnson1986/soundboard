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
    fun setTileOpacity(value: Float)
    fun setTileBorder(value: TileBorder)
    fun setDefaultPageRows(value: Int)
    fun setDefaultPageColumns(value: Int)
    fun setLandscapeLayout(layout: LandscapeLayout)
    fun setRowHeight(rowHeight: RowHeight)
    fun setLabelStyle(style: LabelStyle)
}

package com.example.soundboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.LandscapeLayout
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.Tile
import com.example.soundboard.model.TileBorder
import com.example.soundboard.model.resolvedColor
import kotlin.math.roundToInt

/** Horizontal padding on each side of a page's grid (and the pinned row above it). */
private val GRID_PADDING = 12.dp

/** Gap between neighboring tiles, both across and down. */
internal val TILE_SPACING = 8.dp

/** Narrowest a tile gets when Fit to screen adds landscape columns — a comfortable touch target. */
private val MIN_FIT_TO_SCREEN_TILE_WIDTH = 56.dp

/** Inset between a tile's edge and its label. */
internal val TILE_CONTENT_PADDING = 6.dp

/**
 * The shared label style for a grid of [tiles], each [tileWidthPx] x [tileHeightPx]: one
 * size for every label, as large as [labelStyle] allows while all of them still fit.
 */
@Composable
private fun rememberTilesLabelStyle(
    tiles: List<Tile>,
    tileWidthPx: Float,
    tileHeightPx: Float,
    labelStyle: LabelStyle
): TextStyle {
    val insetPx = with(LocalDensity.current) { (TILE_CONTENT_PADDING * 2).toPx() }
    val texts = remember(tiles, labelStyle.allCaps) { tiles.map { tileDisplayText(it, labelStyle.allCaps) } }
    return rememberGridLabelStyle(
        texts = texts,
        boxWidthPx = (tileWidthPx - insetPx).toInt(),
        boxHeightPx = (tileHeightPx - insetPx).toInt(),
        labelStyle = labelStyle
    )
}

/**
 * [page]'s row height: its portrait row height, capped by its row height setting (the
 * page's own override, else [boardRowHeight]) — see [cappedRowHeightPx]. Used in portrait
 * and by landscape's page grid alike, so rows keep the same height in both orientations.
 * Portrait's grid width is taken from the window's shorter side, since that's the side
 * that's horizontal in portrait — this works even when the app launched in landscape.
 */
@Composable
private fun pageRowHeight(page: Page, boardRowHeight: RowHeight): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    return with(density) {
        val portraitGridWidthPx = minOf(window.width, window.height) - (GRID_PADDING * 2).toPx()
        cappedRowHeightPx(
            portraitGridWidthPx = portraitGridWidthPx,
            columns = page.columns,
            aspectRatio = page.tileAspectRatio,
            spacingPx = TILE_SPACING.toPx(),
            maxScale = (page.rowHeight ?: boardRowHeight).maxScale
        ).toDp()
    }
}

/**
 * The fixed row shown above every non-home page, mirroring the home page's own pinned
 * first row: its first portrait row ([Page.columns] tiles) in both orientations, so the
 * same tiles stay pinned when the device rotates — stretched across the wider screen in
 * landscape, at the page's standard row height. See [PageGrid]'s pinFirstRow.
 */
@Composable
internal fun PinnedRow(
    homePage: Page,
    boardRowHeight: RowHeight,
    labelStyle: LabelStyle,
    editMode: Boolean,
    globalTileOpacity: Float,
    globalTileBorder: TileBorder,
    hapticFeedbackEnabled: Boolean,
    performanceModeEnabled: Boolean,
    speakUnrecordedTilesEnabled: Boolean,
    hideBlankTilesEnabled: Boolean,
    onTap: (Tile) -> Unit,
    onPreviewSound: (Tile) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val columns = homePage.columns
    val tiles = homePage.visibleTiles.take(columns)
    val rowHeight = pageRowHeight(homePage, boardRowHeight)
    val density = LocalDensity.current
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val tileWidthPx = if (columns > 0) (rowWidthPx - with(density) { TILE_SPACING.toPx() } * (columns - 1)) / columns else 0f
    val labelTextStyle = rememberTilesLabelStyle(tiles, tileWidthPx, with(density) { rowHeight.toPx() }, labelStyle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GRID_PADDING, vertical = 8.dp)
            .onSizeChanged { rowWidthPx = it.width },
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)
    ) {
        tiles.forEach { tile ->
            TileCard(
                tile = tile,
                editMode = editMode,
                aspectRatio = homePage.tileAspectRatio,
                rowHeight = rowHeight,
                labelTextStyle = labelTextStyle,
                allCaps = labelStyle.allCaps,
                pageColor = homePage.color?.let { Color(it) },
                opacity = homePage.opacityFor(tile, globalTileOpacity),
                border = homePage.borderFor(tile, globalTileBorder),
                performanceModeEnabled = performanceModeEnabled,
                hidden = !tile.hasSound && hideBlankTilesEnabled && !editMode,
                modifier = Modifier.weight(1f),
                onTap = { onTap(tile) },
                // Long-press previews what a pad will say without "using" it for real,
                // same as PageGrid's hold-without-moving (#4) — not offered in edit mode,
                // where a tap already opens the editor's own Play/Preview button.
                onLongClick = if (!editMode && tile.isPlayable(speakUnrecordedTilesEnabled)) {
                    {
                        if (hapticFeedbackEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onPreviewSound(tile)
                    }
                } else null
            )
        }
    }
}

/**
 * One page's scrollable grid. Drag-to-reorder only attaches when [isActive] — the page
 * actually on screen. Portrait shows [Page.visibleTiles]; landscape shows either
 * [Page.landscapeTiles] on the page's own landscape grid or the portrait tiles reflowed
 * to fit, per [landscapeLayout]. When [pinFirstRow] and the page has more than one row, its first
 * portrait row ([Page.columns] tiles, in either orientation — the same tiles [PinnedRow]
 * pins on other pages) renders fixed above a scrolling grid for the rest, instead of one
 * plain grid — see [Page.isHome]/`Board.stickyHomeRowEnabled`. Reordering across that
 * boundary just works: a tile is "pinned" purely by occupying one of the first
 * [Page.columns] slots, the same flat list [Page.withTileMoved] already reorders by index —
 * no separate pinned-tile concept needed. The one visible seam is that
 * [Modifier.animateItem] only applies within the scrolling grid's own item scope, so a
 * reorder crossing the pinned/scrolling boundary pops instead of sliding.
 */
@Composable
internal fun PageGrid(
    page: Page,
    editMode: Boolean,
    isActive: Boolean,
    hapticFeedbackEnabled: Boolean,
    performanceModeEnabled: Boolean,
    speakUnrecordedTilesEnabled: Boolean,
    hideBlankTilesEnabled: Boolean,
    globalTileOpacity: Float,
    globalTileBorder: TileBorder,
    landscapeLayout: LandscapeLayout,
    boardRowHeight: RowHeight,
    labelStyle: LabelStyle,
    // The height budget for the landscape column math — measured once, above the pager,
    // covering the space available before the sticky row (PinnedRow) is subtracted. Using
    // a shared reference instead of this page's own pager-remaining height keeps tile size
    // consistent across pages: without it, a page with the sticky row showing above it gets
    // a shorter pager than the home page (which never has one), and would otherwise need
    // more columns (smaller tiles) than the home page just to still hit targetRows.
    referenceHeightPx: Int,
    pinFirstRow: Boolean,
    onTap: (Tile) -> Unit,
    onPreviewSound: (Tile) -> Unit,
    onPreviewMove: (Int, Int) -> Unit,
    onCommitOrder: () -> Unit,
    onDragActiveChanged: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var gridSizePx by remember { mutableStateOf(IntSize.Zero) }
    val gridWidthPx = gridSizePx.width
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val usePageGrid = isLandscape && landscapeLayout == LandscapeLayout.PAGE_GRID
    val spacingPx = with(density) { TILE_SPACING.toPx() }
    val columns = when {
        usePageGrid -> page.effectiveLandscapeColumns
        isLandscape && gridWidthPx > 0 && referenceHeightPx > 0 -> landscapeColumnCount(
            baseColumns = page.columns,
            aspectRatio = page.tileAspectRatio,
            widthPx = gridWidthPx.toFloat(),
            heightPx = referenceHeightPx.toFloat(),
            spacingPx = spacingPx,
            minTileWidthPx = with(density) { MIN_FIT_TO_SCREEN_TILE_WIDTH.toPx() }
        )
        else -> page.columns
    }
    val shownTiles = if (usePageGrid) page.landscapeTiles else page.visibleTiles
    // Fit to screen sizes landscape tiles by their shape alone, as it always has.
    val rowHeight = if (isLandscape && !usePageGrid) null else pageRowHeight(page, boardRowHeight)
    val tileWidthPx = if (columns > 0 && gridWidthPx > 0) {
        (gridWidthPx - spacingPx * (columns - 1)) / columns
    } else 0f
    val cellStepXPx = if (tileWidthPx > 0f) tileWidthPx + spacingPx else 0f
    val tileHeightPx = rowHeight?.let { with(density) { it.toPx() } } ?: (tileWidthPx / page.tileAspectRatio)
    val cellStepYPx = if (tileWidthPx > 0f) tileHeightPx + spacingPx else 0f
    val labelTextStyle = rememberTilesLabelStyle(shownTiles, tileWidthPx, tileHeightPx, labelStyle)

    val pinnedCount = page.columns
    // The first row is drawn fixed above a scrolling grid of the rest, rather than as
    // one plain grid; with a single row there's nothing below it to scroll.
    val showPinnedRowSeparately = pinFirstRow && shownTiles.size > pinnedCount

    fun tileDragModifier(index: Int, tile: Tile): Modifier {
        val isDragged = index == draggedIndex
        return Modifier
            .graphicsLayer {
                if (isDragged) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    if (!performanceModeEnabled) {
                        shadowElevation = 8f
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
                }
            }
            .zIndex(if (isDragged) 1f else 0f)
            .pointerInput(tile.id, columns, isActive, hapticFeedbackEnabled) {
                if (!isActive) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (hapticFeedbackEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        draggedIndex = index
                        dragOffset = Offset.Zero
                        onDragActiveChanged(true)
                    },
                    onDragEnd = {
                        // A hold that never moved the tile to a different index wasn't a
                        // reorder at all — treat it as a request to preview what the tile
                        // says/plays without "using" it for real (#4), same as PinnedRow's
                        // dedicated onLongClick (which has no competing drag gesture to share it with).
                        if (!editMode && draggedIndex == index && tile.isPlayable(speakUnrecordedTilesEnabled)) {
                            onPreviewSound(tile)
                        }
                        draggedIndex = null
                        dragOffset = Offset.Zero
                        onDragActiveChanged(false)
                        onCommitOrder()
                    },
                    onDragCancel = {
                        draggedIndex = null
                        dragOffset = Offset.Zero
                        onDragActiveChanged(false)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                        val current = draggedIndex ?: return@detectDragGesturesAfterLongPress
                        if (cellStepXPx <= 0f || cellStepYPx <= 0f) return@detectDragGesturesAfterLongPress
                        val colDelta = (dragOffset.x / cellStepXPx).roundToInt()
                        val rowDelta = (dragOffset.y / cellStepYPx).roundToInt()
                        if (colDelta == 0 && rowDelta == 0) return@detectDragGesturesAfterLongPress
                        val target = dragTargetIndex(
                            index = current,
                            rowDelta = rowDelta,
                            colDelta = colDelta,
                            columns = columns,
                            pinnedCount = if (showPinnedRowSeparately) pinnedCount else 0,
                            lastIndex = shownTiles.lastIndex
                        )
                        if (target != current) {
                            onPreviewMove(current, target)
                            draggedIndex = target
                            dragOffset -= Offset(colDelta * cellStepXPx, rowDelta * cellStepYPx)
                        }
                    }
                )
            }
    }

    // The pinned row keeps the page's standard row height in every layout, stretched
    // across the width — Fit to screen's shape-sized tiles would be huge at 4 across.
    val pinnedRowHeight = pageRowHeight(page, boardRowHeight)
    // Sized on its own tiles, exactly as PinnedRow sizes the same row on other pages, so
    // the pinned labels look the same wherever they show.
    val pinnedTileWidthPx = if (gridWidthPx > 0) (gridWidthPx - spacingPx * (pinnedCount - 1)) / pinnedCount else 0f
    val pinnedLabelTextStyle = rememberTilesLabelStyle(
        shownTiles.take(pinnedCount),
        pinnedTileWidthPx,
        with(density) { pinnedRowHeight.toPx() },
        labelStyle
    )
    val pageColor = page.color?.let { Color(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GRID_PADDING)
            .onSizeChanged { gridSizePx = it }
    ) {
        if (showPinnedRowSeparately) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)
            ) {
                shownTiles.take(pinnedCount).forEachIndexed { index, tile ->
                    TileCard(
                        tile = tile,
                        editMode = editMode,
                        aspectRatio = page.tileAspectRatio,
                        rowHeight = pinnedRowHeight,
                        labelTextStyle = pinnedLabelTextStyle,
                        allCaps = labelStyle.allCaps,
                        pageColor = pageColor,
                        opacity = page.opacityFor(tile, globalTileOpacity),
                        border = page.borderFor(tile, globalTileBorder),
                        performanceModeEnabled = performanceModeEnabled,
                        hidden = !tile.hasSound && hideBlankTilesEnabled && !editMode,
                        modifier = Modifier.weight(1f).then(tileDragModifier(index, tile)),
                        onTap = { onTap(tile) }
                    )
                }
            }
        }
        val scrollingTiles = if (showPinnedRowSeparately) shownTiles.drop(pinnedCount) else shownTiles
        val scrollingStartIndex = if (showPinnedRowSeparately) pinnedCount else 0
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            verticalArrangement = Arrangement.spacedBy(TILE_SPACING)
        ) {
            itemsIndexed(scrollingTiles, key = { _, tile -> tile.id }) { i, tile ->
                val index = scrollingStartIndex + i
                val isDragged = index == draggedIndex
                TileCard(
                    tile = tile,
                    editMode = editMode,
                    aspectRatio = page.tileAspectRatio,
                    rowHeight = rowHeight,
                    labelTextStyle = labelTextStyle,
                    allCaps = labelStyle.allCaps,
                    pageColor = pageColor,
                    opacity = page.opacityFor(tile, globalTileOpacity),
                    border = page.borderFor(tile, globalTileBorder),
                    performanceModeEnabled = performanceModeEnabled,
                    hidden = !tile.hasSound && hideBlankTilesEnabled && !editMode,
                    modifier = Modifier
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .then(tileDragModifier(index, tile)),
                    onTap = { onTap(tile) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCard(
    tile: Tile,
    editMode: Boolean,
    aspectRatio: Float,
    pageColor: Color?,
    opacity: Float,
    border: TileBorder,
    performanceModeEnabled: Boolean,
    hidden: Boolean = false,
    // A fixed row height, overriding [aspectRatio] — rows are capped by the row height
    // setting, and landscape's page grid keeps them at their portrait height even though
    // its tiles are a different width.
    rowHeight: Dp? = null,
    // The grid's shared label style, already sized by rememberGridLabelStyle.
    labelTextStyle: TextStyle,
    allCaps: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val sizeModifier = if (rowHeight != null) Modifier.height(rowHeight) else Modifier.aspectRatio(aspectRatio)
    if (hidden) {
        // Not just invisible — no Card, no border, no combinedClickable, so a stray
        // tap in this grid cell reaches no callback at all. Keeping the same modifier
        // (and thus size/weight) preserves every other tile's exact position.
        Spacer(modifier = modifier.then(sizeModifier))
        return
    }

    val filled = tile.hasSound
    // A preset can ship a tile with a label but no recording yet (see
    // BoardRepository.sanitizeMissingSounds) — flag that distinctly from a
    // plain blank tile so it reads as "still needs recording," not "empty."
    val needsRecording = !tile.hasSound && tile.label.isNotBlank()
    val customColor = tile.colorArgb?.let { Color(it) }
    val defaultColor = pageColor.takeIf { filled }
    val containerColor = customColor ?: defaultColor ?: if (filled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = (customColor ?: defaultColor)?.let { textColorFor(it) } ?: if (filled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .then(sizeModifier)
            .alpha(opacity)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = if (performanceModeEnabled) null else LocalIndication.current,
                onClick = onTap,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (filled && !performanceModeEnabled) 2.dp else 0.dp
        ),
        border = if (border.enabled) {
            BorderStroke(border.widthDp.dp, border.resolvedColor())
        } else if (!filled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(TILE_CONTENT_PADDING)
        ) {
            Text(
                text = tileDisplayText(tile, allCaps),
                textAlign = TextAlign.Center,
                maxLines = LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                style = labelTextStyle,
                color = contentColor,
                modifier = Modifier.align(Alignment.Center)
            )
            if (editMode) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit mode",
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                )
            }
            if (needsRecording) {
                Icon(
                    Icons.Filled.MicOff,
                    contentDescription = "Needs recording",
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

/**
 * Black or white, whichever contrasts with [background]. Material3's own
 * `contentColorFor()` only resolves a real color when [background] exactly
 * matches a theme role; for an arbitrary custom tile color it falls back to
 * the ambient theme text color, which is light in dark mode — light text on
 * a light custom background. Deciding from the color's own luminance instead
 * keeps every custom color readable regardless of theme.
 */
private fun textColorFor(background: Color): Color {
    val luminance = background.luminance()
    // WCAG contrast ratio: (lighter + 0.05) / (darker + 0.05). Comparing against
    // black (luminance 0) and white (luminance 1) picks whichever contrasts more;
    // the crossover is at luminance ≈ 0.179, not the naive halfway point of 0.5.
    val contrastWithBlack = (luminance + 0.05f) / 0.05f
    val contrastWithWhite = 1.05f / (luminance + 0.05f)
    return if (contrastWithBlack >= contrastWithWhite) Color.Black else Color.White
}

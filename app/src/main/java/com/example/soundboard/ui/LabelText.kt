package com.example.soundboard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.soundboard.R
import com.example.soundboard.model.LabelFont
import com.example.soundboard.model.LabelStyle
import com.example.soundboard.model.Tile

/** Most lines a tile label wraps to before it ellipsizes. */
internal const val LABEL_MAX_LINES = 3

/** The text a tile shows: its label, "Unnamed" for a filled tile without one, or "+" for a blank tile. */
internal fun tileDisplayText(tile: Tile, allCaps: Boolean): String {
    val text = when {
        tile.label.isNotBlank() -> tile.label
        tile.hasSound -> "Unnamed"
        else -> "+"
    }
    return if (allCaps) text.uppercase() else text
}

private val CondensedFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold)
)

private val AtkinsonHyperlegibleFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold)
)

// One variable font file, pinned to each weight the labels use.
@OptIn(ExperimentalTextApi::class)
private val LexendFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
        Font(R.font.lexend, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

internal fun LabelFont.fontFamily(): FontFamily = when (this) {
    LabelFont.DEFAULT -> FontFamily.Default
    LabelFont.CONDENSED -> CondensedFamily
    LabelFont.SERIF -> FontFamily.Serif
    LabelFont.ATKINSON_HYPERLEGIBLE -> AtkinsonHyperlegibleFamily
    LabelFont.LEXEND -> LexendFamily
}

internal fun LabelFont.displayName(): String = when (this) {
    LabelFont.DEFAULT -> "Default (Roboto)"
    LabelFont.CONDENSED -> "Roboto Condensed"
    LabelFont.SERIF -> "Noto Serif"
    LabelFont.ATKINSON_HYPERLEGIBLE -> "Atkinson Hyperlegible"
    LabelFont.LEXEND -> "Lexend"
}

/**
 * The tile-label text style for [labelStyle] before a size is picked: today's labelLarge,
 * swapped to the chosen font and weight, with line height kept proportional to the size.
 */
@Composable
internal fun baseLabelTextStyle(labelStyle: LabelStyle): TextStyle {
    val labelLarge = MaterialTheme.typography.labelLarge
    return remember(labelLarge, labelStyle.font, labelStyle.bold) {
        labelLarge.copy(
            fontFamily = labelStyle.font.fontFamily(),
            fontWeight = if (labelStyle.bold) FontWeight.Bold else labelLarge.fontWeight,
            // labelLarge's own 20sp-on-14sp spacing, as a ratio so it scales with the size.
            lineHeight = (20f / 14f).em
        )
    }
}

/**
 * The single label size for a grid whose tiles leave a [boxWidthPx] x [boxHeightPx] box for
 * text: the largest whole sp in [LabelStyle.minSizeSp]..[LabelStyle.maxSizeSp] at which every
 * one of [texts] fits (see [labelFits]). Falls back to the minimum when even that doesn't fit —
 * a too-long label then ellipsizes, as it always has.
 */
@Composable
internal fun rememberGridLabelStyle(
    texts: Collection<String>,
    boxWidthPx: Int,
    boxHeightPx: Int,
    labelStyle: LabelStyle
): TextStyle {
    val base = baseLabelTextStyle(labelStyle)
    val measurer = rememberTextMeasurer()
    // Order doesn't change what fits, so a drag-reorder doesn't redo the measuring.
    val distinct = remember(texts) { texts.toSortedSet().toList() }
    return remember(distinct, boxWidthPx, boxHeightPx, base, labelStyle.minSizeSp, labelStyle.maxSizeSp) {
        val min = labelStyle.minSizeSp.toInt()
        val size = if (boxWidthPx <= 0 || boxHeightPx <= 0) {
            min
        } else {
            largestFittingSize(min, labelStyle.maxSizeSp.toInt()) { sp ->
                val style = base.copy(fontSize = sp.sp)
                distinct.all { measurer.labelFits(it, style, boxWidthPx, boxHeightPx) }
            }
        }
        base.copy(fontSize = size.sp)
    }
}

/**
 * Whether [text] fits a [widthPx] x [heightPx] box at [style] within [LABEL_MAX_LINES] lines,
 * without a word having to be split across lines to do it.
 */
private fun TextMeasurer.labelFits(text: String, style: TextStyle, widthPx: Int, heightPx: Int): Boolean {
    val result = measure(
        text = text,
        style = style,
        maxLines = LABEL_MAX_LINES,
        constraints = Constraints(maxWidth = widthPx)
    )
    if (result.hasVisualOverflow || result.size.height > heightPx) return false
    val lineEnds = (0 until result.lineCount - 1).map { result.getLineEnd(it) }
    return !breaksInsideWord(text, lineEnds)
}

/**
 * The largest size in [minSp]..[maxSp] for which [fits] holds, assuming anything that fits
 * at one size also fits at every smaller one; [minSp] if nothing does.
 */
internal fun largestFittingSize(minSp: Int, maxSp: Int, fits: (Int) -> Boolean): Int {
    var low = minSp
    var high = maxSp
    var best = minSp
    while (low <= high) {
        val mid = (low + high) / 2
        if (fits(mid)) {
            best = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
}

/** Whether any soft line break in [text] (at the offsets [lineEnds]) lands in the middle of a word. */
internal fun breaksInsideWord(text: String, lineEnds: List<Int>): Boolean = lineEnds.any { end ->
    end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() &&
        text[end - 1] != '-'
}

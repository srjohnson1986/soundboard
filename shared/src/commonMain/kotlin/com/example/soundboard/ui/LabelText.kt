package com.example.soundboard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.soundboard.model.LabelFont
import com.example.soundboard.resources.Res
import com.example.soundboard.resources.atkinson_hyperlegible_bold
import com.example.soundboard.resources.atkinson_hyperlegible_regular
import com.example.soundboard.resources.lexend
import org.jetbrains.compose.resources.Font
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

// The bundled fonts come from Compose Resources, whose Font() is @Composable (on the web
// it loads the file asynchronously), so these families are built in composition.
@Composable
private fun atkinsonHyperlegibleFamily() = FontFamily(
    Font(Res.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(Res.font.atkinson_hyperlegible_bold, FontWeight.Bold)
)

// One variable font file, pinned to each weight the labels use.
@OptIn(ExperimentalTextApi::class)
@Composable
private fun lexendFamily() = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
        Font(Res.font.lexend, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

@Composable
internal fun LabelFont.fontFamily(): FontFamily = when (this) {
    LabelFont.DEFAULT -> FontFamily.Default
    LabelFont.CONDENSED -> condensedFontFamily
    LabelFont.SERIF -> FontFamily.Serif
    LabelFont.ATKINSON_HYPERLEGIBLE -> atkinsonHyperlegibleFamily()
    LabelFont.LEXEND -> lexendFamily()
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
    val fontFamily = labelStyle.font.fontFamily()
    return remember(labelLarge, fontFamily, labelStyle.bold) {
        labelLarge.copy(
            fontFamily = fontFamily,
            fontWeight = if (labelStyle.bold) FontWeight.Bold else labelLarge.fontWeight,
            // labelLarge's own 20sp-on-14sp spacing, as a ratio so it scales with the size.
            lineHeight = (20f / 14f).em
        )
    }
}

/** The smallest a single label shrinks to, below its grid's size, to keep a word whole. */
internal const val LABEL_FLOOR_SP = 10

/**
 * The label styles for one grid: [base] for every label, except the few in [exceptions]
 * (keyed by display text) that only fit at a smaller size — see [rememberGridLabelStyle].
 */
internal class GridLabelStyle(val base: TextStyle, private val exceptions: Map<String, TextStyle> = emptyMap()) {
    fun forText(text: String): TextStyle = exceptions[text] ?: base
}

/**
 * The label styles for a grid whose tiles leave a [boxWidthPx] x [boxHeightPx] box for text.
 * Every label shares one size: the largest whole sp in [LabelStyle.minSizeSp]..[LabelStyle.maxSizeSp]
 * at which all of [texts] fit (see [labelFits]), or the minimum when even that doesn't fit.
 * A label that still doesn't fit at that size (typically one word wider than its tile, which
 * would otherwise be split mid-word, #209) gets a size of its own, as large as lets it fit,
 * down to [LABEL_FLOOR_SP]; one that fits at no size keeps the shared one and ellipsizes.
 */
@Composable
internal fun rememberGridLabelStyle(
    texts: Collection<String>,
    boxWidthPx: Int,
    boxHeightPx: Int,
    labelStyle: LabelStyle
): GridLabelStyle {
    val base = baseLabelTextStyle(labelStyle)
    val measurer = rememberTextMeasurer()
    // Order doesn't change what fits, so a drag-reorder doesn't redo the measuring.
    val distinct = remember(texts) { texts.distinct().sorted() }
    return remember(distinct, boxWidthPx, boxHeightPx, base, labelStyle.minSizeSp, labelStyle.maxSizeSp) {
        val min = labelStyle.minSizeSp.toInt()
        if (boxWidthPx <= 0 || boxHeightPx <= 0) return@remember GridLabelStyle(base.copy(fontSize = min.sp))
        fun fits(text: String, sp: Int) = measurer.labelFits(text, base.copy(fontSize = sp.sp), boxWidthPx, boxHeightPx)
        val size = largestFittingSize(min, labelStyle.maxSizeSp.toInt()) { sp -> distinct.all { fits(it, sp) } }
        val exceptions = labelSizeExceptions(distinct, size, LABEL_FLOOR_SP, ::fits)
        GridLabelStyle(base.copy(fontSize = size.sp), exceptions.mapValues { (_, sp) -> base.copy(fontSize = sp.sp) })
    }
}

/**
 * The labels among [texts] that don't fit at their grid's shared [gridSp], each with the
 * largest size in [floorSp] until [gridSp] at which it does. A label that fits at [gridSp], or
 * at no smaller size either, isn't in the result.
 */
internal fun labelSizeExceptions(
    texts: Collection<String>,
    gridSp: Int,
    floorSp: Int,
    fits: (text: String, sp: Int) -> Boolean
): Map<String, Int> {
    if (floorSp >= gridSp) return emptyMap()
    return texts.filterNot { fits(it, gridSp) }.mapNotNull { text ->
        val sp = largestFittingSize(floorSp, gridSp - 1) { fits(text, it) }
        if (fits(text, sp)) text to sp else null
    }.toMap()
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

package com.example.soundboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.soundboard.model.Tile
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Longest side a tile picture is kept at in memory: plenty for a tile, and a whole grid of photos stays small. */
internal const val TILE_IMAGE_MAX_SIDE_PX = 384

/** Longest side a picture is kept at for Show mode's full screen. */
internal const val SHOW_IMAGE_MAX_SIDE_PX = 1280

/** How much of the smaller side of its box an emoji fills. */
private const val EMOJI_FILL = 0.62f

/**
 * Loads tile pictures for the board: reads a file's bytes, decodes them and shrinks the result
 * to [TILE_IMAGE_MAX_SIDE_PX], then remembers it, so a photo is decoded once however many times
 * its tile is drawn. Decoding one at a time keeps the peak memory to a single full-size photo.
 */
internal class TileImages(private val read: suspend (String) -> ByteArray?) {
    private val cache = mutableMapOf<Pair<String, Int>, ImageBitmap>()
    private val lock = Mutex()

    /** [name] shrunk to at most [maxSide] px on its longer side; a tile uses the default, Show mode asks for more. */
    suspend fun load(name: String, maxSide: Int = TILE_IMAGE_MAX_SIDE_PX): ImageBitmap? {
        val key = name to maxSide
        cache[key]?.let { return it }
        return lock.withLock {
            cache[key] ?: withContext(Dispatchers.Default) {
                runCatching { read(name)?.decodeToImageBitmap()?.scaledDownTo(maxSide) }.getOrNull()
            }?.also { cache[key] = it }
        }
    }
}

/** Where tile pictures come from; the board screen provides the real one, anything else gets none. */
internal val LocalTileImages = compositionLocalOf { TileImages { null } }

/** This bitmap shrunk so its longer side is at most [maxSide], keeping its shape; itself if it's already that small. */
internal fun ImageBitmap.scaledDownTo(maxSide: Int): ImageBitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val scale = maxSide.toFloat() / longest
    val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val newHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = ImageBitmap(newWidth, newHeight)
    Canvas(scaled).drawImageRect(
        image = this,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(newWidth, newHeight),
        paint = Paint().apply { filterQuality = FilterQuality.Medium }
    )
    return scaled
}

/**
 * The large picture or emoji of [tile], filling its box (the caller sizes it). A picture is
 * [ContentScale.Fit], so nothing of it is cropped; it draws nothing while it loads or if it can't.
 */
@Composable
internal fun TileIcon(tile: Tile, color: Color, modifier: Modifier = Modifier, maxSidePx: Int = TILE_IMAGE_MAX_SIDE_PX) {
    val imageName = tile.imageFileName
    if (imageName != null) {
        val images = LocalTileImages.current
        val bitmap by produceState<ImageBitmap?>(null, imageName, images, maxSidePx) { value = images.load(imageName, maxSidePx) }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = tile.label.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = modifier.fillMaxSize()
            )
        }
        return
    }
    val emoji = tile.emoji?.takeIf { it.isNotBlank() } ?: return
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Dp to sp through the density, so the glyph keeps its share of the box at any font scale.
        val size = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * EMOJI_FILL).toSp() }
        Text(
            text = emoji,
            style = TextStyle(fontSize = size, lineHeight = size, color = color),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

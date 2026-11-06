package dev.aiharness.app.ui.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.theme.Stationery
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.Lamplight
import dev.aiharness.core.model.OutlineWidth
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerSettings
import dev.aiharness.core.model.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The sticker settings in force (size, outline width), provided at the top of the app. */
val LocalStickerSettings = staticCompositionLocalOf { StickerSettings() }

/** How a sticker meets the paper: die-cut with an outline, inked (stamps), or plain (tape). */
enum class StickerFinish { DIE_CUT, INKED, PLAIN }

/** A sticker's picture, baked with its outline and shadow, and how it meets the paper. */
class BakedSticker(val image: ImageBitmap, val finish: StickerFinish)

fun StickerArt?.finish(): StickerFinish = when (this?.kind) {
    StickerArtKind.STAMP -> StickerFinish.INKED
    StickerArtKind.DECOR -> if (name.startsWith("tape")) StickerFinish.PLAIN else StickerFinish.DIE_CUT
    else -> StickerFinish.DIE_CUT
}

/**
 * Baked sticker pictures. The shape fills [CONTENT] pixels, with [PAD] of clear space on each side
 * for the outline and shadow, so a sticker drawn at a size draws its picture [BOX] times larger.
 */
object StickerBaker {
    const val CONTENT = 240
    private const val PAD = 40
    const val BOX: Float = (CONTENT + 2f * PAD) / CONTENT

    private val cache = LruCache<String, BakedSticker>(96)

    fun key(ref: StickerRef, outline: Color, width: OutlineWidth) = when (ref) {
        is StickerRef.Emoji -> "e:${ref.emoji}"
        is StickerRef.Art -> "a:${ref.artId}"
    } + ":${outline.toArgb()}:${width.name}"

    fun cached(key: String): BakedSticker? = cache.get(key)

    /** Bakes [source] (any size, clear around the shape) with its outline; null if it's empty. */
    fun bake(key: String, source: Bitmap, finish: StickerFinish, outline: Color, width: OutlineWidth): BakedSticker? {
        val fitted = fit(source) ?: return null
        val w = fitted.width
        val h = fitted.height
        val pixels = IntArray(w * h).also { fitted.getPixels(it, 0, w, 0, 0, w, h) }
        val baked = when (finish) {
            StickerFinish.DIE_CUT -> DieCut.bake(pixels, w, h, outline.toArgb(), width.dp / NOMINAL_DP * CONTENT)
            StickerFinish.PLAIN -> DieCut.bake(pixels, w, h, 0, 0f, shadow = true)
            StickerFinish.INKED -> pixels
        }
        val sticker = BakedSticker(Bitmap.createBitmap(baked, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(), finish)
        cache.put(key, sticker)
        return sticker
    }

    /** The emoji drawn as large as fits the shape's square. */
    fun emoji(emoji: String): Bitmap {
        val bitmap = Bitmap.createBitmap(CONTENT, CONTENT, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = CONTENT * 0.8f; textAlign = Paint.Align.CENTER }
        val bounds = Rect().also { paint.getTextBounds(emoji, 0, emoji.length, it) }
        Canvas(bitmap).drawText(emoji, CONTENT / 2f, CONTENT / 2f - bounds.exactCenterY(), paint)
        return bitmap
    }

    /** Trims clear margins and scales the shape into the square, centred, with padding around. */
    private fun fit(source: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 8) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        val cw = right - left + 1
        val ch = bottom - top + 1
        val scale = CONTENT.toFloat() / max(cw, ch)
        val side = CONTENT + 2 * PAD
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val dw = (cw * scale).roundToInt()
        val dh = (ch * scale).roundToInt()
        val dx = (side - dw) / 2
        val dy = (side - dh) / 2
        Canvas(out).drawBitmap(source, Rect(left, top, right + 1, bottom + 1), Rect(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** The size the outline widths in the settings are measured against (a medium sticker). */
    private const val NOMINAL_DP = 52f
}

/** The outline colour for a sticker stuck by someone whose ink is [ownerInk]. */
fun Stationery.outlineFor(ownerInk: Color): Color = when (outlineSetting) {
    ThemeSettings.OUTLINE_PAPER -> dayColors.kami
    ThemeSettings.OUTLINE_INK -> ownerInk
    else -> outline
}

/**
 * Lamplight: at night, things on the desk keep their day colours, dimmed a little and warmed as if
 * by a desk lamp (white reads as warm off-white). Null by day, or when the setting leaves them be.
 */
fun Stationery.deskLight(): ColorFilter? =
    if (!isNight || lamplight == Lamplight.NONE) null else ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.90f, 0f, 0f, 0f, 0f,
                0f, 0.84f, 0f, 0f, 0f,
                0f, 0f, 0.72f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

/**
 * One sticker's picture, filling [modifier]'s box (which is [StickerBaker.BOX] times the sticker's
 * size, for the outline and shadow). Emoji are drawn with the phone's emoji font; pictures come
 * from the sticker's art.
 */
@Composable
fun StickerPicture(ref: StickerRef, ownerInk: Color, modifier: Modifier = Modifier, name: String? = null) {
    val container = LocalAppContainer.current
    val s = stationery
    val width = LocalStickerSettings.current.outlineWidth
    val outline = s.outlineFor(ownerInk)
    val key = StickerBaker.key(ref, outline, width)
    val baked by produceState(StickerBaker.cached(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            when (ref) {
                is StickerRef.Emoji -> StickerBaker.bake(key, StickerBaker.emoji(ref.emoji), StickerFinish.DIE_CUT, outline, width)
                is StickerRef.Art -> {
                    val art = container.harness.stickers.getArt(ref.artId) ?: return@withContext null
                    val bytes = withContext(Dispatchers.IO) { container.blobStore.get(art.imageRef) } ?: return@withContext null
                    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    StickerBaker.bake(key, source, art.finish(), outline, width)
                }
            }
        }
    }
    val filter = s.deskLight()
    val label = name ?: when (ref) {
        is StickerRef.Emoji -> ref.emoji
        is StickerRef.Art -> "sticker"
    }
    // Described once it's drawn, so a screen reader (and a test) finds it only when it's there.
    Box(if (baked != null) modifier.semantics { contentDescription = label } else modifier) {
        val sticker = baked ?: return@Box
        val image = sticker.image
        Canvas(Modifier.matchParentSize()) {
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = filter,
                blendMode = if (sticker.finish == StickerFinish.INKED && !s.isNight) BlendMode.Multiply else BlendMode.SrcOver,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

package dev.aiharness.app.ui.stickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import dev.aiharness.app.platform.LruCache
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
    internal const val PAD = 40
    const val BOX: Float = (CONTENT + 2f * PAD) / CONTENT

    private val cache = LruCache<String, BakedSticker>(96)

    fun key(ref: StickerRef, outline: Color, width: OutlineWidth) = when (ref) {
        is StickerRef.Emoji -> "e:${ref.emoji}"
        is StickerRef.Art -> "a:${ref.artId}"
    } + ":${outline.toArgb()}:${width.name}"

    fun cached(key: String): BakedSticker? = cache.get(key)

    /** Bakes [fitted] (see [StickerRaster]) with its outline; null if there's nothing to bake. */
    internal fun bake(key: String, fitted: StickerPixels?, finish: StickerFinish, outline: Color, width: OutlineWidth): BakedSticker? {
        if (fitted == null) return null
        val w = fitted.width
        val h = fitted.height
        val pixels = fitted.argb
        val baked = when (finish) {
            StickerFinish.DIE_CUT -> DieCut.bake(pixels, w, h, outline.toArgb(), width.dp / NOMINAL_DP * CONTENT)
            StickerFinish.PLAIN -> DieCut.bake(pixels, w, h, 0, 0f, shadow = true)
            StickerFinish.INKED -> pixels
        }
        val sticker = BakedSticker(StickerRaster.image(baked, w, h), finish)
        cache.put(key, sticker)
        return sticker
    }

    /** The size the outline widths in the settings are measured against (a medium sticker). */
    private const val NOMINAL_DP = 52f
}

/** A sticker's picture as ARGB pixels, trimmed and fitted into the baking square (see [StickerRaster]). */
internal class StickerPixels(val width: Int, val height: Int, val argb: IntArray)

/**
 * The platform's drawing for stickers. Each picture is trimmed of clear margins and scaled into the
 * [StickerBaker.CONTENT] square, centred, with [StickerBaker.PAD] around it for the outline.
 */
internal expect object StickerRaster {
    /** The emoji drawn as large as fits the shape's square, with the device's emoji font. */
    fun emoji(emoji: String): StickerPixels?

    /** A picture from its bytes; null if it isn't one, or it's all clear. */
    fun picture(bytes: ByteArray): StickerPixels?

    fun image(argb: IntArray, width: Int, height: Int): ImageBitmap
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
                is StickerRef.Emoji -> StickerBaker.bake(key, StickerRaster.emoji(ref.emoji), StickerFinish.DIE_CUT, outline, width)
                is StickerRef.Art -> {
                    val art = container.harness.stickers.getArt(ref.artId) ?: return@withContext null
                    val bytes = withContext(Dispatchers.IO) { container.blobStore.get(art.imageRef) } ?: return@withContext null
                    StickerBaker.bake(key, StickerRaster.picture(bytes), art.finish(), outline, width)
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

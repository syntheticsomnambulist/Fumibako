package dev.aiharness.core.stickers

import dev.aiharness.core.image.ImageService
import dev.aiharness.core.image.PicturePrompt
import dev.aiharness.core.model.ImageGenSettings
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.StickerRepository
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.newId

/**
 * Cuts a drawn sticker out of its plain background and returns it as a PNG with a clear one, or
 * null when it can't. The host does this (on Android, with bitmaps); see `DieCut` in the app.
 */
fun interface StickerCutter {
    suspend fun cutOut(bytes: ByteArray): ByteArray?
}

/**
 * Draws stickers: for a persona, from how it looks, what it's wearing and its drawing style; for
 * the shared library, from a description alone. Drawn on plain white, then cut out.
 */
class StickerMaker(
    private val images: ImageService,
    private val blobs: BlobStore,
    private val stickers: StickerRepository,
    private val cutter: StickerCutter,
    private val clock: Clock,
) {
    /**
     * Draws a sticker of what [shows] (e.g. "sleepy, hugging a pillow") and returns the cut-out
     * picture's blob ref, not yet on any sheet: [keep] puts it there, or [discard] throws it away.
     * With a [persona] it's of the persona; without, of the thing described.
     */
    suspend fun draw(settings: ImageGenSettings, persona: Persona?, shows: String): String {
        val scene = if (persona != null) "$STICKER_TAGS, $shows" else "$THING_TAGS, $shows"
        val image = images.generate(
            settings = settings,
            prompt = scene,
            negativePrompt = NEGATIVE,
            orientation = "square",
            persona = persona,
            parts = if (persona != null) PicturePrompt.Parts() else PicturePrompt.Parts.STYLE_ONLY,
        )
        val drawn = blobs.get(image.ref) ?: error("The drawing went missing")
        val cut = cutter.cutOut(drawn) ?: error("Couldn't cut the sticker out of its background")
        blobs.delete(image.ref)
        return blobs.put(cut, "png")
    }

    /** Puts a drawn sticker on [ownerPersonaId]'s sheet (or the shared library for null) as `:name:`. */
    suspend fun keep(
        imageRef: String,
        ownerPersonaId: String?,
        name: String,
        feeling: String? = null,
        prompt: String? = null,
        kind: StickerArtKind = StickerArtKind.STICKER,
    ): StickerArt {
        val art = StickerArt(
            id = newId(),
            name = uniqueName(slug(name), ownerPersonaId),
            ownerPersonaId = ownerPersonaId,
            kind = kind,
            imageRef = imageRef,
            feeling = feeling?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            prompt = prompt,
            createdAt = clock.nowMillis(),
        )
        stickers.upsertArt(art)
        return art
    }

    suspend fun discard(imageRef: String) = blobs.delete(imageRef)

    /** Takes a sticker off its sheet for good, with its pictures. Stickers already stuck keep a placeholder. */
    suspend fun remove(art: StickerArt) {
        stickers.deleteArt(art.id)
        blobs.delete(art.imageRef)
        art.outlinedRef?.let { blobs.delete(it) }
    }

    /** A name for a sticker of [persona] showing [shows]: `aria-sleepy`. */
    fun suggestName(persona: Persona?, shows: String): String {
        val words = slug(shows).split('-').filter { it.length > 2 }.take(2)
        return listOfNotNull(persona?.name?.let(::slug)?.takeIf { it.isNotEmpty() }).plus(words).joinToString("-").ifEmpty { "sticker" }
    }

    /** [name] made unique on its sheet and among the shared ones (which every persona sees too). */
    private suspend fun uniqueName(name: String, ownerPersonaId: String?): String {
        val taken = (stickers.art(ownerPersonaId) + stickers.art(null)).map { it.name }.toSet()
        if (name !in taken) return name
        return (2..999).asSequence().map { "$name-$it" }.first { it !in taken }
    }

    companion object {
        /** Most stickers on one persona's sheet. */
        const val SHEET_SIZE = 24

        private const val STICKER_TAGS = "chibi, sticker, cute, simple shapes, clean lines, white background, plain background, centered, single character"
        private const val THING_TAGS = "sticker, cute, simple shapes, clean lines, white background, plain background, centered, single object"
        private const val NEGATIVE = "busy background, scenery, text, watermark, multiple views, cropped"

        /** `:Aria Wave!:` becomes `aria-wave`: lowercase letters, digits and dashes. */
        fun slug(text: String): String =
            text.trim().trim(':').lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32)
    }
}

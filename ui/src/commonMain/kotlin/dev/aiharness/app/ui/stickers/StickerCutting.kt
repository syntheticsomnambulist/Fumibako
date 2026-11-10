package dev.aiharness.app.ui.stickers

import dev.aiharness.core.stickers.StickerCutter

/** Cuts drawn stickers out of their plain white background, on the device (see [DieCut.cutOutPlainBackground]). */
expect object PlainBackgroundCutter : StickerCutter {
    override suspend fun cutOut(bytes: ByteArray): ByteArray?
}

/** A sticker cut from a photo: a PNG, and whether the subject was found (else a plain background was cut away). */
class PhotoCut(val png: ByteArray, val bySegmentation: Boolean)

/**
 * Cuts the subject out of a photo for a sticker. On the phone that's ML Kit's subject segmentation
 * (Google Play services), else cutting away a plain background; on a computer, the plain background.
 */
expect object PhotoCutter {
    suspend fun cutOut(bytes: ByteArray): PhotoCut?
}

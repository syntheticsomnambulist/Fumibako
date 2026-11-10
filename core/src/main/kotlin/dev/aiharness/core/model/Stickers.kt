package dev.aiharness.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Stickers (v0.3; see docs/design/README.md#stickers). A sticker is an overlay on something, never
 * part of its text, so placing one on an old letter doesn't rewrite history.
 */

/** What a sticker is on. A kind and an id rather than only a message, so the planner can take them too. */
@Serializable
data class StickerTarget(val kind: StickerTargetKind, val id: String) {
    companion object {
        fun message(id: String) = StickerTarget(StickerTargetKind.MESSAGE, id)

        /** The note being written in a conversation, before it's sent. */
        fun draft(conversationId: String) = StickerTarget(StickerTargetKind.DRAFT, conversationId)

        /**
         * An attachment (by its id, or a picture by its ref). Stickers stay stuck: one on a cassette
         * shows wherever that cassette does, in the letter or kept in the memory book.
         */
        fun attachment(id: String) = StickerTarget(StickerTargetKind.ATTACHMENT, id)

        /** A day in one of your personas' planners ("2026-11-06"). */
        fun day(plannerId: String, day: String) = StickerTarget(StickerTargetKind.DAY, "$plannerId/$day")

        /** An entry in a planner: a sticker on a plan goes with it when it moves to another day. */
        fun entry(id: String) = StickerTarget(StickerTargetKind.ENTRY, id)

        /** The "conversation" a planner's stickers are kept under, so a planner's limits and listing work as a letter's do. */
        fun plannerConversation(plannerId: String) = "planner:$plannerId"
    }
}

@Serializable
enum class StickerTargetKind { MESSAGE, DRAFT, ATTACHMENT, DAY, ENTRY }

/** What's stuck: an emoji, or a picture from a sticker sheet or the shared library. */
@Serializable
sealed interface StickerRef {
    /** An emoji, with the name it's known by (`tada` for 🎉). */
    @Serializable
    @SerialName("emoji")
    data class Emoji(val emoji: String, val name: String) : StickerRef

    @Serializable
    @SerialName("art")
    data class Art(val artId: String) : StickerRef
}

/**
 * One sticker, stuck on [target]. [x] and [y] place its centre as fractions of the target's box
 * (0 to 1); [scale] is relative to the usual size. Peeling keeps the row with [peeledAt] set until
 * the persona has been told, then it can go.
 */
@Serializable
data class Sticker(
    val id: String,
    /** The conversation it belongs to, when it's on a letter or a draft. */
    val conversationId: String?,
    val target: StickerTarget,
    val ref: StickerRef,
    /** Who stuck it: a persona's id, or null for you. */
    val byPersonaId: String? = null,
    val x: Float,
    val y: Float,
    val rotationDeg: Float = 0f,
    val scale: Float = 1f,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val peeledAt: Long? = null,
    /** Who peeled it: a persona's id, or null for you. */
    val peeledByPersonaId: String? = null,
) {
    val isPeeled: Boolean get() = peeledAt != null
}

@Serializable
enum class StickerArtKind {
    /** Die-cut, with an outline. */
    STICKER,

    /** Inked onto the paper. */
    STAMP,

    /** Tape, pressed leaves and the like. */
    DECOR,
}

/**
 * A picture that can be stuck: one of a persona's own (on its sheet) or one from the shared
 * library, which every persona can use. Named like custom emoji, `:name:`, which is how personas
 * choose them.
 */
@Serializable
data class StickerArt(
    val id: String,
    /** Without colons, e.g. `aria-wave`. */
    val name: String,
    /** The persona whose sheet it's on, or null for the shared library. */
    val ownerPersonaId: String? = null,
    val kind: StickerArtKind = StickerArtKind.STICKER,
    /** The cut-out picture, without the outline. */
    val imageRef: String,
    /** The picture with its outline baked in, redone when the outline changes. */
    val outlinedRef: String? = null,
    /** The feeling it stands for, so it can be a persona's mark for that feeling. */
    val feeling: String? = null,
    /** What it shows, as asked for when it was drawn, for drawing it again. */
    val prompt: String? = null,
    val createdAt: Long,
)

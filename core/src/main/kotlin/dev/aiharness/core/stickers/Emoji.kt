package dev.aiharness.core.stickers

/**
 * Emoji that can be stuck as stickers, with the names they go by (Discord-style, so personas and
 * people can write `:tada:`). Grouped as the sticker tray shows them. Any other emoji can still be
 * stuck by writing the emoji itself.
 */
object Emoji {
    data class Entry(val emoji: String, val name: String)

    data class Group(val label: String, val entries: List<Entry>)

    val groups: List<Group> = listOf(
        group(
            "Faces",
            "😊" to "blush", "😄" to "smile", "😂" to "joy", "🥹" to "holding_back_tears", "🥺" to "pleading",
            "😭" to "sob", "😍" to "heart_eyes", "🥰" to "smiling_face_with_hearts", "😘" to "kissing_heart",
            "😌" to "relieved", "😴" to "sleeping", "🥱" to "yawning_face", "😳" to "flushed", "🤔" to "thinking",
            "🫣" to "face_with_peeking_eye", "😮" to "open_mouth", "😤" to "triumph", "😠" to "angry", "😔" to "pensive",
            "🙃" to "upside_down", "😅" to "sweat_smile", "🤗" to "hugging", "🤭" to "hand_over_mouth", "😎" to "sunglasses",
            "🥳" to "partying_face", "🤩" to "star_struck", "😇" to "innocent", "🫠" to "melting_face", "😶" to "no_mouth",
        ),
        group(
            "Hearts",
            "❤️" to "heart", "🩷" to "pink_heart", "🧡" to "orange_heart", "💛" to "yellow_heart", "💚" to "green_heart",
            "💙" to "blue_heart", "💜" to "purple_heart", "🤍" to "white_heart", "💕" to "two_hearts", "💖" to "sparkling_heart",
            "💗" to "heartpulse", "💌" to "love_letter", "💔" to "broken_heart", "❣️" to "heart_exclamation",
        ),
        group(
            "Hands",
            "👋" to "wave", "👏" to "clap", "🙌" to "raised_hands", "👍" to "thumbsup", "🤝" to "handshake", "🙏" to "pray",
            "✌️" to "v", "🫶" to "heart_hands", "💪" to "muscle", "🤞" to "crossed_fingers", "👀" to "eyes", "🫡" to "saluting_face",
        ),
        group(
            "Seasons",
            "🌸" to "cherry_blossom", "🌷" to "tulip", "🌻" to "sunflower", "🌿" to "herb", "🍀" to "four_leaf_clover",
            "🍁" to "maple_leaf", "🍂" to "fallen_leaf", "🎃" to "jack_o_lantern", "❄️" to "snowflake", "⛄" to "snowman",
            "🌙" to "crescent_moon", "⭐" to "star", "🌟" to "star2", "✨" to "sparkles", "☀️" to "sunny", "🌈" to "rainbow",
            "☔" to "umbrella", "🌊" to "ocean", "🔥" to "fire", "🎐" to "wind_chime", "🎑" to "rice_scene", "🎍" to "bamboo",
        ),
        group(
            "Food",
            "🍵" to "tea", "☕" to "coffee", "🧋" to "bubble_tea", "🍙" to "rice_ball", "🍡" to "dango", "🍰" to "cake",
            "🍓" to "strawberry", "🍑" to "peach", "🍜" to "ramen", "🍪" to "cookie", "🍫" to "chocolate_bar", "🍷" to "wine_glass",
        ),
        group(
            "Things",
            "🎉" to "tada", "🎂" to "birthday", "🎁" to "gift", "🎀" to "ribbon", "🎈" to "balloon", "📮" to "postbox",
            "✉️" to "envelope", "📚" to "books", "✏️" to "pencil2", "🎵" to "musical_note", "🎧" to "headphones", "📷" to "camera",
            "💡" to "bulb", "🔑" to "key", "🕯️" to "candle", "🏆" to "trophy", "💯" to "100", "💤" to "zzz", "❗" to "exclamation",
            "❓" to "question", "✅" to "white_check_mark",
        ),
        group(
            "Animals",
            "🐈" to "cat2", "🐱" to "cat", "🐶" to "dog", "🐰" to "rabbit", "🐻" to "bear", "🐼" to "panda_face",
            "🦊" to "fox", "🐸" to "frog", "🐧" to "penguin", "🦉" to "owl", "🐝" to "bee", "🦋" to "butterfly",
            "🐟" to "fish", "🐢" to "turtle",
        ),
    )

    val all: List<Entry> = groups.flatMap { it.entries }

    private val byName: Map<String, Entry> = all.associateBy { it.name }
    private val byEmoji: Map<String, Entry> = all.associateBy { strip(it.emoji) }

    /** The emoji for `tada` or `:tada:`, or null when the name isn't known. */
    fun forName(name: String): Entry? = byName[name.trim().trim(':').lowercase()]

    /** The entry for an emoji character, ignoring variation selectors. */
    fun forEmoji(emoji: String): Entry? = byEmoji[strip(emoji.trim())]

    /**
     * Whether [text] looks like a single emoji (so a persona can stick one we have no name for).
     * Loose on purpose: anything short with no letters or digits that starts outside ASCII.
     */
    fun looksLikeEmoji(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.length <= 16 && t[0].code > 0x2000 && t.none { it.isLetterOrDigit() }
    }

    private fun strip(emoji: String) = emoji.replace("️", "")

    private fun group(label: String, vararg pairs: Pair<String, String>) = Group(label, pairs.map { (e, n) -> Entry(e, n) })
}

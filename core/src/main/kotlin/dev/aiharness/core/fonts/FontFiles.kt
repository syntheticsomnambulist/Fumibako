package dev.aiharness.core.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reading a font file someone adds as a hand ("+ Add a font file…"): a .ttf or .otf, or a
 * collection's first face. Only what the sheet needs: its name, whether it has Latin and Japanese,
 * and its x-height, so its size can be matched to the letters. The face itself is loaded by the
 * platform; a file it can't load is refused there.
 */
object FontFiles {
    /** Fonts are kept on the device, up to this size each (Japanese fonts are large). */
    const val MOST_BYTES = 20L shl 20

    /** Yomogi's x-height (of its em), which the other hands are matched to. */
    const val YOMOGI_X_HEIGHT = 0.475f

    data class Info(val name: String, val latin: Boolean, val japanese: Boolean, val xHeight: Float?) {
        /** How much to scale it so its x-height sits with the default hand's (and so the letters'). */
        val scale: Float get() = xHeight?.takeIf { it > 0.2f && it < 0.9f }?.let { (YOMOGI_X_HEIGHT / it).coerceIn(0.7f, 1.5f) } ?: 1f
    }

    sealed interface Read {
        class Font(val info: Info) : Read

        /** Not a font this can use: [why], in plain words. */
        class Not(val why: String) : Read
    }

    fun read(bytes: ByteArray): Read {
        if (bytes.size > MOST_BYTES) return Read.Not("That font is larger than 20 MB.")
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (bytes.size < 12) return Read.Not("That isn't a font file.")
        var start = 0
        if (tag(b, 0) == "ttcf") {
            if (bytes.size < 16 || b.getInt(8) < 1) return Read.Not("That font collection is empty.")
            start = b.getInt(12)
        }
        val version = b.getInt(start)
        if (version != 0x00010000 && tag(b, start) != "OTTO" && tag(b, start) != "true") return Read.Not("That isn't a TrueType or OpenType font.")
        val tables = HashMap<String, Int>()
        val count = b.getShort(start + 4).toInt() and 0xFFFF
        for (i in 0 until count) {
            val at = start + 12 + i * 16
            if (at + 16 > bytes.size) return Read.Not("That font file is damaged.")
            tables[tag(b, at)] = b.getInt(at + 8)
        }
        val name = tables["name"]?.let { runCatching { familyName(b, it) }.getOrNull() }
        val cmap = tables["cmap"]?.let { runCatching { covered(b, it) }.getOrNull() } ?: return Read.Not("That font has no characters this can read.")
        val unitsPerEm = tables["head"]?.let { b.getShort(it + 18).toInt() and 0xFFFF }?.takeIf { it > 0 }
        val xHeight = tables["OS/2"]?.let { os2 ->
            val v = b.getShort(os2).toInt() and 0xFFFF
            if (v >= 2 && unitsPerEm != null) (b.getShort(os2 + 86).toInt()).toFloat() / unitsPerEm else null
        }
        val latin = "abcxyzABC".all { it.code in cmap }
        val japanese = "あアの日本".all { it.code in cmap }
        if (!latin && !japanese) return Read.Not("That font has neither Latin nor Japanese letters.")
        return Read.Font(Info(name?.trim()?.takeIf { it.isNotEmpty() } ?: "Your font", latin, japanese, xHeight))
    }

    private fun tag(b: ByteBuffer, at: Int): String = String(ByteArray(4) { b.get(at + it) }, Charsets.ISO_8859_1)

    /** The typographic family name (16), else the family (1); Windows English first. */
    private fun familyName(b: ByteBuffer, at: Int): String? {
        val count = b.getShort(at + 2).toInt() and 0xFFFF
        val strings = at + (b.getShort(at + 4).toInt() and 0xFFFF)
        data class Entry(val platform: Int, val language: Int, val id: Int, val text: String)
        val entries = (0 until count).mapNotNull { i ->
            val r = at + 6 + i * 12
            val platform = b.getShort(r).toInt() and 0xFFFF
            val language = b.getShort(r + 4).toInt() and 0xFFFF
            val id = b.getShort(r + 6).toInt() and 0xFFFF
            if (id != 1 && id != 16) return@mapNotNull null
            val length = b.getShort(r + 8).toInt() and 0xFFFF
            val offset = b.getShort(r + 10).toInt() and 0xFFFF
            val raw = ByteArray(length) { b.get(strings + offset + it) }
            val text = if (platform == 3 || platform == 0) String(raw, Charsets.UTF_16BE) else String(raw, Charsets.ISO_8859_1)
            Entry(platform, language, id, text)
        }
        return entries.sortedWith(compareBy({ if (it.id == 16) 0 else 1 }, { if (it.platform == 3 && it.language == 0x409) 0 else if (it.platform == 3) 1 else 2 }))
            .firstOrNull()?.text
    }

    /** The characters the font has (from its Unicode cmap: format 4 or 12). */
    private fun covered(b: ByteBuffer, at: Int): Set<Int> {
        val count = b.getShort(at + 2).toInt() and 0xFFFF
        val subtables = (0 until count).map { i ->
            val r = at + 4 + i * 8
            Triple(b.getShort(r).toInt() and 0xFFFF, b.getShort(r + 2).toInt() and 0xFFFF, at + b.getInt(r + 4))
        }
        val best = subtables.sortedBy { (p, e, _) -> if (p == 3 && e == 10) 0 else if (p == 0) 1 else if (p == 3 && e == 1) 2 else 3 }
        for ((_, _, offset) in best) {
            when (b.getShort(offset).toInt() and 0xFFFF) {
                4 -> return format4(b, offset)
                12 -> return format12(b, offset)
            }
        }
        return emptySet()
    }

    private val SAMPLE = ("abcxyzABC" + "あアの日本").map { it.code }

    private fun format4(b: ByteBuffer, at: Int): Set<Int> {
        val segments = (b.getShort(at + 6).toInt() and 0xFFFF) / 2
        val ends = at + 14
        val starts = ends + segments * 2 + 2
        val found = HashSet<Int>()
        for (i in 0 until segments) {
            val end = b.getShort(ends + i * 2).toInt() and 0xFFFF
            val start = b.getShort(starts + i * 2).toInt() and 0xFFFF
            SAMPLE.filter { it in start..end }.forEach { found += it }
        }
        return found
    }

    private fun format12(b: ByteBuffer, at: Int): Set<Int> {
        val groups = b.getInt(at + 12)
        val found = HashSet<Int>()
        for (i in 0 until groups) {
            val g = at + 16 + i * 12
            val start = b.getInt(g)
            val end = b.getInt(g + 4)
            SAMPLE.filter { it in start..end }.forEach { found += it }
        }
        return found
    }
}

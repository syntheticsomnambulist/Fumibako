package dev.aiharness.core.transfer

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Just enough of PNG for character cards: reading and writing the text chunks a card travels in
 * (`chara` for V2, `ccv3` for V3, `chara-ext-asset_:` for a V3 card's embedded assets), and making a
 * plain one-color picture for a persona with no portrait. Pictures themselves are never decoded.
 */
object Png {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun isPng(bytes: ByteArray): Boolean = bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    /** One chunk: its four-letter [type] and its [data]. */
    class Chunk(val type: String, val data: ByteArray)

    /** The chunks in [bytes], in order; empty when it isn't a PNG. Stops at a broken chunk. */
    fun chunks(bytes: ByteArray): List<Chunk> {
        if (!isPng(bytes)) return emptyList()
        val chunks = ArrayList<Chunk>()
        var at = SIGNATURE.size
        while (at + 12 <= bytes.size) {
            val length = readInt(bytes, at)
            if (length < 0 || at + 12L + length > bytes.size) break
            val type = String(bytes, at + 4, 4, Charsets.ISO_8859_1)
            chunks += Chunk(type, bytes.copyOfRange(at + 8, at + 8 + length))
            at += 12 + length
            if (type == "IEND") break
        }
        return chunks
    }

    /**
     * The text chunks (tEXt, zTXt and iTXt) by keyword. A keyword that comes twice keeps its first
     * value, as SillyTavern reads them.
     */
    fun texts(bytes: ByteArray): Map<String, String> {
        val texts = LinkedHashMap<String, String>()
        for (chunk in chunks(bytes)) {
            val (keyword, text) = when (chunk.type) {
                "tEXt" -> tEXt(chunk.data)
                "zTXt" -> zTXt(chunk.data)
                "iTXt" -> iTXt(chunk.data)
                else -> null
            } ?: continue
            texts.putIfAbsent(keyword, text)
        }
        return texts
    }

    /**
     * [bytes] with its text chunks for which [drop] is true taken out and [add] put in (as tEXt,
     * before the picture's end). The picture itself is untouched.
     */
    fun withTexts(bytes: ByteArray, add: List<Pair<String, String>>, drop: (String) -> Boolean): ByteArray {
        require(isPng(bytes)) { "Not a PNG" }
        val out = ByteArrayOutputStream(bytes.size + add.sumOf { it.second.length + 32 })
        out.write(SIGNATURE)
        val data = DataOutputStream(out)
        for (chunk in chunks(bytes)) {
            val keyword = when (chunk.type) {
                "tEXt" -> tEXt(chunk.data)?.first
                "zTXt" -> zTXt(chunk.data)?.first
                "iTXt" -> iTXt(chunk.data)?.first
                else -> null
            }
            if (keyword != null && drop(keyword)) continue
            if (chunk.type == "IEND") {
                add.forEach { (k, v) -> writeChunk(data, "tEXt", k.toByteArray(Charsets.ISO_8859_1) + 0.toByte() + v.toByteArray(Charsets.ISO_8859_1)) }
            }
            writeChunk(data, chunk.type, chunk.data)
        }
        data.flush()
        return out.toByteArray()
    }

    /** A [width] by [height] picture of one color ([rgb], 0xRRGGBB). */
    fun plain(width: Int, height: Int, rgb: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        val data = DataOutputStream(out)
        val header = ByteArrayOutputStream().also { h ->
            DataOutputStream(h).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8) // bit depth
                writeByte(2) // truecolor
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray()
        writeChunk(data, "IHDR", header)
        val row = ByteArray(1 + width * 3)
        for (x in 0 until width) {
            row[1 + x * 3] = (rgb shr 16).toByte()
            row[2 + x * 3] = (rgb shr 8).toByte()
            row[3 + x * 3] = rgb.toByte()
        }
        val pixels = ByteArrayOutputStream()
        DeflaterOutputStream(pixels, Deflater(Deflater.BEST_COMPRESSION)).use { z -> repeat(height) { z.write(row) } }
        writeChunk(data, "IDAT", pixels.toByteArray())
        writeChunk(data, "IEND", ByteArray(0))
        data.flush()
        return out.toByteArray()
    }

    private fun tEXt(data: ByteArray): Pair<String, String>? {
        val nul = data.indexOf(0)
        if (nul <= 0) return null
        return String(data, 0, nul, Charsets.ISO_8859_1) to String(data, nul + 1, data.size - nul - 1, Charsets.ISO_8859_1)
    }

    private fun zTXt(data: ByteArray): Pair<String, String>? {
        val nul = data.indexOf(0)
        if (nul <= 0 || nul + 2 > data.size) return null
        val text = runCatching { inflate(data.copyOfRange(nul + 2, data.size)) }.getOrNull() ?: return null
        return String(data, 0, nul, Charsets.ISO_8859_1) to String(text, Charsets.ISO_8859_1)
    }

    private fun iTXt(data: ByteArray): Pair<String, String>? {
        val nul = data.indexOf(0)
        if (nul <= 0 || nul + 3 > data.size) return null
        val compressed = data[nul + 1].toInt() == 1
        var at = nul + 3
        val language = indexOf(data, 0, at).takeIf { it >= 0 } ?: return null
        at = language + 1
        val translated = indexOf(data, 0, at).takeIf { it >= 0 } ?: return null
        at = translated + 1
        val raw = data.copyOfRange(at, data.size)
        val text = if (compressed) runCatching { inflate(raw) }.getOrNull() ?: return null else raw
        return String(data, 0, nul, Charsets.ISO_8859_1) to String(text, Charsets.UTF_8)
    }

    private fun inflate(bytes: ByteArray): ByteArray = InflaterInputStream(bytes.inputStream()).use { it.readBytes() }

    private fun indexOf(bytes: ByteArray, value: Byte, from: Int): Int {
        for (i in from until bytes.size) if (bytes[i] == value) return i
        return -1
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun writeChunk(out: DataOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.writeInt(data.size)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.writeInt(crc.value.toInt())
    }
}

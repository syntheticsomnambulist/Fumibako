package dev.aiharness.core.media

/**
 * A PDF's own title and author, best effort: from its document information (the trailer's
 * `/Info`), or else its XMP metadata. Titles that only repeat a file name ("Microsoft Word -
 * report.docx", "untitled") are left out. Either may be null; nothing here throws.
 */
object PdfInfo {
    data class Info(val title: String?, val author: String?)

    /** Only this much of a big PDF is looked through: its start and its end, where the information sits. */
    const val LOOK = 2 * 1024 * 1024

    /** [bytes] is the whole PDF, or its first and last [LOOK] bytes. [fileName] filters a title that just repeats it. */
    fun of(bytes: ByteArray, fileName: String? = null): Info = runCatching {
        val text = String(bytes, Charsets.ISO_8859_1)
        val info = infoDictionary(text)
        val title = info?.let { value(it, "Title") }?.let(::tidy) ?: xmp(text, "title")?.let(::tidy)
        val author = info?.let { value(it, "Author") }?.let(::tidy) ?: xmp(text, "creator")?.let(::tidy)
        val stem = fileName?.substringBeforeLast('.')?.lowercase()
        Info(title?.takeUnless { it.lowercase() == stem || it.lowercase() == fileName?.lowercase() }, author)
    }.getOrDefault(Info(null, null))

    private fun infoDictionary(text: String): String? {
        val ref = Regex("""/Info\s+(\d+)\s+(\d+)\s+R""").findAll(text).lastOrNull() ?: return null
        val (n, g) = ref.destructured
        val obj = Regex("""(?<![0-9])$n\s+$g\s+obj""").findAll(text).lastOrNull() ?: return null
        val start = obj.range.last + 1
        val end = text.indexOf("endobj", start).takeIf { it > 0 } ?: (start + 8_000).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    /** The string after `/key` in a dictionary: a literal `(…)` or hex `<…>` string. */
    private fun value(dict: String, key: String): String? {
        val at = Regex("""/$key(?![A-Za-z])\s*""").find(dict) ?: return null
        var i = at.range.last + 1
        return when {
            i >= dict.length -> null
            dict[i] == '(' -> {
                val out = StringBuilder()
                var depth = 1
                i++
                while (i < dict.length && depth > 0) {
                    val c = dict[i]
                    when {
                        c == '\\' && i + 1 < dict.length -> {
                            i++
                            when (val e = dict[i]) {
                                'n' -> out.append('\n')
                                'r' -> out.append('\r')
                                't' -> out.append('\t')
                                'b' -> out.append('\b')
                                'f' -> out.append('\u000C')
                                '\r', '\n' -> Unit
                                in '0'..'7' -> {
                                    var octal = e.toString()
                                    while (octal.length < 3 && i + 1 < dict.length && dict[i + 1] in '0'..'7') octal += dict[++i]
                                    out.append(octal.toInt(8).toChar())
                                }
                                else -> out.append(e)
                            }
                        }
                        c == '(' -> { depth++; out.append(c) }
                        c == ')' -> { depth--; if (depth > 0) out.append(c) }
                        else -> out.append(c)
                    }
                    i++
                }
                decode(out.toString())
            }
            dict[i] == '<' && dict.getOrNull(i + 1) != '<' -> {
                val end = dict.indexOf('>', i).takeIf { it > 0 } ?: return null
                val hex = dict.substring(i + 1, end).filter { !it.isWhitespace() }.let { if (it.length % 2 == 1) it + "0" else it }
                decode(hex.chunked(2).map { it.toInt(16).toChar() }.joinToString(""))
            }
            else -> null
        }
    }

    /** A PDF string's bytes (one per char) as text: UTF-16 with its mark, UTF-8 with its, else (close to) Latin-1. */
    private fun decode(raw: String): String {
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        return when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            else -> raw
        }
    }

    /** `dc:title` or `dc:creator` from the XMP packet (written in UTF-8). */
    private fun xmp(text: String, field: String): String? {
        val block = Regex("""<dc:$field\b[^>]*>(.*?)</dc:$field>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1) ?: return null
        val item = Regex("""<rdf:li\b[^>]*>(.*?)</rdf:li>""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1) ?: block
        return String(item.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
    }

    private val JUNK = Regex("""^(untitled|title|document\d*|microsoft word|presentation\d*|slide \d+|\s*)$""", RegexOption.IGNORE_CASE)
    private val FILE_NAME = Regex("""\.(docx?|pptx?|xlsx?|odt|pages|tex|dvi|indd|rtf|txt|pdf|html?)$""", RegexOption.IGNORE_CASE)

    private fun tidy(text: String): String? {
        val t = text.replace(Regex("[\\u0000-\\u001F]"), " ").replace(Regex("\\s+"), " ").trim()
            .removePrefix("Microsoft Word - ").removePrefix("Microsoft PowerPoint - ").removePrefix("Microsoft Excel - ").trim()
        return t.takeUnless { JUNK.matches(it) || FILE_NAME.containsMatchIn(it) }?.take(160)
    }
}

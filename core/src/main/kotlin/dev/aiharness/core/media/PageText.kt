package dev.aiharness.core.media

/** A page as a persona reads it: its title, its words, and where it ended up after redirects. */
data class PageText(val url: String, val title: String?, val text: String, val cut: Boolean)

/**
 * The readable words of an HTML page: the article (or main part) when it has one, without
 * scripts, styles, menus, headers and footers; paragraphs and headings on lines of their own; links
 * written out after their words, so a persona can follow them later.
 */
object Readable {
    fun of(html: String, maxChars: Int): Pair<String, Boolean> {
        // Prefer the article, then the main part, then the body.
        var body = listOf("article", "main", "body").firstNotNullOfOrNull { tag ->
            Regex("<$tag\\b[^>]*>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
        } ?: html
        body = DROP.fold(body) { text, tag -> Regex("<$tag\\b[^>]*>.*?</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(text, " ") }
        body = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(body, " ")
        // Links keep their address, after their words.
        body = Regex("""<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(body) { m ->
            val words = m.groupValues[2]
            val href = m.groupValues[1]
            if (href.startsWith("http")) "$words ($href)" else words
        }
        body = Regex("<(br|/p|/h[1-6]|/li|/div|/tr|/blockquote|/section)\\b[^>]*>", RegexOption.IGNORE_CASE).replace(body, "\n")
        body = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE).replace(body, "\n- ")
        body = Regex("<[^>]+>").replace(body, " ")
        val text = decode(body)
            .lines()
            .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return if (text.length > maxChars) text.take(maxChars) to true else text to false
    }

    fun decode(text: String): String = text
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&rsquo;", "'").replace("&lsquo;", "'").replace("&ldquo;", "\"")
        .replace("&rdquo;", "\"").replace("&mdash;", "-").replace("&ndash;", "-").replace("&hellip;", "...")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }

    private val DROP = listOf("script", "style", "noscript", "nav", "header", "footer", "aside", "form", "svg", "template", "iframe")
}

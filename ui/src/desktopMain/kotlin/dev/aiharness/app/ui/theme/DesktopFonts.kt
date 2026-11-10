package dev.aiharness.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.aiharness.core.llm.HarnessHttp
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Faces that aren't bundled, on a computer: the same Google Fonts faces the phone gets from Google
 * Play services' provider, downloaded the first time they're wanted into the app-data folder
 * (`fonts/`) and loaded from there after, so they come once and then work offline.
 *
 * - The other hands (Klee One, Zen Kurenaido, Hachi Maru Pop, Yusei Magic), when one is chosen.
 * - The Japanese faces, the first time Japanese shows (or from Settings, Look): Zen Old Mincho,
 *   Zen Kaku Gothic New, Yomogi and Yuji Syuku come whole and replace their Latin subsets. Literata
 *   and Old Standard TT keep their Latin, and their Japanese stays the computer's own: Compose on a
 *   computer can't chain a second face behind a font a character at a time, as the phone does.
 *
 * Only a face's name and weights go to Google Fonts (fonts.googleapis.com, fonts.gstatic.com).
 */
object DesktopFonts {
    enum class State { NOT_YET, COMING, HERE, CANT }

    /** Where the faces are kept; set when the app starts (the app-data folder's `fonts`). */
    @Volatile
    private var dir: File? = null

    internal val japanese = mutableStateMapOf<FontPart, FontFamily>()
    internal val hands = mutableStateMapOf<String, FontFamily>()
    var state by mutableStateOf(State.NOT_YET)
        private set
    private val asked = ConcurrentHashMap.newKeySet<String>()

    /** The Japanese faces, by part: name and weights. */
    private val WHOLE = mapOf(
        FontPart.DISPLAY to ("Zen Old Mincho" to listOf(500, 600)),
        FontPart.UI to ("Zen Kaku Gothic New" to listOf(400, 700)),
        FontPart.HAND to ("Yomogi" to listOf(400)),
        FontPart.BRUSH to ("Yuji Syuku" to listOf(400)),
    )

    /** About what the Japanese faces take (Settings says so before downloading). */
    const val ABOUT_MB = 25

    /** Keeps the faces in [folder]; those already there are loaded now, without the network. */
    fun start(folder: File) {
        dir = folder
        if (WHOLE.values.all { (name, weights) -> weights.all { file(folder, name, it).isFile } }) need()
    }

    /** The Japanese faces: loaded if they're here, else downloaded. Once per run. */
    fun need() {
        val folder = dir ?: return
        if (state == State.COMING || state == State.HERE) return
        state = State.COMING
        thread(isDaemon = true, name = "fumibako-japanese-fonts") {
            var all = true
            WHOLE.forEach { (part, face) ->
                val (name, weights) = face
                val files = weights.map { w -> w to fetch(folder, name, w) }
                if (files.all { it.second != null }) {
                    val family = FontFamily(files.map { (w, f) -> androidx.compose.ui.text.platform.Font(f!!, FontWeight(w)) })
                    post { japanese[part] = family }
                } else {
                    all = false
                }
            }
            post { state = if (all) State.HERE else State.CANT }
        }
    }

    /** A hand that isn't bundled: loaded or downloaded, once asked for; [hands] has it when it's here. */
    fun ask(name: String) {
        val folder = dir ?: return
        if (!asked.add(name)) return
        thread(isDaemon = true, name = "fumibako-hand") {
            val file = fetch(folder, name, 400)
            if (file == null) {
                asked.remove(name)
                return@thread
            }
            val family = FontFamily(androidx.compose.ui.text.platform.Font(file, FontWeight.Normal))
            post { hands[name] = family }
        }
    }

    private fun file(folder: File, name: String, weight: Int) = File(folder, name.lowercase().replace(' ', '-') + "-$weight.ttf")

    /** [name] at [weight], from the folder or from Google Fonts into it; null if it can't come. */
    private fun fetch(folder: File, name: String, weight: Int): File? {
        val file = file(folder, name, weight)
        if (file.isFile && file.length() > 0) return file
        return runCatching {
            folder.mkdirs()
            // Asked for without a browser's name, Google Fonts answers with one whole TrueType file per weight.
            val css = get("https://fonts.googleapis.com/css2?family=${name.replace(' ', '+')}:wght@$weight").toString(Charsets.UTF_8)
            val url = Regex("""src:\s*url\((https://fonts\.gstatic\.com/[^)]+\.ttf)\)""").find(css)?.groupValues?.get(1) ?: return null
            val part = File(folder, file.name + ".part")
            part.writeBytes(get(url))
            check(part.renameTo(file)) { "couldn't keep ${file.name}" }
            file
        }.getOrNull()
    }

    /** On the window's thread (Swing's), where the screens read the faces. */
    private fun post(block: () -> Unit) = javax.swing.SwingUtilities.invokeLater(block)

    private fun get(url: String): ByteArray =
        HarnessHttp.client.newCall(Request.Builder().url(url).header("User-Agent", "Fumibako").build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            response.body!!.bytes()
        }
}

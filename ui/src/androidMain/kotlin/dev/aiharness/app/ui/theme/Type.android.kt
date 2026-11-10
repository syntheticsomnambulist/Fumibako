package dev.aiharness.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.aiharness.app.ui.R

internal actual fun bundledFont(name: String, weight: FontWeight, style: FontStyle): Font = Font(fontIds.getValue(name), weight, style)

private val fontIds = mapOf(
    "brush_regular" to R.font.brush_regular,
    "display_medium" to R.font.display_medium,
    "display_semibold" to R.font.display_semibold,
    "hand_regular" to R.font.hand_regular,
    "news_bold" to R.font.news_bold,
    "news_regular" to R.font.news_regular,
    "read_bold" to R.font.read_bold,
    "read_bold_italic" to R.font.read_bold_italic,
    "read_italic" to R.font.read_italic,
    "read_medium" to R.font.read_medium,
    "read_regular" to R.font.read_regular,
    "ui_bold" to R.font.ui_bold,
    "ui_regular" to R.font.ui_regular,
)

internal actual fun japaneseFace(part: FontPart): androidx.compose.ui.text.font.FontFamily? = JapaneseFonts.faces[part]

/**
 * The Japanese faces, from Google Play services' font provider: asked for once, the first time
 * Japanese shows (or when asked in Settings, Look), and kept by the provider for every app. Faces
 * with their own Japanese (Zen Old Mincho, Zen Kaku Gothic New, Yomogi, Yuji Syuku) come whole and
 * replace the Latin subsets; Literata and Old Standard TT keep their Latin and get Zen Old Mincho
 * and Zen Antique chained behind them, a character at a time (Android 10 and newer; before that
 * their Japanese stays the phone's). Without Play services nothing comes, and nothing changes.
 */
internal object JapaneseFonts {
    enum class State { NOT_YET, COMING, HERE, CANT }

    val faces = androidx.compose.runtime.mutableStateMapOf<FontPart, androidx.compose.ui.text.font.FontFamily>()
    var state by androidx.compose.runtime.mutableStateOf(State.NOT_YET)
        private set

    /** The faces and weights to ask for: name, and weights. */
    private val WHOLE = mapOf(
        FontPart.DISPLAY to ("Zen Old Mincho" to listOf(500, 600)),
        FontPart.UI to ("Zen Kaku Gothic New" to listOf(400, 700)),
        FontPart.HAND to ("Yomogi" to listOf(400)),
        FontPart.BRUSH to ("Yuji Syuku" to listOf(400)),
    )

    /** About what they take (Settings says so before downloading). */
    const val ABOUT_MB = 25

    fun need(context: android.content.Context) {
        if (state != State.NOT_YET) return
        state = State.COMING
        val app = context.applicationContext
        Thread {
            val got = runCatching { fetchAll(app) }.getOrDefault(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post { state = if (got) State.HERE else State.CANT }
        }.apply { isDaemon = true }.start()
    }

    private fun fetchAll(context: android.content.Context): Boolean {
        val fetched = HashMap<Pair<String, Int>, android.os.ParcelFileDescriptor>()
        fun fd(name: String, weight: Int): android.os.ParcelFileDescriptor? = fetched.getOrPut(name to weight) {
            fetch(context, name, weight) ?: return null
        }
        var any = false
        WHOLE.forEach { (part, face) ->
            val (name, weights) = face
            val fonts = weights.mapNotNull { w -> fd(name, w)?.let { androidx.compose.ui.text.font.Font(it, androidx.compose.ui.text.font.FontWeight(w)) } }
            if (fonts.size == weights.size) {
                post { faces[part] = androidx.compose.ui.text.font.FontFamily(fonts) }
                any = true
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            chained(context, FontPart.READ, listOf(R.font.read_regular to (400 to false), R.font.read_italic to (400 to true), R.font.read_medium to (500 to false), R.font.read_bold to (700 to false), R.font.read_bold_italic to (700 to true)), "Zen Old Mincho", ::fd)?.let { any = true }
            chained(context, FontPart.NEWS, listOf(R.font.news_regular to (400 to false), R.font.news_bold to (700 to false)), "Zen Antique", ::fd)?.let { any = true }
        }
        return any
    }

    /** [part]'s bundled Latin faces, with [japanese] behind them for what they don't have. */
    @androidx.annotation.RequiresApi(29)
    private fun chained(
        context: android.content.Context,
        part: FontPart,
        latin: List<Pair<Int, Pair<Int, Boolean>>>,
        japanese: String,
        fd: (String, Int) -> android.os.ParcelFileDescriptor?,
    ): Unit? {
        val behind = fd(japanese, 400) ?: return null
        val family = android.graphics.fonts.FontFamily.Builder(
            android.graphics.fonts.Font.Builder(context.resources, latin.first().first)
                .setWeight(latin.first().second.first).setSlant(if (latin.first().second.second) 1 else 0).build(),
        ).apply {
            latin.drop(1).forEach { (res, style) ->
                addFont(android.graphics.fonts.Font.Builder(context.resources, res).setWeight(style.first).setSlant(if (style.second) 1 else 0).build())
            }
        }.build()
        val fallback = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(behind).build()).build()
        val typeface = android.graphics.Typeface.CustomFallbackBuilder(family).addCustomFallback(fallback).build()
        post { faces[part] = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(typeface)) }
        return Unit
    }

    /** One face at one weight from the provider, as a file it keeps open. */
    private fun fetch(context: android.content.Context, name: String, weight: Int): android.os.ParcelFileDescriptor? {
        val request = androidx.core.provider.FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            "name=$name&weight=$weight",
            R.array.com_google_android_gms_fonts_certs,
        )
        val result = androidx.core.provider.FontsContractCompat.fetchFonts(context, null, request)
        if (result.statusCode != androidx.core.provider.FontsContractCompat.FontFamilyResult.STATUS_OK) return null
        val info = result.fonts.firstOrNull { it.resultCode == androidx.core.provider.FontsContractCompat.Columns.RESULT_CODE_OK } ?: return null
        return context.contentResolver.openFileDescriptor(info.uri, "r")
    }

    private fun post(block: () -> Unit) = android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}

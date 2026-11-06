package dev.aiharness.app.ui

import androidx.compose.foundation.layout.Arrangement
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aiharness.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Loads an image blob (generated image or avatar), downsampled to roughly [maxSize] px. */
@Composable
fun BlobImage(ref: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, maxSize: Int = 1280) {
    val container = LocalAppContainer.current
    val bitmap by produceState<ImageBitmap?>(null, ref, maxSize) {
        value = withContext(Dispatchers.IO) {
            val file = container.blobStore.fileFor(ref)?.takeIf { it.exists() } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
fun Avatar(name: String, ref: String?, size: Dp = 40.dp) {
    val shape = Modifier.size(size).clip(CircleShape)
    if (ref != null) {
        BlobImage(ref, shape, maxSize = 256)
    } else {
        Box(shape.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase().ifEmpty { "?" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, ink: androidx.compose.ui.graphics.Color? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PaperType.noteBody.copy(fontSize = 15.sp, lineHeight = 20.sp), color = paper.sumi)
            subtitle?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = paper.usuzumi0) }
        }
        dev.aiharness.app.ui.paper.TickBox(checked, onCheckedChange = null, ink = ink ?: dev.aiharness.app.ui.theme.stationery.you)
    }
}

/** A read-only field that opens a dropdown of [options]. */
@Composable
fun <T> PickerField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
) = dev.aiharness.app.ui.paper.PaperSelect(label, options, selected, optionLabel, onSelect, modifier, noneLabel)

/** A number typed onto an index card (for values without a natural range; otherwise use a stepper). */
@Composable
fun NumberField(label: String, value: Number?, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    dev.aiharness.app.ui.paper.IndexCardField(
        label = label,
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        modifier = modifier,
        singleLine = true,
        style = dev.aiharness.app.ui.theme.PaperType.noteBody,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

/** Lightweight chat formatting: *actions* in italics, **bold**, and `code`. */
fun formatChatText(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1 && !text.substring(i + 1, end).contains('\n')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = accent)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

/** Navigation must happen on the main thread; use this after suspending work inside a coroutine. */
suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main.immediate) { block() }

fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - millis) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
    }
}

/** Times on letters and cards: "08:14" today, "Yesterday", a weekday this week, else "12 Oct". */
fun letterTime(millis: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val time = java.time.Instant.ofEpochMilli(millis).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = time.toLocalDate()
    return when {
        date == today -> "%02d:%02d".format(time.hour, time.minute)
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> time.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        else -> "${date.dayOfMonth} ${date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}"
    }
}

/** [letterTime] for the middle of a sentence: "at 09:30", "yesterday", "on Mon", "on 3 Nov". */
fun letterTimeInSentence(millis: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val date = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val label = letterTime(millis, now, zone)
    return when {
        date == today -> "at $label"
        date == today.minusDays(1) -> "yesterday"
        else -> "on $label"
    }
}

/** 秋昼 for `aki-hiru`: the season and time of the current theme, as written in the app bar. */
fun seasonKanji(themeKey: String): String {
    val (season, time) = themeKey.split('-').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
    val s = when (season) { "haru" -> "春"; "natsu" -> "夏"; "aki" -> "秋"; else -> "冬" }
    return s + if (time == "yoru") "夜" else "昼"
}

/** How an activity reads after the persona's name: "wrote you a letter", "saved 2 memories". */
fun activityPhrase(kind: dev.aiharness.core.model.ActivityKind, count: Int = 1, note: String? = null): String {
    fun n(one: String, many: String) = if (count == 1) one else many.replace("#", count.toString())
    return when (kind) {
        dev.aiharness.core.model.ActivityKind.WROTE_LETTER -> n("wrote you a letter", "wrote you # letters")
        dev.aiharness.core.model.ActivityKind.STAYED_QUIET -> n("thought things over and stayed quiet", "thought things over # times and stayed quiet")
        dev.aiharness.core.model.ActivityKind.RESTED -> "rested" + (note?.let { ": $it" } ?: "")
        dev.aiharness.core.model.ActivityKind.SAVED_MEMORIES -> n("saved a memory", "saved # memories")
        dev.aiharness.core.model.ActivityKind.RECORDED_EPISODE -> "wrote up your recent talks"
        dev.aiharness.core.model.ActivityKind.REFLECTED -> n("wrote a reflection", "wrote # reflections")
        dev.aiharness.core.model.ActivityKind.REVISITED_FEELINGS -> n("felt differently about a memory", "felt differently about # memories")
        dev.aiharness.core.model.ActivityKind.SETTLED_MEMORIES -> n("let a memory settle", "let # memories settle")
        dev.aiharness.core.model.ActivityKind.LET_GO -> n("let a memory go", "let # memories go")
        dev.aiharness.core.model.ActivityKind.CHANGED_OUTFIT -> note?.takeIf { it.isNotBlank() }?.let { "changed into $it" } ?: "changed back into the usual clothes"
        dev.aiharness.core.model.ActivityKind.STUCK_STICKER -> "stuck ${note ?: "a sticker"} on your note"
        dev.aiharness.core.model.ActivityKind.DREW_STICKERS -> if (count == 1) "drew a new sticker" else "drew $count new stickers"
    }
}

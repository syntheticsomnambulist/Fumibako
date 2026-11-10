package dev.aiharness.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.planner.AppClock
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * A reply in the post, at the foot of the thread: a 〒 postmark in the persona's ink and "Aria's
 * reply is on its way". With Settings › Heartbeat › "Show when letters arrive", a little pocket
 * watch says when ([arrivesAt]); without it, only that it's coming.
 */
@Composable
fun OnItsWay(name: String, inkName: String?, arrivesAt: Long?, modifier: Modifier = Modifier, line: String? = null) {
    val ink = stationery.ink(inkName)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).rotate(-8f).border(1.5.dp, ink.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("〒", style = PaperType.label.copy(fontSize = 17.sp, letterSpacing = 0.sp), color = ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("$name's reply is on its way")
                    // Her line, in her ink: what she's doing as it goes to the post.
                    if (line != null) withStyle(androidx.compose.ui.text.SpanStyle(color = ink)) { append(" · $line") }
                },
                style = PaperType.postscript,
                color = paper.usuzumi1,
            )
            if (arrivesAt != null) {
                val words = "arrives ${arrivalWords(arrivesAt, LocalAppContainer.current.harness.clock.nowMillis())}"
                Row(
                    Modifier.semantics { contentDescription = words },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PocketWatch(arrivesAt, ink, Modifier.size(width = 22.dp, height = 27.dp))
                    Text(words, style = PaperType.meta, color = paper.kasure2)
                }
            }
        }
    }
}

/** A little pocket watch, its bow and crown on top, its hands at [at]'s time of day. */
@Composable
fun PocketWatch(at: Long, ink: Color, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    val face = paper.kami
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val minutes = time.minute
    val hours = time.hour % 12 + minutes / 60f
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.08f
        val r = w / 2f - stroke
        val center = Offset(w / 2f, size.height - r - stroke)
        // The bow (ring) and the crown above the case.
        drawCircle(ink, w * 0.13f, Offset(center.x, center.y - r - w * 0.24f), style = Stroke(stroke * 0.9f))
        drawRect(ink, Offset(center.x - w * 0.09f, center.y - r - w * 0.14f), Size(w * 0.18f, w * 0.16f))
        drawCircle(face, r, center)
        drawCircle(ink, r, center, style = Stroke(stroke))
        // Twelve, three, six and nine.
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0)
            val outer = Offset(center.x + (r * 0.82f * sin(a)).toFloat(), center.y - (r * 0.82f * cos(a)).toFloat())
            drawCircle(ink, stroke * 0.45f, outer)
        }
        fun hand(turn: Float, length: Float, width: Float) {
            val a = Math.toRadians(turn * 360.0)
            drawLine(ink, center, Offset(center.x + (length * sin(a)).toFloat(), center.y - (length * cos(a)).toFloat()), width, StrokeCap.Round)
        }
        hand(hours / 12f, r * 0.5f, stroke * 1.1f)
        hand(minutes / 60f, r * 0.72f, stroke * 0.8f)
        drawCircle(ink, stroke * 0.6f, center)
    }
}

/** When a letter arrives, in words: "at 3:42 pm", "tomorrow at 9:10 am", "on Tuesday at 4:05 pm", "on 12 November". */
fun arrivalWords(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = AppClock.time(at, zone)
    return when {
        day == today -> "at $time"
        day == today.plusDays(1) -> "tomorrow at $time"
        day.isBefore(today.plusDays(7)) -> "on ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)} at $time"
        else -> "on ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, Locale.US)}"
    }
}

/** A travel time on a choice's cell. */
fun travelLabel(t: dev.aiharness.core.model.LetterTravel): String = when (t) {
    dev.aiharness.core.model.LetterTravel.STRAIGHT_AWAY -> "Straight away"
    dev.aiharness.core.model.LetterTravel.MINUTES -> "Minutes"
    dev.aiharness.core.model.LetterTravel.HALF_HOUR -> "Half an hour"
    dev.aiharness.core.model.LetterTravel.HOURS -> "Hours"
    dev.aiharness.core.model.LetterTravel.DAY -> "A day"
    dev.aiharness.core.model.LetterTravel.DAYS -> "Days"
}

/** What a travel time comes to, give or take. */
fun travelWords(t: dev.aiharness.core.model.LetterTravel): String = when (t) {
    dev.aiharness.core.model.LetterTravel.STRAIGHT_AWAY -> "Replies come as they're written, and you watch them being written."
    dev.aiharness.core.model.LetterTravel.MINUTES -> "Two or three minutes."
    dev.aiharness.core.model.LetterTravel.HALF_HOUR -> "Twenty minutes to forty."
    dev.aiharness.core.model.LetterTravel.HOURS -> "Two hours to four."
    dev.aiharness.core.model.LetterTravel.DAY -> "About a day: twenty hours to twenty-eight."
    dev.aiharness.core.model.LetterTravel.DAYS -> "Two days to three."
}

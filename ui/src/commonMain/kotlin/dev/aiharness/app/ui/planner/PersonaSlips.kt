package dev.aiharness.app.ui.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.planner.Occurrence
import dev.aiharness.core.seasons.Seasons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayText
import java.util.Locale
import dev.aiharness.app.platform.deviceNoun

/** A persona's signature seal: the character it picked, or its name's first letter until it does. */
fun sealOf(persona: Persona): String = persona.planner.seal ?: persona.name.take(1).ifBlank { "?" }

/** Its seal, inked: a small square stamp in its ink, a little crooked. */
@Composable
fun PersonaSeal(persona: Persona, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val ink = stationery.ink(persona.ink)
    Box(
        modifier
            .rotate(-6f)
            .size(size)
            .border(1.4.dp, ink, RoundedCornerShape(3.dp))
            .semantics { contentDescription = "${persona.name}'s seal" },
        contentAlignment = Alignment.Center,
    ) {
        Text(sealOf(persona), style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = (size.value * 0.6f).sp, lineHeight = (size.value * 0.62f).sp), color = lerp(ink, paper.sumi, 0.15f))
    }
}

/**
 * Where a persona means to wake and write: its wake mark (a character or emoji; its seal until it
 * picks one) in a small ring of its ink. Kept, it shows its done mark (済 by default).
 */
@Composable
fun WakeMark(persona: Persona, kept: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val ink = stationery.ink(persona.ink)
    val glyph = if (kept) persona.planner.doneMark ?: "済" else persona.planner.wakeMark ?: sealOf(persona)
    // One of its stickers (by id), drawn as itself; else the character in a ring.
    if (isStickerMark(glyph)) {
        dev.aiharness.app.ui.stickers.StickerPicture(dev.aiharness.core.model.StickerRef.Art(glyph), ink, modifier.size(size * 1.4f))
        return
    }
    Box(
        modifier.size(size).background(paper.kami, CircleShape).border(1.2.dp, ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = (size.value * 0.55f).sp, lineHeight = (size.value * 0.6f).sp), color = ink, maxLines = 1)
    }
}

/** A mark is one of the persona's stickers when it's a sticker's id, not a character or emoji. */
fun isStickerMark(mark: String): Boolean = mark.length >= 16 && mark.none { it.isWhitespace() }

/**
 * One thing a persona wrote in your planner, as it sits under the week's grid or on a day's page:
 * a note or haiku on a 一筆箋 (a narrow ruled slip with the season's motif, signed with its seal),
 * a suggestion on a 付箋 (a dashed sticky tab with "Add to my planner" and "Not this time"), a
 * reminder with its wake mark, or its time away.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PersonaSlip(
    o: Occurrence,
    persona: Persona,
    zone: ZoneId,
    hemisphereOf: (LocalDate) -> dev.aiharness.core.model.Hemisphere,
    clock: ClockFormat,
    onAdd: (PlannerEntry) -> Unit,
    onDecline: (PlannerEntry) -> Unit,
) {
    val e = o.entry
    val colors = paper
    val ink = stationery.ink(persona.ink)
    val day = o.day
    val dateWords = "${day.dayOfWeek.getDisplayName(DayText.SHORT, Locale.US)} ${day.dayOfMonth} ${day.month.getDisplayName(DayText.SHORT, Locale.US)}"
    val at = e.start?.let { clock.time(PlannerDays.minuteOf(it, day, zone)) }
    when (e.kind) {
        EntryKind.NOTE, EntryKind.HAIKU -> {
            val micro = Seasons.microOn(day, zone, hemisphereOf(day))
            val signed = if (e.kind == EntryKind.HAIKU) "${persona.name.uppercase()} · ON ${micro.kanji}, ${day.dayOfMonth} ${day.month.getDisplayName(DayText.SHORT, Locale.US).uppercase()}"
            else "${persona.name.uppercase()} · ${dateWords.uppercase()}${at?.let { " · $it" } ?: ""}"
            Box(
                Modifier
                    .padding(start = 10.dp, end = 30.dp, top = 4.dp, bottom = 4.dp)
                    .rotate(if (e.id.hashCode() % 2 == 0) -1.2f else 0.9f)
                    .fillMaxWidth()
                    .background(colors.kami)
                    .border(1.dp, lerp(colors.nijimi1, ink, 0.25f))
                    // Ruled lines in its ink, as a one-brush slip has.
                    .drawBehind {
                        val step = 20.dp.toPx()
                        var y = 10.dp.toPx() + step
                        while (y < size.height - 4.dp.toPx()) {
                            drawLine(ink.copy(alpha = 0.2f), Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), 1.dp.toPx())
                            y += step
                        }
                    }
                    .semantics { contentDescription = "${if (e.kind == EntryKind.HAIKU) "A haiku" else "A note"} from ${persona.name}, $dateWords: ${e.title}" }
                    .padding(start = 14.dp, end = 40.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Column {
                    Text(e.title, style = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 14.sp, lineHeight = 20.sp), color = lerp(ink, colors.sumi, 0.3f))
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PersonaSeal(persona)
                        Text(signed, style = PaperType.meta.copy(fontSize = 9.5.sp), color = colors.kasure2)
                    }
                }
                // The season's motif, over the slip's corner and a little off it.
                SeasonMotif(micro.index / 3, Modifier.align(Alignment.BottomEnd).offset(x = 30.dp, y = 4.dp).size(26.dp).rotate(14f))
            }
        }
        EntryKind.SUGGESTION -> {
            val declined = e.state == EntryState.DECLINED
            val added = e.state == EntryState.ADDED
            Column(
                Modifier
                    .padding(start = 6.dp, end = 30.dp, top = 4.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .background(lerp(colors.kami, ink, 0.1f), RoundedCornerShape(2.dp))
                    .drawBehind {
                        drawRoundRect(
                            ink.copy(alpha = 0.7f), cornerRadius = CornerRadius(2.dp.toPx()),
                            style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PersonaSeal(persona, size = 18.dp)
                    Text("${persona.name.uppercase()} SUGGESTS · ${dateWords.uppercase()}${at?.let { " · $it" } ?: ""}", style = PaperType.meta.copy(fontSize = 9.5.sp), color = ink)
                }
                Text(
                    e.title,
                    style = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 15.sp, lineHeight = 20.sp, textDecoration = if (declined) TextDecoration.LineThrough else null),
                    color = lerp(ink, colors.sumi, 0.3f).copy(alpha = if (declined) 0.6f else 1f),
                )
                when {
                    added -> Text("Added to your planner.", style = PaperType.postscript, color = colors.usuzumi1)
                    declined -> Text("Not this time.", style = PaperType.postscript, color = colors.usuzumi1)
                    // Side by side when they fit, else one under the other.
                    else -> androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperButton("Add to my planner", ink = ink) { onAdd(e) }
                        PaperButton("Not this time") { onDecline(e) }
                    }
                }
            }
        }
        EntryKind.REMINDER -> {
          // Long past and still waiting: its wakeup never came (a heartbeat marks it so when it next runs).
          val missed = e.state == EntryState.MISSED || (e.state == EntryState.OPEN && (e.start ?: Long.MAX_VALUE) < System.currentTimeMillis() - dev.aiharness.core.planner.Planner.MISSED_AFTER)
          Row(
            Modifier.padding(start = 6.dp, end = 30.dp, top = 2.dp, bottom = 2.dp).fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WakeMark(persona, kept = e.state == EntryState.KEPT, size = 20.dp)
            Text(
                when {
                    e.state == EntryState.KEPT -> "${persona.name} kept this: ${e.title}"
                    missed -> "${persona.name} missed this (the $deviceNoun didn't wake them in time): ${e.title}"
                    else -> "${persona.name} will wake ${dateWords}${at?.let { " at $it" } ?: ""}: ${e.title}"
                },
                style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 13.5.sp, lineHeight = 18.sp),
                color = lerp(ink, colors.sumi, 0.3f),
            )
          }
        }
        EntryKind.AWAY -> AwayLine(persona, e, zone, clock)
        else -> Unit
    }
}

/** "Aria is away until Sunday, 9:00 am: busy with the garden", with a crescent moon. */
@Composable
fun AwayLine(persona: Persona, e: PlannerEntry, zone: ZoneId, clock: ClockFormat) {
    val ink = stationery.ink(persona.ink)
    val until = e.end?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val untilWords = until?.let { "${it.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)}, ${clock.time(it.hour * 60 + it.minute)}" }
    Row(
        Modifier.padding(start = 6.dp, end = 30.dp, top = 2.dp, bottom = 2.dp).fillMaxWidth().background(ink.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Moon(ink, Modifier.size(11.dp))
        Text(
            "${persona.name} is away" + (untilWords?.let { " until $it" } ?: "") + (e.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
            style = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 13.sp, lineHeight = 17.sp),
            color = lerp(ink, paper.sumi, 0.35f),
        )
    }
}

/** A crescent moon, for time away. */
@Composable
fun Moon(ink: Color, modifier: Modifier = Modifier) {
    val bg = paper.kami
    Canvas(modifier) {
        drawCircle(ink, size.minDimension / 2f)
        drawCircle(bg, size.minDimension * 0.42f, Offset(size.width * 0.68f, size.height * 0.35f))
    }
}

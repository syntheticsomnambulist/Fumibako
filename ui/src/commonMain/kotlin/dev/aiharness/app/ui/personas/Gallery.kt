package dev.aiharness.app.ui.personas

import dev.aiharness.app.ui.openFromList
import dev.aiharness.app.ui.LocalOpenPersona
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.chats.ScreenTitle
import dev.aiharness.app.ui.paper.InkPortrait
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.Polaroid
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.washiStrip
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona

/** Personas as polaroids taped into the page, then the user's own personas ("Writing as"). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonasScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val memoryCounts by produceState(emptyMap<String, Int>(), personas) {
        value = personas.associate { it.id to harness.memory.all(it.id).size }
    }

    val openPersona = LocalOpenPersona.current
    val importCard = dev.aiharness.app.ui.transfer.rememberOpenFile(nav)
    val pageScroll = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().scrollbar(pageScroll),
        state = pageScroll,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { ScreenTitle("Personas", Modifier.statusBarsPadding()) }
        val cells: List<Persona?> = personas + null
        items(cells.chunked(2), key = { row -> row.joinToString { it?.id ?: "new" } }) { pair ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEachIndexed { index, persona ->
                    Box(Modifier.weight(1f)) {
                        if (persona == null) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                NewPolaroid { nav.openFromList(Routes.personaPage(Routes.NEW), Routes.PERSONAS) }
                                dev.aiharness.app.ui.paper.InkLink("Import a card…", stationery.you) { importCard() }
                            }
                        } else {
                            val latest = conversations.filter { it.personaId == persona.id }.maxByOrNull { it.updatedAt }
                            GalleryPolaroid(persona, latest, memoryCounts[persona.id], index, open = persona.id == openPersona) {
                                nav.openFromList(Routes.personaPage(persona.id), Routes.PERSONAS)
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Text("WRITING AS", style = PaperType.label, color = paper.kasure2, modifier = Modifier.padding(top = 8.dp))
        }
        items(users, key = { it.id }) { user ->
            val active = user.id == settings?.activeUserPersonaId
            WritingAsCard(user, active) { nav.navigate(Routes.youPage(user.id)) }
        }
        item {
            Text(
                "All your personas",
                style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = stationery.you,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).combinedClickable(onClick = { nav.navigate(Routes.USER_PERSONAS) }).padding(6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryPolaroid(persona: Persona, latest: Conversation?, memories: Int?, column: Int, open: Boolean, onClick: () -> Unit) {
    val s = stationery
    val seed = persona.id.hashCode()
    val tilt = if (column == 0) -1.6f - (seed % 3) * 0.4f else 1.4f + (seed % 3) * 0.4f
    val tapeTilt = if (column == 0) 4f else -5f
    Polaroid(
        persona,
        Modifier.fillMaxWidth().semantics { selected = open }.combinedClickable(onClick = onClick, onClickLabel = "Open ${persona.name}'s page"),
        tiltDegrees = tilt,
        tapeTiltDegrees = tapeTilt,
        foldedCorner = open,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 13.dp, start = 2.dp, end = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(persona.name, style = PaperType.polaroidName, color = s.frameInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoodWord(latest?.mood, s.frameInks.ink(persona.ink), style = PaperType.mood.copy(fontSize = 12.5.sp))
            listOfNotNull(
                memories?.let { if (it == 1) "1 memory" else "$it memories" },
                latest?.lastUserMessageAt?.let { "talked ${talkedWhen(it)}" },
            ).forEach { line ->
                Text(line, style = PaperType.meta.copy(letterSpacing = 0.sp), color = lerp(s.frameInk, s.frame, 0.38f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewPolaroid(label: String = "New persona", onClick: () -> Unit) {
    val s = stationery
    val dash = lerp(s.frame, s.colors.nijimi2, 0.55f)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clip(RoundedCornerShape(3.dp))
            .combinedClickable(onClick = onClick, onClickLabel = label),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", style = PaperType.heroName.copy(fontSize = 34.sp, lineHeight = 36.sp), color = paper.usuzumi0)
        Text(label, style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.kasure2)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WritingAsCard(user: UserPersona, active: Boolean, onClick: () -> Unit) {
    val s = stationery
    val ink = s.ink(user.ink)
    Row(
        Modifier
            .washiStrip(Tape(ink, user.tapePattern), tiltDegrees = 0.8f)
            .paperCard()
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onClickLabel = "Write as ${user.name}")
            .padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val portrait = Modifier.size(34.dp).clip(CircleShape)
        if (user.avatarRef != null) BlobImage(user.avatarRef!!, portrait, maxSize = 256) else InkPortrait(user.id, ink, portrait, figure = false)
        Column(Modifier.weight(1f)) {
            Text(user.name, style = PaperType.letterName.copy(fontSize = 18.sp), color = paper.sumi)
            if (user.description.isNotBlank()) {
                Text(user.description, style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = paper.kasure2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active) Text("WRITING AS", style = PaperType.meta.copy(fontSize = 11.sp), color = ink)
    }
}

internal fun talkedWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7} weeks ago"
        else -> "a while ago"
    }
}

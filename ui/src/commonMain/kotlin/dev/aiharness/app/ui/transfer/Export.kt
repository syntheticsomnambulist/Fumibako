package dev.aiharness.app.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.platform.pngOf
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.platform.rememberSaveAs
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.words
import dev.aiharness.core.transfer.BackupPart
import dev.aiharness.core.transfer.BackupRequest
import dev.aiharness.core.transfer.BackupSurvey
import dev.aiharness.core.transfer.CardExport
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Export: one persona (the default; as a backup, or as a character card) or everything. A backup
 * can be locked with a password. Never API keys. The file is made when it's saved or shared.
 */
@Composable
fun ExportScreen(nav: NavController, personaId: String?) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var everything by rememberSaveable { mutableStateOf(personaId == EVERYTHING) }
    var who by rememberSaveable { mutableStateOf(personaId?.takeIf { it != EVERYTHING }) }
    var asCard by rememberSaveable { mutableStateOf(false) }
    // One persona's ticks.
    var memories by rememberSaveable { mutableStateOf(true) }
    var pictures by rememberSaveable { mutableStateOf(true) }
    var letters by rememberSaveable { mutableStateOf(false) }
    var lore by rememberSaveable { mutableStateOf(true) }
    var otherMemories by rememberSaveable { mutableStateOf(false) }
    // Everything's ticks.
    var eMemories by rememberSaveable { mutableStateOf(true) }
    var ePictures by rememberSaveable { mutableStateOf(true) }
    var eLetters by rememberSaveable { mutableStateOf(true) }
    var ePlanner by rememberSaveable { mutableStateOf(true) }
    var eSettings by rememberSaveable { mutableStateOf(true) }
    var lock by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var making by remember { mutableStateOf(false) }

    val persona = personas.firstOrNull { it.id == who } ?: personas.firstOrNull()
    val request: BackupRequest? = when {
        everything -> BackupRequest.Everything(eMemories, ePictures, eLetters, ePlanner, eSettings)
        persona != null -> BackupRequest.OnePersona(persona.id, memories, pictures, letters)
        else -> null
    }
    val surveyKey = if (everything) "everything" else persona?.id
    val survey by produceState<BackupSurvey?>(null, surveyKey) {
        value = null
        val r = if (everything) BackupRequest.Everything() else persona?.let { BackupRequest.OnePersona(it.id) }
        value = r?.let { runCatching { harness.backups.survey(it) }.getOrNull() }
    }
    val herMemories by produceState(emptyList<dev.aiharness.core.model.MemoryEntry>(), persona?.id) {
        value = persona?.let { harness.memories.listForPersona(it.id) }.orEmpty()
    }
    val card = !everything && asCard
    val canLock = !card
    val locked = lock && canLock
    val passwordOk = !locked || (password.length >= 1 && password == again)
    val outside = rememberOutside()
    val scope = rememberCoroutineScope()
    val saveAs = rememberSaveAs { saved -> if (saved) outside.toast("Saved.") }

    val ink = persona?.takeIf { !everything }?.let { stationery.ink(it.ink) } ?: stationery.you
    val tape = dev.aiharness.app.ui.paper.tape(ink, persona?.takeIf { !everything }?.tapePattern ?: stationery.youPattern.takeIf { it != TapePattern.PLAIN } ?: TapePattern.STRIPES, persona?.takeIf { !everything }?.tapeShade ?: stationery.youShade)
    val layouts = tapeLayouts("export", 4)
    val w = persona?.words
    val inkRgb = (persona?.let { stationery.ink(it.ink).toArgb() } ?: 0xA26480) and 0xFFFFFF
    val fileName by produceState("", request, locked) { value = request?.let { harness.backups.fileName(it, locked) }.orEmpty() }

    /** Makes the file, then hands it to [then] with its name and type. */
    fun make(then: (File, String, String) -> Unit) {
        val r = request ?: return
        val p = persona
        making = true
        scope.launch {
            val made = runCatching {
                withContext(Dispatchers.IO) {
                    if (card && p != null) {
                        val original = p.fromCard?.cardRef?.let { harness.blobs.get(it) }?.let { runCatching { HarnessJson.parseToJsonElement(it.decodeToString()) as JsonObject }.getOrNull() }
                        val moodPaths = p.moodPortraits.mapIndexed { i, m -> "emotion/$i-${m.name}" }
                        val v3 = CardExport.v3(p, herMemories, original, CardExport.Choices(lore, otherMemories), now = System.currentTimeMillis(), moodAssets = moodPaths)
                        val portrait = p.avatarRef?.let { harness.blobs.get(it) }?.let { bytes -> if (dev.aiharness.core.transfer.Png.isPng(bytes)) bytes else pngOf(bytes) }
                        val moods = p.moodPortraits.zip(moodPaths).mapNotNull { (m, path) -> harness.blobs.get(m.ref)?.let { path to it } }
                        val png = CardExport.png(portrait, v3, inkRgb, moods)
                        val name = cardName(p)
                        Triple(container.transfers.fresh(name).apply { writeBytes(png) }, name, "image/png")
                    } else {
                        val name = harness.backups.fileName(r, locked)
                        val file = container.transfers.fresh(name)
                        file.outputStream().buffered().use { out ->
                            harness.backups.write(r, out, app = container.platform.about.version ?: "0", password = if (locked) password.toCharArray() else null)
                        }
                        Triple(file, name, "application/octet-stream")
                    }
                }
            }.getOrNull()
            dev.aiharness.app.ui.onMain {
                making = false
                if (made == null) outside.toast("Couldn't make the file.") else then(made.first, made.second, made.third)
            }
        }
    }

    TransferPage(nav, "Export", "Back") {
        item {
            PaperChoice(listOf(false to "One persona", true to "Everything"), everything, { everything = it }, ink = ink)
        }
        if (!everything) {
            item {
                TapedCard(layouts[0], tape, tilt = cardTilt("export-who")) {
                    CardTitle("Who")
                    if (personas.size > 1) {
                        PaperChoice(personas.map { it.id to it.name }, persona?.id, { who = it }, ink = ink, columns = minOf(3, personas.size))
                    } else {
                        Text(persona?.name ?: "No personas yet", style = PaperType.noteBody.copy(fontSize = 15.sp), color = paper.sumi)
                    }
                    CardTitle("As")
                    PaperChoice(listOf(false to "A backup", true to "A character card"), asCard, { asCard = it }, ink = ink)
                    Note(
                        if (asCard) "PNG · for SillyTavern and other apps. ${w?.their?.replaceFirstChar { it.uppercase() } ?: "Her"} portrait with the card inside it: Character Card V3, and a V2 copy for older apps. Ink, tape, pronouns and picture prompts travel too, so ${w?.they ?: "she"} comes back whole."
                        else ".fumibako · to keep ${w?.them ?: "her"} safe, or move ${w?.them ?: "her"} to another ${dev.aiharness.app.platform.deviceNoun}.",
                    )
                }
            }
        }
        item {
            TapedCard(layouts[1], tape, tilt = cardTilt("export-what")) {
                CardTitle("What goes in")
                val s = survey
                val size = { part: BackupPart -> if (s != null && request != null) sizeOf(s.bytes(part, request)) else null }
                when {
                    everything -> {
                        TickRow("Personas · ${personas.size}", "Pages, looks, stickers and lines", true, null, size(BackupPart.PERSONAS))
                        TickRow("Your personas · ${s?.count(BackupPart.YOUR_PERSONAS) ?: "…"}", "And how you look in pictures", true, null, size(BackupPart.YOUR_PERSONAS))
                        TickRow("Memories · ${s?.count(BackupPart.MEMORIES) ?: "…"}", "With their keepsakes", eMemories, { eMemories = it }, size(BackupPart.MEMORIES))
                        TickRow("Pictures · ${s?.pictureCount(request!!) ?: "…"}", "Drawings, photos and portraits", ePictures, { ePictures = it }, size(BackupPart.PICTURES))
                        TickRow("Letters · ${s?.count(BackupPart.LETTERS)?.let(::counted) ?: "…"}", "Every thread, with stickers and frames", eLetters, { eLetters = it }, size(BackupPart.LETTERS))
                        TickRow("Planner", "Plans, typical weeks and holidays", ePlanner, { ePlanner = it }, size(BackupPart.PLANNER))
                        TickRow("Settings and models", "Without keys: add them again after restoring", eSettings, { eSettings = it }, size(BackupPart.SETTINGS))
                    }
                    persona == null -> Note("There's no one to export yet.")
                    asCard -> {
                        val loreCount = herMemories.count { it.kind == MemoryKind.LORE && !it.isReplaced }
                        val others = herMemories.count { it.kind != MemoryKind.LORE && !it.isReplaced }
                        TickRow("Who ${w!!.they} ${w.be}", "Description, personality, scenario, greetings, example dialogue", true, null, ink = ink)
                        TickRow(
                            if (loreCount > 0) "${w.their.replaceFirstChar { it.uppercase() }} lore as the lorebook · $loreCount" else "${w.their.replaceFirstChar { it.uppercase() }} lore as the lorebook",
                            if (loreCount > 0) "Lore memories, with their recall words as keys" else "${w.theyCapital} ${w.verb("has", "have")} no lore memories",
                            lore,
                            { lore = it },
                            ink = ink,
                        )
                        TickRow("${w.their.replaceFirstChar { it.uppercase() }} other memories as lore too · $others", "Other apps can't feel them: they go as plain facts", otherMemories, { otherMemories = it }, ink = ink)
                    }
                    else -> {
                        val one = request as BackupRequest.OnePersona
                        val herself = if (persona.pronouns != null) "${persona.name} ${w!!.themselves}" else persona.name
                        TickRow(herself, "${w!!.their.replaceFirstChar { it.uppercase() }} page, look, stickers and picture prompts", true, null, size(BackupPart.PERSONAS), ink)
                        TickRow("${w.their.replaceFirstChar { it.uppercase() }} memories · ${s?.count(BackupPart.MEMORIES) ?: "…"}", "With their keepsakes", memories, { memories = it }, size(BackupPart.MEMORIES), ink)
                        TickRow("${w.their.replaceFirstChar { it.uppercase() }} pictures · ${s?.pictureCount(one) ?: "…"}", "Drawings, photos and ${w.their} portraits", pictures, { pictures = it }, size(BackupPart.PICTURES), ink)
                        TickRow(
                            "Your letters with ${w.them} · ${s?.count(BackupPart.LETTERS)?.let(::counted) ?: "…"}",
                            "Off unless you want them: a persona travels without them",
                            letters,
                            { letters = it },
                            size(BackupPart.LETTERS),
                            ink,
                        )
                    }
                }
            }
        }
        if (canLock && request != null) {
            item {
                TapedCard(layouts[2], tape, tilt = cardTilt("export-lock")) {
                    TickRow("Lock it with a password", "The file is encrypted: nothing in it can be read without the password", lock, { lock = it }, ink = ink)
                    if (lock) {
                        Column(Modifier.padding(start = 34.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hidden = PasswordVisualTransformation()
                            val keys = KeyboardOptions(keyboardType = KeyboardType.Password)
                            IndexCardField("Password", password, { password = it }, ink = ink, style = PaperType.noteBody, singleLine = true, keyboardOptions = keys, visualTransformation = hidden)
                            IndexCardField("The same again", again, { again = it }, ink = ink, style = PaperType.noteBody, singleLine = true, keyboardOptions = keys, visualTransformation = hidden)
                            if (again.isNotEmpty() && again != password) Note("The two don't match yet.")
                            Note("A forgotten password can't be recovered, by Fumibako or anyone: the backup won't open without it.", stationery.ink("beni"))
                        }
                    }
                }
            }
        }
        if (request != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val total = survey?.takeIf { !card }?.total(request)
                    val name = if (card && persona != null) cardName(persona) else fileName
                    Text(
                        name + (total?.let { " · about ${sizeOf(it)}" } ?: ""),
                        style = PaperType.noteBody.copy(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = paper.sumi,
                    )
                    Note("Never in an export: your API keys. They stay encrypted on this ${dev.aiharness.app.platform.deviceNoun}.")
                    if (!card) Note("Later versions of Fumibako open it too, for as long as they can bring it up to date.")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperButton(if (making) "Making the file…" else "Save the file…", Modifier.weight(1f), ink = ink, enabled = !making && passwordOk) {
                        make { file, name, mime -> saveAs(file, name, mime) }
                    }
                    if (outside.canShare) {
                        PaperButton("Share…", enabled = !making && passwordOk) {
                            make { file, name, mime -> outside.shareFile(file, name, mime) }
                        }
                    }
                }
            }
        }
    }
}

/** A card's file name: `aria.png`. */
private fun cardName(p: Persona): String = (p.name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "persona" }) + ".png"

/** The route argument that opens Export on Everything ("Export everything first"). */
private const val EVERYTHING = "everything"

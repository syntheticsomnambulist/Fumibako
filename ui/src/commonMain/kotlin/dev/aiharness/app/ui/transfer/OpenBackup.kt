package dev.aiharness.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.aiharness.app.platform.deviceNoun
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.letterTimeInSentence
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.words
import dev.aiharness.core.transfer.BackupPeek
import dev.aiharness.core.transfer.Backups
import dev.aiharness.core.transfer.Lock
import dev.aiharness.core.transfer.RestorePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Open a backup: the file (when it was made, by which version, its size) and what's in it. A
 * persona already here can be kept beside the backup's or replaced; an Everything backup replaces
 * all of Fumibako (the keys stay). A locked one shows nothing until it's unlocked; one too old or
 * too new says so.
 */
@Composable
fun OpenBackupScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val harness = container.harness
    // Its own copy: clearing the waiting file when done mustn't take this page away a second time.
    val opened = remember { container.transfers.opened }
    if (opened?.peek == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var peek by remember { mutableStateOf(opened.peek) }
    var zip by remember { mutableStateOf<File?>(opened.file.takeIf { opened.peek is BackupPeek.Open }) }
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val outside = rememberOutside()
    val preview by produceState<RestorePreview?>(null, zip) {
        value = zip?.let { z -> runCatching { harness.backups.preview(z) }.getOrNull() }
    }
    val tape = Tape(stationery.you, stationery.youPattern.takeIf { it != TapePattern.PLAIN } ?: TapePattern.STRIPES)
    val layouts = tapeLayouts("open-backup", 4)
    val beni = stationery.ink("beni")

    fun finish(to: String?) {
        container.transfers.opened = null
        opened.file.delete()
        zip?.takeIf { it != opened.file }?.delete()
        nav.popBackStack()
        to?.let { nav.navigate(it) }
    }

    TransferPage(nav, "Open a backup", "Cancel") {
        item { FileTag(opened.name, meta(peek, opened.file.length())) }
        when (val p = peek) {
            is BackupPeek.TooOld -> item { Refused("This backup is from Fumibako ${p.app}", "This version can bring backups up to date from 0.5.0 on. This one is older, so it can't open it; open it in ${p.app} to see what's inside.") }
            is BackupPeek.TooNew -> item { Refused("This backup is from Fumibako ${p.app}", "That's newer than this version. Update Fumibako to open it.") }
            is BackupPeek.Locked -> item {
                TapedCard(layouts[0], tape, tilt = cardTilt("backup-locked")) {
                    CardTitle("This backup is locked")
                    Note("Enter the password it was saved with. Until then, not even what's inside shows.")
                    IndexCardField(
                        "Password", password, { password = it; problem = null }, style = PaperType.noteBody, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                    )
                    problem?.let { Note(it, beni) }
                    PaperButton(if (busy) "Unlocking…" else "Unlock", Modifier.fillMaxWidth(), ink = stationery.you, enabled = !busy && password.isNotEmpty()) {
                        busy = true
                        scope.launch {
                            val into = container.transfers.fresh("unlocked.zip")
                            val result = runCatching { harness.backups.unlock(opened.file, password.toCharArray(), into) }
                            val inside = if (result.isSuccess) withContext(Dispatchers.IO) { harness.backups.peek(into) } else null
                            dev.aiharness.app.ui.onMain {
                            busy = false
                            result.onSuccess {
                                if (inside is BackupPeek.Open) {
                                    zip = into
                                    peek = inside
                                } else {
                                    problem = "It opened, but what's inside isn't a backup this version can read."
                                }
                            }.onFailure { e ->
                                problem = when (e) {
                                    is Lock.WrongPassword -> "That isn't the password it was saved with."
                                    is Lock.Damaged -> "This backup is damaged: part of it is missing or changed."
                                    else -> "It couldn't be unlocked."
                                }
                            }
                            }
                        }
                    }
                    Note("A forgotten password can't be recovered, by Fumibako or anyone.")
                }
            }
            is BackupPeek.Open -> {
                val pv = preview
                if (pv == null) {
                    item { Note("Reading the backup…") }
                } else if (pv.manifest.everything) {
                    item {
                        TapedCard(layouts[0], tape, tilt = cardTilt("backup-all")) {
                            CardTitle("In the backup")
                            Text("Everything, from ${day(pv.manifest.madeAt)}", style = PaperType.noteBody.copy(fontSize = 15.sp), color = paper.sumi)
                            Note(everythingLine(pv))
                        }
                    }
                    item {
                        TapedCard(layouts[1], Tape(beni, TapePattern.STRIPES), tilt = cardTilt("backup-replaces")) {
                            CardTitle("It replaces what's here")
                            Text(goesLine(pv), style = PaperType.prose.copy(fontSize = 14.sp, lineHeight = 20.sp), color = paper.sumi)
                            Note("Your keys stay on this $deviceNoun, so everyone can write straight away.")
                            InkLink("Export everything first", stationery.you) { nav.navigate(Routes.EXPORT_EVERYTHING) }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PaperButton(if (busy) "Replacing…" else "Replace everything", Modifier.weight(1f), ink = beni, enabled = !busy) {
                                busy = true
                                scope.launch {
                                    val done = runCatching { harness.backups.restoreEverything(zip!!) }
                                    dev.aiharness.app.ui.onMain {
                                        busy = false
                                        if (done.isSuccess) {
                                            outside.toast("Everything is as it was on ${day(pv.manifest.madeAt)}.")
                                            finish(null)
                                        } else {
                                            problem = "It couldn't be restored; nothing here was changed."
                                        }
                                    }
                                }
                            }
                            PaperButton("Cancel") { finish(null) }
                        }
                    }
                    problem?.let { item { Note(it, beni) } }
                } else {
                    val persona = pv.persona
                    val w = persona?.words
                    val name = persona?.name ?: "Them"
                    item {
                        TapedCard(layouts[0], Tape(stationery.ink(persona?.ink), persona?.tapePattern ?: TapePattern.DOTS), tilt = cardTilt("backup-one")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(stationery.ink(persona?.ink).copy(alpha = 0.55f), CircleShape))
                                Column {
                                    Text("IN THE BACKUP", style = PaperType.label.copy(fontSize = 10.sp), color = paper.kasure2)
                                    Text(name, style = PaperType.letterName.copy(fontSize = 20.sp), color = paper.sumi)
                                    Note(oneLine(pv))
                                }
                            }
                        }
                    }
                    val here = pv.here
                    if (here != null && w != null) {
                        item {
                            TapedCard(layouts[1], Tape(stationery.ink(here.ink), here.tapePattern), tilt = cardTilt("backup-here")) {
                                CardTitle("${here.name} is already here")
                                Note(
                                    "Here now: ${pv.hereMemories} ${if (pv.hereMemories == 1) "memory" else "memories"}" +
                                        (pv.hereLastLetterAt?.let { at -> ", and ${w.their} last letter was ${letterTimeInSentence(at).let { t -> if (t.startsWith("at ")) "today $t" else t }}." } ?: "."),
                                )
                                PaperChoice(listOf(false to "Keep both", true to "Replace ${w.them}"), replace, { replace = it }, ink = if (replace) beni else stationery.you)
                                Note(
                                    if (!replace) "The backup's $name comes in beside ${w.them}, as \"$name (${shortDay(pv.manifest.madeAt)})\", with a thread of ${w.their} own. Rename either on ${w.their} page."
                                    else "This ${here.name}'s page, memories and pictures become the backup's. Your letters with ${w.them} stay, in the same thread. What ${w.they} ${w.verb("has", "have")} kept since ${shortDay(pv.manifest.madeAt)} is lost.",
                                )
                            }
                        }
                    }
                    pv.model?.let { model ->
                        item {
                            TapedCard(layouts[2], tape, tilt = cardTilt("backup-model")) {
                                CardTitle("${w?.their?.replaceFirstChar { it.uppercase() } ?: "Their"} model")
                                Text(model.name, style = PaperType.noteBody.copy(fontSize = 14.sp), color = paper.sumi)
                                if (!model.hasKey) {
                                    Note("Keys don't travel in backups, and this $deviceNoun has no ${model.provider.displayName} key yet. ${w?.theyCapital ?: name} comes in either way; ${w?.they ?: name} writes once there's a key.")
                                    InkLink("Add a key", stationery.you) { nav.navigate(Routes.KEYS) }
                                } else {
                                    Note("${w?.theyCapital ?: name} writes with this $deviceNoun's ${model.provider.displayName} key.")
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val label = when {
                                here == null -> "Bring ${w?.them ?: name} in"
                                replace -> "Replace ${here.name}"
                                else -> "Bring ${w?.them ?: name} in beside ${here.name}"
                            }
                            PaperButton(if (busy) "Bringing ${w?.them ?: name} in…" else label, Modifier.weight(1f), ink = if (replace && here != null) beni else stationery.you, enabled = !busy) {
                                busy = true
                                scope.launch {
                                    val done = runCatching { harness.backups.restorePersona(zip!!, if (replace) Backups.WhenHere.REPLACE else Backups.WhenHere.KEEP_BOTH) }
                                    dev.aiharness.app.ui.onMain {
                                        busy = false
                                        done.onSuccess { finish(Routes.personaPage(it.id)) }.onFailure { problem = "${w?.theyCapital ?: name} couldn't be brought in; nothing here was changed." }
                                    }
                                }
                            }
                            PaperButton("Cancel") { finish(null) }
                        }
                    }
                    problem?.let { item { Note(it, beni) } }
                }
            }
            BackupPeek.NotABackup -> item { Refused("This isn't a Fumibako backup", "It may be a character card: open it with Import a card.") }
        }
    }
}

/** The file, as a tag: its name, and what it says of itself. */
@Composable
private fun FileTag(name: String, meta: String) {
    Column(
        Modifier.fillMaxWidth().background(stationery.ink("yamabuki").copy(alpha = 0.28f), dev.aiharness.app.ui.paper.PaperCorner).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(name, style = PaperType.noteBody.copy(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = paper.sumi)
        Text(meta, style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.usuzumi1)
    }
}

@Composable
private fun Refused(title: String, detail: String) {
    TapedCard(dev.aiharness.app.ui.paper.TapeLayout.LEFT, Tape(stationery.ink("beni"), TapePattern.PLAIN)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text("旧", style = PaperType.label.copy(fontSize = 18.sp), color = stationery.ink("beni"))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = PaperType.noteBody.copy(fontSize = 15.sp), color = paper.sumi)
                Note(detail)
            }
        }
    }
}

private fun meta(peek: BackupPeek?, size: Long): String = when (peek) {
    is BackupPeek.Open -> "Made ${dateTime(peek.manifest.madeAt)} · Fumibako ${peek.manifest.app} · ${sizeOf(size)}"
    is BackupPeek.Locked -> "Locked · Fumibako ${peek.header.app} · ${sizeOf(size)}"
    is BackupPeek.TooOld -> "Fumibako ${peek.app} · ${sizeOf(size)}"
    is BackupPeek.TooNew -> "Fumibako ${peek.app} · ${sizeOf(size)}"
    else -> sizeOf(size)
}

/** "212 memories · 48 pictures · no letters". */
private fun oneLine(pv: RestorePreview): String {
    val m = pv.manifest
    return listOf(
        plural(m.memories, "memory", "memories"),
        plural(m.pictures, "picture", "pictures"),
        if (m.letters == 0) "no letters" else plural(m.letters, "letter", "letters"),
    ).joinToString(" · ")
}

/** "Aria and Juno · your persona Sam · 358 memories · 70 pictures · 2,210 letters · the planner · settings and models". */
private fun everythingLine(pv: RestorePreview): String {
    val m = pv.manifest
    return listOfNotNull(
        names(m.personas.map { it.name }),
        m.userPersonas.takeIf { it.isNotEmpty() }?.let { (if (it.size == 1) "your persona " else "your personas ") + names(it) },
        plural(m.memories, "memory", "memories"),
        plural(m.pictures, "picture", "pictures"),
        plural(m.letters, "letter", "letters"),
        "the planner".takeIf { m.planner },
        "settings and models".takeIf { m.settings },
    ).joinToString(" · ")
}

/** "Everything on this phone now is swapped for the backup: Aria, Juno and Kit, 2,260 letters, and all that's happened since 6 Nov. Kit isn't in the backup, so Kit goes." */
private fun goesLine(pv: RestorePreview): String {
    val goes = pv.goes ?: return ""
    val since = shortDay(pv.manifest.madeAt)
    val here = listOfNotNull(names(goes.personas).takeIf { goes.personas.isNotEmpty() }, plural(goes.letters, "letter", "letters"))
    val out = goes.notInBackup.takeIf { it.isNotEmpty() }?.let { n ->
        " ${names(n)} ${if (n.size == 1) "isn't" else "aren't"} in the backup, so ${if (n.size == 1) n.single() else "they"} ${if (n.size == 1) "goes" else "go"}."
    }.orEmpty()
    return "Everything on this $deviceNoun now is swapped for the backup: ${here.joinToString(", ")}, and all that's happened since $since.$out"
}

private fun names(list: List<String>): String = when (list.size) {
    0 -> "no one"
    1 -> list.single()
    else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "${counted(n)} $many"

private fun dateTime(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun day(millis: Long): String = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun shortDay(millis: Long): String = DateTimeFormatter.ofPattern("d MMM", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

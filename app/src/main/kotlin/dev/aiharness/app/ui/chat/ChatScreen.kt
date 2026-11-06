package dev.aiharness.app.ui.chat

import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperChoice
import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.engine.GenerationManager
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.ChatMessage
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(nav: NavController, conversationId: String) {
    val container = LocalAppContainer.current
    val vm: ChatViewModel = viewModel(key = conversationId) { ChatViewModel(container, conversationId) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val generation by vm.generation.collectAsStateWithLifecycle()
    val imageReady by vm.imageGenReady.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var imageDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val persona = header.persona
    val personaName = persona?.name ?: "…"
    val rows = remember(messages) { buildThread(messages) }
    val writing = if (generation.active) {
        Writing(generation.streamingText, generation.reasoning, GenerationManager.describeTool(generation.activeTool))
    } else {
        null
    }
    // A turn in progress continues the last letter (earlier tool rounds are already saved into it).
    val writingInto = (rows.lastOrNull() as? ThreadRow.Letter)?.takeIf { writing != null && messages.lastOrNull()?.role != dev.aiharness.core.model.MessageRole.USER }

    LaunchedEffect(messages.size, writing != null) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }
    // Everything on screen counts as read, including letters that arrive while it is open.
    LaunchedEffect(messages.lastOrNull()?.id) { vm.markRead() }

    Column(Modifier.fillMaxSize().background(paper.hyoshi).imePadding()) {
        LettersTopBar(
            vm = vm,
            header = header,
            imageReady = imageReady,
            onBack = { nav.popBackStack() },
            onImage = { imageDialog = true },
            onDelete = { confirmDelete = true },
            nav = nav,
        )

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            if (writing != null && writingInto == null) {
                item(key = "writing") { LetterSheet(null, persona, writing, onLongPress = {}, onImage = { viewing = it }) }
            }
            items(rows.asReversed(), key = { it.key }) { row ->
                when (row) {
                    is ThreadRow.Day -> DayHeading(row.date)
                    is ThreadRow.Note -> NoteSlip(row.message, header.userPersona, onLongPress = { selected = listOf(row.message) }, onImage = { viewing = it })
                    is ThreadRow.Letter -> LetterSheet(
                        row,
                        persona,
                        writing.takeIf { row === writingInto },
                        onLongPress = { selected = row.messages },
                        onImage = { viewing = it },
                    )
                }
            }
        }

        generation.error?.let { error ->
            ErrorSlip(error, onRetry = { vm.dismissError(); vm.regenerate() }, onDismiss = { vm.dismissError() })
        }

        EntryBar(
            value = input,
            onValueChange = { input = it },
            placeholder = "Write to $personaName…",
            personaInk = stationery.ink(persona?.ink),
            youInk = header.userPersona?.ink?.let { stationery.ink(it) } ?: stationery.you,
            busy = generation.active,
            onSend = { vm.send(input); input = "" },
            onStop = { vm.stop() },
        )
    }

    selected?.let { group -> MessageActions(group, vm, onDismiss = { selected = null }) }
    viewing?.let { ref ->
        Dialog(onDismissRequest = { viewing = null }) {
            BlobImage(ref, Modifier.fillMaxWidth().clickable { viewing = null }, contentScale = ContentScale.Fit, maxSize = 2048)
        }
    }
    if (imageDialog) {
        ImageDialog(personaName, onDismiss = { imageDialog = false }) { prompt, includePersona, orientation ->
            imageDialog = false
            vm.generateImage(prompt, includePersona, orientation)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete these letters?", style = PaperType.entryTitle) },
            text = { Text("Memories $personaName formed are kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteConversation { nav.popBackStack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LettersTopBar(
    vm: ChatViewModel,
    header: ChatHeader,
    imageReady: Boolean,
    onBack: () -> Unit,
    onImage: () -> Unit,
    onDelete: () -> Unit,
    nav: NavController,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val users by vm.userPersonas.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<String?>(null) }
    val colors = paper
    val persona = header.persona
    val ink = stationery.ink(persona?.ink)
    val iconTint = colors.usuzumi1
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.hyoshi)
            .drawBehind { drawLine(colors.nijimi0, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = iconTint) }
        PortraitDot(persona, 34.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(persona?.name ?: "…", style = PaperType.letterName.copy(fontSize = 22.sp, lineHeight = 24.sp), color = colors.sumi, maxLines = 1)
                MoodWord(header.conversation?.mood, ink, Modifier.padding(bottom = 2.dp))
            }
            Text(
                listOfNotNull(header.profile?.name ?: "No model", header.userPersona?.let { "as ${it.name}" }).joinToString(" · "),
                style = PaperType.meta.copy(letterSpacing = 0.sp),
                color = colors.kasure2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = "model" }) { Icon(Icons.Filled.SmartToy, "Model", tint = iconTint) }
            DropdownMenu(expanded = menu == "model", onDismissRequest = { menu = null }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name + if (profile.id == header.profile?.id) "  ✓" else "") },
                        onClick = { vm.switchModel(profile); menu = null },
                    )
                }
                DropdownMenuItem(text = { Text("Manage models…") }, onClick = { menu = null; nav.navigate(Routes.MODELS) })
            }
        }
        Box {
            IconButton(onClick = { menu = "user" }) { Icon(Icons.Filled.Person, "Writing as", tint = iconTint) }
            DropdownMenu(expanded = menu == "user", onDismissRequest = { menu = null }) {
                users.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.name + if (user.id == header.userPersona?.id) "  ✓" else "") },
                        onClick = { vm.switchUserPersona(user); menu = null },
                    )
                }
                DropdownMenuItem(text = { Text("Manage your personas…") }, onClick = { menu = null; nav.navigate(Routes.USER_PERSONAS) })
            }
        }
        if (imageReady) IconButton(onClick = onImage) { Icon(Icons.Filled.Image, "Draw a picture", tint = iconTint) }
        Box {
            IconButton(onClick = { menu = "more" }) { Icon(Icons.Filled.MoreVert, "More", tint = iconTint) }
            DropdownMenu(expanded = menu == "more", onDismissRequest = { menu = null }) {
                DropdownMenuItem(text = { Text("Rewrite the last letter") }, onClick = { menu = null; vm.regenerate() })
                persona?.let { p ->
                    DropdownMenuItem(text = { Text("${p.name}'s page") }, onClick = { menu = null; nav.navigate(Routes.personaPage(p.id)) })
                    DropdownMenuItem(text = { Text("${p.name}'s memories") }, onClick = { menu = null; nav.navigate(Routes.memory(p.id)) })
                }
                DropdownMenuItem(text = { Text("Refresh context") }, onClick = { menu = null; vm.refreshContext() })
                DropdownMenuItem(text = { Text("Delete conversation") }, onClick = { menu = null; onDelete() })
            }
        }
    }
}

/** The writing field: its border runs from the persona's ink to the user's; send is in the user's ink. */
@Composable
private fun EntryBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    personaInk: androidx.compose.ui.graphics.Color,
    youInk: androidx.compose.ui.graphics.Color,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.kami)
            .drawBehind { drawLine(colors.nijimi0, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = PaperCorner
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = PaperType.preview.copy(fontSize = 15.sp, lineHeight = 22.sp, color = colors.sumi),
            cursorBrush = SolidColor(youInk),
            maxLines = 6,
            modifier = Modifier
                .weight(1f)
                .border(1.5.dp, Brush.horizontalGradient(listOf(personaInk, youInk)), shape)
                .background(colors.kami, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = PaperType.preview.copy(fontStyle = FontStyle.Italic), color = colors.kasure2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
            },
        )
        val canSend = value.isNotBlank()
        val active = busy || canSend
        // Dimmed by colour, not by an alpha layer: adding and removing that layer as you typed left the
        // button undrawn (though still tappable).
        val fill = if (active) youInk else androidx.compose.ui.graphics.lerp(youInk, colors.kami, 0.5f)
        Box(
            Modifier
                .size(42.dp)
                .clip(PaperCorner)
                .background(fill)
                .clickable(enabled = active, onClickLabel = if (busy) "Stop" else "Send") { if (busy) onStop() else onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, if (busy) "Stop" else "Send", tint = colors.onInk(fill).copy(alpha = if (active) 1f else 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ErrorSlip(error: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val s = stationery
    Row(
        Modifier
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .paperCard(color = tint(s.colors.kami, s.ink("beni"), 0.08f))
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(error, Modifier.weight(1f), style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp), color = s.ink("beni"))
        TextButton(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun MessageActions(group: List<ChatMessage>, vm: ChatViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val editable = group.lastOrNull { it.displayText.isNotBlank() }
    val isLetter = group.any { it.role != dev.aiharness.core.model.MessageRole.USER }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(editable?.displayText.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit" else if (isLetter) "This letter" else "Your note", style = PaperType.entryTitle) },
        text = {
            if (editing) {
                dev.aiharness.app.ui.paper.IndexCardField(if (isLetter) "The letter" else "Your note", text, { text = it }, style = PaperType.prose, minLines = 3)
            } else {
                Column {
                    TextButton(onClick = {
                        val all = group.map { it.displayText }.filter { it.isNotBlank() }.joinToString("\n\n")
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("letter", all))) }
                        onDismiss()
                    }) { Text("Copy") }
                    if (editable != null) TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = { vm.deleteMessages(group); onDismiss() }) { Text("Delete") }
                    Text(
                        "Editing or deleting earlier messages starts a fresh context for the model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (editing && editable != null) TextButton(onClick = { vm.editMessage(editable, text); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageDialog(personaName: String, onDismiss: () -> Unit, onGenerate: (String, Boolean, String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var includePersona by remember { mutableStateOf(true) }
    var orientation by remember { mutableStateOf("portrait") }
    val options = listOf("portrait", "landscape", "square")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw a picture", style = PaperType.entryTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dev.aiharness.app.ui.paper.IndexCardField("Tags or a description", prompt, { prompt = it }, style = PaperType.noteBody, minLines = 2, placeholder = "smiling, sitting in a cafe, rainy window")
                SwitchRow("Include $personaName's appearance", includePersona, { includePersona = it })
                PaperChoice(options.map { it to it.replaceFirstChar { c -> c.uppercase() } }, orientation, { orientation = it })
            }
        },
        confirmButton = { TextButton(onClick = { onGenerate(prompt, includePersona, orientation) }, enabled = prompt.isNotBlank()) { Text("Generate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package dev.aiharness.app.ui.chat

import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.paper.StationeryIcons
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Sticker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import dev.aiharness.app.ui.stickers.LocalStickerSettings
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTargetKind
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(nav: NavController, conversationId: String) {
    val container = LocalAppContainer.current
    val vm: ChatViewModel = viewModel(key = conversationId) { ChatViewModel(container, conversationId) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val generation by vm.generation.collectAsStateWithLifecycle()
    val imageReady by vm.imageGenReady.collectAsStateWithLifecycle()
    val stickers by vm.stickers.collectAsStateWithLifecycle()
    val stickerArt by vm.stickerArt.collectAsStateWithLifecycle()
    val stickerName: (Sticker) -> String = { stickerLabel(it, stickerArt) }
    val recentStickers by vm.recentStickers.collectAsStateWithLifecycle()

    // Stickers: the tray, the one selected (and its live position while it moves), dragging one in.
    var trayOpen by rememberSaveable { mutableStateOf(false) }
    var stickerPage by rememberSaveable { mutableStateOf(false) }
    val going by vm.going.collectAsStateWithLifecycle()
    var editingMix by remember { mutableStateOf<dev.aiharness.core.model.ContentPart.Attachment?>(null) }
    val scope = rememberCoroutineScope()
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris -> if (uris.isNotEmpty()) { vm.addPhotos(uris); trayOpen = false } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.addVideo(it); trayOpen = false } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.addFile(it); trayOpen = false } }
    val pickSongs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { vm.addSongs(uris) { mix -> editingMix = mix }; trayOpen = false }
    }
    val moreSongs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val mix = editingMix ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) scope.launch {
            val added = vm.moreTracks(uris)
            val updated = mix.copy(tracks = mix.tracks + added, lengthMs = (mix.tracks + added).sumOf { it.lengthMs ?: 0L })
            editingMix = updated
            vm.replace(Going.Item(updated))
        }
    }
    var cameraShot by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val shot = cameraShot
        if (ok && shot != null) { vm.addPhotos(listOf(shot)); trayOpen = false }
    }
    val container2 = LocalAppContainer.current
    val pickFromTray: (TrayItem) -> Unit = { item ->
        when (item) {
            TrayItem.PHOTOS -> pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            TrayItem.CAMERA -> {
                val (_, uri) = container2.media.cameraTarget()
                cameraShot = uri
                takePicture.launch(uri)
            }
            TrayItem.FILE -> pickFile.launch(arrayOf("*/*"))
            TrayItem.MUSIC -> pickSongs.launch(arrayOf("audio/*"))
            TrayItem.VIDEO -> pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            TrayItem.STICKERS -> stickerPage = true
        }
    }
    var selectedStickerId by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<Sticker?>(null) }
    var peeled by remember { mutableStateOf<Sticker?>(null) }
    var dragging by remember { mutableStateOf<Pair<StickerRef, Offset>?>(null) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    val papers = remember { mutableStateMapOf<StickerTarget, Rect>() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(moving) {
        val m = moving ?: return@LaunchedEffect
        delay(400)
        vm.moveSticker(m)
    }
    val finishSticker = {
        moving?.let { vm.moveSticker(it) }
        moving = null
        selectedStickerId = null
    }
    val shownStickers = remember(stickers, moving) {
        val m = moving
        if (m == null) stickers else stickers.mapValues { (_, list) -> list.map { if (it.id == m.id) m else it } }
    }
    val handling = StickerHandling(
        selectedId = selectedStickerId,
        onTap = { sticker ->
            peeled = null
            if (selectedStickerId == sticker.id) finishSticker() else {
                moving?.let { vm.moveSticker(it) }
                moving = null
                selectedStickerId = sticker.id
            }
        },
        onChange = { moving = it },
        paper = { target ->
            Modifier.onGloballyPositioned { papers[target] = it.boundsInRoot() }
        },
        on = { id -> shownStickers[id].orEmpty() },
        forget = { target -> papers.remove(target) },
    )
    val selectedSticker = selectedStickerId?.let { id -> shownStickers.values.flatten().firstOrNull { it.id == id } }

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

    Box(Modifier.fillMaxSize().onGloballyPositioned { screenOrigin = it.positionInRoot() }) {
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

        val inlineStickers = remember(stickerArt, persona?.id) {
            stickerArt.values.filter { it.ownerPersonaId == null || it.ownerPersonaId == persona?.id }.associateBy { it.name }
        }
        val reader = header.persona?.let { persona -> header.profile?.let { PersonaReader(persona.name, it) } }
        CompositionLocalProvider(LocalInlineStickers provides inlineStickers, LocalPersonaReader provides reader) {
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
                        is ThreadRow.Note -> NoteSlip(
                            row.message,
                            header.userPersona,
                            onLongPress = { selected = listOf(row.message) },
                            onImage = { viewing = it },
                            stickers = shownStickers[row.message.id].orEmpty(),
                            persona = persona,
                            stickerName = stickerName,
                            handling = handling,
                        )
                        is ThreadRow.Letter -> LetterSheet(
                            row,
                            persona,
                            writing.takeIf { row === writingInto },
                            onLongPress = { selected = row.messages },
                            onImage = { viewing = it },
                            stickers = row.messages.flatMap { shownStickers[it.id].orEmpty() },
                            user = header.userPersona,
                            stickerName = stickerName,
                            handling = handling,
                        )
                    }
                    // A row that scrolls away can't be dropped on.
                    if (row !is ThreadRow.Day) {
                        val id = when (row) {
                            is ThreadRow.Note -> row.message.id
                            is ThreadRow.Letter -> row.messages.first().id
                            else -> ""
                        }
                        DisposableEffect(id) { onDispose { papers.remove(StickerTarget.message(id)) } }
                    }
                }
            }

        }

        generation.error?.let { error ->
            ErrorSlip(error, onRetry = { vm.dismissError(); vm.regenerate() }, onDismiss = { vm.dismissError() })
        }

        val youCanPeel = header.persona?.stickers?.youCanPeel == true
        selectedSticker?.let { sticker ->
            val mine = sticker.byPersonaId == null
            StickerSlip(
                text = if (mine) "Your ${stickerName(sticker).removeSuffix(" sticker")}, stuck ${letterStamp(sticker.createdAt).lowercase().replaceFirstChar { it.uppercase() }}."
                else "$personaName stuck this ${letterStamp(sticker.createdAt).lowercase().replaceFirstChar { it.uppercase() }}." + if (youCanPeel) "" else " Only $personaName can peel it off.",
                peel = if (mine || youCanPeel) ({ vm.peelSticker(sticker); peeled = sticker; moving = null; selectedStickerId = null }) else null,
                done = { finishSticker() },
            )
        }
        peeled?.let { sticker ->
            StickerSlip(
                text = "Peeled off." + if (sticker.target.kind == StickerTargetKind.MESSAGE) " $personaName will hear about it next time." else "",
                putBack = if (sticker.target.kind == StickerTargetKind.MESSAGE) ({ vm.unpeelSticker(sticker); peeled = null }) else null,
                done = { peeled = null },
            )
        }

        GoingWithYourNote(going, onRemove = { vm.remove(it) }, onOpen = { item ->
            ((item as? Going.Item)?.attachment)?.takeIf { it.kind == dev.aiharness.core.model.AttachmentKind.PLAYLIST }?.let { editingMix = it }
        })
        val youInk = header.userPersona?.ink?.let { stationery.ink(it) } ?: stationery.you
        val sheet = stationery
        // Half-typed :name: — offer the stickers it could be.
        partialStickerName(input)?.let { partial ->
            val matches = stickerSuggestions(partial, inlineStickers.values)
            if (matches.isNotEmpty()) {
                StickerSuggestions(partial, matches, sheet.dayColors.ink(header.userPersona?.ink ?: "sora")) { name ->
                    input = input.dropLast(partial.length + 1) + ":$name: "
                }
            }
        }
        EntryBar(
            value = input,
            onValueChange = { input = it; vm.noticeLinks(it) },
            placeholder = "Write to $personaName…",
            personaInk = stationery.ink(persona?.ink),
            youInk = youInk,
            busy = generation.active,
            onSend = { vm.send(input); input = ""; trayOpen = false },
            hasAttachments = going.isNotEmpty() && going.none { it is Going.Preparing },
            onStop = { vm.stop() },
            trayOpen = trayOpen,
            onTray = {
                if (trayOpen) trayOpen = false else {
                    keyboard?.hide()
                    focus.clearFocus()
                    stickerPage = false
                    trayOpen = true
                }
            },
            onFieldFocus = { trayOpen = false },
            draft = { field ->
                StuckOn(
                    stickers = shownStickers[conversationId].orEmpty(),
                    inkOf = { stickerInk(it, sheet, persona, header.userPersona) },
                    modifier = Modifier.weight(1f),
                    nameOf = stickerName,
                    selectedId = selectedStickerId,
                    onTap = handling.onTap,
                    onChange = handling.onChange,
                ) { field() }
            },
        )
        if (trayOpen && !stickerPage) TrayButtons(pickFromTray)
        if (trayOpen && stickerPage) {
            StickerTray(
                onBack = { stickerPage = false },
                personaName = personaName,
                personaId = persona?.id,
                recent = recentStickers,
                art = stickerArt,
                youInk = stationery.dayColors.ink(header.userPersona?.ink ?: "sora"),
                onPick = { vm.stickOnDraft(it) },
                onDragStart = { ref, at -> dragging = ref to at },
                onDrag = { delta -> dragging = dragging?.let { (ref, at) -> ref to (at + delta) } },
                onDragEnd = {
                    dragging?.let { (ref, at) ->
                        // The innermost paper under the finger: a frame before the letter it's in.
                        papers.entries.filter { it.value.contains(at) }.minByOrNull { it.value.width * it.value.height }?.let { (target, rect) ->
                            vm.stickOn(target, ref, (at.x - rect.left) / rect.width, (at.y - rect.top) / rect.height)
                        }
                    }
                    dragging = null
                },
            )
        }
    }
    dragging?.let { (ref, at) ->
        val side = (LocalStickerSettings.current.size.dp * StickerBaker.BOX).dp
        val half = with(LocalDensity.current) { side.toPx() / 2f }
        StickerPicture(
            ref,
            stationery.dayColors.ink(header.userPersona?.ink ?: "sora"),
            Modifier
                .offset { IntOffset((at.x - screenOrigin.x - half).roundToInt(), (at.y - screenOrigin.y - half).roundToInt()) }
                .size(side)
                .graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; rotationZ = -6f },
        )
    }
    }

    editingMix?.let { mix ->
        PlaylistEditor(
            mix,
            stationery.ink(header.userPersona?.ink ?: "sora"),
            onChange = { changed -> editingMix = changed; vm.replace(Going.Item(changed)) },
            onAddSongs = { moreSongs.launch(arrayOf("audio/*")) },
            onDone = {
                // Down to one song, the mix goes as a ticket; with none, it comes out of the note.
                when (mix.tracks.size) {
                    0 -> vm.remove(Going.Item(mix))
                    1 -> mix.tracks.single().let { t ->
                        vm.replace(Going.Item(mix.copy(kind = dev.aiharness.core.model.AttachmentKind.SONG, ref = t.ref, mimeType = t.mimeType, name = t.name, title = t.title, artist = t.artist, lengthMs = t.lengthMs, tracks = emptyList(), subtitle = null)))
                    }
                    else -> Unit
                }
                editingMix = null
            },
        )
    }
    selected?.let { group -> MessageActions(group, vm, onDismiss = { selected = null }) }
    viewing?.let { ref ->
        Dialog(onDismissRequest = { viewing = null }) {
            BlobImage(ref, Modifier.fillMaxWidth().clickable { viewing = null }, contentScale = ContentScale.Fit, maxSize = 2048)
        }
    }
    if (imageDialog) {
        ImageDialog(personaName, persona?.imageProfileId, header.userPersona, onDismiss = { imageDialog = false }) { prompt, parts, orientation, profileId ->
            imageDialog = false
            vm.generateImage(prompt, parts, orientation, profileId)
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
            IconButton(onClick = { menu = "model" }) { Icon(StationeryIcons.Nib, "Model", tint = iconTint) }
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
            IconButton(onClick = { menu = "user" }) { Icon(StationeryIcons.Seal, "Writing as", tint = iconTint) }
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
        if (imageReady) IconButton(onClick = onImage) { Icon(StationeryIcons.Polaroid, "Draw a picture", tint = iconTint) }
        Box {
            IconButton(onClick = { menu = "more" }) { Icon(StationeryIcons.InkDots, "More", tint = iconTint) }
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
    trayOpen: Boolean = false,
    onTray: () -> Unit = {},
    hasAttachments: Boolean = false,
    onFieldFocus: () -> Unit = {},
    draft: @Composable RowScope.(field: @Composable () -> Unit) -> Unit = { it() },
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
        // The + slip opens the tray (stickers now; photos, files and more later). It turns into × while open.
        val turn by animateFloatAsState(if (trayOpen) 45f else 0f, label = "plus")
        Box(
            Modifier
                .size(42.dp)
                .clip(shape)
                .background(if (trayOpen) colors.chitsu else colors.kami)
                .border(1.dp, if (trayOpen) colors.nijimi2 else colors.nijimi1, shape)
                .clickable(onClickLabel = if (trayOpen) "Close the tray" else "Add something") { onTray() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, if (trayOpen) "Close the tray" else "Add something", tint = colors.sumi, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
        }
        draft {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = PaperType.preview.copy(fontSize = 15.sp, lineHeight = 22.sp, color = colors.sumi),
            cursorBrush = SolidColor(youInk),
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFieldFocus() }
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
        }
        val canSend = value.isNotBlank() || hasAttachments
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

/**
 * Drawing a picture in a letter: what to draw, which parts of the persona go in (its looks, its
 * clothes, its drawing style), the shape, and, with more than one, the image profile.
 */
@Composable
private fun ImageDialog(
    personaName: String,
    personaProfileId: String?,
    you: dev.aiharness.core.model.UserPersona?,
    onDismiss: () -> Unit,
    onGenerate: (String, dev.aiharness.core.image.PicturePrompt.Parts, String, String?) -> Unit,
) {
    val harness = LocalAppContainer.current.harness
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val profiles = settings?.imageGen?.profiles.orEmpty().filter { it.isUsable }
    var profile by remember { mutableStateOf<dev.aiharness.core.model.ImageProfile?>(null) }
    val chosen = profile ?: settings?.imageGen?.profileFor(personaProfileId)
    var prompt by remember { mutableStateOf("") }
    var looks by remember { mutableStateOf(true) }
    var clothes by remember { mutableStateOf(true) }
    var style by remember { mutableStateOf(true) }
    var withYou by remember { mutableStateOf(false) }
    // You can be in it once your page says how you look or what you wear.
    val youCanBeIn = you != null && (you.selfPrompt.isNotBlank() || you.outfit.isNotBlank())
    var orientation by remember { mutableStateOf("portrait") }
    val options = listOf("portrait", "landscape", "square")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw a picture", style = PaperType.entryTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dev.aiharness.app.ui.paper.IndexCardField("Tags or a description", prompt, { prompt = it }, style = PaperType.noteBody, minLines = 2, placeholder = "smiling, sitting in a cafe, rainy window")
                SwitchRow("How $personaName looks", looks, { looks = it })
                SwitchRow("What $personaName is wearing", clothes, { clothes = it })
                SwitchRow("$personaName's drawing style", style, { style = it })
                if (youCanBeIn) SwitchRow("${you!!.name} in it too", withYou, { withYou = it }, subtitle = "How you look and what you wear, from your page.")
                PaperChoice(options.map { it to it.replaceFirstChar { c -> c.uppercase() } }, orientation, { orientation = it })
                if (profiles.size > 1) {
                    dev.aiharness.app.ui.paper.PaperSelect("Draw with", profiles, chosen, { it.name }, { profile = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(prompt, dev.aiharness.core.image.PicturePrompt.Parts(self = looks, outfit = clothes, style = style, you = withYou && youCanBeIn), orientation, chosen?.id) },
                enabled = prompt.isNotBlank(),
            ) { Text("Draw") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A slip above the composer about the selected sticker: whose it is, and peel it off or be done. */
@Composable
private fun StickerSlip(text: String, peel: (() -> Unit)? = null, putBack: (() -> Unit)? = null, done: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .paperCard()
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PaperType.postscript, color = paper.usuzumi1, modifier = Modifier.weight(1f))
        peel?.let { TextButton(onClick = it) { Text("Peel off", color = stationery.ink("beni")) } }
        putBack?.let { TextButton(onClick = it) { Text("Put it back") } }
        TextButton(onClick = done) { Text("Done") }
    }
}

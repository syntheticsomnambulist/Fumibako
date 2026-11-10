package dev.aiharness.app.ui.chat

import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import dev.aiharness.app.ui.LocalBesideList
import dev.aiharness.app.ui.LocalReadingSides
import dev.aiharness.app.ui.WideLayout
import dev.aiharness.app.ui.centeredWithin
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.paper.StationeryIcons
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperChoice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import dev.aiharness.app.ui.media.MiniPlayer
import dev.aiharness.app.ui.media.FullPlayer
import dev.aiharness.core.media.MediaSupport
import androidx.compose.ui.graphics.toArgb
import dev.aiharness.app.ui.media.PhotoViewer
import dev.aiharness.app.ui.media.VideoPlayer
import dev.aiharness.core.model.ContentPart
import dev.aiharness.app.ui.media.EmbedPlayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.engine.GenerationManager
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.core.model.FoundLetter
import dev.aiharness.core.model.words
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
import dev.aiharness.app.platform.PickKind
import dev.aiharness.app.platform.PlatformBackHandler
import dev.aiharness.app.platform.inFrontOfYou
import dev.aiharness.app.platform.rememberCamera
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.platform.receiveFiles
import dev.aiharness.app.platform.sendOnCtrlEnter
import dev.aiharness.app.platform.rememberPicker

@Composable
fun ChatScreen(nav: NavController, conversationId: String) {
    val container = LocalAppContainer.current
    val vm: ChatViewModel = viewModel(key = conversationId) { ChatViewModel(container, conversationId) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val generation by vm.generation.collectAsStateWithLifecycle()
    val arriving by vm.arriving.collectAsStateWithLifecycle()
    val postage by vm.postage.collectAsStateWithLifecycle()
    val imageReady by vm.imageGenReady.collectAsStateWithLifecycle()
    val stickers by vm.stickers.collectAsStateWithLifecycle()
    val stickerArt by vm.stickerArt.collectAsStateWithLifecycle()
    val stickerName: (Sticker) -> String = { stickerLabel(it, stickerArt) }
    val recentStickers by vm.recentStickers.collectAsStateWithLifecycle()
    val nowPlaying by container.playback.state.collectAsStateWithLifecycle()
    val punched by vm.punched.collectAsStateWithLifecycle()
    val reads by vm.reads.collectAsStateWithLifecycle()
    // Stamps already there when the thread opened don't land again; new ones do.
    var readBaseline by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(reads != null) { if (readBaseline == null) reads?.let { readBaseline = it.keys } }
    var embedding by remember { mutableStateOf<Embedding?>(null) }
    // Music carries on after leaving the thread, unless the Media page says otherwise.
    val keepPlaying = dev.aiharness.app.ui.media.LocalMediaSettings.current.keepPlaying
    DisposableEffect(keepPlaying) { onDispose { if (!keepPlaying) container.playback.stop() } }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    // Stickers: the tray, the one selected (and its live position while it moves), dragging one in.
    var trayOpen by rememberSaveable { mutableStateOf(false) }
    var stickerPage by rememberSaveable { mutableStateOf(false) }
    var musicPage by rememberSaveable { mutableStateOf(false) }
    // Back (the phone's gesture, Escape on a computer) puts the tray away before it leaves the
    // letter: from its sticker or song page back to the tray, then the tray closed.
    PlatformBackHandler(enabled = trayOpen) {
        when {
            stickerPage -> stickerPage = false
            musicPage -> musicPage = false
            else -> trayOpen = false
        }
    }
    val going by vm.going.collectAsStateWithLifecycle()
    var editingMix by remember { mutableStateOf<dev.aiharness.core.model.ContentPart.Attachment?>(null) }
    val scope = rememberCoroutineScope()
    val pickPhotos = rememberPicker(PickKind.PHOTOS) { uris -> if (uris.isNotEmpty()) { vm.addPhotos(uris); trayOpen = false } }
    val pickVideo = rememberPicker(PickKind.VIDEO) { uris -> uris.firstOrNull()?.let { vm.addVideo(it); trayOpen = false } }
    val pickFile = rememberPicker(PickKind.FILE) { uris -> uris.firstOrNull()?.let { vm.addFile(it) { mix -> editingMix = mix }; trayOpen = false } }
    val pickSongs = rememberPicker(PickKind.SONGS) { uris ->
        if (uris.isNotEmpty()) { vm.addSongs(uris) { mix -> editingMix = mix }; trayOpen = false; musicPage = false }
    }
    val moreSongs = rememberPicker(PickKind.SONGS) { uris ->
        val mix = editingMix ?: return@rememberPicker
        if (uris.isNotEmpty()) scope.launch {
            val added = vm.moreTracks(uris)
            val updated = mix.copy(tracks = mix.tracks + added, lengthMs = (mix.tracks + added).sumOf { it.lengthMs ?: 0L })
            editingMix = updated
            vm.replace(Going.Item(updated))
        }
    }
    val takePicture = rememberCamera { shot -> vm.addPhotos(listOf(shot)); trayOpen = false }
    val pickFromTray: (TrayItem) -> Unit = { item ->
        when (item) {
            TrayItem.PHOTOS -> pickPhotos()
            TrayItem.CAMERA -> takePicture?.invoke()
            TrayItem.FILE -> pickFile()
            TrayItem.MUSIC -> musicPage = true
            TrayItem.VIDEO -> pickVideo()
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

    // What you were writing here, kept until it's sent (leaving the letters or the app doesn't lose it).
    var input by remember(conversationId) { mutableStateOf(vm.draftText) }
    LaunchedEffect(input) { vm.keepText(input) }
    var selected by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var watching by remember { mutableStateOf<Watching?>(null) }
    // The letters already here when the thread opened; anything after them has just arrived.
    var baseline by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(messages.isNotEmpty()) { if (baseline == null && messages.isNotEmpty()) baseline = messages.map { it.id }.toSet() }
    val droppedFrames = remember { mutableSetOf<String>() }
    var imageDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val persona = header.persona
    val personaName = persona?.name ?: "…"
    val prompts by vm.prompts.collectAsStateWithLifecycle()
    val rows = remember(messages, prompts) { buildThread(messages, prompts = prompts) }
    // A reply in the post isn't watched being written: the thread says it's on its way instead.
    val writing = if (generation.active && !postage.travels) {
        Writing(generation.streamingText, generation.reasoning, GenerationManager.describeTool(generation.activeTool))
    } else {
        null
    }
    // A turn in progress continues the last letter (earlier tool rounds are already saved into it).
    val writingInto = (rows.lastOrNull() as? ThreadRow.Letter)?.takeIf { writing != null && messages.lastOrNull()?.role != dev.aiharness.core.model.MessageRole.USER }

    // Opened at one of its letters (a P.S. or the index card named it): there, instead of the newest.
    val letterRequest by container.letterFocus.request.collectAsStateWithLifecycle()
    val focusHere = letterRequest?.takeIf { it.conversationId == conversationId }
    LaunchedEffect(messages.size, writing != null) {
        if (messages.isNotEmpty() && focusHere == null) listState.animateScrollToItem(0)
    }
    // A search being stepped through here (from Search letters): its matches, newest first, and the one shown.
    var finding by remember(conversationId) { mutableStateOf<LetterFocus.Find?>(null) }
    var findAt by remember(conversationId) { mutableStateOf(0) }
    // A new thread holding only her first letter offers her other greetings, until you write.
    val greetings = persona?.let { container.harness.greetingsOf(it) }.orEmpty()
    val firstLetter = (rows.singleOrNull() as? ThreadRow.Letter)?.messages?.singleOrNull()?.takeIf { it.role == dev.aiharness.core.model.MessageRole.ASSISTANT }
    val greetingAt = if (greetings.size > 1 && firstLetter != null && writing == null && !generation.active) {
        container.harness.greetingIndex(persona!!, firstLetter.text, header.userPersona?.name ?: "you") ?: 0
    } else {
        null
    }
    val leading = (if (arriving != null || (generation.active && postage.travels)) 1 else 0) + (if (writing != null && writingInto == null) 1 else 0) +
        (if (greetingAt != null) 1 else 0)
    fun rowIndexOf(messageId: String) = rows.asReversed().indexOfFirst { row ->
        when (row) {
            is ThreadRow.Note -> row.message.id == messageId
            is ThreadRow.Letter -> row.messages.any { it.id == messageId }
            else -> false
        }
    }
    LaunchedEffect(focusHere, rows) {
        val request = focusHere ?: return@LaunchedEffect
        val index = rowIndexOf(request.messageId)
        if (index < 0) return@LaunchedEffect
        request.find?.let { find ->
            finding = find
            findAt = find.matches.indexOf(request.messageId).coerceAtLeast(0)
        }
        listState.animateScrollToItem(index + leading)
        container.letterFocus.done(request)
    }
    fun stepTo(at: Int) {
        val find = finding ?: return
        val id = find.matches.getOrNull(at) ?: return
        findAt = at
        val index = rowIndexOf(id)
        if (index >= 0) scope.launch { listState.animateScrollToItem(index + leading) }
    }
    val findTerms = remember(finding) { finding?.let { dev.aiharness.core.letters.LetterWords.terms(it.query) }.orEmpty() }
    val currentMatch = finding?.matches?.getOrNull(findAt)
    val kamiColor = paper.kami
    val personaDayInk = stationery.dayColors.ink(header.persona?.ink)
    val youDayInk = stationery.dayColors.ink(header.userPersona?.ink ?: "sora")
    fun findFor(messages: List<ChatMessage>, byYou: Boolean): FindView? {
        val find = finding ?: return null
        if (messages.none { it.id in find.matches }) return null
        val current = messages.any { it.id == currentMatch }
        val ink = androidx.compose.ui.graphics.lerp(kamiColor, if (byYou) youDayInk else personaDayInk, if (current) 0.55f else 0.28f)
        return FindView(Marks(findTerms, ink), current, foundInPicture(messages, findTerms))
    }
    val openLetter: (FoundLetter) -> Unit = { found ->
        container.letterFocus.open(found.conversationId, found.messageId)
        if (found.conversationId != conversationId) nav.navigate(Routes.chat(found.conversationId))
    }
    // Everything on screen counts as read, including letters that arrive while it is open (on a
    // computer, while the window is in front of you: not while it waits in the tray).
    val seen = inFrontOfYou()
    LaunchedEffect(messages.lastOrNull()?.id, seen) { if (seen) vm.markRead() }

    // On a computer, files dropped on the letter or pasted into it (nothing on the phone).
    val receive = Modifier.receiveFiles { uris -> vm.addReceived(uris) { mix -> editingMix = mix }; trayOpen = false }
    Box(Modifier.fillMaxSize().then(receive).onGloballyPositioned { screenOrigin = it.positionInRoot() }) {
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
        finding?.let { find ->
            FindStrip(
                query = find.query,
                at = findAt,
                total = find.matches.size,
                personaName = personaName,
                onOlder = { stepTo(findAt + 1) },
                onNewer = { stepTo(findAt - 1) },
                onDone = { finding = null },
            )
        }

        val inlineStickers = remember(stickerArt, persona?.id) {
            stickerArt.values.filter { it.ownerPersonaId == null || it.ownerPersonaId == persona?.id }.associateBy { it.name }
        }
        val reader = header.persona?.let { persona -> header.profile?.let { PersonaReader(persona.name, it) } }
        // Frames in letters that arrive (and notes sent) while the thread is open drop onto the page.
        val arrivals = remember(messages, baseline) {
            baseline?.let { seen ->
                val order = mutableMapOf<String, Int>()
                messages.filter { it.id !in seen }.forEach { m ->
                    var place = 0
                    m.images.forEach { order[it.ref] = place++ }
                    m.attachments.forEach { order[it.id] = place++ }
                }
                Arrivals(order, droppedFrames)
            }
        }
        val taps = remember(conversationId) {
            FrameTaps(
                play = { attachment, ink, fromYou -> container.playback.play(attachment, conversationId, ink.toArgb(), fromYou) },
                watch = { attachment, ink, fromYou -> watching = Watching(attachment, ink, fromYou) },
                embed = { attachment, embed, ink -> container.playback.pause(); embedding = Embedding(attachment, embed, ink) },
            )
        }
        val personaInk = stationery.dayColors.ink(persona?.ink)
        val stamps = remember(reads, readBaseline, personaInk) {
            val all = reads.orEmpty()
            ReadStamps(all.filterValues { it == persona?.id }.mapValues { personaInk }, all.keys - (readBaseline ?: all.keys))
        }
        CompositionLocalProvider(
            LocalInlineStickers provides inlineStickers,
            LocalPersonaReader provides reader,
            LocalFrameTaps provides taps,
            LocalArrivals provides arrivals,
            LocalReadStamps provides stamps,
            LocalPunched provides punched,
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth().scrollbar(listState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // In a wide window, letters keep their reading width in the middle, and the thread still scrolls from its edges.
                contentPadding = PaddingValues(horizontal = LocalReadingSides.current ?: 18.dp, vertical = 18.dp),
            ) {
                if (arriving != null || (generation.active && postage.travels)) {
                    item(key = "on-its-way") { OnItsWay(personaName, persona?.ink, arriving.takeIf { postage.showArrival }) }
                }
                if (writing != null && writingInto == null) {
                    item(key = "writing") { LetterSheet(null, persona, writing, onLongPress = {}, onImage = { viewing = it }) }
                }
                if (greetingAt != null) {
                    item(key = "greetings") {
                        OtherGreetings(greetingAt, greetings.size, stationery.ink(persona?.ink)) { next ->
                            scope.launch { container.harness.swapGreeting(conversationId, next) }
                        }
                    }
                }
                // The letter holding the sticker picked or being moved comes in front of its neighbors,
                // so a big one (and its handle) isn't under the next letter.
                val liftedId = selectedStickerId ?: moving?.id
                val liftedOn = liftedId?.let { id -> shownStickers.entries.firstOrNull { (_, list) -> list.any { it.id == id } }?.key }
                fun holds(messages: List<ChatMessage>) = liftedOn != null && messages.any { m ->
                    m.id == liftedOn || m.attachments.any { it.id == liftedOn } || m.images.any { it.ref == liftedOn }
                }
                items(rows.asReversed(), key = { it.key }) { row ->
                    val lifted = when (row) {
                        is ThreadRow.Note -> holds(listOf(row.message))
                        is ThreadRow.Letter -> holds(row.messages)
                        else -> false
                    }
                    Box(Modifier.zIndex(if (lifted) 1f else 0f)) {
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
                            find = findFor(listOf(row.message), byYou = true),
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
                            onOpenLetter = openLetter,
                            find = findFor(row.messages, byYou = false),
                        )
                        is ThreadRow.ModelChange -> ModelChangeNotice(
                            row,
                            persona?.words ?: dev.aiharness.core.model.Words(personaName, null),
                            personaName,
                            // When it was changed on its page, if that's this change.
                            changedAt = persona?.modelSince?.takeIf { it <= row.at && it >= (row.after ?: 0L) },
                        )
                    }
                    }
                    // A row that scrolls away can't be dropped on.
                    if (row is ThreadRow.Note || row is ThreadRow.Letter) {
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

        // Under the thread, at the letters' reading width in a wide window (the whole width on the phone).
        val readingSides = LocalReadingSides.current
        Column(Modifier.fillMaxWidth().centeredWithin(readingSides?.let { WideLayout.READING_WIDTH + WideLayout.PAGE_PADDING * 2 })) {
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

            nowPlaying?.let { MiniPlayer(it, container.playback, onOpen = { playerOpen = true }) }
            GoingWithYourNote(going, onRemove = { vm.remove(it) }, switchesTo = vm::switchesTo, onOpen = { item ->
                if (vm.switchesTo(item) != null) vm.switchSongOrCutting(item)
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
            val sendOutside = rememberOutside()
            EntryBar(
                value = input,
                onValueChange = { input = it; vm.noticeLinks(it) },
                placeholder = "Write to $personaName…",
                personaInk = stationery.ink(persona?.ink),
                youInk = youInk,
                busy = generation.active,
                onSend = {
                    if (vm.send(input)) {
                        input = ""
                        trayOpen = false
                    } else {
                        // A photo, file, song or video still being made ready holds the note back; say so.
                        going.firstOrNull { it is Going.Preparing }?.let { waiting ->
                            sendOutside.toast("Still getting ${(waiting as Going.Preparing).label} ready.")
                        }
                    }
                },
                hasAttachments = going.isNotEmpty() && going.none { it is Going.Preparing },
                onStop = { vm.stop() },
                trayOpen = trayOpen,
                onTray = {
                    if (trayOpen) trayOpen = false else {
                        keyboard?.hide()
                        focus.clearFocus()
                        stickerPage = false
                        musicPage = false
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
            if (trayOpen && !stickerPage && !musicPage) TrayButtons(pickFromTray)
            if (trayOpen && musicPage) {
                val songSearch by vm.songSearch.collectAsStateWithLifecycle()
                MusicTray(
                    search = songSearch,
                    onQuery = vm::searchSongs,
                    onPick = { song -> vm.addFoundSong(song) { mix -> editingMix = mix }; trayOpen = false; musicPage = false },
                    onFromPhone = { pickSongs() },
                    onBack = { musicPage = false },
                    youInk = stationery.dayColors.ink(header.userPersona?.ink ?: "sora"),
                )
            }
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
    // The full player, over the thread; it shrinks back to the mini player.
    val playing = nowPlaying
    if (playerOpen && playing != null) {
        FullPlayer(
            playing,
            container.playback,
            knows = if (playing.fromYou) reader(header)?.let { MediaSupport.cantTakeIn(playing.attachment, it.profile, it.name) } else null,
            onClose = { playerOpen = false },
        )
    }
    LaunchedEffect(playing == null) { if (playing == null) playerOpen = false }

    // A photo opens the viewer, with the rest of its letter's or note's photos to swipe through.
    viewing?.let { ref ->
        val images = messages.firstOrNull { m -> m.images.any { it.ref == ref } }?.images ?: listOf(ContentPart.Image(ref))
        PhotoViewer(images, images.indexOfFirst { it.ref == ref }.coerceAtLeast(0), onClose = { viewing = null })
    }
    // A persona's song from YouTube, SoundCloud or Bandcamp plays in the service's own player.
    embedding?.let { open ->
        EmbedPlayer(open.song, open.embed, open.ink, from = personaName, onClose = { embedding = null })
    }
    // A film strip opens the video player, with the letter's other clips.
    watching?.let { open ->
        val clips = messages.firstOrNull { m -> m.attachments.any { it.id == open.video.id } }
            ?.attachments?.filter { it.kind == dev.aiharness.core.model.AttachmentKind.VIDEO && it.ref != null } ?: listOf(open.video)
        VideoPlayer(
            clips,
            clips.indexOfFirst { it.id == open.video.id }.coerceAtLeast(0),
            from = if (open.fromYou) header.userPersona?.name ?: "you" else personaName,
            ink = open.ink,
            line = { video -> if (open.fromYou) reader(header)?.let { MediaSupport.cantTakeIn(video, it.profile, it.name) } else null },
            onOpen = { container.playback.pause() },
            onClose = { watching = null },
        )
    }
    }

    editingMix?.let { mix ->
        PlaylistEditor(
            mix,
            stationery.ink(header.userPersona?.ink ?: "sora"),
            onChange = { changed -> editingMix = changed; vm.replace(Going.Item(changed)) },
            onAddSongs = { moreSongs() },
            onDone = {
                // Down to one song, the mix goes as a ticket; with none, it comes out of the note.
                when (mix.tracks.size) {
                    0 -> vm.remove(Going.Item(mix))
                    // Its preview, cover and link too, for a song from the search.
                    1 -> vm.replace(Going.Item(mix.tracks.single().ticket(mix.id)))
                    else -> Unit
                }
                editingMix = null
            },
        )
    }
    selected?.let { group -> MessageActions(group, vm, onDismiss = { selected = null }) }
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
        // Beside the letters list (a wide window) there's no back arrow: the list is right there.
        if (LocalBesideList.current) Spacer(Modifier.width(14.dp)) else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = iconTint) }
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
                    DropdownMenuItem(text = { Text("Search these letters") }, onClick = { menu = null; nav.navigate(Routes.searchFor(p.id)) })
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
        val fieldStyle = PaperType.preview.copy(fontSize = 15.sp, lineHeight = 22.sp, color = colors.sumi)
        // The buttons stand as tall as the field holding one line (its line and its 10 dp above and
        // below), measured in its own type, so they match at any text size.
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val side = remember(fieldStyle, density) {
            with(density) { measurer.measure("A", fieldStyle).size.height.toDp() } + 20.dp
        }.coerceAtLeast(42.dp)
        // The + slip opens the tray (stickers now; photos, files and more later). It turns into × while open.
        val turn by animateFloatAsState(if (trayOpen) 45f else 0f, label = "plus")
        Box(
            Modifier
                .size(side)
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
            textStyle = fieldStyle,
            cursorBrush = SolidColor(youInk),
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFieldFocus() }
                // On a computer, Ctrl+Enter sends (Enter starts a new line: letters are long).
                .sendOnCtrlEnter { if (!busy && (value.isNotBlank() || hasAttachments)) onSend() }
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
                .size(side)
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
    val outside = rememberOutside()
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
                        outside.copyText(all, label = "letter")
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
internal fun StickerSlip(text: String, peel: (() -> Unit)? = null, putBack: (() -> Unit)? = null, done: () -> Unit) {
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

private fun reader(header: ChatHeader): PersonaReader? = header.persona?.let { persona -> header.profile?.let { PersonaReader(persona.name, it) } }

/** A video being watched: which, in whose ink, and whether you sent it. */
private data class Watching(val video: ContentPart.Attachment, val ink: androidx.compose.ui.graphics.Color, val fromYou: Boolean)

/** A persona's song playing in its service's embedded player. */
private data class Embedding(val song: ContentPart.Attachment, val embed: String, val ink: androidx.compose.ui.graphics.Color)

/**
 * The strip under the header while a search's matches are stepped through: the words, which one
 * this is ("2 of 3 in your letters with Aria"), older and newer, and Done.
 */
@Composable
private fun FindStrip(query: String, at: Int, total: Int, personaName: String, onOlder: () -> Unit, onNewer: () -> Unit, onDone: () -> Unit) {
    val colors = paper
    val rule = androidx.compose.ui.graphics.lerp(colors.kami, stationery.ink("beni"), 0.4f)
    val card = if (colors.isNight) androidx.compose.ui.graphics.lerp(colors.kami, colors.hyoshi, 0.3f) else androidx.compose.ui.graphics.lerp(colors.kami, androidx.compose.ui.graphics.Color.White, 0.45f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(card)
            .drawBehind {
                drawLine(rule, Offset(0f, 3.dp.toPx()), Offset(size.width, 3.dp.toPx()), 1.5.dp.toPx())
                drawLine(colors.nijimi0, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .semantics(mergeDescendants = false) { contentDescription = "Matches in these letters" }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(StationeryIcons.Loupe, null, tint = colors.usuzumi0, modifier = Modifier.size(16.dp))
        Text("“$query”", style = PaperType.preview.copy(fontSize = 15.sp), color = colors.sumi, modifier = Modifier.padding(start = 8.dp))
        Text(
            "${at + 1} of $total with $personaName",
            style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
            color = colors.usuzumi0,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButton(onClick = onOlder, enabled = at < total - 1) {
            Icon(Icons.Default.KeyboardArrowUp, "Older match", tint = colors.sumi.copy(alpha = if (at < total - 1) 1f else 0.35f))
        }
        IconButton(onClick = onNewer, enabled = at > 0) {
            Icon(Icons.Default.KeyboardArrowDown, "Newer match", tint = colors.sumi.copy(alpha = if (at > 0) 1f else 0.35f))
        }
        TextButton(onClick = onDone) { Text("Done", style = PaperType.label.copy(letterSpacing = 0.sp, fontSize = 12.5.sp), color = stationery.you) }
    }
}

/** Under a new thread's only letter: which of her first letters it is, and another to read instead. */
@Composable
private fun OtherGreetings(at: Int, count: Int, ink: androidx.compose.ui.graphics.Color, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("First letter ${at + 1} of $count", style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.kasure2)
        Spacer(Modifier.width(8.dp))
        dev.aiharness.app.ui.paper.InkLink("Another", ink) { onPick((at + 1) % count) }
    }
}

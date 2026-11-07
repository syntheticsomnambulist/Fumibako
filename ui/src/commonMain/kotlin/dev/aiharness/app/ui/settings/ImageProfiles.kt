package dev.aiharness.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperSelect
import dev.aiharness.app.ui.paper.StepperRow
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.personas.PageBar
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.image.ComfyUiImageGenerator
import dev.aiharness.core.image.GoogleImageGenerator
import dev.aiharness.core.image.ImageProfiles
import dev.aiharness.core.image.NovelAiImageGenerator
import dev.aiharness.core.image.OpenAiImageGenerator
import dev.aiharness.core.image.StableDiffusionImageGenerator
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.model.ImageProfile
import dev.aiharness.core.util.newId
import kotlinx.coroutines.launch

private fun keyKind(host: ImageHost): CredentialKind? = when (host) {
    ImageHost.NOVELAI -> CredentialKind.NOVELAI
    ImageHost.OPENAI -> CredentialKind.OPENAI_COMPATIBLE
    ImageHost.GOOGLE -> CredentialKind.GEMINI
    ImageHost.STABLE_DIFFUSION, ImageHost.COMFYUI -> null
}

/** What each host is, in a line, for the "start from" list. */
private fun hostNote(host: ImageHost) = when (host) {
    ImageHost.NOVELAI -> "Anime and illustration, with a NovelAI key."
    ImageHost.STABLE_DIFFUSION -> "Your own server running Forge or Automatic1111 with its API on."
    ImageHost.COMFYUI -> "Your own ComfyUI server, with a default workflow you can replace."
    ImageHost.OPENAI -> "GPT image models, with an OpenAI key."
    ImageHost.GOOGLE -> "Gemini and Imagen, with a Google AI Studio key."
}

/**
 * One image profile: where pictures are drawn and how. A new one ([Routes.NEW]) starts by
 * choosing its host; the fields shown are the ones that host uses.
 */
@Composable
fun ImageProfileScreen(nav: NavController, profileId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val isNew = profileId == Routes.NEW
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val credentials by remember { harness.credentials.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var draft by remember { mutableStateOf<ImageProfile?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var trial by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val you = stationery.you
    val tape = Tape(you, stationery.youPattern)
    val layouts = tapeLayouts("image-$profileId", 5)
    val s = settings ?: return
    if (!isNew && draft == null) draft = s.imageGen.profiles.firstOrNull { it.id == profileId }
    val p = draft

    fun update(transform: (ImageProfile) -> ImageProfile) {
        draft = draft?.let(transform)
    }

    /** Stores the draft (adding it if it's new); [asDefault] also makes it the default. */
    fun store(asDefault: Boolean, then: () -> Unit) {
        val profile = draft ?: return
        scope.launch {
            harness.settings.update { a ->
                val known = a.imageGen.profiles.any { it.id == profile.id }
                val list = if (known) a.imageGen.profiles.map { if (it.id == profile.id) profile else it } else a.imageGen.profiles + profile
                val default = if (asDefault) profile.id else a.imageGen.defaultProfileId ?: profile.id
                a.copy(imageGen = a.imageGen.copy(profiles = list, defaultProfileId = default))
            }
            onMain(then)
        }
    }

    fun save() = store(asDefault = false) { nav.popBackStack() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("bar") {
            PageBar(onBack = { nav.popBackStack() }) {
                if (p != null) PaperButton("Save", ink = you) { save() }
            }
        }
        item("title") {
            Column {
                Text(if (p == null) "New image profile" else p.name.ifBlank { "Image profile" }, style = PaperType.screenTitle.copy(fontSize = 26.sp, lineHeight = 30.sp), color = paper.sumi)
                Text((if (p == null) "Where pictures are drawn" else p.host.label).uppercase(), style = PaperType.meta, color = paper.kasure2)
            }
        }
        if (p == null) {
            if (!isNew) {
                item("gone") { Text("This profile isn't here any more.", style = PaperType.prose.copy(fontStyle = FontStyle.Italic), color = paper.usuzumi0) }
                return@LazyColumn
            }
            item("hosts") {
                TapedCard(layouts[0], tape, tilt = cardTilt("image-hosts")) {
                    CardTitle("Start from")
                    ImageHost.entries.forEach { host ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(paper.hyoshi, PaperCorner)
                                .clickable(role = Role.Button) {
                                    val made = ImageProfiles.preset(host, newId())
                                    val key = keyKind(host)?.let { kind -> credentials.firstOrNull { it.kind == kind } }
                                    draft = made.copy(credentialId = key?.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(host.label, style = PaperType.noteBody, color = paper.sumi)
                            Text(hostNote(host), style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = paper.usuzumi0)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item("where") {
            TapedCard(layouts[0], tape, tilt = cardTilt("${p.id}-where")) {
                CardTitle("Where")
                IndexCardField("Name", p.name, { v -> update { it.copy(name = v) } }, ink = you, singleLine = true, style = PaperType.prose)
                if (p.host.needsKey) {
                    val kind = keyKind(p.host)
                    val keys = credentials.filter { kind == null || it.kind == kind }.ifEmpty { credentials }
                    PickerField("Key", keys, credentials.firstOrNull { it.id == p.credentialId }, { it.label }, { c -> update { it.copy(credentialId = c?.id) } }, noneLabel = "None")
                    InkLink("Add a key", you) { nav.navigate(Routes.KEYS) }
                }
                if (p.host.needsServer) {
                    IndexCardField("Server address", p.baseUrl.orEmpty(), { v -> update { it.copy(baseUrl = v.trim().ifBlank { null }) } }, ink = you, singleLine = true, style = PaperType.noteBody, placeholder = if (p.host == ImageHost.COMFYUI) "http://192.168.1.20:8188" else "http://192.168.1.20:7860")
                    Hint(if (p.host == ImageHost.COMFYUI) "Start ComfyUI with --listen so the phone can reach it on your Wi-Fi." else "Start it with --api --listen so the phone can reach it on your Wi-Fi.")
                }
                ModelField(p) { v -> update { it.copy(model = v) } }
            }
        }
        item("drawing") {
            TapedCard(layouts[1], tape, tilt = cardTilt("${p.id}-drawing")) {
                CardTitle("Drawing")
                val sizes = when (p.host) {
                    ImageHost.OPENAI -> listOf(1024 to 1536, 1536 to 1024, 1024 to 1024)
                    ImageHost.GOOGLE -> listOf(896 to 1200, 1200 to 896, 1024 to 1024)
                    else -> listOf(832 to 1216, 1216 to 832, 1024 to 1024, 512 to 768)
                }
                PaperSelect("Size", sizes, p.width to p.height, { "${it.first} × ${it.second}" }, { v -> v?.let { (w, h) -> update { it.copy(width = w, height = h) } } })
                when (p.host) {
                    ImageHost.NOVELAI, ImageHost.STABLE_DIFFUSION, ImageHost.COMFYUI -> {
                        StepperRow("Steps", p.steps, { n -> update { it.copy(steps = n) } }, 1..80)
                        StepperRow("Guidance", (p.scale * 10).toInt(), { n -> update { it.copy(scale = n / 10.0) } }, 0..200, step = 5, describe = { "%.1f".format(it / 10.0) })
                        val samplers = when (p.host) {
                            ImageHost.NOVELAI -> NovelAiImageGenerator.SAMPLERS
                            ImageHost.COMFYUI -> ComfyUiImageGenerator.SAMPLERS
                            else -> StableDiffusionImageGenerator.SAMPLERS
                        }
                        PaperSelect("Sampler", (samplers + p.sampler).distinct(), p.sampler, { it }, { v -> v?.let { x -> update { it.copy(sampler = x) } } })
                        when (p.host) {
                            ImageHost.NOVELAI -> PaperSelect("Noise schedule", NovelAiImageGenerator.NOISE_SCHEDULES, p.noiseSchedule, { it }, { v -> v?.let { x -> update { it.copy(noiseSchedule = x) } } })
                            ImageHost.COMFYUI -> PaperSelect("Scheduler", ComfyUiImageGenerator.SCHEDULERS, p.noiseSchedule, { it }, { v -> v?.let { x -> update { it.copy(noiseSchedule = x) } } })
                            else -> IndexCardField("Scheduler (optional)", p.noiseSchedule, { v -> update { it.copy(noiseSchedule = v.trim()) } }, ink = you, singleLine = true, style = PaperType.noteBody, placeholder = "Karras")
                        }
                    }
                    ImageHost.OPENAI -> PaperSelect("Quality", OpenAiImageGenerator.QUALITIES, p.quality, { it }, { v -> v?.let { q -> update { it.copy(quality = q) } } })
                    ImageHost.GOOGLE -> Hint("Google picks the size from the shape: tall, wide or square.")
                }
            }
        }
        item("tags") {
            TapedCard(layouts[2], tape, tilt = cardTilt("${p.id}-tags")) {
                CardTitle(if (p.host.takesTags) "Tags" else "Avoiding")
                if (p.host == ImageHost.NOVELAI) {
                    SwitchRow("Add quality tags", p.qualityToggle, { v -> update { it.copy(qualityToggle = v) } })
                    PaperSelect("Undesired content preset", NovelAiImageGenerator.UC_PRESETS, p.ucPreset, { it }, { v -> v?.let { x -> update { it.copy(ucPreset = x) } } })
                }
                IndexCardField("Things to avoid", p.negativePrompt, { v -> update { it.copy(negativePrompt = v) } }, ink = you, style = PaperType.noteBody, minLines = 2)
                if (!p.host.takesTags) Hint("${p.host.label} takes descriptions, so prompts are sent as sentences and these are added as \"Avoid: …\".")
                if (p.host == ImageHost.NOVELAI) Hint("Opus subscribers draw for free at up to 1024×1024 and 28 steps; larger pictures cost Anlas.")
            }
        }
        if (p.host == ImageHost.COMFYUI) {
            item("workflow") {
                TapedCard(layouts[3], tape, tilt = cardTilt("${p.id}-workflow")) {
                    CardTitle("Workflow")
                    IndexCardField(
                        "Workflow (API format JSON)",
                        p.workflow.orEmpty(),
                        { v -> update { it.copy(workflow = v.ifBlank { null }) } },
                        ink = you,
                        style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        minLines = 3,
                        placeholder = "Blank: a plain text-to-image workflow",
                    )
                    Hint("Export it from ComfyUI with \"Save (API format)\". These are filled in: %prompt%, %negative%, %checkpoint%, %sampler%, %scheduler%, %width%, %height%, %steps%, %cfg% and %seed%.")
                }
            }
        }
        item("try") {
            TapedCard(layouts[4], tape, tilt = cardTilt("${p.id}-try")) {
                CardTitle("Try it")
                PaperButton(if (busy) "Drawing…" else "Draw a test picture", Modifier.fillMaxWidth(), ink = you, enabled = !busy && p.isUsable) {
                    busy = true
                    status = null
                    scope.launch {
                        try {
                            val image = harness.images.generate(s.imageGen, "a cup of tea on a wooden desk, morning light", profile = p, orientation = "square")
                            trial = image.ref
                        } catch (e: Exception) {
                            status = "It didn't work: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
                if (!p.isUsable) Hint(if (p.host.needsKey) "Choose a key first." else "Add the server's address first.")
                trial?.let { BlobImage(it, Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, paper.nijimi1, PaperCorner), maxSize = 1024) }
                status?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp), color = paper.usuzumi1) }
            }
        }
        item("default") {
            val isDefault = s.imageGen.profileFor(null)?.id == p.id
            Row(Modifier.fillMaxWidth().clickable(enabled = !isDefault) { store(asDefault = true) {} }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Default for pictures", style = PaperType.noteBody, color = paper.sumi)
                    Text("Personas use it unless their page chooses another.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
                }
                TickBox(isDefault, null, ink = you)
            }
        }
        if (!isNew) {
            item("delete") {
                val danger = stationery.ink("beni")
                Text(
                    "Delete this profile",
                    style = PaperType.noteBody.copy(fontSize = 14.sp),
                    color = danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, danger, PaperCorner)
                        .clickable(role = Role.Button) {
                            scope.launch {
                                harness.settings.update { a ->
                                    val rest = a.imageGen.profiles.filter { it.id != p.id }
                                    a.copy(imageGen = a.imageGen.copy(profiles = rest, defaultProfileId = a.imageGen.defaultProfileId.takeIf { it != p.id }))
                                }
                                onMain { nav.popBackStack() }
                            }
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** The model: a list for hosted models (with your own allowed), a checkpoint name for local ones. */
@Composable
private fun ModelField(p: ImageProfile, onChange: (String) -> Unit) {
    val known = when (p.host) {
        ImageHost.NOVELAI -> NovelAiImageGenerator.MODELS
        ImageHost.OPENAI -> OpenAiImageGenerator.MODELS
        ImageHost.GOOGLE -> GoogleImageGenerator.MODELS
        ImageHost.STABLE_DIFFUSION, ImageHost.COMFYUI -> emptyList()
    }
    when (p.host) {
        ImageHost.STABLE_DIFFUSION -> IndexCardField("Checkpoint (optional)", p.model, onChange, ink = stationery.you, singleLine = true, style = PaperType.noteBody, placeholder = "Blank: whatever the server has loaded")
        ImageHost.COMFYUI -> IndexCardField("Checkpoint", p.model, onChange, ink = stationery.you, singleLine = true, style = PaperType.noteBody, placeholder = "animagine-xl-4.0.safetensors")
        else -> {
            PaperSelect("Model", (known + p.model).filter { it.isNotBlank() }.distinct(), p.model.ifBlank { null }, { it }, { v -> v?.let(onChange) })
            IndexCardField("Or another model id", p.model.takeIf { it !in known }.orEmpty(), { v -> onChange(v.trim()) }, ink = stationery.you, singleLine = true, style = PaperType.noteBody)
        }
    }
}

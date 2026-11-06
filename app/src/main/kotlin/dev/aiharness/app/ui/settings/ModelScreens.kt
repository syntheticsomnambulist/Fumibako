package dev.aiharness.app.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.aiharness.app.data.ModelPresets
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.NumberField
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.personas.PageBar
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.complete
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.media.MediaSupport
import dev.aiharness.core.media.Senses
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.ToolMode
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject

private fun effortOptions(provider: ProviderType) = when (provider) {
    ProviderType.ANTHROPIC -> listOf("low", "medium", "high", "xhigh", "max")
    ProviderType.OPENAI_COMPATIBLE -> listOf("minimal", "low", "medium", "high")
    ProviderType.GEMINI -> listOf("low", "high")
    ProviderType.NOVELAI_TEXT -> emptyList()
}

/**
 * One model profile, as a page of cards. A new one ([Routes.NEW]) starts by choosing a preset to
 * fill it in from.
 */
@Composable
fun ModelEditScreen(nav: NavController, profileId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val isNew = profileId == Routes.NEW
    val credentials by remember { harness.credentials.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var draft by remember { mutableStateOf<ModelProfile?>(null) }
    var loaded by remember { mutableStateOf(isNew) }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFetched by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var extraBodyText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val you = stationery.you
    val tape = Tape(you, stationery.youPattern)
    val layouts = tapeLayouts("model-$profileId", 5)

    LaunchedEffect(profileId) {
        if (!isNew) {
            draft = harness.profiles.get(profileId)
            extraBodyText = draft?.extraBody?.toString().orEmpty()
            loaded = true
        }
    }

    val profile = draft
    fun update(transform: (ModelProfile) -> ModelProfile) {
        draft = draft?.let(transform)
    }

    fun withExtraBody(p: ModelProfile): ModelProfile? {
        if (extraBodyText.isBlank()) return p.copy(extraBody = null)
        return runCatching { p.copy(extraBody = HarnessJson.parseToJsonElement(extraBodyText).jsonObject) }.getOrNull()
    }

    fun save() {
        val p = profile ?: return
        val final = withExtraBody(p)
        if (final == null) {
            status = "Extra request fields must be a JSON object."
            return
        }
        scope.launch {
            harness.profiles.upsert(final)
            onMain { nav.popBackStack() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("bar") {
            PageBar(onBack = { nav.popBackStack() }) {
                if (profile != null) PaperButton("Save", ink = you) { save() }
            }
        }
        item("title") {
            Column {
                Text(if (profile == null) "New model profile" else profile.name.ifBlank { "Model profile" }, style = PaperType.screenTitle.copy(fontSize = 26.sp, lineHeight = 30.sp), color = paper.sumi)
                Text(if (profile == null) "START FROM A PROVIDER" else "${profile.provider.displayName} · ${profile.modelId.ifBlank { "no model chosen" }}".uppercase(), style = PaperType.meta, color = paper.kasure2)
            }
        }
        if (profile == null) {
            if (!loaded) return@LazyColumn
            if (!isNew) {
                item("gone") { Text("This profile isn't here any more.", style = PaperType.prose.copy(fontStyle = FontStyle.Italic), color = paper.usuzumi0) }
                return@LazyColumn
            }
            item("presets") {
                TapedCard(layouts[0], tape, tilt = cardTilt("presets")) {
                    CardTitle("Start from")
                    ModelPresets.all.forEach { preset ->
                        Text(
                            preset.label,
                            style = PaperType.noteBody,
                            color = paper.sumi,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(paper.hyoshi, PaperCorner)
                                .clickable(role = Role.Button) {
                                    val built = preset.build()
                                    // The first key you've added for this provider, so a new profile works straight away.
                                    val key = credentials.firstOrNull { it.kind == ModelPresets.credentialKindFor(built.provider) }
                                    val made = if (built.credentialId == null && key != null) built.copy(credentialId = key.id) else built
                                    draft = made
                                    extraBodyText = made.extraBody?.toString().orEmpty()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    Hint("You can change everything on the next page.")
                }
            }
            return@LazyColumn
        }

        item("who") {
            TapedCard(layouts[0], tape, tilt = cardTilt("${profile.id}-who")) {
                CardTitle("Provider and key")
                IndexCardField("Name", profile.name, { v -> update { it.copy(name = v) } }, ink = you, singleLine = true, style = PaperType.prose)
                PickerField("Provider", ProviderType.entries, profile.provider, { it.displayName }, { v ->
                    v?.let { type -> update { it.copy(provider = type, reasoningEffort = null) } }
                })
                if (profile.provider != ProviderType.NOVELAI_TEXT) {
                    IndexCardField("Base URL (blank for the provider's own)", profile.baseUrl.orEmpty(), { v -> update { it.copy(baseUrl = v.ifBlank { null }) } }, ink = you, singleLine = true, style = PaperType.noteBody)
                }
                val matching = credentials.filter { it.kind == ModelPresets.credentialKindFor(profile.provider) }
                PickerField("API key", matching.ifEmpty { credentials }, credentials.firstOrNull { it.id == profile.credentialId }, { it.label }, { c ->
                    update { it.copy(credentialId = c?.id) }
                }, noneLabel = "None")
                InkLink("Add a key") { nav.navigate(Routes.KEYS) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    IndexCardField("Model id", profile.modelId, { v -> update { it.copy(modelId = v.trim()) } }, Modifier.weight(1f), ink = you, singleLine = true, style = PaperType.noteBody)
                    Box {
                        PaperButton(if (busy) "…" else "Fetch", enabled = !busy) {
                            busy = true
                            status = null
                            scope.launch {
                                try {
                                    val key = harness.apiKey(profile.credentialId)
                                    fetched = harness.providers.raw(profile).listModels(profile, key)
                                    showFetched = fetched.isNotEmpty()
                                    if (fetched.isEmpty()) status = "The server returned no models."
                                } catch (e: Exception) {
                                    status = "Couldn't list models: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        DropdownMenu(expanded = showFetched, onDismissRequest = { showFetched = false }) {
                            fetched.forEach { id ->
                                DropdownMenuItem(text = { Text(id) }, onClick = {
                                    showFetched = false
                                    update { it.copy(modelId = id) }
                                })
                            }
                        }
                    }
                }
            }
        }
        item("limits") {
            TapedCard(layouts[1], tape, tilt = cardTilt("${profile.id}-limits")) {
                CardTitle("Limits")
                NumberField("Longest reply (tokens)", profile.maxOutputTokens, { v -> v.toIntOrNull()?.let { n -> update { it.copy(maxOutputTokens = n.coerceAtLeast(16)) } } })
                NumberField("Context window (tokens)", profile.contextWindowTokens, { v -> v.toIntOrNull()?.let { n -> update { it.copy(contextWindowTokens = n.coerceAtLeast(1_000)) } } })
                NumberField("Summarize after (tokens)", profile.contextBudgetTokens, { v -> v.toIntOrNull()?.let { n -> update { it.copy(contextBudgetTokens = n.coerceAtLeast(2_000)) } } })
            }
        }
        item("generation") {
            TapedCard(layouts[2], tape, tilt = cardTilt("${profile.id}-gen")) {
                CardTitle("Writing")
                val efforts = effortOptions(profile.provider)
                if (efforts.isNotEmpty()) {
                    PickerField(
                        if (profile.provider == ProviderType.GEMINI) "Thinking level" else "Reasoning effort",
                        efforts, profile.reasoningEffort, { it }, { v -> update { it.copy(reasoningEffort = v) } }, noneLabel = "Provider default",
                    )
                }
                if (profile.provider == ProviderType.ANTHROPIC) {
                    Hint("Current Claude models don't take temperature or top P; effort does that job.")
                    SwitchRow("Adaptive thinking", profile.options.anthropicAdaptiveThinking, { v -> update { it.copy(options = it.options.copy(anthropicAdaptiveThinking = v)) } },
                        subtitle = "Turn off only for older models that don't support it.")
                    SwitchRow("Show thought summaries", profile.options.anthropicThinkingDisplay != "omitted", { v ->
                        update { it.copy(options = it.options.copy(anthropicThinkingDisplay = if (v) "summarized" else "omitted")) }
                    })
                    SwitchRow("Refusal fallback", profile.options.anthropicServerFallback, { v -> update { it.copy(options = it.options.copy(anthropicServerFallback = v)) } },
                        subtitle = "If the model declines, the API retries on a fallback model (supported models only).")
                    SwitchRow("Prompt caching", profile.options.anthropicPromptCaching, { v -> update { it.copy(options = it.options.copy(anthropicPromptCaching = v)) } })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Temperature", profile.temperature, { v -> update { it.copy(temperature = v.toDoubleOrNull()) } }, Modifier.weight(1f), decimal = true)
                        NumberField("Top P", profile.topP, { v -> update { it.copy(topP = v.toDoubleOrNull()) } }, Modifier.weight(1f), decimal = true)
                    }
                }
                if (profile.provider == ProviderType.OPENAI_COMPATIBLE) {
                    PickerField("Max tokens field", listOf("max_tokens", "max_completion_tokens"), profile.options.openAiMaxTokensField, { it }, { v ->
                        v?.let { f -> update { it.copy(options = it.options.copy(openAiMaxTokensField = f)) } }
                    })
                    SwitchRow("Request usage stats", profile.options.openAiIncludeUsage, { v -> update { it.copy(options = it.options.copy(openAiIncludeUsage = v)) } },
                        subtitle = "Turn off if the server rejects stream_options.")
                }
                PickerField("Tools", ToolMode.entries, profile.toolMode, {
                    when (it) {
                        ToolMode.NATIVE -> "Native function calling"
                        ToolMode.EMULATED -> "Prompt-based (for models without tool calling)"
                        ToolMode.NONE -> "No tools"
                    }
                }, { v -> v?.let { m -> update { it.copy(toolMode = m) } } })
                // Claude and Gemini models always take pictures in; servers behind the OpenAI form vary.
                if (profile.provider == ProviderType.OPENAI_COMPATIBLE) {
                    SwitchRow("Sees pictures", profile.supportsVision, { v -> update { it.copy(supportsVision = v) } })
                }
                Text(takesIn(MediaSupport.of(profile)), style = PaperType.postscript, color = paper.usuzumi1)
                if (profile.provider != ProviderType.ANTHROPIC) {
                    IndexCardField("Extra request fields (JSON)", extraBodyText, { extraBodyText = it }, ink = you, style = PaperType.noteBody, placeholder = "{\"min_p\": 0.05}")
                }
            }
        }
        item("check") {
            TapedCard(layouts[3], tape, tilt = cardTilt("${profile.id}-check")) {
                CardTitle("Try it")
                PaperButton(if (busy) "Sending…" else "Send a test message", Modifier.fillMaxWidth(), ink = you, enabled = !busy && profile.modelId.isNotBlank()) {
                    busy = true
                    status = "Sending a test message…"
                    scope.launch {
                        status = try {
                            val p = withExtraBody(profile) ?: profile
                            val result = harness.providers.forProfile(p.copy(toolMode = ToolMode.NONE)).complete(
                                LlmRequest(p, harness.apiKey(p.credentialId), "Reply in five words or fewer.", listOf(LlmMessage.User(listOf(LlmPart.Text("Say hello!"))))),
                            )
                            "It answered: \"${result.text.trim()}\" (${result.modelId ?: p.modelId})"
                        } catch (e: Exception) {
                            "It didn't work: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
                status?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp), color = paper.usuzumi1) }
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
                        .clickable(role = Role.Button) { confirmDelete = true }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }

    if (confirmDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${profile.name}?", style = PaperType.entryTitle) },
            text = { Text("Letters using it fall back to the default model.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        harness.profiles.delete(profile.id)
                        onMain { nav.popBackStack() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** What a model takes in besides text, as a line under its settings. */
internal fun takesIn(senses: Senses): String {
    fun list(items: List<String>) = if (items.size == 1) items[0] else items.dropLast(1).joinToString(", ") + " and " + items.last()
    val takes = listOfNotNull(
        "pictures".takeIf { senses.pictures },
        "PDFs".takeIf { senses.pdfs },
        "songs".takeIf { senses.audio },
        "videos".takeIf { senses.video },
    ) + "text files"
    val sees = if (senses.pictures) listOfNotNull("a PDF's first page".takeIf { !senses.pdfs }, "three frames of a video".takeIf { !senses.video }) else emptyList()
    val labels = listOfNotNull(
        "pictures".takeIf { !senses.pictures },
        "PDFs".takeIf { !senses.pdfs && !senses.pictures },
        "songs".takeIf { !senses.audio },
        "videos".takeIf { !senses.video && !senses.pictures },
    )
    return buildString {
        append("Takes in ${list(takes)}.")
        if (sees.isNotEmpty()) append(" Sees ${list(sees)}.")
        if (labels.isNotEmpty()) append(" ${list(labels).replaceFirstChar { it.uppercase() }} go by their labels.")
    }
}

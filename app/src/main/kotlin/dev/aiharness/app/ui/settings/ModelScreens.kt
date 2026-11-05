package dev.aiharness.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.data.ModelPresets
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.NumberField
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SectionTitle
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.complete
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.ToolMode
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val profiles by harness.profiles.observeAll().collectAsStateWithLifecycle(emptyList())
    var choosing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Model profiles") },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { choosing = true }) { Icon(Icons.Filled.Add, "Add model") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (profiles.isEmpty()) {
                item { Text("No models yet. Tap + and pick a provider.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(profiles, key = { it.id }) { profile ->
                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text("${profile.provider.displayName} · ${profile.modelId.ifBlank { "no model id" }}") },
                    modifier = Modifier.clickable { nav.navigate(Routes.model(profile.id)) },
                )
            }
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Start from") },
            text = {
                LazyColumn {
                    items(ModelPresets.all) { preset ->
                        ListItem(
                            headlineContent = { Text(preset.label) },
                            modifier = Modifier.clickable {
                                choosing = false
                                scope.launch {
                                    val profile = preset.build()
                                    harness.profiles.upsert(profile)
                                    onMain { nav.navigate(Routes.model(profile.id)) }
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}

private fun effortOptions(provider: ProviderType) = when (provider) {
    ProviderType.ANTHROPIC -> listOf("low", "medium", "high", "xhigh", "max")
    ProviderType.OPENAI_COMPATIBLE -> listOf("minimal", "low", "medium", "high")
    ProviderType.GEMINI -> listOf("low", "high")
    ProviderType.NOVELAI_TEXT -> emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditScreen(nav: NavController, profileId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val credentials by harness.credentials.observeAll().collectAsStateWithLifecycle(emptyList())
    var draft by remember { mutableStateOf<ModelProfile?>(null) }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFetched by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var extraBodyText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        draft = harness.profiles.get(profileId)
        extraBodyText = draft?.extraBody?.toString().orEmpty()
    }
    val profile = draft ?: return

    fun update(transform: (ModelProfile) -> ModelProfile) {
        draft = transform(profile)
    }

    fun withExtraBody(p: ModelProfile): ModelProfile? {
        if (extraBodyText.isBlank()) return p.copy(extraBody = null)
        return runCatching { p.copy(extraBody = HarnessJson.parseToJsonElement(extraBodyText).jsonObject) }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(profile.name.ifBlank { "Model" }) },
                actions = {
                    TextButton(onClick = {
                        val final = withExtraBody(profile)
                        if (final == null) {
                            status = "Extra body must be a JSON object."
                        } else {
                            scope.launch {
                                harness.profiles.upsert(final)
                                onMain { nav.popBackStack() }
                            }
                        }
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(profile.name, { v -> update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PickerField("Provider", ProviderType.entries, profile.provider, { it.displayName }, { v ->
                v?.let { type -> update { it.copy(provider = type, reasoningEffort = null) } }
            })
            if (profile.provider != ProviderType.NOVELAI_TEXT) {
                OutlinedTextField(
                    profile.baseUrl.orEmpty(),
                    { v -> update { it.copy(baseUrl = v.ifBlank { null }) } },
                    label = { Text("Base URL (blank = provider default)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val matching = credentials.filter { it.kind == ModelPresets.credentialKindFor(profile.provider) }
            PickerField("API key", matching.ifEmpty { credentials }, credentials.firstOrNull { it.id == profile.credentialId }, { it.label }, { c ->
                update { it.copy(credentialId = c?.id) }
            }, noneLabel = "None")
            TextButton(onClick = { nav.navigate(Routes.KEYS) }) { Text("Add an API key…") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    profile.modelId,
                    { v -> update { it.copy(modelId = v.trim()) } },
                    label = { Text("Model id") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    OutlinedButton(enabled = !busy, onClick = {
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
                    }) { Text("Fetch") }
                    DropdownMenu(expanded = showFetched, onDismissRequest = { showFetched = false }) {
                        fetched.forEach { id ->
                            DropdownMenuItem(text = { Text(id) }, onClick = { showFetched = false; update { it.copy(modelId = id) } })
                        }
                    }
                }
            }

            SectionTitle("Limits")
            NumberField("Max output tokens", profile.maxOutputTokens, { v -> v.toIntOrNull()?.let { n -> update { it.copy(maxOutputTokens = n.coerceAtLeast(16)) } } })
            NumberField("Context window (tokens)", profile.contextWindowTokens, { v -> v.toIntOrNull()?.let { n -> update { it.copy(contextWindowTokens = n.coerceAtLeast(1_000)) } } })
            NumberField("Context budget before summarizing (tokens)", profile.contextBudgetTokens, { v ->
                v.toIntOrNull()?.let { n -> update { it.copy(contextBudgetTokens = n.coerceAtLeast(2_000)) } }
            })

            SectionTitle("Generation")
            val efforts = effortOptions(profile.provider)
            if (efforts.isNotEmpty()) {
                PickerField(
                    if (profile.provider == ProviderType.GEMINI) "Thinking level" else "Reasoning effort",
                    efforts, profile.reasoningEffort, { it }, { v -> update { it.copy(reasoningEffort = v) } }, noneLabel = "Provider default",
                )
            }
            if (profile.provider == ProviderType.ANTHROPIC) {
                Text(
                    "Current Claude models don't accept temperature or top_p; use effort instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            SwitchRow("Supports images (vision)", profile.supportsVision, { v -> update { it.copy(supportsVision = v) } })
            if (profile.provider != ProviderType.ANTHROPIC) {
                OutlinedTextField(
                    extraBodyText,
                    { extraBodyText = it },
                    label = { Text("Extra request fields (JSON)") },
                    placeholder = { Text("{\"min_p\": 0.05}") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle("Check")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && profile.modelId.isNotBlank(), onClick = {
                    busy = true
                    status = "Sending a test message…"
                    scope.launch {
                        status = try {
                            val p = withExtraBody(profile) ?: profile
                            val result = harness.providers.forProfile(p.copy(toolMode = ToolMode.NONE)).complete(
                                LlmRequest(p, harness.apiKey(p.credentialId), "Reply in five words or fewer.",
                                    listOf(LlmMessage.User(listOf(LlmPart.Text("Say hello!"))))),
                            )
                            "OK: \"${result.text.trim()}\" (${result.modelId ?: p.modelId})"
                        } catch (e: Exception) {
                            "Failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Test") }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("", Modifier.padding(bottom = 32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Conversations using it fall back to the default model.") },
            confirmButton = { TextButton(onClick = { scope.launch { harness.profiles.delete(profile.id); onMain { nav.popBackStack() } } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}


package dev.aiharness.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.heartbeat.HeartbeatScheduler
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.NumberField
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SectionTitle
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.core.image.NovelAiImageGenerator
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.EmbeddingProviderType
import dev.aiharness.core.model.EmbeddingSettings
import dev.aiharness.core.model.HeartbeatMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by harness.settings.settings.collectAsStateWithLifecycle(null)
    val profiles by harness.profiles.observeAll().collectAsStateWithLifecycle(emptyList())
    val credentials by harness.credentials.observeAll().collectAsStateWithLifecycle(emptyList())
    val s = settings ?: return

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { harness.settings.update(transform) }
    }

    Scaffold(topBar = { dev.aiharness.app.ui.chats.ScreenTitle("Settings", Modifier.statusBarsPadding().padding(horizontal = 18.dp)) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NavRow("Model profiles", "Swappable models across providers") { nav.navigate(Routes.MODELS) }
            NavRow("API keys", "Stored encrypted with the Android Keystore") { nav.navigate(Routes.KEYS) }
            NavRow("Your personas", "Who you are in conversations") { nav.navigate(Routes.USER_PERSONAS) }

            LookSection(s, ::update)

            SectionTitle("Models")
            PickerField("Default model", profiles, profiles.firstOrNull { it.id == s.defaultModelProfileId }, { it.name }, { p ->
                update { it.copy(defaultModelProfileId = p?.id) }
            }, noneLabel = "First available")
            PickerField("Utility model (summaries, memory upkeep)", profiles, profiles.firstOrNull { it.id == s.utilityModelProfileId }, { it.name }, { p ->
                update { it.copy(utilityModelProfileId = p?.id) }
            }, noneLabel = "Same as the conversation")

            SectionTitle("Heartbeat")
            val modes = listOf(HeartbeatMode.PROACTIVE to "Proactive", HeartbeatMode.TIME_AWARENESS to "Time aware", HeartbeatMode.OFF to "Off")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = s.heartbeat.mode == mode,
                        onClick = { update { it.copy(heartbeat = it.heartbeat.copy(mode = mode)) } },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label) }
                }
            }
            Text(
                when (s.heartbeat.mode) {
                    HeartbeatMode.PROACTIVE -> "Personas wake up periodically to reflect, tend their memories, and may message you."
                    HeartbeatMode.TIME_AWARENESS -> "No background activity. Each message tells the persona the time and how long it's been."
                    HeartbeatMode.OFF -> "No background activity and no time information."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.heartbeat.mode == HeartbeatMode.PROACTIVE) {
                val intervals = listOf(15, 30, 60, 120, 240, 480)
                PickerField("Wake every", intervals, s.heartbeat.intervalMinutes, { if (it < 60) "$it minutes" else "${it / 60} hour(s)" }, { v ->
                    v?.let { update { s2 -> s2.copy(heartbeat = s2.heartbeat.copy(intervalMinutes = it)) } }
                })
                val hours = (0..23).toList()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField("Quiet from", hours, s.heartbeat.quietHoursStart, { "%02d:00".format(it) }, { v ->
                        update { it.copy(heartbeat = it.heartbeat.copy(quietHoursStart = v)) }
                    }, Modifier.weight(1f), noneLabel = "No quiet hours")
                    PickerField("Until", hours, s.heartbeat.quietHoursEnd, { "%02d:00".format(it) }, { v ->
                        update { it.copy(heartbeat = it.heartbeat.copy(quietHoursEnd = v)) }
                    }, Modifier.weight(1f), noneLabel = "—")
                }
                NumberField("Only reach out after this many minutes of silence", s.heartbeat.minSilenceMinutes, { v ->
                    v.toIntOrNull()?.let { n -> update { it.copy(heartbeat = it.heartbeat.copy(minSilenceMinutes = n.coerceAtLeast(0))) } }
                })
                NumberField("Max proactive messages per day", s.heartbeat.maxProactivePerDay, { v ->
                    v.toIntOrNull()?.let { n -> update { it.copy(heartbeat = it.heartbeat.copy(maxProactivePerDay = n.coerceAtLeast(0))) } }
                })
                NumberField("Max unanswered messages in a row", s.heartbeat.maxUnansweredProactive, { v ->
                    v.toIntOrNull()?.let { n -> update { it.copy(heartbeat = it.heartbeat.copy(maxUnansweredProactive = n.coerceAtLeast(0))) } }
                })
                SwitchRow("Only on unmetered networks", s.heartbeat.requireUnmeteredNetwork, { v ->
                    update { it.copy(heartbeat = it.heartbeat.copy(requireUnmeteredNetwork = v)) }
                })
                OutlinedButton(onClick = { HeartbeatScheduler.runNow(context, s.heartbeat) }) { Text("Run a heartbeat now") }
            }
            if (s.heartbeat.mode != HeartbeatMode.OFF) {
                NumberField("Mention elapsed time after (minutes)", s.heartbeat.timeGapNoteMinutes, { v ->
                    v.toIntOrNull()?.let { n -> update { it.copy(heartbeat = it.heartbeat.copy(timeGapNoteMinutes = n.coerceAtLeast(0))) } }
                })
            }

            SectionTitle("Memory")
            SwitchRow("Long-term memory", s.memory.enabled, { v -> update { it.copy(memory = it.memory.copy(enabled = v)) } })
            if (s.memory.enabled) {
                SwitchRow(
                    "Memory tools",
                    s.memory.memoryToolsEnabled,
                    { v -> update { it.copy(memory = it.memory.copy(memoryToolsEnabled = v)) } },
                    subtitle = "Personas save, search and edit their own memories.",
                )
                NumberField("Memories recalled per message", s.memory.retrievalTopK, { v ->
                    v.toIntOrNull()?.let { n -> update { it.copy(memory = it.memory.copy(retrievalTopK = n.coerceIn(0, 30))) } }
                })
                NumberField("Fold conversation into an episode every N messages", s.memory.episodeEveryMessages, { v ->
                    v.toIntOrNull()?.let { n -> update { it.copy(memory = it.memory.copy(episodeEveryMessages = n.coerceAtLeast(4))) } }
                })
                val embedding = s.memory.embedding
                PickerField(
                    "Semantic recall (embeddings)",
                    EmbeddingProviderType.entries,
                    embedding?.provider,
                    { if (it == EmbeddingProviderType.GEMINI) "Gemini" else "OpenAI-compatible" },
                    { type ->
                        update {
                            it.copy(
                                memory = it.memory.copy(
                                    embedding = type?.let { t ->
                                        EmbeddingSettings(
                                            provider = t,
                                            modelId = if (t == EmbeddingProviderType.GEMINI) "gemini-embedding-001" else "text-embedding-3-small",
                                        )
                                    },
                                ),
                            )
                        }
                    },
                    noneLabel = "Off (keyword recall)",
                )
                if (embedding != null) {
                    OutlinedTextField(embedding.modelId, { v -> update { it.copy(memory = it.memory.copy(embedding = embedding.copy(modelId = v))) } },
                        label = { Text("Embedding model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (embedding.provider == EmbeddingProviderType.OPENAI_COMPATIBLE) {
                        OutlinedTextField(embedding.baseUrl.orEmpty(), { v -> update { it.copy(memory = it.memory.copy(embedding = embedding.copy(baseUrl = v.ifBlank { null }))) } },
                            label = { Text("Base URL (default https://api.openai.com/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    PickerField("Embedding API key", credentials, credentials.firstOrNull { it.id == embedding.credentialId }, { it.label }, { c ->
                        update { it.copy(memory = it.memory.copy(embedding = embedding.copy(credentialId = c?.id))) }
                    }, noneLabel = "None")
                }
            }

            SectionTitle("Image generation (NovelAI)")
            val img = s.imageGen
            SwitchRow("Image generation", img.enabled, { v -> update { it.copy(imageGen = it.imageGen.copy(enabled = v)) } })
            if (img.enabled) {
                val naiKeys = credentials.filter { it.kind == CredentialKind.NOVELAI }
                PickerField("NovelAI API key", naiKeys, naiKeys.firstOrNull { it.id == img.credentialId }, { it.label }, { c ->
                    update { it.copy(imageGen = it.imageGen.copy(credentialId = c?.id)) }
                }, noneLabel = "Not set (add one under API keys)")
                SwitchRow("Personas can send images", img.agentToolEnabled, { v -> update { it.copy(imageGen = it.imageGen.copy(agentToolEnabled = v)) } },
                    subtitle = "Adds a generate_image tool for models with tool calling.")
                PickerField("Model", NovelAiImageGenerator.MODELS, img.model, { it }, { v -> v?.let { update { s2 -> s2.copy(imageGen = s2.imageGen.copy(model = it)) } } })
                val sizes = listOf(832 to 1216, 1216 to 832, 1024 to 1024, 512 to 768)
                PickerField("Base size", sizes, img.width to img.height, { "${it.first} × ${it.second}" }, { v ->
                    v?.let { (w, h) -> update { it.copy(imageGen = it.imageGen.copy(width = w, height = h)) } }
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Steps", img.steps, { v -> v.toIntOrNull()?.let { n -> update { it.copy(imageGen = it.imageGen.copy(steps = n.coerceIn(1, 50))) } } }, Modifier.weight(1f))
                    NumberField("Guidance", img.scale, { v -> v.toDoubleOrNull()?.let { n -> update { it.copy(imageGen = it.imageGen.copy(scale = n.coerceIn(0.0, 10.0))) } } }, Modifier.weight(1f), decimal = true)
                }
                PickerField("Sampler", NovelAiImageGenerator.SAMPLERS, img.sampler, { it }, { v -> v?.let { update { s2 -> s2.copy(imageGen = s2.imageGen.copy(sampler = it)) } } })
                PickerField("Noise schedule", NovelAiImageGenerator.NOISE_SCHEDULES, img.noiseSchedule, { it }, { v -> v?.let { update { s2 -> s2.copy(imageGen = s2.imageGen.copy(noiseSchedule = it)) } } })
                PickerField("Undesired content preset", NovelAiImageGenerator.UC_PRESETS, img.ucPreset, { it }, { v -> v?.let { update { s2 -> s2.copy(imageGen = s2.imageGen.copy(ucPreset = it)) } } })
                SwitchRow("Add quality tags", img.qualityToggle, { v -> update { it.copy(imageGen = it.imageGen.copy(qualityToggle = v)) } })
                OutlinedTextField(img.negativePrompt, { v -> update { it.copy(imageGen = it.imageGen.copy(negativePrompt = v)) } },
                    label = { Text("Extra negative prompt") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Opus subscribers generate for free at up to 1024×1024 and 28 steps; larger images cost Anlas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Prompting")
            OutlinedTextField(s.baseInstructions, { v -> update { it.copy(baseInstructions = v) } },
                label = { Text("Guidelines added to every persona") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            NumberField("Max tool rounds per reply", s.maxToolRounds, { v -> v.toIntOrNull()?.let { n -> update { it.copy(maxToolRounds = n.coerceIn(1, 12)) } } })
            Text("", Modifier.padding(bottom = 32.dp))
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

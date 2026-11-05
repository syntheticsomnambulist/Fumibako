package dev.aiharness.app.ui.personas

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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.Avatar
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SectionTitle
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.core.model.Persona
import dev.aiharness.core.util.newId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val personas by harness.personas.observeAll().collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("Personas") }) },
        floatingActionButton = { FloatingActionButton(onClick = { nav.navigate(Routes.persona(Routes.NEW)) }) { Icon(Icons.Filled.Add, "New persona") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(personas, key = { it.id }) { persona ->
                ListItem(
                    leadingContent = { Avatar(persona.name, persona.avatarRef, 48.dp) },
                    headlineContent = { Text(persona.name) },
                    supportingContent = { Text(persona.description, maxLines = 2, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { nav.navigate(Routes.memory(persona.id)) }) { Icon(Icons.Filled.Psychology, "Memories") }
                            IconButton(onClick = {
                                scope.launch {
                                    val existing = harness.conversations.getAll().filter { it.personaId == persona.id && !it.archived }.maxByOrNull { it.updatedAt }
                                    val conversation = existing ?: harness.createConversation(persona.id)
                                    onMain { nav.navigate(Routes.chat(conversation.id)) }
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.Chat, "Chat") }
                        }
                    },
                    modifier = Modifier.clickable { nav.navigate(Routes.persona(persona.id)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditScreen(nav: NavController, personaId: String) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val scope = rememberCoroutineScope()
    val profiles by harness.profiles.observeAll().collectAsStateWithLifecycle(emptyList())
    val settings by harness.settings.settings.collectAsStateWithLifecycle(null)
    var draft by remember { mutableStateOf<Persona?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var avatarBusy by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(personaId) {
        val now = System.currentTimeMillis()
        draft = if (personaId == Routes.NEW) Persona(id = newId(), name = "", createdAt = now, updatedAt = now) else harness.personas.get(personaId)
    }
    val persona = draft ?: return

    fun update(transform: (Persona) -> Persona) {
        draft = transform(persona)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (personaId == Routes.NEW) "New persona" else persona.name.ifBlank { "Persona" }) },
                actions = {
                    TextButton(
                        enabled = persona.name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                harness.personas.upsert(persona.copy(updatedAt = System.currentTimeMillis()))
                                onMain { nav.popBackStack() }
                            }
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(persona.name, persona.avatarRef, 72.dp)
                Column {
                    OutlinedButton(
                        enabled = !avatarBusy && settings?.imageGen?.credentialId != null && persona.appearanceTags.isNotBlank(),
                        onClick = {
                            avatarBusy = true
                            avatarError = null
                            scope.launch {
                                try {
                                    val image = harness.images.generate(
                                        settings = harness.settings.current().imageGen.copy(width = 1024, height = 1024),
                                        prompt = "portrait, upper body, looking at viewer, simple background",
                                        persona = persona,
                                    )
                                    draft = (draft ?: persona).copy(avatarRef = image.ref)
                                } catch (e: Exception) {
                                    avatarError = e.message
                                } finally {
                                    avatarBusy = false
                                }
                            }
                        },
                    ) { Text(if (avatarBusy) "Generating…" else "Generate avatar") }
                    Text(
                        avatarError ?: "Uses the appearance tags below and your NovelAI key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (avatarError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(persona.name, { v -> update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(persona.description, { v -> update { it.copy(description = v) } }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(persona.personality, { v -> update { it.copy(personality = v) } }, label = { Text("Personality") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(persona.scenario, { v -> update { it.copy(scenario = v) } }, label = { Text("Scenario (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(persona.greeting, { v -> update { it.copy(greeting = v) } }, label = { Text("First message (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(persona.exampleDialogue, { v -> update { it.copy(exampleDialogue = v) } }, label = { Text("Example dialogue (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                persona.appearanceTags,
                { v -> update { it.copy(appearanceTags = v) } },
                label = { Text("Appearance tags (for images)") },
                placeholder = { Text("1girl, short black hair, glasses, hoodie") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Behaviour")
            PickerField(
                label = "Default model",
                options = profiles,
                selected = profiles.firstOrNull { it.id == persona.defaultModelProfileId },
                optionLabel = { it.name },
                onSelect = { p -> update { it.copy(defaultModelProfileId = p?.id) } },
                noneLabel = "App default",
            )
            SwitchRow(
                "Heartbeat",
                persona.heartbeatEnabled,
                { v -> update { it.copy(heartbeatEnabled = v) } },
                subtitle = "Lets ${persona.name.ifBlank { "this persona" }} reflect and reach out while you're away (see Settings).",
            )
            OutlinedTextField(
                persona.systemPromptOverride.orEmpty(),
                { v -> update { it.copy(systemPromptOverride = v.ifBlank { null }) } },
                label = { Text("Custom system prompt (advanced)") },
                supportingText = { Text("Replaces the generated character section. {{char}} and {{user}} are filled in. Memory and context notes are still added.") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (personaId != Routes.NEW) {
                SectionTitle("Danger zone")
                Button(onClick = { confirmDelete = true }) { Text("Delete persona") }
            }
            Text("", Modifier.padding(bottom = 32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${persona.name}?") },
            text = { Text("This also deletes their conversations and memories.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        harness.conversations.getAll().filter { it.personaId == persona.id }.forEach { harness.conversations.delete(it.id) }
                        harness.memory.all(persona.id).forEach { harness.memory.delete(it.id) }
                        harness.personas.delete(persona.id)
                        onMain { nav.popBackStack() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

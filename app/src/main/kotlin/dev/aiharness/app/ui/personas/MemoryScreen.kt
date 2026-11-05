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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.relativeTime
import dev.aiharness.core.memory.TextTokens
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import kotlinx.coroutines.launch

/** Browse, search and hand-edit a persona's long-term memory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(nav: NavController, personaId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val memories by harness.memories.observeForPersona(personaId).collectAsStateWithLifecycle(emptyList())
    val personas by harness.personas.observeAll().collectAsStateWithLifecycle(emptyList())
    val persona = personas.firstOrNull { it.id == personaId }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    val shown = remember(memories, query) {
        val terms = TextTokens.tokenize(query).toSet()
        val filtered = if (terms.isEmpty()) memories else memories.filter { m -> TextTokens.tokenize(m.content + " " + m.tags.joinToString(" ")).any { it in terms } }
        filtered.sortedWith(compareByDescending<MemoryEntry> { it.isCore }.thenByDescending { it.updatedAt })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("${persona?.name ?: "Persona"}'s memories") },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add memory") } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search ${memories.size} memories") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { memory ->
                    ListItem(
                        leadingContent = { if (memory.isCore) Icon(Icons.Filled.PushPin, "Core memory", tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(memory.content) },
                        supportingContent = {
                            Text(
                                "${memory.kind.name.lowercase()} · importance ${memory.importance} · ${relativeTime(memory.updatedAt)}" +
                                    if (memory.tags.isNotEmpty()) " · ${memory.tags.joinToString()}" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickable { editing = memory },
                    )
                }
            }
        }
    }

    if (adding) {
        MemoryEditor(
            initial = null,
            onDismiss = { adding = false },
            onSave = { content, importance, pinned, kind ->
                adding = false
                scope.launch { harness.memory.save(personaId, content, kind = kind, importance = importance, pinned = pinned) }
            },
        )
    }
    editing?.let { memory ->
        MemoryEditor(
            initial = memory,
            onDismiss = { editing = null },
            onSave = { content, importance, pinned, kind ->
                editing = null
                scope.launch {
                    harness.memory.update(memory.id, content = content, importance = importance, pinned = pinned)
                    harness.memory.get(memory.id)?.let { harness.memories.upsert(it.copy(kind = kind)) }
                }
            },
            onDelete = {
                editing = null
                scope.launch { harness.memory.delete(memory.id) }
            },
        )
    }
}

@Composable
private fun MemoryEditor(
    initial: MemoryEntry?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean, MemoryKind) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var importance by remember { mutableStateOf((initial?.importance ?: 5).toFloat()) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }
    var kind by remember { mutableStateOf(initial?.kind ?: MemoryKind.FACT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New memory" else "Edit memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(content, { content = it }, label = { Text("Memory") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Importance: ${importance.toInt()}", style = MaterialTheme.typography.bodyMedium)
                Slider(value = importance, onValueChange = { importance = it }, valueRange = 1f..10f, steps = 8)
                PickerField("Kind", MemoryKind.entries, kind, { it.name.lowercase() }, { kind = it ?: MemoryKind.FACT })
                SwitchRow("Core memory", pinned, { pinned = it }, subtitle = "Always included in the persona's prompt.")
                if (onDelete != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = content.isNotBlank(), onClick = { onSave(content, importance.toInt(), pinned, kind) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package dev.aiharness.app.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.Avatar
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.relativeTime
import dev.aiharness.core.model.Conversation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val conversations by harness.conversations.observeAll().collectAsStateWithLifecycle(emptyList())
    val personas by harness.personas.observeAll().collectAsStateWithLifecycle(emptyList())
    val profiles by harness.profiles.observeAll().collectAsStateWithLifecycle(null)
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chats") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, "New chat") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (profiles?.isEmpty() == true) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp).clickable { nav.navigate(Routes.MODELS) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Set up a model", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add an API key and a model profile (Claude, Gemini, OpenAI-compatible or NovelAI) to start chatting. Tap here.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (conversations.isEmpty()) {
                item {
                    Text(
                        "No conversations yet. Tap + to start one with a persona.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(conversations.filter { !it.archived }, key = { it.id }) { conversation ->
                val persona = personas.firstOrNull { it.id == conversation.personaId }
                ListItem(
                    leadingContent = { Avatar(persona?.name ?: "?", persona?.avatarRef) },
                    headlineContent = { Text(persona?.name ?: conversation.title) },
                    supportingContent = { Text("Updated ${relativeTime(conversation.updatedAt)}") },
                    trailingContent = {
                        IconButton(onClick = { deleting = conversation }) { Icon(Icons.Filled.Delete, "Delete") }
                    },
                    modifier = Modifier.clickable { nav.navigate(Routes.chat(conversation.id)) },
                )
            }
        }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Chat with…") },
            text = {
                LazyColumn {
                    items(personas, key = { it.id }) { persona ->
                        ListItem(
                            leadingContent = { Avatar(persona.name, persona.avatarRef) },
                            headlineContent = { Text(persona.name) },
                            supportingContent = { Text(persona.description, maxLines = 2) },
                            modifier = Modifier.clickable {
                                picking = false
                                scope.launch {
                                    val conversation = harness.createConversation(persona.id)
                                    onMain { nav.navigate(Routes.chat(conversation.id)) }
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false; nav.navigate(Routes.persona(Routes.NEW)) }) { Text("New persona") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete conversation?") },
            text = { Text("Messages are deleted. The persona keeps the memories it formed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { harness.conversations.delete(conversation.id) }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

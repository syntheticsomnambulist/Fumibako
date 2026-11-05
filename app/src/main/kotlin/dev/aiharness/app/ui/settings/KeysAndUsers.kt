package dev.aiharness.app.ui.settings

import dev.aiharness.app.ui.paper.TapePicker
import dev.aiharness.app.ui.paper.InkPicker
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.PickerField
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.util.newId
import kotlinx.coroutines.launch

private fun CredentialKind.label() = when (this) {
    CredentialKind.ANTHROPIC -> "Anthropic"
    CredentialKind.OPENAI_COMPATIBLE -> "OpenAI-compatible"
    CredentialKind.GEMINI -> "Google Gemini"
    CredentialKind.NOVELAI -> "NovelAI"
    CredentialKind.OTHER -> "Other"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val credentials by harness.credentials.observeAll().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Credential?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("API keys") },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add key") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Keys are encrypted on this device with the Android Keystore and only sent to the provider they belong to. " +
                        "For NovelAI, use a persistent API token from your account settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(credentials, key = { it.id }) { credential ->
                ListItem(
                    headlineContent = { Text(credential.label) },
                    supportingContent = { Text(credential.kind.label()) },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch {
                                harness.secrets.remove(SecretStore.credentialKey(credential.id))
                                harness.credentials.delete(credential.id)
                            }
                        }) { Icon(Icons.Filled.Delete, "Delete") }
                    },
                    modifier = Modifier.clickable { editing = credential },
                )
            }
        }
    }

    if (adding || editing != null) {
        val existing = editing
        var label by remember(existing) { mutableStateOf(existing?.label.orEmpty()) }
        var kind by remember(existing) { mutableStateOf(existing?.kind ?: CredentialKind.ANTHROPIC) }
        var secret by remember(existing) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (existing == null) "Add API key" else "Edit ${existing.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField("Provider", CredentialKind.entries, kind, { it.label() }, { v -> v?.let { kind = it } })
                    OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        secret,
                        { secret = it },
                        label = { Text(if (existing == null) "Key" else "New key (blank keeps the current one)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (existing != null || secret.isNotBlank()),
                    onClick = {
                        val credential = Credential(id = existing?.id ?: newId(), label = label.ifBlank { kind.label() }, kind = kind)
                        scope.launch {
                            harness.credentials.upsert(credential)
                            if (secret.isNotBlank()) harness.secrets.put(SecretStore.credentialKey(credential.id), secret.trim())
                        }
                        adding = false
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPersonasScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val users by harness.userPersonas.observeAll().collectAsStateWithLifecycle(emptyList())
    val settings by harness.settings.settings.collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf<UserPersona?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Your personas") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val now = System.currentTimeMillis()
                editing = UserPersona(id = newId(), name = "", createdAt = now, updatedAt = now)
            }) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Tap the check to make a persona the default for new chats. You can also switch inside a chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(users, key = { it.id }) { user ->
                val active = settings?.activeUserPersonaId == user.id
                ListItem(
                    headlineContent = { Text(user.name) },
                    supportingContent = { Text(user.description.ifBlank { "No description" }, maxLines = 2) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { harness.settings.update { it.copy(activeUserPersonaId = user.id) } } }) {
                            Icon(Icons.Filled.CheckCircle, "Use by default", tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        }
                    },
                    modifier = Modifier.clickable { editing = user },
                )
            }
        }
    }

    editing?.let { user ->
        var name by remember(user.id) { mutableStateOf(user.name) }
        var description by remember(user.id) { mutableStateOf(user.description) }
        var ink by remember(user.id) { mutableStateOf(user.ink) }
        var tape by remember(user.id) { mutableStateOf(user.tapePattern) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (user.name.isBlank()) "New persona" else "Edit ${user.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        description,
                        { description = it },
                        label = { Text("About you (shared with the persona)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Your ink", style = MaterialTheme.typography.labelMedium)
                    InkPicker(ink, { ink = it })
                    Text("Your tape", style = MaterialTheme.typography.labelMedium)
                    TapePicker(ink, tape, { tape = it })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { harness.userPersonas.upsert(user.copy(name = name.trim(), description = description, ink = ink, tapePattern = tape, updatedAt = System.currentTimeMillis())) }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Column {
                    if (users.any { it.id == user.id } && users.size > 1) {
                        TextButton(onClick = { scope.launch { harness.userPersonas.delete(user.id) }; editing = null }) { Text("Delete") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

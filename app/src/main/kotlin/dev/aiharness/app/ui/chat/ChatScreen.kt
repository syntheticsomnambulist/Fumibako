package dev.aiharness.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.engine.GenerationManager
import dev.aiharness.app.ui.Avatar
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.formatChatText
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(nav: NavController, conversationId: String) {
    val container = LocalAppContainer.current
    val vm: ChatViewModel = viewModel(key = conversationId) { ChatViewModel(container, conversationId) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val header by vm.header.collectAsStateWithLifecycle()
    val generation by vm.generation.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val users by vm.userPersonas.collectAsStateWithLifecycle()
    val imageReady by vm.imageGenReady.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var imageDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val personaName = header.persona?.name ?: "…"
    LaunchedEffect(messages.size, generation.streamingText.length > 0) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(personaName, header.persona?.avatarRef, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(personaName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                listOfNotNull(header.profile?.name ?: "No model", header.userPersona?.let { "as ${it.name}" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = "model" }) { Icon(Icons.Filled.SmartToy, "Model") }
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
                        IconButton(onClick = { menu = "user" }) { Icon(Icons.Filled.Person, "You are") }
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
                    if (imageReady) IconButton(onClick = { imageDialog = true }) { Icon(Icons.Filled.Image, "Generate image") }
                    Box {
                        IconButton(onClick = { menu = "more" }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu == "more", onDismissRequest = { menu = null }) {
                            DropdownMenuItem(text = { Text("Regenerate last reply") }, onClick = { menu = null; vm.regenerate() })
                            header.persona?.let { persona ->
                                DropdownMenuItem(text = { Text("${persona.name}'s memories") }, onClick = { menu = null; nav.navigate(Routes.memory(persona.id)) })
                            }
                            DropdownMenuItem(text = { Text("Refresh context") }, onClick = { menu = null; vm.refreshContext() })
                            DropdownMenuItem(text = { Text("Delete conversation") }, onClick = { menu = null; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                if (generation.active) {
                    item(key = "streaming") {
                        StreamingBubble(generation.streamingText, generation.reasoning, GenerationManager.describeTool(generation.activeTool))
                    }
                }
                items(messages.asReversed(), key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        personaName = personaName,
                        onLongPress = { selected = message },
                        onImage = { viewing = it },
                    )
                }
            }

            generation.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.dismissError(); vm.regenerate() }) { Text("Retry") }
                        TextButton(onClick = { vm.dismissError() }) { Text("Dismiss") }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message $personaName") },
                    modifier = Modifier.weight(1f),
                    maxLines = 6,
                )
                Spacer(Modifier.width(6.dp))
                if (generation.active) {
                    IconButton(onClick = { vm.stop() }) { Icon(Icons.Filled.Stop, "Stop") }
                } else {
                    IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    }

    selected?.let { message -> MessageActions(message, vm, onDismiss = { selected = null }) }
    viewing?.let { ref ->
        Dialog(onDismissRequest = { viewing = null }) {
            BlobImage(ref, Modifier.fillMaxWidth().clickable { viewing = null }, contentScale = ContentScale.Fit, maxSize = 2048)
        }
    }
    if (imageDialog) {
        ImageDialog(personaName, onDismiss = { imageDialog = false }) { prompt, includePersona, orientation ->
            imageDialog = false
            vm.generateImage(prompt, includePersona, orientation)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this conversation?") },
            text = { Text("Memories $personaName formed are kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteConversation { nav.popBackStack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(message: ChatMessage, personaName: String, onLongPress: () -> Unit, onImage: (String) -> Unit) {
    when (message.role) {
        MessageRole.TOOL -> {
            Column(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.toolResults.forEach { result ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (result.isError) "${result.name} failed" else GenerationManager.describeToolDone(result.name),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
                message.images.forEach { image -> ImageCard(image, onImage) }
            }
        }
        MessageRole.USER, MessageRole.ASSISTANT -> {
            val mine = message.role == MessageRole.USER
            val text = message.text
            if (text.isBlank() && message.images.isEmpty() && message.errorMessage == null) return
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!mine && message.origin == MessageOrigin.HEARTBEAT) {
                    Text("$personaName reached out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (!mine) message.reasoning?.let { ReasoningToggle(it) }
                if (text.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = onLongPress),
                    ) {
                        Text(
                            formatChatText(text, MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                message.images.forEach { image -> ImageCard(image, onImage) }
                message.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ImageCard(image: ContentPart.Image, onImage: (String) -> Unit) {
    BlobImage(
        image.ref,
        Modifier.padding(top = 4.dp).widthIn(max = 260.dp).height(360.dp).clickable { onImage(image.ref) },
        contentScale = ContentScale.Crop,
        maxSize = 900,
    )
}

@Composable
private fun ReasoningToggle(reasoning: String) {
    var open by remember { mutableStateOf(false) }
    Text(
        if (open) "Hide thoughts" else "Show thoughts",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { open = !open }.padding(vertical = 2.dp),
    )
    if (open) {
        Text(
            reasoning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun StreamingBubble(text: String, reasoning: String, status: String) {
    Column(Modifier.fillMaxWidth()) {
        if (text.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 320.dp)) {
                Text(
                    formatChatText(text, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (reasoning.isNotEmpty()) "$status… ${reasoning.takeLast(80).replace('\n', ' ')}" else "$status…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageActions(message: ChatMessage, vm: ChatViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(message.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit message" else "Message") },
        text = {
            if (editing) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            } else {
                Column {
                    TextButton(onClick = {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", message.text))) }
                        onDismiss()
                    }) { Text("Copy") }
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = { vm.deleteMessage(message); onDismiss() }) { Text("Delete") }
                    Text(
                        "Editing or deleting earlier messages starts a fresh context for the model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (editing) TextButton(onClick = { vm.editMessage(message, text); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageDialog(personaName: String, onDismiss: () -> Unit, onGenerate: (String, Boolean, String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var includePersona by remember { mutableStateOf(true) }
    var orientation by remember { mutableStateOf("portrait") }
    val options = listOf("portrait", "landscape", "square")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate an image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Tags / description") },
                    placeholder = { Text("smiling, sitting in a cafe, rainy window") },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow("Include $personaName's appearance", includePersona, { includePersona = it })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = orientation == option,
                            onClick = { orientation = option },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(option.replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onGenerate(prompt, includePersona, orientation) }, enabled = prompt.isNotBlank()) { Text("Generate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

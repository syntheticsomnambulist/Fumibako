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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.PickerField
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.personas.PageBar
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.image.ImageProfiles
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.util.newId
import kotlinx.coroutines.launch

internal fun CredentialKind.label() = when (this) {
    CredentialKind.ANTHROPIC -> "Anthropic"
    CredentialKind.OPENAI_COMPATIBLE -> "OpenAI-compatible"
    CredentialKind.GEMINI -> "Google Gemini"
    CredentialKind.NOVELAI -> "NovelAI"
    CredentialKind.OTHER -> "Other"
}

private val TagShape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 12.dp, bottomEnd = 12.dp)

/** A key as a luggage tag: the punched hole, its label, and what it's for. */
@Composable
internal fun KeyTag(key: Credential, onClick: (() -> Unit)? = null) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.nijimi1, TagShape)
            .background(colors.kami, TagShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Edit ${key.label}", onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(9.dp).background(colors.hyoshi, CircleShape).border(1.dp, colors.nijimi2, CircleShape))
        Text(key.label, style = PaperType.entryTitle.copy(fontSize = 17.sp), color = colors.sumi, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(key.kind.label().uppercase(), style = PaperType.meta, color = colors.kasure2)
    }
}

/** The API keys, as tags; tap one to change or remove it. */
@Composable
fun KeysScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val credentials by remember { harness.credentials.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Credential?>(null) }
    var adding by remember { mutableStateOf(false) }
    val tape = Tape(stationery.you, stationery.youPattern)
    val layouts = tapeLayouts("keys", 2)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("bar") { PageBar(onBack = { nav.popBackStack() }) }
        item("title") {
            Column {
                Text("Keys", style = PaperType.screenTitle, color = paper.sumi)
                Text("KEPT ENCRYPTED ON THIS PHONE", style = PaperType.meta, color = paper.kasure2)
            }
        }
        item("keys") {
            TapedCard(layouts[0], tape, tilt = cardTilt("keys")) {
                CardTitle("Your keys")
                if (credentials.isEmpty()) Hint("No keys yet. Add one to start writing.")
                credentials.forEach { key -> KeyTag(key) { editing = key } }
                DashedButton("+ Add a key") { adding = true }
            }
        }
        item("about") {
            TapedCard(layouts[1], tape, tilt = cardTilt("keys-about")) {
                Hint("Keys are encrypted with the Android Keystore and only sent to the service they belong to.")
                Hint("For NovelAI, use a persistent API token from your account settings.")
            }
        }
    }

    if (adding || editing != null) {
        val existing = editing
        var label by remember(existing) { mutableStateOf(existing?.label.orEmpty()) }
        var kind by remember(existing) { mutableStateOf(existing?.kind ?: CredentialKind.ANTHROPIC) }
        var secret by remember(existing) { mutableStateOf("") }
        var confirmRemove by remember(existing) { mutableStateOf(false) }
        fun close() {
            adding = false
            editing = null
        }
        AlertDialog(
            onDismissRequest = { close() },
            title = { Text(if (existing == null) "Add a key" else existing.label, style = PaperType.entryTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField("For", CredentialKind.entries, kind, { it.label() }, { v -> v?.let { kind = it } })
                    IndexCardField("Label", label, { label = it }, singleLine = true, style = PaperType.noteBody, placeholder = kind.label())
                    IndexCardField(
                        if (existing == null) "Key" else "New key (blank keeps the current one)",
                        secret,
                        { secret = it },
                        singleLine = true,
                        style = PaperType.noteBody,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (existing != null) {
                        if (confirmRemove) {
                            InkLink("Tap again to remove ${existing.label}", stationery.ink("beni")) {
                                scope.launch {
                                    harness.secrets.remove(SecretStore.credentialKey(existing.id))
                                    harness.credentials.delete(existing.id)
                                }
                                close()
                            }
                        } else {
                            InkLink("Remove this key", stationery.ink("beni")) { confirmRemove = true }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = existing != null || secret.isNotBlank(),
                    onClick = {
                        val credential = Credential(id = existing?.id ?: newId(), label = label.ifBlank { kind.label() }, kind = kind)
                        scope.launch {
                            harness.credentials.upsert(credential)
                            if (secret.isNotBlank()) harness.secrets.put(SecretStore.credentialKey(credential.id), secret.trim())
                            // A key that draws pictures turns pictures on with it.
                            if (secret.isNotBlank()) {
                                harness.settings.update { s -> ImageProfiles.turnOnWith(s.imageGen, credential)?.let { s.copy(imageGen = it) } ?: s }
                            }
                        }
                        close()
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { close() }) { Text("Cancel") } },
        )
    }
}

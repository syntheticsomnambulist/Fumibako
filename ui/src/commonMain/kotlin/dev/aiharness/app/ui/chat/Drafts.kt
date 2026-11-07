package dev.aiharness.app.ui.chat

import dev.aiharness.core.model.ContentPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Your unsent notes, one to a conversation: the words in the box and what's waiting to go with
 * them. The app keeps them, not the letter's screen, so leaving the letters doesn't lose them and
 * what's being made ready (a video shrinking) carries on; each is written to a small file, so it
 * outlasts the app being closed too. Sending a note clears its draft.
 */
class Drafts(private val dir: File, private val scope: CoroutineScope) {
    /** One conversation's draft. */
    class Draft internal constructor(val conversationId: String, stored: Stored) {
        /** The words in the box. */
        val text = MutableStateFlow(stored.text)

        /** What's waiting to go with them. */
        val going = MutableStateFlow(stored.waiting.mapNotNull { it.going() })

        /** Tickets made from a link's cutting, by the ticket's id: switching back gives the cutting as it was. */
        val cuttingsOf = ConcurrentHashMap(stored.cuttings)

        /** Links whose cutting you took out: they stay in the text without coming back as cuttings. */
        val dismissedLinks: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { addAll(stored.dismissed) }

        /** The note on its way (waiting a moment for a link's cutting), so it isn't sent twice. */
        @Volatile var sending: Job? = null
    }

    /** What a draft keeps on disk: what's still being made ready isn't kept (it starts again if picked again). */
    @Serializable
    class Stored(
        val text: String = "",
        val waiting: List<Waiting> = emptyList(),
        val cuttings: Map<String, ContentPart.Attachment> = emptyMap(),
        val dismissed: List<String> = emptyList(),
    )

    /** One thing waiting: a photo or an attachment. */
    @Serializable
    class Waiting(val image: ContentPart.Image? = null, val attachment: ContentPart.Attachment? = null) {
        fun going(): Going? = image?.let { Going.Picture(it) } ?: attachment?.let { Going.Item(it) }
    }

    private val open = ConcurrentHashMap<String, Draft>()

    /** [conversationId]'s draft, read from its file the first time it's asked for. */
    fun of(conversationId: String): Draft = open.computeIfAbsent(conversationId) { id ->
        Draft(id, read(id)).also(::keep)
    }

    /** Forgets [conversationId]'s draft (the conversation is gone). */
    fun discard(conversationId: String) {
        open.remove(conversationId)?.let { d ->
            d.text.value = ""
            d.going.value = emptyList()
        }
        scope.launch(Dispatchers.IO) { file(conversationId).delete() }
    }

    /** Writes the draft down a moment after it changes. */
    @OptIn(FlowPreview::class)
    private fun keep(draft: Draft) {
        scope.launch {
            // From what it first sees (it may already have changed by the time this starts): writing a
            // draft just read back is harmless, and missing a change isn't.
            combine(draft.text, draft.going) { text, going -> text to going }
                .debounce(WRITE_AFTER_MS)
                .collect { (text, going) -> write(draft, text, going) }
        }
    }

    private suspend fun write(draft: Draft, text: String, going: List<Going>) = withContext(Dispatchers.IO) {
        val waiting = going.mapNotNull {
            when (it) {
                is Going.Picture -> Waiting(image = it.image)
                is Going.Item -> Waiting(attachment = it.attachment)
                is Going.Preparing -> null
            }
        }
        val file = file(draft.conversationId)
        runCatching {
            if (text.isBlank() && waiting.isEmpty()) {
                file.delete()
            } else {
                dir.mkdirs()
                val kept = waiting.mapNotNull { it.attachment?.id }.toSet()
                val stored = Stored(text, waiting, draft.cuttingsOf.filterKeys { it in kept }, draft.dismissedLinks.toList())
                val temp = File(dir, file.name + ".new")
                temp.writeText(JSON.encodeToString(Stored.serializer(), stored))
                temp.renameTo(file)
            }
        }
    }

    private fun read(conversationId: String): Stored =
        runCatching { JSON.decodeFromString(Stored.serializer(), file(conversationId).readText()) }.getOrDefault(Stored())

    private fun file(conversationId: String) = File(dir, conversationId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    companion object {
        private const val WRITE_AFTER_MS = 400L
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

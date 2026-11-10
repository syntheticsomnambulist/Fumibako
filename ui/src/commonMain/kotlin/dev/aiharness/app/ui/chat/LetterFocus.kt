package dev.aiharness.app.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A request to open a thread at one of its letters: from a P.S. ("Looked back at: ..."), the index
 * card's letters it looked up, or (step 3) a search. The thread scrolls there and clears it.
 */
class LetterFocus {
    /** Open [conversationId] at [messageId]; with [find], the strip for stepping through a search's matches. */
    data class Request(val conversationId: String, val messageId: String, val find: Find? = null)

    /** A search a thread was opened from: its words, and its matches in that thread (message ids, newest first). */
    data class Find(val query: String, val matches: List<String>)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request

    fun open(conversationId: String, messageId: String, find: Find? = null) {
        _request.value = Request(conversationId, messageId, find)
    }

    /** The thread is there: the request is done (unless a newer one came meanwhile). */
    fun done(request: Request) {
        _request.compareAndSet(request, null)
    }
}

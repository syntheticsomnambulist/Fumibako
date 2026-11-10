package dev.aiharness.app.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A request to open a thread at one of its letters: from a P.S. ("Looked back at: ..."), the index
 * card's letters it looked up, or (step 3) a search. The thread scrolls there and clears it.
 */
class LetterFocus {
    data class Request(val conversationId: String, val messageId: String)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request

    fun open(conversationId: String, messageId: String) {
        _request.value = Request(conversationId, messageId)
    }

    /** The thread is there: the request is done (unless a newer one came meanwhile). */
    fun done(request: Request) {
        _request.compareAndSet(request, null)
    }
}

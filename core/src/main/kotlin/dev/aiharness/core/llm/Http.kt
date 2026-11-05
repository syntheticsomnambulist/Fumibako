package dev.aiharness.core.llm

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.aiharness.core.util.HarnessJson
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

object HarnessHttp {
    /** Shared client: long read timeout because model streams can pause while the model thinks. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    val JSON = "application/json; charset=utf-8".toMediaType()
}

fun JsonElement.toRequestBody(): RequestBody = toString().toRequestBody(HarnessHttp.JSON)

/** One server-sent event. */
data class SseEvent(val event: String?, val data: String)

/**
 * Runs [block] while making sure [call] is cancelled if the coroutine is cancelled, so blocking
 * reads on the response body are interrupted promptly.
 */
suspend fun <T> withCancellableCall(call: Call, block: suspend () -> T): T = coroutineScope {
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
    try {
        block()
    } finally {
        watcher.cancel()
    }
}

/** Executes [request] and streams its SSE events. Throws [LlmException] on HTTP errors. */
fun OkHttpClient.sseFlow(request: Request, providerName: String): Flow<SseEvent> = flow {
    val call = newCall(request)
    withCancellableCall(call) {
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw LlmException("$providerName: network error: ${e.message}", retryable = true, cause = e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) throw resp.toLlmException(providerName)
            val source = resp.body?.source() ?: throw LlmException("$providerName: empty response body")
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val event = readSseEvent(source) ?: break
                    emit(event)
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw LlmException("$providerName: stream interrupted: ${e.message}", retryable = true, cause = e)
            }
        }
    }
}.flowOn(Dispatchers.IO)

/**
 * Reads the next server-sent event from [source], or returns null at EOF. Implements the subset of
 * the SSE spec that model APIs use: `event:`, multi-line `data:`, and `:` comments.
 */
fun readSseEvent(source: BufferedSource): SseEvent? {
    var eventName: String? = null
    val data = StringBuilder()
    var hasData = false
    while (true) {
        val line = source.readUtf8Line() ?: return if (hasData) SseEvent(eventName, data.toString()) else null
        if (line.isEmpty()) {
            if (hasData) return SseEvent(eventName, data.toString())
            eventName = null
            continue
        }
        if (line.startsWith(":")) continue
        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        var value = if (colon == -1) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> eventName = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
    }
}

/** Executes a non-streaming request and returns the body string. */
suspend fun OkHttpClient.executeForString(request: Request, providerName: String): String =
    withContext(Dispatchers.IO) {
        val call = newCall(request)
        withCancellableCall(call) {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw resp.toLlmException(providerName)
                    resp.body?.string() ?: ""
                }
            } catch (e: IOException) {
                throw LlmException("$providerName: network error: ${e.message}", retryable = true, cause = e)
            }
        }
    }

/** Executes a request and returns the raw response bytes plus content type. */
suspend fun OkHttpClient.executeForBytes(request: Request, providerName: String): Pair<ByteArray, String?> =
    withContext(Dispatchers.IO) {
        val call = newCall(request)
        withCancellableCall(call) {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw resp.toLlmException(providerName)
                    (resp.body?.bytes() ?: ByteArray(0)) to resp.header("Content-Type")
                }
            } catch (e: IOException) {
                throw LlmException("$providerName: network error: ${e.message}", retryable = true, cause = e)
            }
        }
    }

fun Response.toLlmException(providerName: String): LlmException {
    val raw = try {
        body?.string().orEmpty()
    } catch (_: IOException) {
        ""
    }
    val detail = extractErrorMessage(raw) ?: raw.take(500).ifBlank { message }
    val retryable = code == 408 || code == 409 || code == 429 || code >= 500
    return LlmException("$providerName error $code: $detail", statusCode = code, retryable = retryable)
}

private fun extractErrorMessage(raw: String): String? = try {
    val obj = HarnessJson.parseToJsonElement(raw).jsonObject
    val err = obj["error"]
    when {
        err is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
        err != null -> err.jsonPrimitive.contentOrNull
        else -> obj["message"]?.jsonPrimitive?.contentOrNull
    }
} catch (_: Exception) {
    null
}

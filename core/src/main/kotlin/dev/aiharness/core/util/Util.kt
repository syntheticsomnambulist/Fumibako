package dev.aiharness.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Shared JSON configuration for persistence and provider payloads. */
val HarnessJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

fun newId(): String = UUID.randomUUID().toString()

/** Time source, injectable so heartbeat and recency logic can be tested. */
interface Clock {
    fun nowMillis(): Long
    val zone: ZoneId
    fun now(): Instant = Instant.ofEpochMilli(nowMillis())
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}

fun sha256Hex(vararg parts: String?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        digest.update((part ?: "\u0000").toByteArray(Charsets.UTF_8))
        digest.update(0x1F)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
}

/** Converts a kotlinx JSON tree into plain Kotlin maps/lists/primitives. */
fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
}

/** Formats a duration in a compact human readable way ("3 hours", "2 days"). */
fun humanizeDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "less than a minute"
        minutes < 60 -> plural(minutes, "minute")
        minutes < 60 * 24 -> plural(minutes / 60, "hour")
        minutes < 60 * 24 * 14 -> plural(minutes / (60 * 24), "day")
        minutes < 60 * 24 * 60 -> plural(minutes / (60 * 24 * 7), "week")
        else -> plural(minutes / (60 * 24 * 30), "month")
    }
}

private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"

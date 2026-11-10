package dev.aiharness.core.agent

import dev.aiharness.core.letters.WayLines
import dev.aiharness.core.tools.RewriteWayLinesTool

/**
 * Now and then, in a quiet moment that's happening anyway, shows the persona its lines for the
 * postmark and lets it rewrite its own: at most weekly, so they stay recognizable.
 */
class WayLinesActivity : HeartbeatActivity {
    override val name = "postmark_lines"
    override val optional = true

    override suspend fun invite(beat: Beat): String? {
        val persona = beat.persona
        if (beat.h.tools.available(beat.setup.settings, persona).none { it.spec.name == RewriteWayLinesTool.NAME }) return null
        if ((beat.lastInvited(name) ?: 0L) > beat.now - WayLines.WEEK) return null
        if ((persona.wayLinesWrittenAt ?: 0L) > beat.now - WayLines.WEEK) return null
        val lines = if (persona.wayLines.isEmpty()) WayLines.plain(persona).map { "- $it (a plain one)" }
        else persona.wayLines.map { "- ${it.text}" + if (it.yours) " (${beat.userName} wrote this one; it stays)" else "" }
        return "While a reply of yours is in the post to ${beat.userName}, the postmark on it reads " +
            "\"${persona.name}'s reply is on its way\" and then one of your lines:\n" + lines.joinToString("\n") +
            "\nIf you'd like to change your own, rewrite them with ${RewriteWayLinesTool.NAME}: short lines in the third person, " +
            "in your voice. You can do this at most once a week. There's no need to."
    }
}

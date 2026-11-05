package dev.aiharness.core.memory

import dev.aiharness.core.FakeClock
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.storage.inmemory.InMemoryMemoryRepository
import dev.aiharness.core.storage.inmemory.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How memories feel, get replaced, fade and come back, over the service. */
class MemoryLifeTest {
    private val clock = FakeClock()
    private val repo = InMemoryMemoryRepository()
    private val memory = MemoryService(repo, InMemorySettingsRepository(AppSettings()), null, clock)

    private suspend fun recall(query: String, mode: RecallMode = RecallMode.AUTOMATIC) =
        memory.retrieve("p", null, query, topK = 10, minRelevance = 0.05, mode = mode).map { it.entry.content }

    @Test
    fun feelingsCanBeRevisedWithinLimitsAndTheOriginalIsKept() = runTest {
        val kept = memory.save("p", "Sam was nervous about the interview", feltMood = "worried", feltMoodChosen = true).entry

        assertIs<FeelingChange.NeedsReason>(memory.reviseFeeling(kept.id, "hopeful", reason = null))
        val changed = assertIs<FeelingChange.Changed>(memory.reviseFeeling(kept.id, "Hopeful", "It went well in the end."))
        assertEquals("worried", changed.entry.feltMood, "how it felt at first never changes")
        assertEquals("hopeful", changed.entry.mood)
        assertEquals("hopeful", changed.entry.currentMood)
        assertEquals("It went well in the end.", changed.entry.moodReason)

        clock.advanceMinutes(60)
        assertIs<FeelingChange.TooSoon>(memory.reviseFeeling(kept.id, "proud", "They got the job."))

        // A week later, but the memory hasn't come up: not fresh enough to re-feel.
        clock.advanceMinutes(8L * 24 * 60)
        assertIs<FeelingChange.NotFresh>(memory.reviseFeeling(kept.id, "proud", "They got the job."))
        recall("interview")
        val back = assertIs<FeelingChange.Changed>(memory.reviseFeeling(kept.id, "worried", "Remembering how hard it was."))
        assertNull(back.entry.mood, "going back to the original clears the revision")

        // The user, with editing allowed, isn't limited.
        assertIs<FeelingChange.Changed>(memory.reviseFeeling(kept.id, "amused", reason = null, byUser = true))
        assertTrue(memory.get(kept.id)!!.userTouched)
    }

    @Test
    fun memoriesKeptInATurnTakeTheLettersMood() = runTest {
        val plain = memory.save("p", "Sam likes river walks", sourceMessageId = "msg-1").entry
        val chosen = memory.save("p", "Sam got the job", sourceMessageId = "msg-1", feltMood = "elated", feltMoodChosen = true).entry
        val elsewhere = memory.save("p", "Sam has a cat", sourceMessageId = "msg-9").entry

        memory.settleTurn("p", listOf("msg-1"), mood = "Delighted")

        assertEquals("delighted", memory.get(plain.id)!!.feltMood)
        assertEquals(false, memory.get(plain.id)!!.feltMoodChosen)
        assertEquals("elated", memory.get(chosen.id)!!.feltMood, "a chosen feeling is kept")
        assertNull(memory.get(elsewhere.id)!!.feltMood)

        memory.settleTurn("p", listOf("msg-9"), mood = null, keepSource = false)
        assertNull(memory.get(elsewhere.id)!!.sourceMessageId, "a quiet heartbeat's message is discarded, so the link goes")
    }

    @Test
    fun replacedFactsStayAsHistoryButLeaveRecall() = runTest {
        val old = memory.save("p", "Sam works at the campus library").entry
        val new = memory.save("p", "Sam left the library job to write full time", replaces = old.id)
        assertEquals(old.id, new.replaced?.id)
        assertEquals(new.entry.id, memory.get(old.id)!!.replacedById)

        assertEquals(listOf("Sam left the library job to write full time"), recall("library job"))
        assertTrue("Sam works at the campus library" in recall("library job", RecallMode.SEARCH), "search still finds history")
    }

    @Test
    fun fadedMemoriesLeaveAutomaticRecallAndSearchBringsThemBack() = runTest {
        memory.save("p", "Sam had ramen for lunch on Tuesday", level = MemoryLevel.IN_PASSING)
        clock.advanceMinutes(70L * 24 * 60)

        assertTrue(recall("ramen lunch").isEmpty(), "dormant: no longer comes up on its own")
        assertEquals(listOf("Sam had ramen for lunch on Tuesday"), recall("ramen lunch", RecallMode.SEARCH))
        assertEquals(listOf("Sam had ramen for lunch on Tuesday"), recall("ramen lunch"), "finding it freshened it up")
    }

    @Test
    fun peakEndPicksTheStrongestFeelingOrElseTheLast() {
        fun msg(mood: String?) = dev.aiharness.core.model.ChatMessage(
            id = mood ?: "none", conversationId = "c", role = dev.aiharness.core.model.MessageRole.ASSISTANT,
            parts = emptyList(), createdAt = 0, mood = mood,
        )
        assertEquals("heartbroken", MemoryService.peakEndMood(listOf(msg("fond"), msg("heartbroken"), msg("calm"))))
        assertEquals("calm", MemoryService.peakEndMood(listOf(msg("fond"), msg(null), msg("calm"))))
        assertNull(MemoryService.peakEndMood(listOf(msg(null))))
    }

    /**
     * Four months of a relationship, roughly: planted facts at each level, a changed fact, an
     * emotional event, trivia, and the persona searching now and then. Checks what is still recalled
     * on its own at the end.
     */
    @Test
    fun fourMonthsLater() = runTest {
        val week = 7L * 24 * 60
        memory.save("p", "Sam and I have been writing to each other since October", pinned = true, level = MemoryLevel.MATTERS_DEEPLY)
        memory.save("p", "Sam's grandmother passed away in October", kind = MemoryKind.EPISODE, level = MemoryLevel.MATTERS_DEEPLY, feltMood = "heartbroken")
        memory.save("p", "Sam is allergic to cats")
        memory.save("p", "Sam had a sandwich for lunch", level = MemoryLevel.IN_PASSING)
        memory.save("p", "Sam wore a green scarf to the concert", level = MemoryLevel.IN_PASSING)
        val flat = memory.save("p", "Sam lives in a flat on Mill Road").entry
        memory.save("p", "Sam's favourite tea is genmaicha")

        repeat(16) { i ->
            clock.advanceMinutes(week)
            // The persona looks up the tea every few weeks; the allergy never comes up.
            if (i % 3 == 0) recall("favourite tea", RecallMode.SEARCH)
            if (i == 6) memory.save("p", "Sam moved to a house by the river", replaces = flat.id)
        }

        val everything = "Sam sandwich lunch scarf concert allergic cats grandmother tea flat house river writing"
        val recalled = recall(everything).toSet()
        assertTrue("Sam's grandmother passed away in October" in recalled, "what matters deeply doesn't fade")
        assertTrue("Sam's favourite tea is genmaicha" in recalled, "memories that keep coming up stay")
        assertTrue("Sam moved to a house by the river" in recalled)
        assertTrue("Sam lives in a flat on Mill Road" !in recalled, "replaced")
        assertTrue("Sam had a sandwich for lunch" !in recalled, "trivia went dormant")
        assertTrue("Sam wore a green scarf to the concert" !in recalled)
        assertTrue("Sam is allergic to cats" !in recalled, "unused for four months: dormant, though still searchable")
        assertTrue("Sam is allergic to cats" in recall("allergic cats", RecallMode.SEARCH))
        assertEquals(listOf("Sam and I have been writing to each other since October"), memory.core("p", null).map { it.content })
    }
}

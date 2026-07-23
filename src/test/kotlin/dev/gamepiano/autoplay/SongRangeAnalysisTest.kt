package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.application.SongPlanService
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.model.ScoreNote
import dev.gamepiano.autoplay.projection.ProjectionResult
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongRangeAnalysisTest {
    private val service = SongPlanService()

    @Test
    fun `range recommendation covers low central and high songs`() {
        assertEquals(GameToneRange.G1, service.recommendRange(scoreOf(36, 43, 48)))
        assertEquals(GameToneRange.G3, service.recommendRange(scoreOf(60, 64, 69)))
        assertEquals(GameToneRange.G5, service.recommendRange(scoreOf(84, 90, 96)))
        assertEquals(GameToneRange.G6, service.recommendRange(scoreOf(102, 107, 112)))
    }

    @Test
    fun `range recommendation ignores drum channel`() {
        val score = MidiScore(
            "melody and drums",
            listOf(
                ScoreNote(0, 100_000, 36, channel = 9),
                ScoreNote(0, 100_000, 84),
                ScoreNote(100_000, 100_000, 88),
            ),
        )

        assertEquals(GameToneRange.G5, service.recommendRange(score))
    }

    @Test
    fun `starting at timeline position removes elapsed batches and rebases time`() {
        val plan = SongPlan(
            source = Path.of("test.mid"),
            range = GameToneRange.G3,
            recommendedRange = GameToneRange.G3,
            score = MidiScore("test", listOf(ScoreNote(0, 4_000_000, 60))),
            projection = ProjectionResult(emptyList(), 0, 0),
            batches = listOf(
                KeyBatch(500_000, listOf(Piano23Layout.keyAt(0))),
                KeyBatch(2_000_000, listOf(Piano23Layout.keyAt(1))),
                KeyBatch(3_500_000, listOf(Piano23Layout.keyAt(2))),
            ),
        )

        val section = plan.startingAt(1_250_000)

        assertTrue(section.playable)
        assertEquals(listOf(750_000L, 2_250_000L), section.batches.map { it.atMicros })
        assertFalse(plan.startingAt(4_000_000).playable)
    }

    private fun scoreOf(vararg notes: Int): MidiScore = MidiScore(
        "range",
        notes.mapIndexed { index, midi -> ScoreNote(index * 100_000L, 80_000, midi) },
    )
}

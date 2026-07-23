package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.RangeCalibration
import dev.gamepiano.autoplay.model.ScoreNote
import dev.gamepiano.autoplay.projection.OutOfRangePolicy
import dev.gamepiano.autoplay.projection.ProjectionSettings
import dev.gamepiano.autoplay.projection.ScoreProjector
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreProjectorTest {
    private val calibration = RangeCalibration.provisionalMiddlePiano()

    @Test
    fun `provisional G3 maps F sharp 3 through E5 to all 23 keys`() {
        val score = MidiScore(
            "range",
            listOf(note(54), note(60), note(76)),
        )

        val result = ScoreProjector.project(score, ProjectionSettings(GameToneRange.G3, calibration))

        assertEquals(listOf(0, 6, 22), result.events.map { it.keyIndex })
        assertEquals(0, result.foldedNotes)
    }

    @Test
    fun `out of range notes can be folded by octave`() {
        val result = ScoreProjector.project(
            MidiScore("fold", listOf(note(77))),
            ProjectionSettings(GameToneRange.G3, calibration, outOfRangePolicy = OutOfRangePolicy.FOLD_BY_OCTAVE),
        )

        assertEquals(listOf(11), result.events.map { it.keyIndex })
        assertEquals(1, result.foldedNotes)
    }

    @Test
    fun `out of range notes can be dropped`() {
        val result = ScoreProjector.project(
            MidiScore("drop", listOf(note(77))),
            ProjectionSettings(GameToneRange.G3, calibration, outOfRangePolicy = OutOfRangePolicy.DROP),
        )

        assertEquals(0, result.events.size)
        assertEquals(1, result.droppedNotes)
    }

    @Test
    fun `general midi percussion channel is not mapped to piano keys`() {
        val score = MidiScore(
            "with drums",
            listOf(note(36, channel = 9), note(60, channel = 0)),
        )

        val result = ScoreProjector.project(score, ProjectionSettings(GameToneRange.G3, calibration))

        assertEquals(listOf(6), result.events.map { it.keyIndex })
        assertEquals(0, result.foldedNotes)
    }

    private fun note(midi: Int, channel: Int = 0) = ScoreNote(0, 900_000, midi, channel = channel)
}

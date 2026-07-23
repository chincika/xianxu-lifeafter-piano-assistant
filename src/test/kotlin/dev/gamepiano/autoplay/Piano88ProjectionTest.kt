package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.ScoreNote
import dev.gamepiano.autoplay.projection.Piano88ScoreProjector
import kotlin.test.Test
import kotlin.test.assertEquals

class Piano88ProjectionTest {
    @Test
    fun `88 key projection preserves piano pitches and drops only notes outside A0 to C8`() {
        val score = MidiScore(
            "full piano",
            listOf(
                note(20),
                note(21),
                note(60),
                note(108),
                note(109),
                note(36, channel = 9),
            ),
        )

        val result = Piano88ScoreProjector.project(score)

        assertEquals(listOf(0, 39, 87), result.events.map { it.keyIndex })
        assertEquals(listOf(21, 60, 108), result.events.map { it.sourceMidiNote })
        assertEquals(0, result.foldedNotes)
        assertEquals(2, result.droppedNotes)
    }

    private fun note(midiNote: Int, channel: Int = 0) = ScoreNote(
        startMicros = midiNote.toLong(),
        durationMicros = 100_000,
        midiNote = midiNote,
        channel = channel,
    )
}

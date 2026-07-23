package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.application.SongPlanService
import dev.gamepiano.autoplay.application.midiNoteName
import dev.gamepiano.autoplay.midi.ExampleMidiGenerator
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.RangeCalibration
import java.nio.file.Files
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongPlanServiceTest {
    @Test
    fun `generated twinkle example stays playable in G3`() {
        val path = Files.createTempFile("twinkle-example-", ".mid")
        try {
            ExampleMidiGenerator.writeTwinkleTwinkle(path)
            val plan = SongPlanService().prepare(path, GameToneRange.G3)

            assertEquals(42, plan.score.notes.size)
            assertEquals(42, plan.batches.size)
            assertTrue(plan.score.notes.all { it.midiNote in 60..69 })
            assertEquals(0, plan.projection.foldedNotes)
            assertEquals(0, plan.projection.droppedNotes)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `prepares a midi file into the calibrated 23 key layout`() {
        val path = Files.createTempFile("game-piano-plan-", ".mid")
        try {
            val sequence = Sequence(Sequence.PPQ, 480)
            val track = sequence.createTrack()
            track.add(note(ShortMessage.NOTE_ON, 0, 54, 100))
            track.add(note(ShortMessage.NOTE_ON, 0, 56, 100))
            track.add(note(ShortMessage.NOTE_OFF, 240, 54, 0))
            track.add(note(ShortMessage.NOTE_OFF, 240, 56, 0))
            track.add(note(ShortMessage.NOTE_ON, 480, 55, 100))
            track.add(note(ShortMessage.NOTE_OFF, 720, 55, 0))
            assertTrue(MidiSystem.write(sequence, 1, path.toFile()) > 0)

            val plan = SongPlanService().prepare(
                path = path,
                range = GameToneRange.G3,
                calibration = RangeCalibration.fromCenterRangeLowest(54),
            )

            assertEquals(3, plan.score.notes.size)
            assertEquals(2, plan.batches.size)
            assertEquals(listOf("1", "2"), plan.batches[0].keys.map { it.keyboardLabel })
            assertEquals(listOf("Q"), plan.batches[1].keys.map { it.keyboardLabel })
            assertEquals(0, plan.projection.foldedNotes)
            assertEquals(0, plan.projection.droppedNotes)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `formats standard midi note names`() {
        assertEquals("C4", midiNoteName(60))
        assertEquals("F#3", midiNoteName(54))
    }

    private fun note(command: Int, tick: Long, note: Int, velocity: Int): MidiEvent =
        MidiEvent(ShortMessage(command, 0, note, velocity), tick)
}

package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.model.RangeCalibration
import dev.gamepiano.autoplay.model.ScoreNote
import dev.gamepiano.autoplay.preview.GameMappedPreviewSequence
import dev.gamepiano.autoplay.projection.ProjectionResult
import java.nio.file.Path
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class GameMappedPreviewSequenceTest {
    @Test
    fun `mapped preview uses selected game range pitches and batch timing`() {
        val calibration = RangeCalibration.fromCenterRangeLowest(54)
        val plan = SongPlan(
            source = Path.of("source.mid"),
            range = GameToneRange.G5,
            recommendedRange = GameToneRange.G5,
            score = MidiScore("mapped", listOf(ScoreNote(0, 2_000_000, 60))),
            projection = ProjectionResult(emptyList(), foldedNotes = 1, droppedNotes = 0),
            batches = listOf(
                KeyBatch(500_000, listOf(Piano23Layout.keyAt(0), Piano23Layout.keyAt(22))),
                KeyBatch(550_000, listOf(Piano23Layout.keyAt(0))),
            ),
        )

        val sequence = GameMappedPreviewSequence.build(plan, calibration)
        val noteOns = (0 until sequence.tracks.single().size())
            .map(sequence.tracks.single()::get)
            .filter { event ->
                val message = event.message
                message is ShortMessage && message.command == ShortMessage.NOTE_ON && message.data2 > 0
            }

        val repeatedNoteOffs = (0 until sequence.tracks.single().size())
            .map(sequence.tracks.single()::get)
            .filter { event ->
                val message = event.message
                message is ShortMessage && message.command == ShortMessage.NOTE_OFF && message.data1 == 78
            }

        assertEquals(listOf(78, 100, 78), noteOns.map { (it.message as ShortMessage).data1 })
        assertEquals(listOf(500L, 500L, 550L), noteOns.map { it.tick })
        assertEquals(listOf(549L, 640L), repeatedNoteOffs.map { it.tick })
    }
}

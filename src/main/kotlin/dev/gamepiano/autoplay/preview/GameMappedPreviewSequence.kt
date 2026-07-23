package dev.gamepiano.autoplay.preview

import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.model.RangeCalibration
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.model.Piano88Layout
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

object GameMappedPreviewSequence {
    private const val TICKS_PER_QUARTER = 1_000
    private const val MICROS_PER_QUARTER = 1_000_000
    private const val NOTE_DURATION_MICROS = 90_000L

    /**
     * Builds a piano MIDI sequence from the same de-duplicated key batches sent to the game.
     * One tick equals one millisecond, so batch timing stays aligned with the source timeline.
     */
    fun build(plan: SongPlan, calibration: RangeCalibration): Sequence {
        val sequence = Sequence(Sequence.PPQ, TICKS_PER_QUARTER)
        val track = sequence.createTrack()
        track.add(MidiEvent(tempoMessage(), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0), 0))

        val lowestMidiNote = when (plan.performanceMode) {
            PerformanceMode.KEYBOARD_23 -> calibration.lowestMidiNote(plan.range)
            PerformanceMode.PIANO_88_MOUSE -> Piano88Layout.LOWEST_MIDI_NOTE
        }
        val keyCount = when (plan.performanceMode) {
            PerformanceMode.KEYBOARD_23 -> 23
            PerformanceMode.PIANO_88_MOUSE -> Piano88Layout.KEY_COUNT
        }
        val nextStartByKey = LongArray(keyCount) { Long.MAX_VALUE }
        val mappedNotes = buildList {
            plan.batches.asReversed().forEach { batch ->
                batch.keys.asReversed().forEach { key ->
                    val nextStart = nextStartByKey[key.index]
                    val naturalEnd = batch.atMicros + NOTE_DURATION_MICROS
                    val endBeforeRepeat = if (nextStart == Long.MAX_VALUE) {
                        naturalEnd
                    } else {
                        (nextStart - 1_000L).coerceAtLeast(batch.atMicros + 1_000L)
                    }
                    add(MappedNote(batch.atMicros, minOf(naturalEnd, endBeforeRepeat), lowestMidiNote + key.index))
                    nextStartByKey[key.index] = batch.atMicros
                }
            }
        }.asReversed()
        mappedNotes.forEach { note ->
            val noteOnTick = microsToTicks(note.startMicros)
            val noteOffTick = microsToTicks(note.endMicros)
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note.midiNote, 96), noteOnTick))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note.midiNote, 0), noteOffTick))
        }

        val endTick = maxOf(
            microsToTicks(plan.score.durationMicros),
            plan.batches.lastOrNull()?.let { microsToTicks(it.atMicros + NOTE_DURATION_MICROS) } ?: 0,
        )
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), endTick))
        return sequence
    }

    private fun microsToTicks(micros: Long): Long = micros / 1_000L

    private data class MappedNote(
        val startMicros: Long,
        val endMicros: Long,
        val midiNote: Int,
    )

    private fun tempoMessage(): MetaMessage {
        val data = byteArrayOf(
            ((MICROS_PER_QUARTER shr 16) and 0xFF).toByte(),
            ((MICROS_PER_QUARTER shr 8) and 0xFF).toByte(),
            (MICROS_PER_QUARTER and 0xFF).toByte(),
        )
        return MetaMessage(0x51, data, data.size)
    }
}

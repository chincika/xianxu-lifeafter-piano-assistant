package dev.gamepiano.autoplay.projection

import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.Piano88Layout
import dev.gamepiano.autoplay.model.RangeCalibration
import dev.gamepiano.autoplay.model.TapEvent

enum class OutOfRangePolicy {
    DROP,
    FOLD_BY_OCTAVE,
}

data class ProjectionSettings(
    val range: GameToneRange,
    val calibration: RangeCalibration,
    val transposeSemitones: Int = 0,
    val outOfRangePolicy: OutOfRangePolicy = OutOfRangePolicy.FOLD_BY_OCTAVE,
    /** General MIDI channel 10 (zero-based channel 9) is percussion, not pitched piano. */
    val ignoredChannels: Set<Int> = setOf(9),
)

data class ProjectionResult(
    val events: List<TapEvent>,
    val foldedNotes: Int,
    val droppedNotes: Int,
)

object ScoreProjector {
    fun project(score: MidiScore, settings: ProjectionSettings): ProjectionResult {
        val lowest = settings.calibration.lowestMidiNote(settings.range)
        val highest = settings.calibration.highestMidiNote(settings.range)
        var folded = 0
        var dropped = 0

        val events = score.notes.asSequence()
            .filterNot { it.channel in settings.ignoredChannels }
            .mapNotNull { note ->
            val transposed = note.midiNote + settings.transposeSemitones
            val projected = when {
                transposed in lowest..highest -> transposed
                settings.outOfRangePolicy == OutOfRangePolicy.DROP -> null
                else -> foldByOctave(transposed, lowest, highest)
            }

            if (projected == null) {
                dropped += 1
                null
            } else {
                if (projected != transposed) folded += 1
                TapEvent(
                    atMicros = note.startMicros,
                    keyIndex = projected - lowest,
                    sourceMidiNote = note.midiNote,
                    velocity = note.velocity,
                )
            }
            }.sortedWith(compareBy(TapEvent::atMicros, TapEvent::sourceMidiNote)).toList()

        return ProjectionResult(events, folded, dropped)
    }

    private fun foldByOctave(note: Int, lowest: Int, highest: Int): Int? {
        var folded = note
        while (folded < lowest) folded += 12
        while (folded > highest) folded -= 12
        return folded.takeIf { it in lowest..highest }
    }
}

/** Preserves the source pitch for a standard 88-key A0..C8 piano surface. */
object Piano88ScoreProjector {
    fun project(
        score: MidiScore,
        transposeSemitones: Int = 0,
        ignoredChannels: Set<Int> = setOf(9),
    ): ProjectionResult {
        var dropped = 0
        val events = score.notes.asSequence()
            .filterNot { it.channel in ignoredChannels }
            .mapNotNull { note ->
                val projected = note.midiNote + transposeSemitones
                if (projected !in Piano88Layout.LOWEST_MIDI_NOTE..Piano88Layout.HIGHEST_MIDI_NOTE) {
                    dropped += 1
                    null
                } else {
                    TapEvent(
                        atMicros = note.startMicros,
                        keyIndex = projected - Piano88Layout.LOWEST_MIDI_NOTE,
                        sourceMidiNote = note.midiNote,
                        velocity = note.velocity,
                    )
                }
            }
            .sortedWith(compareBy(TapEvent::atMicros, TapEvent::sourceMidiNote))
            .toList()
        return ProjectionResult(events, foldedNotes = 0, droppedNotes = dropped)
    }
}

package dev.gamepiano.autoplay.application

import dev.gamepiano.autoplay.adapter.GameProfile
import dev.gamepiano.autoplay.adapter.LifeAfterPianoProfile
import dev.gamepiano.autoplay.midi.MidiFileReader
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.model.Piano88Layout
import dev.gamepiano.autoplay.model.RangeCalibration
import dev.gamepiano.autoplay.playback.ChordBatcher
import dev.gamepiano.autoplay.projection.OutOfRangePolicy
import dev.gamepiano.autoplay.projection.ProjectionResult
import dev.gamepiano.autoplay.projection.ProjectionSettings
import dev.gamepiano.autoplay.projection.Piano88ScoreProjector
import dev.gamepiano.autoplay.projection.ScoreProjector
import java.nio.file.Path

data class SongPlan(
    val source: Path,
    val range: GameToneRange,
    val recommendedRange: GameToneRange,
    val score: MidiScore,
    val projection: ProjectionResult,
    val batches: List<KeyBatch>,
    val performanceMode: PerformanceMode = PerformanceMode.KEYBOARD_23,
) {
    val playable: Boolean = batches.isNotEmpty()

    /** Creates a plan whose first event starts immediately at the selected song position. */
    fun startingAt(positionMicros: Long): SongPlan {
        val start = positionMicros.coerceIn(0L, score.durationMicros)
        return copy(
            batches = batches.asSequence()
                .filter { it.atMicros >= start }
                .map { it.copy(atMicros = it.atMicros - start) }
                .toList(),
        )
    }
}

data class RangeFit(
    val range: GameToneRange,
    val outsideNoteCount: Int,
    val outsideDistanceSemitones: Long,
    val centerDistanceSemitones: Double,
)

class SongPlanService(
    private val profile: GameProfile = LifeAfterPianoProfile.provisional(),
) {
    fun prepare(
        path: Path,
        range: GameToneRange,
        calibration: RangeCalibration = profile.calibration,
        transposeSemitones: Int = 0,
        outOfRangePolicy: OutOfRangePolicy = OutOfRangePolicy.FOLD_BY_OCTAVE,
        performanceMode: PerformanceMode = PerformanceMode.KEYBOARD_23,
    ): SongPlan {
        require(transposeSemitones in -48..48)
        val score = MidiFileReader.read(path)
        val recommendedRange = recommendRange(score, calibration, transposeSemitones)
        val projection = when (performanceMode) {
            PerformanceMode.KEYBOARD_23 -> ScoreProjector.project(
                score,
                ProjectionSettings(
                    range = range,
                    calibration = calibration,
                    transposeSemitones = transposeSemitones,
                    outOfRangePolicy = outOfRangePolicy,
                ),
            )
            PerformanceMode.PIANO_88_MOUSE -> Piano88ScoreProjector.project(
                score = score,
                transposeSemitones = transposeSemitones,
            )
        }
        val layoutKeys = when (performanceMode) {
            PerformanceMode.KEYBOARD_23 -> profile.keys
            PerformanceMode.PIANO_88_MOUSE -> Piano88Layout.keys
        }
        return SongPlan(
            source = path,
            range = range,
            recommendedRange = recommendedRange,
            score = score,
            projection = projection,
            batches = ChordBatcher.batch(projection.events, profile.chordWindowMicros, layoutKeys),
            performanceMode = performanceMode,
        )
    }

    fun recommendRange(
        score: MidiScore,
        calibration: RangeCalibration = profile.calibration,
        transposeSemitones: Int = 0,
    ): GameToneRange {
        val pitchedNotes = score.notes
            .asSequence()
            .filterNot { it.channel == 9 }
            .map { it.midiNote + transposeSemitones }
            .toList()
        if (pitchedNotes.isEmpty()) return GameToneRange.G3

        val medianPitch = pitchedNotes.sorted().let { sorted ->
            if (sorted.size % 2 == 1) {
                sorted[sorted.size / 2].toDouble()
            } else {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
            }
        }

        return GameToneRange.entries.map { candidate ->
            val lowest = calibration.lowestMidiNote(candidate)
            val highest = calibration.highestMidiNote(candidate)
            RangeFit(
                range = candidate,
                outsideNoteCount = pitchedNotes.count { it !in lowest..highest },
                outsideDistanceSemitones = pitchedNotes.sumOf { note ->
                    when {
                        note < lowest -> (lowest - note).toLong()
                        note > highest -> (note - highest).toLong()
                        else -> 0L
                    }
                },
                centerDistanceSemitones = kotlin.math.abs(medianPitch - (lowest + highest) / 2.0),
            )
        }.minWith(
            compareBy<RangeFit>(RangeFit::outsideNoteCount)
                .thenBy(RangeFit::outsideDistanceSemitones)
                .thenBy(RangeFit::centerDistanceSemitones)
                .thenBy { kotlin.math.abs(it.range.octaveOffsetFromCenter) },
        ).range
    }
}

fun midiNoteName(midiNote: Int): String {
    require(midiNote in 0..127)
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return names[midiNote % 12] + (midiNote / 12 - 1)
}

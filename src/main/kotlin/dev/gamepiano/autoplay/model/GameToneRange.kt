package dev.gamepiano.autoplay.model

enum class GameToneRange(val octaveOffsetFromCenter: Int) {
    G1(-2),
    G2(-1),
    G3(0),
    G4(1),
    G5(2),
    G6(3),
}

data class RangeCalibration(
    private val lowestMidiNoteByRange: Map<GameToneRange, Int>,
) {
    init {
        require(lowestMidiNoteByRange.keys.containsAll(GameToneRange.entries)) {
            "All G1-G6 ranges must be calibrated"
        }
        lowestMidiNoteByRange.values.forEach { note ->
            require(note in 0..105) { "A 23-key range must stay inside MIDI 0..127" }
        }
    }

    fun lowestMidiNote(range: GameToneRange): Int =
        requireNotNull(lowestMidiNoteByRange[range])

    fun highestMidiNote(range: GameToneRange): Int = lowestMidiNote(range) + Piano23Layout.KEY_COUNT - 1

    companion object {
        /** Builds all six game ranges from the lowest note of the G3 calibration range. */
        fun fromCenterRangeLowest(g3LowestMidiNote: Int, rangeStepSemitones: Int = 12): RangeCalibration =
            RangeCalibration(
                GameToneRange.entries.associateWith { range ->
                    g3LowestMidiNote + range.octaveOffsetFromCenter * rangeStepSemitones
                },
            )

        /**
         * Provisional only: the G3 calibration range starts at F#3, with adjacent game ranges
         * one octave apart.
         */
        fun provisionalMiddlePiano(): RangeCalibration = fromCenterRangeLowest(54)
    }
}

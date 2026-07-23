package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.RangeCalibration
import kotlin.test.Test
import kotlin.test.assertEquals

class GameToneRangeTest {
    @Test
    fun `G1 through G6 are derived from the G3 calibration`() {
        val calibration = RangeCalibration.fromCenterRangeLowest(54)

        assertEquals(listOf(30, 42, 54, 66, 78, 90), GameToneRange.entries.map(calibration::lowestMidiNote))
        assertEquals(listOf(52, 64, 76, 88, 100, 112), GameToneRange.entries.map(calibration::highestMidiNote))
    }
}

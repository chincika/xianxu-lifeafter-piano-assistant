package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.TapEvent
import dev.gamepiano.autoplay.playback.ChordBatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordBatcherTest {
    @Test
    fun `events within two milliseconds form one chord and duplicate keys are removed`() {
        val batches = ChordBatcher.batch(
            listOf(
                event(0, 6),
                event(1_000, 10),
                event(1_500, 6),
                event(2_001, 13),
            ),
        )

        assertEquals(2, batches.size)
        assertEquals(listOf(6, 10), batches[0].keys.map { it.index })
        assertEquals(listOf(13), batches[1].keys.map { it.index })
    }

    private fun event(atMicros: Long, keyIndex: Int) = TapEvent(atMicros, keyIndex, 60 + keyIndex, 100)
}

package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.GameKey
import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.ui.noteDensityBuckets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteDensityBucketsTest {
    @Test
    fun reflectsRealBatchTimingAndChordWeight() {
        val key = GameKey(0, "Q", 0x10)
        val buckets = noteDensityBuckets(
            batches = listOf(
                KeyBatch(100_000, listOf(key)),
                KeyBatch(1_100_000, listOf(key, key, key)),
                KeyBatch(1_200_000, listOf(key)),
            ),
            durationMicros = 2_000_000,
            count = 4,
        )

        assertEquals(4, buckets.size)
        assertTrue(buckets[0] > 0f)
        assertEquals(0f, buckets[1])
        assertTrue(buckets[2] > buckets[0])
        assertEquals(0f, buckets[3])
    }
}

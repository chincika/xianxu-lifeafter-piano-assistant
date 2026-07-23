package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.playback.MonotonicClock
import dev.gamepiano.autoplay.playback.PlaybackEngine
import dev.gamepiano.autoplay.playback.PlaybackOptions
import dev.gamepiano.autoplay.playback.RecordingInputSink
import dev.gamepiano.autoplay.playback.WaitStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackEngineTest {
    @Test
    fun `engine schedules taps and always releases keys`() {
        val clock = FakeClock()
        val engine = PlaybackEngine(clock, WaitStrategy { _, target, _ -> clock.now = target })
        val sink = RecordingInputSink()
        val batches = listOf(
            KeyBatch(0, listOf(Piano23Layout.keyAt(6))),
            KeyBatch(250_000, listOf(Piano23Layout.keyAt(10))),
        )

        val report = engine.play(batches, sink, PlaybackOptions(tapDurationMillis = 35))

        assertEquals(2, report.executedBatches)
        assertEquals(2, sink.taps.size)
        assertEquals(1, sink.releaseAllCalls)
        assertFalse(report.cancelled)
    }

    private class FakeClock(var now: Long = 0) : MonotonicClock {
        override fun nowNanos(): Long = now
    }
}

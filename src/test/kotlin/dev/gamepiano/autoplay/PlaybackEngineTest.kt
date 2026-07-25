package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.playback.MonotonicClock
import dev.gamepiano.autoplay.playback.PlaybackEngine
import dev.gamepiano.autoplay.playback.PlaybackOptions
import dev.gamepiano.autoplay.playback.InputSink
import dev.gamepiano.autoplay.playback.RecordingInputSink
import dev.gamepiano.autoplay.playback.WaitStrategy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `pause freezes the timeline until playback resumes`() {
        val engine = PlaybackEngine()
        val firstTap = CountDownLatch(1)
        val secondTap = CountDownLatch(1)
        val tapCount = AtomicInteger()
        val sink = object : InputSink {
            override fun tap(keys: List<dev.gamepiano.autoplay.model.GameKey>, holdMillis: Long) {
                if (tapCount.incrementAndGet() == 1) firstTap.countDown() else secondTap.countDown()
            }

            override fun releaseAll() = Unit
        }
        val batches = listOf(
            KeyBatch(0, listOf(Piano23Layout.keyAt(6))),
            KeyBatch(150_000, listOf(Piano23Layout.keyAt(10))),
        )
        val playback = Thread { engine.play(batches, sink) }.apply { start() }

        assertTrue(firstTap.await(1, TimeUnit.SECONDS))
        assertTrue(engine.pause())
        Thread.sleep(220)
        assertEquals(1, tapCount.get(), "A paused engine must not send the next batch")

        assertTrue(engine.resume())
        assertTrue(secondTap.await(1, TimeUnit.SECONDS))
        playback.join(1_000)
        assertFalse(playback.isAlive)
    }

    @Test
    fun `losing target readiness freezes progress and resumes without catching up`() {
        val engine = PlaybackEngine()
        val ready = AtomicBoolean(true)
        val firstTap = CountDownLatch(1)
        val secondTap = CountDownLatch(1)
        val tapCount = AtomicInteger()
        val progress = CopyOnWriteArrayList<Long>()
        val readiness = CopyOnWriteArrayList<Boolean>()
        val sink = object : InputSink {
            override fun tap(keys: List<dev.gamepiano.autoplay.model.GameKey>, holdMillis: Long) {
                if (tapCount.incrementAndGet() == 1) firstTap.countDown() else secondTap.countDown()
            }

            override fun releaseAll() = Unit
        }
        val batches = listOf(
            KeyBatch(0, listOf(Piano23Layout.keyAt(6))),
            KeyBatch(180_000, listOf(Piano23Layout.keyAt(10))),
        )
        val playback = Thread {
            engine.play(
                batches = batches,
                sink = sink,
                durationMicros = 180_000,
                readinessProbe = ready::get,
                onReadinessChange = readiness::add,
                onProgress = progress::add,
            )
        }.apply { start() }

        assertTrue(firstTap.await(1, TimeUnit.SECONDS))
        ready.set(false)
        assertTrue(waitUntil { readiness.lastOrNull() == false })
        val frozenAt = progress.last()
        Thread.sleep(260)
        assertEquals(1, tapCount.get(), "Waiting playback must not send a late batch")
        assertTrue(
            progress.last() - frozenAt <= 20_000,
            "Progress must remain frozen while the target window is unavailable",
        )

        ready.set(true)
        assertTrue(secondTap.await(1, TimeUnit.SECONDS))
        playback.join(1_000)
        assertFalse(playback.isAlive)
        assertEquals(180_000L, progress.last())
        assertTrue(readiness.containsAll(listOf(false, true)))
    }

    private fun waitUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private class FakeClock(var now: Long = 0) : MonotonicClock {
        override fun nowNanos(): Long = now
    }
}

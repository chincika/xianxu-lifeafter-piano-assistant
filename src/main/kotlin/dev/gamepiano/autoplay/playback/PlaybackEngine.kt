package dev.gamepiano.autoplay.playback

import dev.gamepiano.autoplay.model.KeyBatch
import java.util.concurrent.locks.LockSupport

fun interface MonotonicClock {
    fun nowNanos(): Long
}

fun interface WaitStrategy {
    fun await(clock: MonotonicClock, targetNanos: Long, cancelled: () -> Boolean)
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

object HybridWaitStrategy : WaitStrategy {
    override fun await(clock: MonotonicClock, targetNanos: Long, cancelled: () -> Boolean) {
        while (!cancelled()) {
            val remaining = targetNanos - clock.nowNanos()
            if (remaining <= 0) return
            if (remaining > 2_000_000) {
                Thread.sleep(minOf((remaining - 1_000_000) / 1_000_000, 10))
            } else {
                LockSupport.parkNanos(minOf(remaining, 250_000))
            }
        }
    }
}

data class PlaybackOptions(
    val tapDurationMillis: Long = 0,
    val maxLateMillis: Long = 120,
) {
    init {
        require(tapDurationMillis in 0..200)
        require(maxLateMillis >= 0)
    }
}

data class PlaybackReport(
    val executedBatches: Int,
    val droppedLateBatches: Int,
    val cancelled: Boolean,
)

class PlaybackEngine(
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val waitStrategy: WaitStrategy = HybridWaitStrategy,
) {
    @Volatile
    private var stopRequested: Boolean = false

    fun stop() {
        stopRequested = true
    }

    fun play(
        batches: List<KeyBatch>,
        sink: InputSink,
        options: PlaybackOptions = PlaybackOptions(),
    ): PlaybackReport {
        stopRequested = false
        val originNanos = clock.nowNanos()
        var executed = 0
        var dropped = 0
        var playbackFailure: Throwable? = null

        try {
            for (batch in batches.sortedBy(KeyBatch::atMicros)) {
                if (stopRequested) break
                val targetNanos = originNanos + batch.atMicros * 1_000
                waitStrategy.await(clock, targetNanos) { stopRequested }
                if (stopRequested) break

                val lateNanos = clock.nowNanos() - targetNanos
                if (lateNanos > options.maxLateMillis * 1_000_000) {
                    dropped += 1
                    continue
                }
                sink.tap(batch.keys, options.tapDurationMillis)
                executed += 1
            }
        } catch (throwable: Throwable) {
            playbackFailure = throwable
            throw throwable
        } finally {
            try {
                sink.releaseAll()
            } catch (releaseFailure: Throwable) {
                if (playbackFailure == null) throw releaseFailure
                playbackFailure.addSuppressed(releaseFailure)
            }
        }

        return PlaybackReport(executed, dropped, stopRequested)
    }
}

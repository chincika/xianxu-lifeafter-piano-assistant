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

class PlaybackTargetUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class PlaybackEngine(
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val waitStrategy: WaitStrategy = HybridWaitStrategy,
) {
    private val pauseLock = Object()

    @Volatile
    private var stopRequested: Boolean = false

    @Volatile
    private var paused: Boolean = false

    fun stop() {
        stopRequested = true
        synchronized(pauseLock) {
            paused = false
            pauseLock.notifyAll()
        }
    }

    fun pause(): Boolean = synchronized(pauseLock) {
        if (stopRequested || paused) return@synchronized false
        paused = true
        true
    }

    fun resume(): Boolean = synchronized(pauseLock) {
        if (!paused) return@synchronized false
        paused = false
        pauseLock.notifyAll()
        true
    }

    fun togglePaused(): Boolean? = synchronized(pauseLock) {
        if (stopRequested) return@synchronized null
        paused = !paused
        if (!paused) pauseLock.notifyAll()
        paused
    }

    fun isPaused(): Boolean = paused

    fun play(
        batches: List<KeyBatch>,
        sink: InputSink,
        options: PlaybackOptions = PlaybackOptions(),
        durationMicros: Long = batches.maxOfOrNull(KeyBatch::atMicros) ?: 0L,
        readinessProbe: () -> Boolean = { true },
        onReadinessChange: (Boolean) -> Unit = {},
        onProgress: (Long) -> Unit = {},
    ): PlaybackReport {
        require(durationMicros >= 0)
        stopRequested = false
        paused = false
        val originNanos = clock.nowNanos()
        var suspendedNanos = 0L
        var suspensionStartedNanos: Long? = null
        var lastProgressEmitNanos = Long.MIN_VALUE
        var lastReportedReady: Boolean? = null
        var executed = 0
        var dropped = 0
        var playbackFailure: Throwable? = null

        fun beginSuspension(nowNanos: Long) {
            if (suspensionStartedNanos == null) suspensionStartedNanos = nowNanos
        }

        fun endSuspension(nowNanos: Long) {
            val startedAt = suspensionStartedNanos ?: return
            suspendedNanos += (nowNanos - startedAt).coerceAtLeast(0L)
            suspensionStartedNanos = null
        }

        fun positionAt(nowNanos: Long): Long {
            val activeSuspension = suspensionStartedNanos
                ?.let { startedAt -> (nowNanos - startedAt).coerceAtLeast(0L) }
                ?: 0L
            return ((nowNanos - originNanos - suspendedNanos - activeSuspension) / 1_000)
                .coerceIn(0L, durationMicros)
        }

        fun emitProgress(nowNanos: Long, force: Boolean = false) {
            if (!force && lastProgressEmitNanos != Long.MIN_VALUE &&
                nowNanos - lastProgressEmitNanos < PROGRESS_INTERVAL_NANOS
            ) return
            lastProgressEmitNanos = nowNanos
            onProgress(positionAt(nowNanos))
        }

        fun targetReady(nowNanos: Long = clock.nowNanos()): Boolean {
            val ready = runCatching(readinessProbe).getOrDefault(false)
            if (!ready && !stopRequested) beginSuspension(nowNanos)
            if (lastReportedReady != ready) {
                if (!ready) emitProgress(nowNanos, force = true)
                lastReportedReady = ready
                onReadinessChange(ready)
            }
            return ready
        }

        fun waitUntilPlayable(): Boolean {
            while (!stopRequested) {
                val now = clock.nowNanos()
                val ready = targetReady(now)
                if (!paused && ready) {
                    endSuspension(now)
                    emitProgress(now, force = true)
                    return true
                }
                beginSuspension(now)
                emitProgress(now)
                synchronized(pauseLock) {
                    if (!stopRequested && (paused || !ready)) pauseLock.wait(20)
                }
            }
            return false
        }

        fun shouldInterruptWait(): Boolean {
            val now = clock.nowNanos()
            val ready = targetReady(now)
            val interrupted = stopRequested || paused || !ready
            if (interrupted && !stopRequested) beginSuspension(now)
            emitProgress(now)
            return interrupted
        }

        val trackingClock = MonotonicClock {
            clock.nowNanos().also { emitProgress(it) }
        }

        try {
            onProgress(0L)
            batchLoop@ for (batch in batches.sortedBy(KeyBatch::atMicros)) {
                while (true) {
                    if (!waitUntilPlayable()) break@batchLoop
                    val targetNanos = originNanos + batch.atMicros * 1_000 + suspendedNanos
                    waitStrategy.await(trackingClock, targetNanos, ::shouldInterruptWait)
                    if (stopRequested) break@batchLoop
                    if (paused || !targetReady()) continue

                    val now = clock.nowNanos()
                    val lateNanos = now - targetNanos
                    if (lateNanos > options.maxLateMillis * 1_000_000) {
                        dropped += 1
                        emitProgress(now, force = true)
                        break
                    }
                    try {
                        sink.tap(batch.keys, options.tapDurationMillis)
                        executed += 1
                        emitProgress(clock.nowNanos(), force = true)
                        break
                    } catch (_: PlaybackTargetUnavailableException) {
                        beginSuspension(clock.nowNanos())
                        if (lastReportedReady != false) {
                            lastReportedReady = false
                            onReadinessChange(false)
                        }
                    }
                }
            }
        } catch (throwable: Throwable) {
            playbackFailure = throwable
            throw throwable
        } finally {
            val finishedAt = clock.nowNanos()
            if (!stopRequested && playbackFailure == null) {
                onProgress(durationMicros)
            } else {
                emitProgress(finishedAt, force = true)
            }
            try {
                sink.releaseAll()
            } catch (releaseFailure: Throwable) {
                if (playbackFailure == null) throw releaseFailure
                playbackFailure.addSuppressed(releaseFailure)
            }
        }

        return PlaybackReport(executed, dropped, stopRequested)
    }

    private companion object {
        const val PROGRESS_INTERVAL_NANOS = 50_000_000L
    }
}

package dev.gamepiano.autoplay.application

import dev.gamepiano.autoplay.input.KeyboardGateway
import dev.gamepiano.autoplay.input.MouseGateway
import dev.gamepiano.autoplay.input.Piano88MouseInputSink
import dev.gamepiano.autoplay.input.ScanCodeInputSink
import dev.gamepiano.autoplay.input.WindowClientAreaProvider
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.WindowsPlaybackHotkeyMonitor
import dev.gamepiano.autoplay.input.windows.WindowsForegroundGuard
import dev.gamepiano.autoplay.input.windows.WindowsForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.WindowsClientAreaProvider
import dev.gamepiano.autoplay.input.windows.WindowsMouseInputGateway
import dev.gamepiano.autoplay.input.windows.WindowsSendInputGateway
import dev.gamepiano.autoplay.input.windows.WindowsWindowMessageMouseGateway
import dev.gamepiano.autoplay.model.MouseInputMode
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.playback.InputSink
import dev.gamepiano.autoplay.playback.PlaybackEngine
import dev.gamepiano.autoplay.playback.PlaybackOptions
import dev.gamepiano.autoplay.playback.PlaybackReport
import dev.gamepiano.autoplay.playback.PlaybackTargetUnavailableException

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data class Countdown(val secondsRemaining: Int) : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data object WaitingForTarget : PlaybackStatus
    data class Completed(val report: PlaybackReport) : PlaybackStatus
    data object Stopped : PlaybackStatus
    data class Failed(val message: String) : PlaybackStatus
}

data class PlaybackProgress(
    val positionMicros: Long,
    val durationMicros: Long,
)

class GamePlaybackCoordinator(
    private val inspector: ForegroundWindowInspector = WindowsForegroundWindowInspector(),
    private val gatewayFactory: () -> KeyboardGateway = ::WindowsSendInputGateway,
    private val mouseGatewayFactory: () -> MouseGateway = ::WindowsMouseInputGateway,
    private val windowMessageMouseGatewayFactory: () -> MouseGateway = ::WindowsWindowMessageMouseGateway,
    private val clientAreaProviderFactory: () -> WindowClientAreaProvider = ::WindowsClientAreaProvider,
    private val hotkeyMonitorFactory: (() -> Unit, () -> Unit) -> AutoCloseable = { onStop, onTogglePause ->
        WindowsPlaybackHotkeyMonitor(
            onStop = onStop,
            onTogglePause = onTogglePause,
        ).start()
    },
) : AutoCloseable {
    private val lock = Any()

    @Volatile
    private var cancellationRequested = false

    private var playbackThread: Thread? = null
    private var engine: PlaybackEngine? = null
    private var activeSink: InputSink? = null
    private var statusCallback: ((PlaybackStatus) -> Unit)? = null
    private var targetReady: Boolean = true

    fun isRunning(): Boolean = synchronized(lock) { playbackThread?.isAlive == true }

    fun start(
        plan: SongPlan,
        target: ForegroundTarget,
        tapDurationMillis: Long,
        mouseInputMode: MouseInputMode = MouseInputMode.SEND_INPUT,
        countdownSeconds: Int = 3,
        onProgress: (PlaybackProgress) -> Unit = {},
        onStatus: (PlaybackStatus) -> Unit,
    ) {
        require(plan.playable) { "The selected MIDI file has no playable notes" }
        require(tapDurationMillis in 0..200)
        require(countdownSeconds in 0..10)

        synchronized(lock) {
            check(playbackThread?.isAlive != true) { "Playback is already running" }
            cancellationRequested = false
            targetReady = true
            statusCallback = onStatus
            val thread = Thread({
                runSession(
                    plan,
                    target,
                    tapDurationMillis,
                    mouseInputMode,
                    countdownSeconds,
                    onProgress,
                    onStatus,
                )
            }, "game-piano-playback")
            playbackThread = thread
            thread.start()
        }
    }

    fun stop() {
        cancellationRequested = true
        synchronized(lock) { engine }?.stop()
    }

    fun togglePause(): Boolean? {
        val (playbackEngine, sink, callback) = synchronized(lock) {
            Triple(engine, activeSink, statusCallback)
        }
        playbackEngine ?: return null
        val paused = playbackEngine.togglePaused() ?: return null
        if (paused) runCatching { sink?.releaseAll() }
        val ready = synchronized(lock) { targetReady }
        callback?.invoke(
            when {
                !ready -> PlaybackStatus.WaitingForTarget
                paused -> PlaybackStatus.Paused
                else -> PlaybackStatus.Playing
            },
        )
        return paused
    }

    private fun runSession(
        plan: SongPlan,
        target: ForegroundTarget,
        tapDurationMillis: Long,
        mouseInputMode: MouseInputMode,
        countdownSeconds: Int,
        onProgress: (PlaybackProgress) -> Unit,
        onStatus: (PlaybackStatus) -> Unit,
    ) {
        var hotkeyMonitor: AutoCloseable? = null
        try {
            for (second in countdownSeconds downTo 1) {
                onStatus(PlaybackStatus.Countdown(second))
                if (waitOneSecondOrCancelled()) {
                    onStatus(PlaybackStatus.Stopped)
                    return
                }
            }
            if (cancellationRequested) {
                onStatus(PlaybackStatus.Stopped)
                return
            }

            val guard = WindowsForegroundGuard(inspector, target)
            val isTargetReady = {
                inspector.current()?.let(target::matches) == true
            }
            val delegateSink: InputSink = when (plan.performanceMode) {
                PerformanceMode.KEYBOARD_23 -> ScanCodeInputSink(gatewayFactory(), guard)
                PerformanceMode.PIANO_88_MOUSE -> Piano88MouseInputSink(
                    inspector = inspector,
                    target = target,
                    gateway = when (mouseInputMode) {
                        MouseInputMode.SEND_INPUT -> mouseGatewayFactory()
                        MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL -> windowMessageMouseGatewayFactory()
                    },
                    areaProvider = clientAreaProviderFactory(),
                )
            }
            val sink = RecoverableForegroundInputSink(delegateSink, isTargetReady)
            val playbackEngine = PlaybackEngine()
            val initiallyReady = isTargetReady()
            synchronized(lock) {
                activeSink = sink
                engine = playbackEngine
                targetReady = initiallyReady
            }
            hotkeyMonitor = hotkeyMonitorFactory(::stop) { togglePause() }

            onStatus(if (initiallyReady) PlaybackStatus.Playing else PlaybackStatus.WaitingForTarget)
            val report = playbackEngine.play(
                plan.batches,
                sink,
                PlaybackOptions(tapDurationMillis = tapDurationMillis),
                durationMicros = plan.remainingDurationMicros,
                readinessProbe = isTargetReady,
                onReadinessChange = { ready ->
                    val status = synchronized(lock) {
                        targetReady = ready
                        when {
                            !ready -> PlaybackStatus.WaitingForTarget
                            playbackEngine.isPaused() -> PlaybackStatus.Paused
                            else -> PlaybackStatus.Playing
                        }
                    }
                    if (!ready) runCatching(sink::releaseAll)
                    onStatus(status)
                },
                onProgress = { relativePosition ->
                    onProgress(
                        PlaybackProgress(
                            positionMicros = (plan.playbackStartMicros + relativePosition)
                                .coerceIn(0L, plan.score.durationMicros),
                            durationMicros = plan.score.durationMicros,
                        ),
                    )
                },
            )
            onStatus(if (report.cancelled) PlaybackStatus.Stopped else PlaybackStatus.Completed(report))
        } catch (throwable: Throwable) {
            onStatus(PlaybackStatus.Failed(throwable.message ?: throwable::class.simpleName.orEmpty()))
        } finally {
            hotkeyMonitor?.close()
            runCatching { synchronized(lock) { activeSink }?.releaseAll() }
            synchronized(lock) {
                activeSink = null
                engine = null
                statusCallback = null
                targetReady = true
                playbackThread = null
            }
        }
    }

    /** Returns true when cancellation was requested during the second. */
    private fun waitOneSecondOrCancelled(): Boolean {
        repeat(20) {
            if (cancellationRequested) return true
            Thread.sleep(50)
        }
        return cancellationRequested
    }

    override fun close() {
        stop()
        val thread = synchronized(lock) { playbackThread }
        if (thread != null && thread !== Thread.currentThread()) thread.join(1_000)
        runCatching { synchronized(lock) { activeSink }?.releaseAll() }
    }
}

private class RecoverableForegroundInputSink(
    private val delegate: InputSink,
    private val targetReady: () -> Boolean,
) : InputSink {
    override fun tap(keys: List<dev.gamepiano.autoplay.model.GameKey>, holdMillis: Long) {
        try {
            delegate.tap(keys, holdMillis)
        } catch (throwable: Throwable) {
            if (!targetReady()) {
                throw PlaybackTargetUnavailableException(
                    "The target game left the foreground; playback is waiting to resume",
                    throwable,
                )
            }
            throw throwable
        }
    }

    override fun releaseAll() = delegate.releaseAll()
}

package dev.gamepiano.autoplay.application

import dev.gamepiano.autoplay.input.KeyboardGateway
import dev.gamepiano.autoplay.input.MouseGateway
import dev.gamepiano.autoplay.input.Piano88MouseInputSink
import dev.gamepiano.autoplay.input.ScanCodeInputSink
import dev.gamepiano.autoplay.input.WindowClientAreaProvider
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.WindowsEmergencyStopMonitor
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

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data class Countdown(val secondsRemaining: Int) : PlaybackStatus
    data object Playing : PlaybackStatus
    data class Completed(val report: PlaybackReport) : PlaybackStatus
    data object Stopped : PlaybackStatus
    data class Failed(val message: String) : PlaybackStatus
}

class GamePlaybackCoordinator(
    private val inspector: ForegroundWindowInspector = WindowsForegroundWindowInspector(),
    private val gatewayFactory: () -> KeyboardGateway = ::WindowsSendInputGateway,
    private val mouseGatewayFactory: () -> MouseGateway = ::WindowsMouseInputGateway,
    private val windowMessageMouseGatewayFactory: () -> MouseGateway = ::WindowsWindowMessageMouseGateway,
    private val clientAreaProviderFactory: () -> WindowClientAreaProvider = ::WindowsClientAreaProvider,
    private val emergencyStopFactory: ((() -> Unit) -> AutoCloseable) = { onStop ->
        WindowsEmergencyStopMonitor(onStop = onStop).start()
    },
) : AutoCloseable {
    private val lock = Any()

    @Volatile
    private var cancellationRequested = false

    private var playbackThread: Thread? = null
    private var engine: PlaybackEngine? = null
    private var activeSink: InputSink? = null

    fun isRunning(): Boolean = synchronized(lock) { playbackThread?.isAlive == true }

    fun start(
        plan: SongPlan,
        target: ForegroundTarget,
        tapDurationMillis: Long,
        mouseInputMode: MouseInputMode = MouseInputMode.SEND_INPUT,
        countdownSeconds: Int = 3,
        onStatus: (PlaybackStatus) -> Unit,
    ) {
        require(plan.playable) { "The selected MIDI file has no playable notes" }
        require(tapDurationMillis in 0..200)
        require(countdownSeconds in 0..10)

        synchronized(lock) {
            check(playbackThread?.isAlive != true) { "Playback is already running" }
            cancellationRequested = false
            val thread = Thread({
                runSession(plan, target, tapDurationMillis, mouseInputMode, countdownSeconds, onStatus)
            }, "game-piano-playback")
            playbackThread = thread
            thread.start()
        }
    }

    fun stop() {
        cancellationRequested = true
        synchronized(lock) { engine }?.stop()
    }

    private fun runSession(
        plan: SongPlan,
        target: ForegroundTarget,
        tapDurationMillis: Long,
        mouseInputMode: MouseInputMode,
        countdownSeconds: Int,
        onStatus: (PlaybackStatus) -> Unit,
    ) {
        var stopMonitor: AutoCloseable? = null
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
            guard.requireReady()
            val sink: InputSink = when (plan.performanceMode) {
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
            val playbackEngine = PlaybackEngine()
            synchronized(lock) {
                activeSink = sink
                engine = playbackEngine
            }
            stopMonitor = emergencyStopFactory(playbackEngine::stop)

            onStatus(PlaybackStatus.Playing)
            val report = playbackEngine.play(
                plan.batches,
                sink,
                PlaybackOptions(tapDurationMillis = tapDurationMillis),
            )
            onStatus(if (report.cancelled) PlaybackStatus.Stopped else PlaybackStatus.Completed(report))
        } catch (throwable: Throwable) {
            onStatus(PlaybackStatus.Failed(throwable.message ?: throwable::class.simpleName.orEmpty()))
        } finally {
            stopMonitor?.close()
            runCatching { synchronized(lock) { activeSink }?.releaseAll() }
            synchronized(lock) {
                activeSink = null
                engine = null
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

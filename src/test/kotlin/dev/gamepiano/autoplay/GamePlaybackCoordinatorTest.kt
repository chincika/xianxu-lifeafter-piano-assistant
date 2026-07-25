package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.application.GamePlaybackCoordinator
import dev.gamepiano.autoplay.application.PlaybackStatus
import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.input.KeyboardGateway
import dev.gamepiano.autoplay.input.KeyboardSendResult
import dev.gamepiano.autoplay.input.MouseGateway
import dev.gamepiano.autoplay.input.MouseSendResult
import dev.gamepiano.autoplay.input.ScanCodeAction
import dev.gamepiano.autoplay.input.ScanCodeStroke
import dev.gamepiano.autoplay.input.WindowClientArea
import dev.gamepiano.autoplay.input.WindowClientAreaProvider
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.MouseInputMode
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.model.Piano88Layout
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.projection.ProjectionResult
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GamePlaybackCoordinatorTest {
    @Test
    fun `F9 callback pauses and resumes the active session`() {
        var togglePause: (() -> Unit)? = null
        val statuses = CopyOnWriteArrayList<PlaybackStatus>()
        val terminal = CountDownLatch(1)
        val coordinator = GamePlaybackCoordinator(
            inspector = fixedInspector("lifeafter.exe", "明日之后"),
            gatewayFactory = {
                KeyboardGateway { strokes -> KeyboardSendResult(strokes.size, strokes.size) }
            },
            hotkeyMonitorFactory = { _, onTogglePause ->
                togglePause = onTogglePause
                AutoCloseable {}
            },
        )
        val base = playablePlan()
        val delayedPlan = base.copy(
            batches = listOf(
                base.batches.single(),
                KeyBatch(5_000_000, listOf(Piano23Layout.keyAt(1))),
            ),
        )

        coordinator.start(
            plan = delayedPlan,
            target = ForegroundTarget(processName = "lifeafter.exe", titleContains = "明日之后"),
            tapDurationMillis = 0,
            countdownSeconds = 0,
        ) { status ->
            statuses += status
            if (status is PlaybackStatus.Stopped) terminal.countDown()
        }
        assertTrue(waitUntil { statuses.any { it is PlaybackStatus.Playing } })

        requireNotNull(togglePause).invoke()
        assertTrue(waitUntil { statuses.any { it is PlaybackStatus.Paused } })
        requireNotNull(togglePause).invoke()
        assertTrue(waitUntil { statuses.count { it is PlaybackStatus.Playing } >= 2 })

        coordinator.stop()
        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        val compactStatuses = statuses.fold(mutableListOf<PlaybackStatus>()) { result, status ->
            if (result.lastOrNull()?.javaClass != status.javaClass) result += status
            result
        }
        assertEquals(4, compactStatuses.size)
        assertIs<PlaybackStatus.Playing>(compactStatuses[0])
        assertIs<PlaybackStatus.Paused>(compactStatuses[1])
        assertIs<PlaybackStatus.Playing>(compactStatuses[2])
        assertIs<PlaybackStatus.Stopped>(compactStatuses[3])
        coordinator.close()
    }

    @Test
    fun `sends a zero-duration tap only after the foreground target matches`() {
        val sent = CopyOnWriteArrayList<List<ScanCodeStroke>>()
        val gateway = KeyboardGateway { strokes ->
            sent += strokes
            KeyboardSendResult(strokes.size, strokes.size)
        }
        val coordinator = GamePlaybackCoordinator(
            inspector = fixedInspector("lifeafter.exe", "明日之后"),
            gatewayFactory = { gateway },
            hotkeyMonitorFactory = { _, _ -> AutoCloseable {} },
        )

        val statuses = runSession(coordinator)

        assertIs<PlaybackStatus.Playing>(statuses.first())
        assertIs<PlaybackStatus.Completed>(statuses.last())
        assertEquals(1, sent.size)
        assertEquals(
            listOf(ScanCodeAction.DOWN, ScanCodeAction.UP),
            sent.single().map(ScanCodeStroke::action),
        )
        coordinator.close()
    }

    @Test
    fun `waits for the target window and resumes from the same playback point`() {
        val foreground = AtomicReference(
            ForegroundWindowSnapshot(
                handle = 9,
                processId = 10,
                processName = "explorer.exe",
                title = "Documents",
            ),
        )
        val sent = CopyOnWriteArrayList<List<ScanCodeStroke>>()
        val statuses = CopyOnWriteArrayList<PlaybackStatus>()
        val progress = CopyOnWriteArrayList<Long>()
        val terminal = CountDownLatch(1)
        val coordinator = GamePlaybackCoordinator(
            inspector = ForegroundWindowInspector { foreground.get() },
            gatewayFactory = {
                KeyboardGateway { strokes ->
                    sent += strokes
                    KeyboardSendResult(strokes.size, strokes.size)
                }
            },
            hotkeyMonitorFactory = { _, _ -> AutoCloseable {} },
        )

        coordinator.start(
            plan = playablePlan(),
            target = ForegroundTarget(processName = "lifeafter.exe", titleContains = "明日之后"),
            tapDurationMillis = 0,
            countdownSeconds = 0,
            onProgress = { progress += it.positionMicros },
        ) { status ->
            statuses += status
            if (status is PlaybackStatus.Completed || status is PlaybackStatus.Failed) {
                terminal.countDown()
            }
        }

        assertTrue(waitUntil { statuses.any { it is PlaybackStatus.WaitingForTarget } })
        Thread.sleep(120)
        assertTrue(sent.isEmpty(), "No input may be sent while the game is not in the foreground")
        assertTrue(progress.maxOrNull() == 0L, "Playback progress must stay frozen while waiting")

        foreground.set(
            ForegroundWindowSnapshot(
                handle = 1,
                processId = 2,
                processName = "lifeafter.exe",
                title = "明日之后",
            ),
        )
        assertTrue(terminal.await(2, TimeUnit.SECONDS), "Playback did not resume after the game returned")
        assertTrue(statuses.any { it is PlaybackStatus.Playing })
        assertIs<PlaybackStatus.Completed>(statuses.last())
        assertEquals(1, sent.size)
        coordinator.close()
    }

    @Test
    fun `experimental mouse mode uses window messages instead of cursor gateway`() {
        var compatibilityGatewayCreated = false
        val clickedHandles = CopyOnWriteArrayList<Long>()
        val coordinator = GamePlaybackCoordinator(
            inspector = fixedInspector("lifeafter.exe", "明日之后"),
            mouseGatewayFactory = {
                compatibilityGatewayCreated = true
                MouseGateway { _, _, _ -> MouseSendResult(3, 3) }
            },
            windowMessageMouseGatewayFactory = {
                MouseGateway { handle, _, _ ->
                    clickedHandles += handle
                    MouseSendResult(2, 2)
                }
            },
            clientAreaProviderFactory = {
                WindowClientAreaProvider { WindowClientArea(0, 0, 2_048, 1_152) }
            },
            hotkeyMonitorFactory = { _, _ -> AutoCloseable {} },
        )

        val statuses = runSession(
            coordinator = coordinator,
            plan = playablePlan(PerformanceMode.PIANO_88_MOUSE),
            mouseInputMode = MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL,
        )

        assertIs<PlaybackStatus.Completed>(statuses.last())
        assertEquals(listOf(1L), clickedHandles)
        assertEquals(false, compatibilityGatewayCreated)
        coordinator.close()
    }

    private fun runSession(
        coordinator: GamePlaybackCoordinator,
        plan: SongPlan = playablePlan(),
        mouseInputMode: MouseInputMode = MouseInputMode.SEND_INPUT,
    ): List<PlaybackStatus> {
        val terminal = CountDownLatch(1)
        val statuses = CopyOnWriteArrayList<PlaybackStatus>()
        coordinator.start(
            plan = plan,
            target = ForegroundTarget(processName = "lifeafter.exe", titleContains = "明日之后"),
            tapDurationMillis = 0,
            mouseInputMode = mouseInputMode,
            countdownSeconds = 0,
        ) { status ->
            statuses += status
            if (status is PlaybackStatus.Completed ||
                status is PlaybackStatus.Stopped ||
                status is PlaybackStatus.Failed
            ) {
                terminal.countDown()
            }
        }
        assertTrue(terminal.await(2, TimeUnit.SECONDS), "Playback session did not finish")
        return statuses.toList()
    }

    private fun playablePlan(
        performanceMode: PerformanceMode = PerformanceMode.KEYBOARD_23,
    ): SongPlan = SongPlan(
        source = Path.of("test.mid"),
        range = GameToneRange.G3,
        recommendedRange = GameToneRange.G3,
        score = MidiScore("test", emptyList()),
        projection = ProjectionResult(emptyList(), foldedNotes = 0, droppedNotes = 0),
        batches = listOf(
            KeyBatch(
                0,
                listOf(
                    when (performanceMode) {
                        PerformanceMode.KEYBOARD_23 -> Piano23Layout.keyAt(0)
                        PerformanceMode.PIANO_88_MOUSE -> Piano88Layout.keyAt(0)
                    },
                ),
            ),
        ),
        performanceMode = performanceMode,
    )

    private fun fixedInspector(processName: String, title: String): ForegroundWindowInspector =
        ForegroundWindowInspector {
            ForegroundWindowSnapshot(
                handle = 1,
                processId = 2,
                processName = processName,
                title = title,
            )
        }

    private fun waitUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}

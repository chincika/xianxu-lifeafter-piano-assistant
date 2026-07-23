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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GamePlaybackCoordinatorTest {
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
            emergencyStopFactory = { AutoCloseable {} },
        )

        val statuses = runSession(coordinator)

        assertIs<PlaybackStatus.Playing>(statuses[0])
        assertIs<PlaybackStatus.Completed>(statuses[1])
        assertEquals(1, sent.size)
        assertEquals(
            listOf(ScanCodeAction.DOWN, ScanCodeAction.UP),
            sent.single().map(ScanCodeStroke::action),
        )
        coordinator.close()
    }

    @Test
    fun `rejects playback when another window is in the foreground`() {
        var gatewayCreated = false
        val coordinator = GamePlaybackCoordinator(
            inspector = fixedInspector("explorer.exe", "Documents"),
            gatewayFactory = {
                gatewayCreated = true
                KeyboardGateway { strokes -> KeyboardSendResult(strokes.size, strokes.size) }
            },
            emergencyStopFactory = { AutoCloseable {} },
        )

        val statuses = runSession(coordinator)

        val failure = assertIs<PlaybackStatus.Failed>(statuses.single())
        assertTrue(failure.message.contains("Foreground window mismatch"))
        assertEquals(false, gatewayCreated)
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
            emergencyStopFactory = { AutoCloseable {} },
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
}

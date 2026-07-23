package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.application.GamePlaybackCoordinator
import dev.gamepiano.autoplay.application.PlaybackStatus
import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.input.KeyboardGateway
import dev.gamepiano.autoplay.input.KeyboardSendResult
import dev.gamepiano.autoplay.input.ScanCodeAction
import dev.gamepiano.autoplay.input.ScanCodeStroke
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.Piano23Layout
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

    private fun runSession(coordinator: GamePlaybackCoordinator): List<PlaybackStatus> {
        val terminal = CountDownLatch(1)
        val statuses = CopyOnWriteArrayList<PlaybackStatus>()
        coordinator.start(
            plan = playablePlan(),
            target = ForegroundTarget(processName = "lifeafter.exe", titleContains = "明日之后"),
            tapDurationMillis = 0,
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

    private fun playablePlan(): SongPlan = SongPlan(
        source = Path.of("test.mid"),
        range = GameToneRange.G3,
        recommendedRange = GameToneRange.G3,
        score = MidiScore("test", emptyList()),
        projection = ProjectionResult(emptyList(), foldedNotes = 0, droppedNotes = 0),
        batches = listOf(KeyBatch(0, listOf(Piano23Layout.keyAt(0)))),
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

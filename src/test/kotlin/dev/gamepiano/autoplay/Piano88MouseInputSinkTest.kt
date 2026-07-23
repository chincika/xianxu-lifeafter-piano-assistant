package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.input.MouseGateway
import dev.gamepiano.autoplay.input.MouseSendResult
import dev.gamepiano.autoplay.input.Piano88MouseGeometry
import dev.gamepiano.autoplay.input.Piano88MouseInputSink
import dev.gamepiano.autoplay.input.ScreenPoint
import dev.gamepiano.autoplay.input.WindowClientArea
import dev.gamepiano.autoplay.input.WindowClientAreaProvider
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.model.Piano88Layout
import kotlin.test.Test
import kotlin.test.assertEquals

class Piano88MouseInputSinkTest {
    private val fullHdReference = WindowClientArea(left = 0, top = 0, width = 2_048, height = 1_152)

    @Test
    fun `standard piano geometry matches the 88 key game surface`() {
        assertEquals(ScreenPoint(20, 1_081), Piano88MouseGeometry.pointForKey(0, fullHdReference))
        assertEquals(ScreenPoint(36, 930), Piano88MouseGeometry.pointForKey(1, fullHdReference))
        assertEquals(ScreenPoint(2_028, 1_081), Piano88MouseGeometry.pointForKey(87, fullHdReference))
    }

    @Test
    fun `mouse sink clicks each chord note only while target is foreground`() {
        val clicked = mutableListOf<ScreenPoint>()
        val sink = Piano88MouseInputSink(
            inspector = fixedInspector(),
            target = ForegroundTarget(processName = "lifeafter.exe"),
            gateway = MouseGateway { point ->
                clicked += point
                MouseSendResult(sent = 3, expected = 3)
            },
            areaProvider = WindowClientAreaProvider { handle ->
                assertEquals(42L, handle)
                fullHdReference
            },
        )

        sink.tap(listOf(Piano88Layout.keyAt(0), Piano88Layout.keyAt(1)), holdMillis = 0)

        assertEquals(listOf(ScreenPoint(20, 1_081), ScreenPoint(36, 930)), clicked)
    }

    private fun fixedInspector(): ForegroundWindowInspector = ForegroundWindowInspector {
        ForegroundWindowSnapshot(
            handle = 42,
            processId = 7,
            processName = "lifeafter.exe",
            title = "明日之后",
        )
    }
}

package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.input.ScreenPoint
import dev.gamepiano.autoplay.input.WindowClientArea
import dev.gamepiano.autoplay.input.windows.ClientPoint
import dev.gamepiano.autoplay.input.windows.WindowMessageMouseCoordinates
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowMessageMouseCoordinatesTest {
    @Test
    fun `converts physical screen point to target client coordinates`() {
        val area = WindowClientArea(left = 300, top = 120, width = 2_048, height = 1_152)

        assertEquals(
            ClientPoint(1_024, 900),
            WindowMessageMouseCoordinates.toClient(ScreenPoint(1_324, 1_020), area),
        )
    }

    @Test
    fun `packs client coordinates into mouse message lParam`() {
        val packed = WindowMessageMouseCoordinates.pack(ClientPoint(0x1234, 0x5678))

        assertEquals(0x56781234L, packed)
    }

    @Test
    fun `clamps target point to the client area`() {
        val area = WindowClientArea(left = 100, top = 200, width = 800, height = 600)

        assertEquals(
            ClientPoint(799, 0),
            WindowMessageMouseCoordinates.toClient(ScreenPoint(10_000, -10), area),
        )
    }
}

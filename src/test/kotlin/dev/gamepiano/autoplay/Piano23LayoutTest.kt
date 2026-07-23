package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.model.Piano23Layout
import kotlin.test.Test
import kotlin.test.assertEquals

class Piano23LayoutTest {
    @Test
    fun `layout matches the screenshot from left to right`() {
        assertEquals(
            listOf("1", "Q", "2", "W", "3", "E", "R", "5", "T", "6", "Y", "U", "7", "I", "8", "O", "9", "P", "[", "-", "]", "=", "/"),
            Piano23Layout.keys.map { it.keyboardLabel },
        )
    }
}

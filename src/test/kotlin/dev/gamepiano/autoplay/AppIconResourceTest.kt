package dev.gamepiano.autoplay

import kotlin.test.Test
import kotlin.test.assertNotNull

class AppIconResourceTest {
    @Test
    fun `window and package icons are bundled`() {
        val loader = requireNotNull(javaClass.classLoader)
        assertNotNull(loader.getResource("icons/xianxu-16.png"))
        assertNotNull(loader.getResource("icons/xianxu-256.png"))
        assertNotNull(loader.getResource("icons/xianxu.ico"))
    }
}

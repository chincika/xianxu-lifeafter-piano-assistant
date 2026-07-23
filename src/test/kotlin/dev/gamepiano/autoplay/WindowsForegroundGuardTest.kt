package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.input.KeyboardInjectionException
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.input.windows.WindowsForegroundGuard
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WindowsForegroundGuardTest {
    private val gameWindow = ForegroundWindowSnapshot(
        handle = 123,
        processId = 456,
        processName = "ExampleGame.exe",
        title = "Example Game - Piano",
    )

    @Test
    fun `process and title constraints are case insensitive and both must match`() {
        val target = ForegroundTarget("examplegame.EXE", "game - piano")

        assertTrue(target.matches(gameWindow))
    }

    @Test
    fun `guard rejects a different foreground process`() {
        val guard = WindowsForegroundGuard(
            inspector = { gameWindow.copy(processName = "Codex.exe") },
            target = ForegroundTarget(processName = "ExampleGame.exe"),
        )

        assertFailsWith<KeyboardInjectionException> {
            guard.requireReady()
        }
    }
}

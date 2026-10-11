package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.input.KeyboardInjectionException
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.input.windows.WindowsForegroundGuard
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains

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

    @Test
    fun `copied executable paths quotes and omitted exe suffix match the same process`() {
        listOf(" ExampleGame ", "\"C:\\Games\\ExampleGame.EXE\"", "'ExampleGame.exe'").forEach { name ->
            assertTrue(ForegroundTarget(processName = name).matches(gameWindow), name)
        }
    }

    @Test
    fun `unknown process never matches a required process even when title matches`() {
        val target = ForegroundTarget("ExampleGame.exe", "Example Game")

        assertFalse(target.matches(gameWindow.copy(processName = null)))
        assertFalse(target.matches(gameWindow.copy(processName = "")))
        assertFalse(target.matches(gameWindow.copy(processName = "ExampleGameHelper.exe")))
    }

    @Test
    fun `matching process still requires configured title`() {
        assertFalse(ForegroundTarget("ExampleGame.exe", "Different Game").matches(gameWindow))
    }

    @Test
    fun `explicit title only target remains supported`() {
        assertTrue(ForegroundTarget(titleContains = "Example Game").matches(gameWindow.copy(processName = null)))
    }

    @Test
    fun `both LifeAfter client names work without changing the configured known name`() {
        listOf("lifeafter.exe", "mingrizhihou.exe", "MINGRIZHIHOU", "\"E:\\Games\\mingrizhihou.exe\"").forEach { configured ->
            val target = ForegroundTarget(processName = configured)
            listOf("lifeafter.exe", "mingrizhihou.exe", "MINGRIZHIHOU.EXE").forEach { actual ->
                assertTrue(target.matches(gameWindow.copy(processName = actual)), "$configured -> $actual")
            }
        }
    }

    @Test
    fun `LifeAfter aliases do not match launchers other processes or unknown identity`() {
        val target = ForegroundTarget(processName = "lifeafter.exe")
        listOf("FeverGamesLauncher.exe", "mrzh_launcher.exe", "mingrizhihou_helper.exe", "Codex.exe", "", null).forEach { actual ->
            assertFalse(target.matches(gameWindow.copy(processName = actual)), "Unexpected match: $actual")
        }
    }

    @Test
    fun `custom targets stay exact and LifeAfter aliases still require configured title`() {
        val newClient = gameWindow.copy(processName = "mingrizhihou.exe", title = "明日之后")

        assertFalse(ForegroundTarget(processName = "CustomGame.exe").matches(newClient))
        assertTrue(ForegroundTarget("lifeafter.exe", "明日之后").matches(newClient))
        assertFalse(ForegroundTarget("lifeafter.exe", "Different Game").matches(newClient))
        assertContains(ForegroundTarget("lifeafter.exe").description(), "mingrizhihou.exe")
    }
}

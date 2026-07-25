package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32

class WindowsPlaybackHotkeyMonitor(
    private val onStop: () -> Unit,
    private val onTogglePause: () -> Unit,
) : AutoCloseable {
    @Volatile
    private var running: Boolean = true

    private val thread = Thread(::poll, "game-piano-playback-hotkeys").apply {
        isDaemon = true
    }

    fun start(): WindowsPlaybackHotkeyMonitor = apply {
        check(Platform.isWindows()) { "Playback hotkey monitor requires Windows" }
        thread.start()
    }

    private fun poll() {
        var stopArmed = false
        var pauseArmed = false
        while (running && !Thread.currentThread().isInterrupted) {
            val stopPressed = isPressed(VK_F8)
            val pausePressed = isPressed(VK_F9)

            if (!stopPressed) stopArmed = true
            if (!pausePressed) pauseArmed = true

            if (stopArmed && stopPressed) {
                stopArmed = false
                onStop()
            }
            if (pauseArmed && pausePressed) {
                pauseArmed = false
                onTogglePause()
            }

            try {
                Thread.sleep(POLL_INTERVAL_MILLIS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun isPressed(virtualKey: Int): Boolean =
        User32.INSTANCE.GetAsyncKeyState(virtualKey).toInt() and KEY_DOWN_MASK != 0

    override fun close() {
        running = false
        thread.interrupt()
        if (Thread.currentThread() !== thread) thread.join(500)
    }

    companion object {
        const val VK_F8: Int = 0x77
        const val VK_F9: Int = 0x78
        private const val KEY_DOWN_MASK = 0x8000
        private const val POLL_INTERVAL_MILLIS = 20L
    }
}

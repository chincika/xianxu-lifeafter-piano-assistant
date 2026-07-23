package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32

class WindowsEmergencyStopMonitor(
    private val virtualKey: Int = VK_F8,
    private val onStop: () -> Unit,
) : AutoCloseable {
    @Volatile
    private var running: Boolean = true

    private val thread = Thread(::poll, "game-piano-emergency-stop").apply {
        isDaemon = true
    }

    fun start(): WindowsEmergencyStopMonitor = apply {
        check(Platform.isWindows()) { "Emergency stop monitor requires Windows" }
        thread.start()
    }

    private fun poll() {
        var armed = false
        while (running && !Thread.currentThread().isInterrupted) {
            val pressed = User32.INSTANCE.GetAsyncKeyState(virtualKey).toInt() and KEY_DOWN_MASK != 0
            if (!pressed) armed = true
            if (armed && pressed) {
                onStop()
                return
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    override fun close() {
        running = false
        thread.interrupt()
        if (Thread.currentThread() !== thread) thread.join(500)
    }

    companion object {
        const val VK_F8: Int = 0x77
        private const val KEY_DOWN_MASK = 0x8000
        private const val POLL_INTERVAL_MILLIS = 20L
    }
}

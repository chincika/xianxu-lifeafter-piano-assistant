package dev.gamepiano.autoplay.input

import dev.gamepiano.autoplay.model.GameKey
import dev.gamepiano.autoplay.diagnostics.AppDiagnostics
import dev.gamepiano.autoplay.playback.InputSink

enum class ScanCodeAction {
    DOWN,
    UP,
}

data class ScanCodeStroke(
    val scanCode: Int,
    val action: ScanCodeAction,
    val extended: Boolean = false,
) {
    init {
        require(scanCode in 1..0xFF) { "Windows scan code must be in 1..255: $scanCode" }
    }
}

data class KeyboardSendResult(
    val sent: Int,
    val expected: Int,
    val nativeErrorCode: Int? = null,
) {
    val successful: Boolean = sent == expected
}

fun interface KeyboardGateway {
    fun send(strokes: List<ScanCodeStroke>): KeyboardSendResult
}

fun interface PlaybackInputGuard {
    fun requireReady()
}

fun interface MillisSleeper {
    fun sleep(millis: Long)
}

class KeyboardInjectionException(message: String) : IllegalStateException(message)

/**
 * Converts game keys to scan-code DOWN/UP batches. The platform gateway and
 * foreground guard are injected so the safety behavior can be tested without
 * sending real keyboard input.
 */
class ScanCodeInputSink(
    private val gateway: KeyboardGateway,
    private val guard: PlaybackInputGuard,
    private val sleeper: MillisSleeper = MillisSleeper(Thread::sleep),
) : InputSink {
    private val lock = Any()
    private val heldKeys = linkedMapOf<Int, GameKey>()

    override fun tap(keys: List<GameKey>, holdMillis: Long) {
        require(holdMillis in 0..200)
        val uniqueKeys = keys.distinctBy(GameKey::windowsScanCode)
        if (uniqueKeys.isEmpty()) return

        synchronized(lock) {
            guard.requireReady()
            uniqueKeys.forEach { heldKeys[it.windowsScanCode] = it }

            if (holdMillis == 0L) {
                sendImmediateTap(uniqueKeys)
                return
            }

            var failure: Throwable? = null
            try {
                sendOrThrow(uniqueKeys.toStrokes(ScanCodeAction.DOWN))
                sleeper.sleep(holdMillis)
            } catch (throwable: Throwable) {
                failure = throwable
            } finally {
                val releaseFailure = runCatching {
                    sendOrThrow(uniqueKeys.toStrokes(ScanCodeAction.UP))
                }.exceptionOrNull()
                if (releaseFailure == null) {
                    uniqueKeys.forEach { heldKeys.remove(it.windowsScanCode) }
                }

                if (releaseFailure != null) {
                    if (failure == null) failure = releaseFailure else failure.addSuppressed(releaseFailure)
                }
            }

            failure?.let { throw it }
        }
    }

    private fun sendImmediateTap(keys: List<GameKey>) {
        var failure: Throwable? = null
        try {
            sendOrThrow(
                keys.toStrokes(ScanCodeAction.DOWN) + keys.toStrokes(ScanCodeAction.UP),
            )
        } catch (throwable: Throwable) {
            failure = throwable
        }

        if (failure == null) {
            keys.forEach { heldKeys.remove(it.windowsScanCode) }
            return
        }
        val primaryFailure = failure

        val releaseFailure = runCatching {
            sendOrThrow(keys.toStrokes(ScanCodeAction.UP))
        }.exceptionOrNull()
        if (releaseFailure == null) {
            keys.forEach { heldKeys.remove(it.windowsScanCode) }
        } else {
            primaryFailure.addSuppressed(releaseFailure)
        }
        throw primaryFailure
    }

    override fun releaseAll() {
        synchronized(lock) {
            if (heldKeys.isEmpty()) return
            val keys = heldKeys.values.toList()
            sendOrThrow(keys.toStrokes(ScanCodeAction.UP))
            heldKeys.clear()
        }
    }

    private fun sendOrThrow(strokes: List<ScanCodeStroke>) {
        val result = gateway.send(strokes)
        AppDiagnostics.event("input.keyboard_send", mapOf(
            "strokes" to strokes, "sent" to result.sent, "expected" to result.expected,
            "win32Error" to result.nativeErrorCode,
        ))
        if (!result.successful) {
            val error = result.nativeErrorCode?.let { ", Win32 error=$it" }.orEmpty()
            throw KeyboardInjectionException(
                "Only ${result.sent}/${result.expected} keyboard inputs were sent$error",
            )
        }
    }

    private fun List<GameKey>.toStrokes(action: ScanCodeAction): List<ScanCodeStroke> =
        map { key -> ScanCodeStroke(key.windowsScanCode, action) }
}

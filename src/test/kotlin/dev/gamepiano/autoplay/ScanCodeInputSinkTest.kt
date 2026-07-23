package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.input.KeyboardGateway
import dev.gamepiano.autoplay.input.KeyboardInjectionException
import dev.gamepiano.autoplay.input.KeyboardSendResult
import dev.gamepiano.autoplay.input.MillisSleeper
import dev.gamepiano.autoplay.input.PlaybackInputGuard
import dev.gamepiano.autoplay.input.ScanCodeAction
import dev.gamepiano.autoplay.input.ScanCodeInputSink
import dev.gamepiano.autoplay.input.ScanCodeStroke
import dev.gamepiano.autoplay.model.Piano23Layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScanCodeInputSinkTest {
    @Test
    fun `zero duration tap submits chord down and up in one SendInput batch`() {
        val gateway = RecordingGateway()
        var sleeperCalled = false
        val sink = ScanCodeInputSink(
            gateway,
            PlaybackInputGuard {},
            MillisSleeper { sleeperCalled = true },
        )

        sink.tap(listOf(Piano23Layout.keyAt(6), Piano23Layout.keyAt(10)), 0)

        assertEquals(false, sleeperCalled)
        assertEquals(1, gateway.calls.size)
        assertEquals(
            listOf(ScanCodeAction.DOWN, ScanCodeAction.DOWN, ScanCodeAction.UP, ScanCodeAction.UP),
            gateway.calls.single().map(ScanCodeStroke::action),
        )
    }

    @Test
    fun `a chord is sent as one down batch followed by one up batch`() {
        val gateway = RecordingGateway()
        var sleptMillis = 0L
        val sink = ScanCodeInputSink(
            gateway,
            PlaybackInputGuard {},
            MillisSleeper { sleptMillis = it },
        )

        sink.tap(listOf(Piano23Layout.keyAt(6), Piano23Layout.keyAt(10)), 35)

        assertEquals(35, sleptMillis)
        assertEquals(2, gateway.calls.size)
        assertEquals(listOf(0x13, 0x15), gateway.calls[0].map(ScanCodeStroke::scanCode))
        assertEquals(listOf(ScanCodeAction.DOWN, ScanCodeAction.DOWN), gateway.calls[0].map(ScanCodeStroke::action))
        assertEquals(listOf(ScanCodeAction.UP, ScanCodeAction.UP), gateway.calls[1].map(ScanCodeStroke::action))
    }

    @Test
    fun `an attempted press is followed by a release even when SendInput is partial`() {
        var call = 0
        val calls = mutableListOf<List<ScanCodeStroke>>()
        val gateway = KeyboardGateway { strokes ->
            calls += strokes
            call += 1
            if (call == 1) KeyboardSendResult(0, strokes.size, 5)
            else KeyboardSendResult(strokes.size, strokes.size)
        }
        val sink = ScanCodeInputSink(gateway, PlaybackInputGuard {}, MillisSleeper {})

        assertFailsWith<KeyboardInjectionException> {
            sink.tap(listOf(Piano23Layout.keyAt(6)), 35)
        }

        assertEquals(2, calls.size)
        assertEquals(ScanCodeAction.DOWN, calls[0].single().action)
        assertEquals(ScanCodeAction.UP, calls[1].single().action)
    }

    @Test
    fun `foreground guard runs before any scan code is sent`() {
        val gateway = RecordingGateway()
        val sink = ScanCodeInputSink(
            gateway,
            PlaybackInputGuard { throw KeyboardInjectionException("wrong window") },
            MillisSleeper {},
        )

        assertFailsWith<KeyboardInjectionException> {
            sink.tap(listOf(Piano23Layout.keyAt(6)), 35)
        }
        assertEquals(0, gateway.calls.size)
    }

    @Test
    fun `releaseAll retries keys whose first release batch failed`() {
        var call = 0
        val calls = mutableListOf<List<ScanCodeStroke>>()
        val gateway = KeyboardGateway { strokes ->
            calls += strokes
            call += 1
            if (call == 2) KeyboardSendResult(0, strokes.size, 5)
            else KeyboardSendResult(strokes.size, strokes.size)
        }
        val sink = ScanCodeInputSink(gateway, PlaybackInputGuard {}, MillisSleeper {})

        assertFailsWith<KeyboardInjectionException> {
            sink.tap(listOf(Piano23Layout.keyAt(6)), 35)
        }
        sink.releaseAll()

        assertEquals(3, calls.size)
        assertEquals(ScanCodeAction.UP, calls[1].single().action)
        assertEquals(ScanCodeAction.UP, calls[2].single().action)
    }

    private class RecordingGateway : KeyboardGateway {
        val calls = mutableListOf<List<ScanCodeStroke>>()

        override fun send(strokes: List<ScanCodeStroke>): KeyboardSendResult {
            calls += strokes
            return KeyboardSendResult(strokes.size, strokes.size)
        }
    }
}

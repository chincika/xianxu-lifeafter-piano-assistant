package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.preview.MidiPreviewPlayer
import dev.gamepiano.autoplay.preview.PreviewTransport
import javax.sound.midi.Sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MidiPreviewPlayerTest {
    @Test
    fun `preview transport can play pause seek and stop`() {
        val fake = FakeTransport(durationMicros = 10_000_000)
        val player = MidiPreviewPlayer { fake }

        player.load(Sequence(Sequence.PPQ, 480))
        player.seek(12_000_000)
        assertEquals(10_000_000, player.snapshot().positionMicros)

        player.play()
        assertTrue(player.snapshot().playing)
        assertEquals(0, player.snapshot().positionMicros)

        fake.positionMicros = 4_000_000
        player.pause()
        assertFalse(player.snapshot().playing)
        assertEquals(4_000_000, player.snapshot().positionMicros)

        player.stop()
        assertEquals(0, player.snapshot().positionMicros)
        player.close()
        assertTrue(fake.closed)
    }

    private class FakeTransport(
        override val durationMicros: Long,
    ) : PreviewTransport {
        override var playing: Boolean = false
        override var positionMicros: Long = 0
        var closed = false

        override fun play() {
            playing = true
        }

        override fun pause() {
            playing = false
        }

        override fun seek(positionMicros: Long) {
            this.positionMicros = positionMicros
        }

        override fun close() {
            closed = true
        }
    }
}

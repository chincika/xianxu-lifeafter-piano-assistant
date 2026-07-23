package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.midi.MidiFileReader
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class MidiFileReaderTest {
    @Test
    fun `PPQ tempo changes are reflected in note timing`() {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(tempo(0, 500_000))
        track.add(noteOn(0, 60))
        track.add(noteOff(480, 60))
        track.add(tempo(480, 1_000_000))
        track.add(noteOn(480, 62))
        track.add(noteOff(960, 62))

        val score = MidiFileReader.parse(sequence, "tempo-test")

        assertEquals(2, score.notes.size)
        assertEquals(0, score.notes[0].startMicros)
        assertEquals(500_000, score.notes[0].durationMicros)
        assertEquals(500_000, score.notes[1].startMicros)
        assertEquals(1_000_000, score.notes[1].durationMicros)
    }

    private fun tempo(tick: Long, microsPerQuarter: Int): MidiEvent {
        val data = byteArrayOf(
            (microsPerQuarter shr 16).toByte(),
            (microsPerQuarter shr 8).toByte(),
            microsPerQuarter.toByte(),
        )
        return MidiEvent(MetaMessage().apply { setMessage(0x51, data, data.size) }, tick)
    }

    private fun noteOn(tick: Long, note: Int): MidiEvent =
        MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note, 100), tick)

    private fun noteOff(tick: Long, note: Int): MidiEvent =
        MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), tick)
}

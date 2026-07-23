package dev.gamepiano.autoplay.midi

import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

object ExampleMidiGenerator {
    private const val TICKS_PER_QUARTER = 480
    private const val NOTE_RELEASE_GAP_TICKS = 30L

    /**
     * Writes a 120 BPM, C-major rendition of "Twinkle, Twinkle, Little Star".
     * All pitches stay inside C4..A4 so the provisional G3 calibration can play it directly.
     */
    fun writeTwinkleTwinkle(path: Path): Path {
        path.parent?.let(Files::createDirectories)

        val sequence = Sequence(Sequence.PPQ, TICKS_PER_QUARTER)
        val track = sequence.createTrack()
        track.add(trackName("Twinkle Twinkle Little Star"))
        track.add(tempo(microsPerQuarter = 500_000))
        track.add(timeSignature())

        var tick = 0L
        twinkleMelody.forEach { note ->
            val durationTicks = note.beats * TICKS_PER_QUARTER
            track.add(noteMessage(ShortMessage.NOTE_ON, tick, note.midiNote, velocity = 92))
            track.add(
                noteMessage(
                    ShortMessage.NOTE_OFF,
                    tick + durationTicks - NOTE_RELEASE_GAP_TICKS,
                    note.midiNote,
                    velocity = 0,
                ),
            )
            tick += durationTicks
        }

        check(MidiSystem.write(sequence, 1, path.toFile()) > 0) {
            "The installed MIDI system could not write a Standard MIDI File"
        }
        return path.toAbsolutePath().normalize()
    }

    private data class MelodyNote(val midiNote: Int, val beats: Long)

    private val twinkleMelody = buildList {
        phrase(C4, C4, G4, G4, A4, A4, ending = G4)
        phrase(F4, F4, E4, E4, D4, D4, ending = C4)
        repeat(2) {
            phrase(G4, G4, F4, F4, E4, E4, ending = D4)
        }
        phrase(C4, C4, G4, G4, A4, A4, ending = G4)
        phrase(F4, F4, E4, E4, D4, D4, ending = C4)
    }

    private fun MutableList<MelodyNote>.phrase(
        first: Int,
        second: Int,
        third: Int,
        fourth: Int,
        fifth: Int,
        sixth: Int,
        ending: Int,
    ) {
        addAll(listOf(first, second, third, fourth, fifth, sixth).map { MelodyNote(it, 1) })
        add(MelodyNote(ending, 2))
    }

    private fun trackName(name: String): MidiEvent {
        val bytes = name.toByteArray(Charsets.UTF_8)
        return MidiEvent(MetaMessage().apply { setMessage(0x03, bytes, bytes.size) }, 0)
    }

    private fun tempo(microsPerQuarter: Int): MidiEvent {
        val bytes = byteArrayOf(
            (microsPerQuarter shr 16).toByte(),
            (microsPerQuarter shr 8).toByte(),
            microsPerQuarter.toByte(),
        )
        return MidiEvent(MetaMessage().apply { setMessage(0x51, bytes, bytes.size) }, 0)
    }

    private fun timeSignature(): MidiEvent {
        val bytes = byteArrayOf(4, 2, 24, 8)
        return MidiEvent(MetaMessage().apply { setMessage(0x58, bytes, bytes.size) }, 0)
    }

    private fun noteMessage(command: Int, tick: Long, note: Int, velocity: Int): MidiEvent =
        MidiEvent(ShortMessage(command, 0, note, velocity), tick)

    private const val C4 = 60
    private const val D4 = 62
    private const val E4 = 64
    private const val F4 = 65
    private const val G4 = 67
    private const val A4 = 69
}

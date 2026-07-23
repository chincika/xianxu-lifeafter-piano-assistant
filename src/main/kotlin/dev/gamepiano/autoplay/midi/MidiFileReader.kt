package dev.gamepiano.autoplay.midi

import dev.gamepiano.autoplay.model.MidiScore
import dev.gamepiano.autoplay.model.ScoreNote
import java.nio.file.Path
import java.util.ArrayDeque
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

object MidiFileReader {
    fun read(path: Path): MidiScore = parse(MidiSystem.getSequence(path.toFile()), path.fileName.toString())

    fun parse(sequence: Sequence, fallbackName: String = "Untitled"): MidiScore {
        val timeline = TickTimeline(sequence)
        val notes = mutableListOf<ScoreNote>()
        var discoveredName: String? = null

        sequence.tracks.forEachIndexed { trackIndex, track ->
            val openNotes = Array(16) { Array(128) { ArrayDeque<OpenNote>() } }
            for (eventIndex in 0 until track.size()) {
                val event = track[eventIndex]
                when (val message = event.message) {
                    is MetaMessage -> {
                        if (message.type == TRACK_NAME && discoveredName == null) {
                            discoveredName = message.data.toString(Charsets.UTF_8).trim().takeIf(String::isNotEmpty)
                        }
                    }
                    is ShortMessage -> {
                        val channel = message.channel
                        val note = message.data1
                        when {
                            message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> {
                                openNotes[channel][note].addLast(OpenNote(event.tick, message.data2))
                            }
                            message.command == ShortMessage.NOTE_OFF ||
                                (message.command == ShortMessage.NOTE_ON && message.data2 == 0) -> {
                                val started = openNotes[channel][note].pollFirst() ?: continue
                                notes += started.toScoreNote(event.tick, note, channel, trackIndex, timeline)
                            }
                        }
                    }
                }
            }

            val closingTick = maxOf(sequence.tickLength, 1L)
            for (channel in openNotes.indices) {
                for (note in openNotes[channel].indices) {
                    while (openNotes[channel][note].isNotEmpty()) {
                        val started = openNotes[channel][note].removeFirst()
                        notes += started.toScoreNote(closingTick, note, channel, trackIndex, timeline)
                    }
                }
            }
        }

        return MidiScore(
            name = discoveredName ?: fallbackName,
            notes = notes.sortedWith(compareBy(ScoreNote::startMicros, ScoreNote::track, ScoreNote::midiNote)),
        )
    }

    private data class OpenNote(val tick: Long, val velocity: Int) {
        fun toScoreNote(
            endTick: Long,
            note: Int,
            channel: Int,
            track: Int,
            timeline: TickTimeline,
        ): ScoreNote {
            val start = timeline.toMicros(tick)
            val end = timeline.toMicros(maxOf(endTick, tick))
            return ScoreNote(
                startMicros = start,
                durationMicros = maxOf(end - start, 0),
                midiNote = note,
                velocity = velocity,
                channel = channel,
                track = track,
            )
        }
    }

    private class TickTimeline(private val sequence: Sequence) {
        private val tempoPoints: List<TempoPoint> = collectTempoPoints(sequence)

        fun toMicros(tick: Long): Long {
            if (sequence.divisionType != Sequence.PPQ) {
                val framesPerSecond = when (sequence.divisionType) {
                    Sequence.SMPTE_24 -> 24.0
                    Sequence.SMPTE_25 -> 25.0
                    Sequence.SMPTE_30DROP -> 29.97
                    Sequence.SMPTE_30 -> 30.0
                    else -> error("Unsupported MIDI division type: ${sequence.divisionType}")
                }
                return (tick * 1_000_000.0 / (framesPerSecond * sequence.resolution)).toLong()
            }

            var previousTick = 0L
            var elapsedMicros = 0L
            var microsPerQuarter = DEFAULT_TEMPO_MICROS_PER_QUARTER
            for (point in tempoPoints) {
                if (point.tick > tick) break
                elapsedMicros += ticksToMicros(point.tick - previousTick, microsPerQuarter, sequence.resolution)
                previousTick = point.tick
                microsPerQuarter = point.microsPerQuarter
            }
            return elapsedMicros + ticksToMicros(tick - previousTick, microsPerQuarter, sequence.resolution)
        }
    }

    private data class TempoPoint(val tick: Long, val microsPerQuarter: Int)

    private fun collectTempoPoints(sequence: Sequence): List<TempoPoint> {
        val points = mutableListOf<TempoPoint>()
        sequence.tracks.forEach { track ->
            for (index in 0 until track.size()) {
                val event = track[index]
                val message = event.message as? MetaMessage ?: continue
                if (message.type != SET_TEMPO || message.data.size < 3) continue
                val tempo = ((message.data[0].toInt() and 0xFF) shl 16) or
                    ((message.data[1].toInt() and 0xFF) shl 8) or
                    (message.data[2].toInt() and 0xFF)
                points += TempoPoint(event.tick, tempo)
            }
        }
        return points
            .sortedBy(TempoPoint::tick)
            .groupBy(TempoPoint::tick)
            .map { (_, values) -> values.last() }
    }

    private fun ticksToMicros(ticks: Long, microsPerQuarter: Int, resolution: Int): Long =
        ticks * microsPerQuarter / resolution

    private const val TRACK_NAME = 0x03
    private const val SET_TEMPO = 0x51
    private const val DEFAULT_TEMPO_MICROS_PER_QUARTER = 500_000
}

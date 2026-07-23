package dev.gamepiano.autoplay.preview

import java.nio.file.Path
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.Sequencer
import javax.sound.midi.Synthesizer

data class PreviewSnapshot(
    val loaded: Boolean,
    val playing: Boolean,
    val positionMicros: Long,
    val durationMicros: Long,
)

internal interface PreviewTransport : AutoCloseable {
    val playing: Boolean
    val positionMicros: Long
    val durationMicros: Long

    fun play()
    fun pause()
    fun seek(positionMicros: Long)
}

/** Local Java Sound player used only for previewing a MIDI file through the computer speakers. */
class MidiPreviewPlayer internal constructor(
    private val transportFactory: (Sequence) -> PreviewTransport,
) : AutoCloseable {
    constructor() : this(::JavaSoundPreviewTransport)

    private val lock = Any()
    private var transport: PreviewTransport? = null

    fun load(path: Path) = load(MidiSystem.getSequence(path.toFile()))

    fun load(sequence: Sequence) {
        val next = transportFactory(sequence)
        synchronized(lock) {
            transport?.close()
            transport = next
        }
    }

    fun play() = synchronized(lock) {
        transport?.let { current ->
            if (current.positionMicros >= current.durationMicros) current.seek(0)
            current.play()
        }
    }

    fun pause() = synchronized(lock) { transport?.pause() }

    fun stop() = synchronized(lock) {
        transport?.run {
            pause()
            seek(0)
        }
    }

    fun seek(positionMicros: Long) = synchronized(lock) {
        transport?.let { current ->
            current.seek(positionMicros.coerceIn(0L, current.durationMicros))
        }
    }

    fun snapshot(): PreviewSnapshot = synchronized(lock) {
        transport?.let { current ->
            PreviewSnapshot(
                loaded = true,
                playing = current.playing,
                positionMicros = current.positionMicros.coerceIn(0L, current.durationMicros),
                durationMicros = current.durationMicros,
            )
        } ?: PreviewSnapshot(false, false, 0, 0)
    }

    override fun close() = synchronized(lock) {
        transport?.close()
        transport = null
    }
}

private class JavaSoundPreviewTransport(sequence: Sequence) : PreviewTransport {
    private val sequencer: Sequencer
    private val synthesizer: Synthesizer

    init {
        val nextSequencer = MidiSystem.getSequencer(false)
            ?: error("当前 Java 运行环境没有 MIDI 音序器")
        val nextSynthesizer = MidiSystem.getSynthesizer()
        try {
            nextSequencer.open()
            nextSynthesizer.open()
            nextSequencer.transmitter.receiver = nextSynthesizer.receiver
            nextSequencer.sequence = sequence
        } catch (throwable: Throwable) {
            runCatching { nextSequencer.close() }
            runCatching { nextSynthesizer.close() }
            throw throwable
        }
        sequencer = nextSequencer
        synthesizer = nextSynthesizer
    }

    override val playing: Boolean
        get() = sequencer.isRunning

    override val positionMicros: Long
        get() = sequencer.microsecondPosition

    override val durationMicros: Long
        get() = sequencer.microsecondLength

    override fun play() = sequencer.start()

    override fun pause() = sequencer.stop()

    override fun seek(positionMicros: Long) {
        sequencer.microsecondPosition = positionMicros
    }

    override fun close() {
        runCatching { sequencer.stop() }
        runCatching { sequencer.close() }
        runCatching { synthesizer.close() }
    }
}

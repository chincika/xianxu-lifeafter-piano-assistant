package dev.gamepiano.autoplay.model

data class ScoreNote(
    val startMicros: Long,
    val durationMicros: Long,
    val midiNote: Int,
    val velocity: Int = 100,
    val channel: Int = 0,
    val track: Int = 0,
) {
    init {
        require(startMicros >= 0)
        require(durationMicros >= 0)
        require(midiNote in 0..127)
        require(velocity in 0..127)
        require(channel in 0..15)
        require(track >= 0)
    }
}

data class MidiScore(
    val name: String,
    val notes: List<ScoreNote>,
) {
    val durationMicros: Long = notes.maxOfOrNull { it.startMicros + it.durationMicros } ?: 0L
}

data class TapEvent(
    val atMicros: Long,
    val keyIndex: Int,
    val sourceMidiNote: Int,
    val velocity: Int,
) {
    init {
        require(atMicros >= 0)
        require(keyIndex in 0 until Piano88Layout.KEY_COUNT)
    }
}

data class KeyBatch(
    val atMicros: Long,
    val keys: List<GameKey>,
)

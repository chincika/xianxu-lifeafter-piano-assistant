package dev.gamepiano.autoplay.model

object Piano88Layout {
    const val LOWEST_MIDI_NOTE: Int = 21 // A0
    const val HIGHEST_MIDI_NOTE: Int = 108 // C8
    const val KEY_COUNT: Int = HIGHEST_MIDI_NOTE - LOWEST_MIDI_NOTE + 1

    val keys: List<GameKey> = (0 until KEY_COUNT).map { index ->
        val midiNote = LOWEST_MIDI_NOTE + index
        GameKey(
            index = index,
            keyboardLabel = noteName(midiNote),
            // The mouse backend uses the index; no keyboard scan code is associated with this key.
            windowsScanCode = 0,
        )
    }

    init {
        require(keys.size == KEY_COUNT)
        require(keys.first().keyboardLabel == "A0")
        require(keys.last().keyboardLabel == "C8")
    }

    fun keyAt(index: Int): GameKey = keys.getOrElse(index) {
        throw IllegalArgumentException("Piano key index must be in 0..${KEY_COUNT - 1}: $index")
    }

    fun midiNoteAt(index: Int): Int = LOWEST_MIDI_NOTE + keyAt(index).index

    fun isBlackMidiNote(midiNote: Int): Boolean = midiNote % 12 in setOf(1, 3, 6, 8, 10)

    private fun noteName(midiNote: Int): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[midiNote % 12] + (midiNote / 12 - 1)
    }
}

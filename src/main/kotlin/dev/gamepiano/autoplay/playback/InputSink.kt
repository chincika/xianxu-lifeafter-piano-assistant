package dev.gamepiano.autoplay.playback

import dev.gamepiano.autoplay.model.GameKey

interface InputSink {
    fun tap(keys: List<GameKey>, holdMillis: Long)
    fun releaseAll()
}

class RecordingInputSink : InputSink {
    data class RecordedTap(val keys: List<GameKey>, val holdMillis: Long)

    val taps: MutableList<RecordedTap> = mutableListOf()
    var releaseAllCalls: Int = 0
        private set

    override fun tap(keys: List<GameKey>, holdMillis: Long) {
        taps += RecordedTap(keys.toList(), holdMillis)
    }

    override fun releaseAll() {
        releaseAllCalls += 1
    }
}

package dev.gamepiano.autoplay.playback

import dev.gamepiano.autoplay.model.KeyBatch
import dev.gamepiano.autoplay.model.GameKey
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.model.TapEvent

object ChordBatcher {
    fun batch(
        events: List<TapEvent>,
        chordWindowMicros: Long = 2_000,
        layoutKeys: List<GameKey> = Piano23Layout.keys,
    ): List<KeyBatch> {
        require(chordWindowMicros >= 0)
        require(layoutKeys.isNotEmpty())
        if (events.isEmpty()) return emptyList()

        val sorted = events.sortedWith(compareBy(TapEvent::atMicros, TapEvent::keyIndex))
        val batches = mutableListOf<KeyBatch>()
        var baseTime = sorted.first().atMicros
        var current = mutableListOf(sorted.first())

        fun flush() {
            val keys = current
                .distinctBy(TapEvent::keyIndex)
                .sortedBy(TapEvent::keyIndex)
                .map { event ->
                    layoutKeys.getOrElse(event.keyIndex) {
                        throw IllegalArgumentException(
                            "Projected key ${event.keyIndex} is outside layout size ${layoutKeys.size}",
                        )
                    }
                }
            batches += KeyBatch(baseTime, keys)
        }

        for (event in sorted.drop(1)) {
            if (event.atMicros - baseTime <= chordWindowMicros) {
                current += event
            } else {
                flush()
                baseTime = event.atMicros
                current = mutableListOf(event)
            }
        }
        flush()
        return batches
    }
}

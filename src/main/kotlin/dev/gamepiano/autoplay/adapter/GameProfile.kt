package dev.gamepiano.autoplay.adapter

import dev.gamepiano.autoplay.model.GameKey
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.model.RangeCalibration

data class GameProfile(
    val id: String,
    val displayName: String,
    val keys: List<GameKey>,
    val calibration: RangeCalibration,
    val tapDurationMillis: Long,
    val chordWindowMicros: Long,
) {
    init {
        require(keys.size == Piano23Layout.KEY_COUNT)
        require(tapDurationMillis in 0..200)
        require(chordWindowMicros in 0..20_000)
    }
}

object LifeAfterPianoProfile {
    fun provisional(): GameProfile = GameProfile(
        id = "lifeafter-piano-23",
        displayName = "明日之后 23 键钢琴",
        keys = Piano23Layout.keys,
        calibration = RangeCalibration.provisionalMiddlePiano(),
        tapDurationMillis = 0,
        chordWindowMicros = 2_000,
    )
}

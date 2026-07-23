package dev.gamepiano.autoplay.model

data class GameKey(
    val index: Int,
    val keyboardLabel: String,
    val windowsScanCode: Int,
)

object Piano23Layout {
    const val KEY_COUNT: Int = 23

    val keys: List<GameKey> = listOf(
        GameKey(0, "1", 0x02),
        GameKey(1, "Q", 0x10),
        GameKey(2, "2", 0x03),
        GameKey(3, "W", 0x11),
        GameKey(4, "3", 0x04),
        GameKey(5, "E", 0x12),
        GameKey(6, "R", 0x13),
        GameKey(7, "5", 0x06),
        GameKey(8, "T", 0x14),
        GameKey(9, "6", 0x07),
        GameKey(10, "Y", 0x15),
        GameKey(11, "U", 0x16),
        GameKey(12, "7", 0x08),
        GameKey(13, "I", 0x17),
        GameKey(14, "8", 0x09),
        GameKey(15, "O", 0x18),
        GameKey(16, "9", 0x0A),
        GameKey(17, "P", 0x19),
        GameKey(18, "[", 0x1A),
        GameKey(19, "-", 0x0C),
        GameKey(20, "]", 0x1B),
        GameKey(21, "=", 0x0D),
        GameKey(22, "/", 0x35),
    )

    init {
        require(keys.size == KEY_COUNT)
        require(keys.map(GameKey::index) == (0 until KEY_COUNT).toList())
        require(keys.map(GameKey::keyboardLabel).toSet().size == KEY_COUNT)
    }

    fun keyAt(index: Int): GameKey = keys.getOrElse(index) {
        throw IllegalArgumentException("Game key index must be in 0..${KEY_COUNT - 1}: $index")
    }
}

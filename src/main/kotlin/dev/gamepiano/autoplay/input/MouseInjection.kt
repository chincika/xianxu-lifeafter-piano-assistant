package dev.gamepiano.autoplay.input

data class ScreenPoint(val x: Int, val y: Int)

data class WindowClientArea(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0)
        require(height > 0)
    }
}

data class MouseSendResult(
    val sent: Int,
    val expected: Int,
    val nativeErrorCode: Int? = null,
) {
    val successful: Boolean = sent == expected
}

fun interface MouseGateway {
    fun click(windowHandle: Long, point: ScreenPoint, area: WindowClientArea): MouseSendResult
}

fun interface WindowClientAreaProvider {
    fun get(handle: Long): WindowClientArea?
}

class MouseInjectionException(message: String) : IllegalStateException(message)

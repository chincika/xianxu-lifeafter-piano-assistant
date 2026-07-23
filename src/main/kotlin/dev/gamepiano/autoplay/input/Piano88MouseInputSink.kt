package dev.gamepiano.autoplay.input

import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.WindowsForegroundGuard
import dev.gamepiano.autoplay.model.GameKey
import dev.gamepiano.autoplay.model.Piano88Layout
import dev.gamepiano.autoplay.playback.InputSink
import kotlin.math.roundToInt

object Piano88MouseGeometry {
    private const val KEYBOARD_TOP_RATIO = 0.737f
    private const val BLACK_KEY_Y_RATIO = 0.807f
    private const val WHITE_KEY_Y_RATIO = 0.938f
    private const val BLACK_CENTER_OFFSET_IN_WHITE_WIDTHS = 0.08f
    private const val WHITE_KEY_COUNT = 52

    fun pointForKey(keyIndex: Int, area: WindowClientArea): ScreenPoint {
        val midiNote = Piano88Layout.midiNoteAt(keyIndex)
        val whiteWidth = area.width.toDouble() / WHITE_KEY_COUNT
        val whiteNotesBefore = (Piano88Layout.LOWEST_MIDI_NOTE until midiNote)
            .count { note -> !Piano88Layout.isBlackMidiNote(note) }
        val xWithinClient = if (Piano88Layout.isBlackMidiNote(midiNote)) {
            (whiteNotesBefore - BLACK_CENTER_OFFSET_IN_WHITE_WIDTHS) * whiteWidth
        } else {
            (whiteNotesBefore + 0.5) * whiteWidth
        }
        val yRatio = if (Piano88Layout.isBlackMidiNote(midiNote)) {
            BLACK_KEY_Y_RATIO
        } else {
            WHITE_KEY_Y_RATIO
        }
        // Guard against accidentally clicking above the keyboard if a future UI scale is unusual.
        val minimumKeyY = area.height * KEYBOARD_TOP_RATIO
        val yWithinClient = maxOf(area.height * yRatio, minimumKeyY + 1)
        return ScreenPoint(
            x = (area.left + xWithinClient.roundToInt()).coerceIn(area.left, area.left + area.width - 1),
            y = (area.top + yWithinClient.roundToInt()).coerceIn(area.top, area.top + area.height - 1),
        )
    }
}

class Piano88MouseInputSink(
    inspector: ForegroundWindowInspector,
    target: ForegroundTarget,
    private val gateway: MouseGateway,
    private val areaProvider: WindowClientAreaProvider,
) : InputSink {
    private val guard = WindowsForegroundGuard(inspector, target)

    override fun tap(keys: List<GameKey>, holdMillis: Long) {
        require(holdMillis in 0..200)
        val uniqueKeys = keys.distinctBy(GameKey::index)
        if (uniqueKeys.isEmpty()) return

        val window = guard.requireReadyWindow()
        val area = areaProvider.get(window.handle)
            ?: throw MouseInjectionException("Cannot read the foreground game client area")
        uniqueKeys.forEach { key ->
            require(key.index in 0 until Piano88Layout.KEY_COUNT) {
                "88-key mouse index must be in 0..${Piano88Layout.KEY_COUNT - 1}: ${key.index}"
            }
            val result = gateway.click(
                windowHandle = window.handle,
                point = Piano88MouseGeometry.pointForKey(key.index, area),
                area = area,
            )
            if (!result.successful) {
                val error = result.nativeErrorCode?.let { ", Win32 error=$it" }.orEmpty()
                throw MouseInjectionException(
                    "Only ${result.sent}/${result.expected} mouse inputs were sent$error",
                )
            }
        }
    }

    override fun releaseAll() = Unit
}

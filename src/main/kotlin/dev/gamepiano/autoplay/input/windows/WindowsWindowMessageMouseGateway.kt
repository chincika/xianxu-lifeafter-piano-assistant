package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import dev.gamepiano.autoplay.input.MouseGateway
import dev.gamepiano.autoplay.input.MouseSendResult
import dev.gamepiano.autoplay.input.ScreenPoint
import dev.gamepiano.autoplay.input.WindowClientArea

data class ClientPoint(val x: Int, val y: Int)

object WindowMessageMouseCoordinates {
    fun toClient(point: ScreenPoint, area: WindowClientArea): ClientPoint = ClientPoint(
        x = (point.x - area.left).coerceIn(0, area.width - 1),
        y = (point.y - area.top).coerceIn(0, area.height - 1),
    )

    fun pack(point: ClientPoint): Long =
        (point.x.toLong() and 0xFFFFL) or ((point.y.toLong() and 0xFFFFL) shl 16)
}

/**
 * Posts client-coordinate mouse messages without moving the system cursor.
 *
 * This is intentionally experimental: PostMessage only confirms that the
 * messages entered the target queue, not that a game consumed them.
 */
class WindowsWindowMessageMouseGateway : MouseGateway {
    override fun click(
        windowHandle: Long,
        point: ScreenPoint,
        area: WindowClientArea,
    ): MouseSendResult {
        if (!Platform.isWindows() || windowHandle == 0L) {
            return MouseSendResult(0, EXPECTED_MESSAGES, ERROR_NOT_SUPPORTED)
        }

        val window = WinDef.HWND(Pointer.createConstant(windowHandle))
        val clientPoint = WindowMessageMouseCoordinates.toClient(point, area)
        val coordinates = WinDef.LPARAM(WindowMessageMouseCoordinates.pack(clientPoint))
        var sent = 0
        var firstError: Int? = null

        if (User32WindowMessages.INSTANCE.PostMessage(
                window,
                WM_LBUTTONDOWN,
                WinDef.WPARAM(MK_LBUTTON.toLong()),
                coordinates,
            )
        ) {
            sent += 1
        } else {
            firstError = Native.getLastError()
        }

        if (User32WindowMessages.INSTANCE.PostMessage(
                window,
                WM_LBUTTONUP,
                WinDef.WPARAM(0),
                coordinates,
            )
        ) {
            sent += 1
        } else if (firstError == null) {
            firstError = Native.getLastError()
        }

        return MouseSendResult(sent, EXPECTED_MESSAGES, firstError)
    }

    private interface User32WindowMessages : StdCallLibrary {
        fun PostMessage(
            window: WinDef.HWND,
            message: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
        ): Boolean

        companion object {
            val INSTANCE: User32WindowMessages = Native.load(
                "user32",
                User32WindowMessages::class.java,
                W32APIOptions.DEFAULT_OPTIONS,
            )
        }
    }

    private companion object {
        const val EXPECTED_MESSAGES = 2
        const val WM_LBUTTONDOWN = 0x0201
        const val WM_LBUTTONUP = 0x0202
        const val MK_LBUTTON = 0x0001
        const val ERROR_NOT_SUPPORTED = 50
    }
}

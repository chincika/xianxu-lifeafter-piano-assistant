package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import dev.gamepiano.autoplay.input.MouseGateway
import dev.gamepiano.autoplay.input.MouseSendResult
import dev.gamepiano.autoplay.input.ScreenPoint
import dev.gamepiano.autoplay.input.WindowClientArea
import dev.gamepiano.autoplay.input.WindowClientAreaProvider

class WindowsClientAreaProvider : WindowClientAreaProvider {
    override fun get(handle: Long): WindowClientArea? {
        if (!Platform.isWindows() || handle == 0L) return null
        val window = WinDef.HWND(Pointer.createConstant(handle))
        val rect = WinDef.RECT()
        if (!User32.INSTANCE.GetClientRect(window, rect)) return null
        val topLeft = WinDef.POINT(rect.left, rect.top)
        val bottomRight = WinDef.POINT(rect.right, rect.bottom)
        if (!User32ClientCoordinates.INSTANCE.ClientToScreen(window, topLeft)) return null
        if (!User32ClientCoordinates.INSTANCE.ClientToScreen(window, bottomRight)) return null

        // ClientToScreen can be DPI-virtualized when this process and the game use
        // different awareness contexts. Convert both corners through the target HWND
        // so SendInput always receives physical virtual-desktop coordinates.
        User32ClientCoordinates.INSTANCE.LogicalToPhysicalPointForPerMonitorDPI(window, topLeft)
        User32ClientCoordinates.INSTANCE.LogicalToPhysicalPointForPerMonitorDPI(window, bottomRight)

        val width = bottomRight.x - topLeft.x
        val height = bottomRight.y - topLeft.y
        if (width <= 0 || height <= 0) return null
        return WindowClientArea(topLeft.x, topLeft.y, width, height)
    }
}

private interface User32ClientCoordinates : StdCallLibrary {
    fun ClientToScreen(window: WinDef.HWND, point: WinDef.POINT): Boolean
    fun LogicalToPhysicalPointForPerMonitorDPI(window: WinDef.HWND, point: WinDef.POINT): Boolean

    companion object {
        val INSTANCE: User32ClientCoordinates = Native.load(
            "user32",
            User32ClientCoordinates::class.java,
            W32APIOptions.DEFAULT_OPTIONS,
        )
    }
}

/** Moves the real cursor and submits a left click through Win32 SendInput. */
class WindowsMouseInputGateway : MouseGateway {
    override fun click(point: ScreenPoint): MouseSendResult {
        if (!Platform.isWindows()) return MouseSendResult(0, EXPECTED_INPUTS, ERROR_NOT_SUPPORTED)
        val virtualLeft = User32.INSTANCE.GetSystemMetrics(SM_XVIRTUALSCREEN)
        val virtualTop = User32.INSTANCE.GetSystemMetrics(SM_YVIRTUALSCREEN)
        val virtualWidth = User32.INSTANCE.GetSystemMetrics(SM_CXVIRTUALSCREEN).coerceAtLeast(1)
        val virtualHeight = User32.INSTANCE.GetSystemMetrics(SM_CYVIRTUALSCREEN).coerceAtLeast(1)
        val normalizedX = normalize(point.x, virtualLeft, virtualWidth)
        val normalizedY = normalize(point.y, virtualTop, virtualHeight)

        val inputBlock = WinUser.INPUT()
        @Suppress("UNCHECKED_CAST")
        val inputs = inputBlock.toArray(EXPECTED_INPUTS) as Array<WinUser.INPUT>
        configure(inputs[0], normalizedX, normalizedY, MOUSEEVENTF_MOVE or MOUSEEVENTF_ABSOLUTE or MOUSEEVENTF_VIRTUALDESK)
        configure(inputs[1], normalizedX, normalizedY, MOUSEEVENTF_LEFTDOWN)
        configure(inputs[2], normalizedX, normalizedY, MOUSEEVENTF_LEFTUP)

        val sent = User32.INSTANCE.SendInput(
            WinDef.DWORD(EXPECTED_INPUTS.toLong()),
            inputs,
            inputBlock.size(),
        ).toInt()
        return MouseSendResult(
            sent = sent,
            expected = EXPECTED_INPUTS,
            nativeErrorCode = if (sent == EXPECTED_INPUTS) null else Native.getLastError(),
        )
    }

    private fun configure(input: WinUser.INPUT, x: Int, y: Int, flags: Int) {
        input.type = WinDef.DWORD(INPUT_MOUSE.toLong())
        input.input.setType("mi")
        input.input.mi.dx = WinDef.LONG(x.toLong())
        input.input.mi.dy = WinDef.LONG(y.toLong())
        input.input.mi.mouseData = WinDef.DWORD(0)
        input.input.mi.dwFlags = WinDef.DWORD(flags.toLong())
        input.input.mi.time = WinDef.DWORD(0)
        input.input.mi.dwExtraInfo = null
        input.write()
    }

    private fun normalize(value: Int, origin: Int, size: Int): Int =
        (((value - origin).toLong() * 65_535L) / (size - 1).coerceAtLeast(1))
            .coerceIn(0L, 65_535L)
            .toInt()

    private companion object {
        const val EXPECTED_INPUTS = 3
        const val INPUT_MOUSE = 0
        const val MOUSEEVENTF_MOVE = 0x0001
        const val MOUSEEVENTF_LEFTDOWN = 0x0002
        const val MOUSEEVENTF_LEFTUP = 0x0004
        const val MOUSEEVENTF_VIRTUALDESK = 0x4000
        const val MOUSEEVENTF_ABSOLUTE = 0x8000
        const val SM_XVIRTUALSCREEN = 76
        const val SM_YVIRTUALSCREEN = 77
        const val SM_CXVIRTUALSCREEN = 78
        const val SM_CYVIRTUALSCREEN = 79
        const val ERROR_NOT_SUPPORTED = 50
    }
}

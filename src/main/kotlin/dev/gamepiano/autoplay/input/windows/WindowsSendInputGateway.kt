package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import dev.gamepiano.autoplay.input.KeyboardGateway
import dev.gamepiano.autoplay.input.KeyboardSendResult
import dev.gamepiano.autoplay.input.ScanCodeAction
import dev.gamepiano.autoplay.input.ScanCodeStroke

/** Sends scan-code keyboard events through the same foreground Win32 route used by games. */
class WindowsSendInputGateway : KeyboardGateway {
    override fun send(strokes: List<ScanCodeStroke>): KeyboardSendResult {
        if (strokes.isEmpty()) return KeyboardSendResult(0, 0)
        if (!Platform.isWindows()) return KeyboardSendResult(0, strokes.size, ERROR_NOT_SUPPORTED)

        val inputBlock = WinUser.INPUT()
        @Suppress("UNCHECKED_CAST")
        val inputs = inputBlock.toArray(strokes.size) as Array<WinUser.INPUT>
        inputs.forEachIndexed { index, input ->
            input.type = WinDef.DWORD(INPUT_KEYBOARD.toLong())
            input.input.setType("ki")
            input.input.ki.wVk = WinDef.WORD(0)
            input.input.ki.wScan = WinDef.WORD(strokes[index].scanCode.toLong())
            input.input.ki.dwFlags = WinDef.DWORD(strokes[index].flags().toLong())
            input.input.ki.time = WinDef.DWORD(0)
            input.input.ki.dwExtraInfo = null
            input.write()
        }

        val sent = User32.INSTANCE.SendInput(
            WinDef.DWORD(strokes.size.toLong()),
            inputs,
            inputBlock.size(),
        ).toInt()
        return KeyboardSendResult(
            sent = sent,
            expected = strokes.size,
            nativeErrorCode = if (sent == strokes.size) null else Native.getLastError(),
        )
    }

    private fun ScanCodeStroke.flags(): Int =
        KEYEVENTF_SCANCODE or
            (if (action == ScanCodeAction.UP) KEYEVENTF_KEYUP else 0) or
            (if (extended) KEYEVENTF_EXTENDEDKEY else 0)

    private companion object {
        const val INPUT_KEYBOARD = 1
        const val KEYEVENTF_EXTENDEDKEY = 0x0001
        const val KEYEVENTF_KEYUP = 0x0002
        const val KEYEVENTF_SCANCODE = 0x0008
        const val ERROR_NOT_SUPPORTED = 50
    }
}

package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Native
import dev.gamepiano.autoplay.diagnostics.AppDiagnostics
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Tlhelp32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference

/** Accept an executable name or a copied Windows path, with optional quotes and .exe suffix. */
internal fun normalizeProcessName(value: String?): String? {
    val name = value?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
        ?.replace('\\', '/')?.substringAfterLast('/')?.trim().orEmpty()
    return (if (name.endsWith(".exe", ignoreCase = true)) name.dropLast(4) else name)
        .takeIf(String::isNotBlank)
}

internal class WindowsProcessNameResolver(
    private val imageName: (Int) -> String? = ::queryProcessImageName,
    private val snapshotName: (Int) -> String? = ::querySnapshotProcessName,
) {
    fun resolve(processId: Int): String? {
        if (processId <= 0) return null
        // Java ProcessHandle.info().command() may be empty when the target has
        // a different integrity level. Query only identity, never process memory.
        val primary = runCatching { imageName(processId) }
        val image = executableName(primary.getOrNull())
        val fallback = if (image == null) runCatching { snapshotName(processId) } else null
        val resolved = image ?: executableName(fallback?.getOrNull())
        AppDiagnostics.state("process.resolve", mapOf(
            "pid" to processId, "name" to resolved,
            "source" to when { image != null -> "QueryFullProcessImageName"; resolved != null -> "ToolhelpSnapshot"; else -> "unresolved" },
            "primaryFailure" to primary.exceptionOrNull()?.javaClass?.simpleName,
            "snapshotFailure" to fallback?.exceptionOrNull()?.javaClass?.simpleName,
        ))
        return resolved
    }

    private fun executableName(path: String?): String? = path?.replace('\\', '/')
        ?.substringAfterLast('/')?.takeIf(String::isNotBlank)
}

private fun queryProcessImageName(processId: Int): String? {
    if (!Platform.isWindows()) return null
    val kernel = Kernel32.INSTANCE
    val process = kernel.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, processId)
    if (process == null) {
        val error = Native.getLastError()
        AppDiagnostics.state("process.open_failed", mapOf("pid" to processId, "win32Error" to error))
        return null
    }
    try {
        // Normal paths need only MAX_PATH characters. Retry long paths without
        // allocating a 64 KiB array on every foreground readiness check.
        for (capacity in intArrayOf(260, 32_768)) {
            val buffer = CharArray(capacity)
            val length = IntByReference(buffer.size)
            if (kernel.QueryFullProcessImageName(process, 0, buffer, length)) {
                return String(buffer, 0, length.value)
            }
            val error = Native.getLastError()
            if (error != WinError.ERROR_INSUFFICIENT_BUFFER) {
                AppDiagnostics.state("process.query_failed", mapOf("pid" to processId, "win32Error" to error))
                return null
            }
        }
        return null
    } finally {
        kernel.CloseHandle(process)
    }
}

private fun querySnapshotProcessName(processId: Int): String? {
    if (!Platform.isWindows()) return null
    val kernel = Kernel32.INSTANCE
    // Process snapshots expose executable names by PID even when OpenProcess is
    // denied. Do not enumerate modules or use a title as a substitute identity.
    val snapshot = kernel.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, DWORD(0))
    if (snapshot == null || snapshot == WinBase.INVALID_HANDLE_VALUE) {
        val error = Native.getLastError()
        AppDiagnostics.state("process.snapshot_failed", mapOf("pid" to processId, "win32Error" to error))
        return null
    }
    try {
        val entry = Tlhelp32.PROCESSENTRY32()
        if (!kernel.Process32First(snapshot, entry)) return null
        do {
            if (entry.th32ProcessID.toInt() == processId) return Native.toString(entry.szExeFile)
        } while (kernel.Process32Next(snapshot, entry))
        return null
    } finally {
        kernel.CloseHandle(snapshot)
    }
}

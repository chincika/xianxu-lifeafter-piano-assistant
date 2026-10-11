package dev.gamepiano.autoplay.input.windows

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.ptr.IntByReference
import dev.gamepiano.autoplay.input.KeyboardInjectionException
import dev.gamepiano.autoplay.input.PlaybackInputGuard

data class ForegroundWindowSnapshot(
    val handle: Long,
    val processId: Int,
    val processName: String?,
    val title: String,
)

/** Executable aliases of the supported LifeAfter clients, excluding launchers. */
internal object LifeAfterProcessNames {
    const val DEFAULT = "lifeafter.exe"
    val executables = listOf(DEFAULT, "mingrizhihou.exe")
    private val normalized = executables.mapNotNull(::normalizeProcessName)

    fun contains(normalizedName: String?): Boolean = normalizedName != null &&
        normalized.any { it.equals(normalizedName, ignoreCase = true) }
}

data class ForegroundTarget(
    val processName: String? = null,
    val titleContains: String? = null,
) {
    init {
        require(!processName.isNullOrBlank() || !titleContains.isNullOrBlank()) {
            "At least one foreground target constraint is required"
        }
    }

    fun matches(window: ForegroundWindowSnapshot): Boolean {
        val expectedName = normalizeProcessName(processName)
        val actualName = normalizeProcessName(window.processName)
        val processMatches = processName.isNullOrBlank() ||
            (expectedName != null && actualName != null && (
                expectedName.equals(actualName, ignoreCase = true) ||
                    (LifeAfterProcessNames.contains(expectedName) && LifeAfterProcessNames.contains(actualName))
                ))
        val titleMatches = titleContains.isNullOrBlank() ||
            window.title.contains(titleContains, ignoreCase = true)
        return processMatches && titleMatches
    }

    fun description(): String = buildList {
        processName?.takeIf(String::isNotBlank)?.let {
            add(if (LifeAfterProcessNames.contains(normalizeProcessName(it))) {
                "process in [${LifeAfterProcessNames.executables.joinToString()}]"
            } else {
                "process=$it"
            })
        }
        titleContains?.takeIf(String::isNotBlank)?.let { add("title contains '$it'") }
    }.joinToString(", ")
}

fun interface ForegroundWindowInspector {
    fun current(): ForegroundWindowSnapshot?
}

class WindowsForegroundWindowInspector : ForegroundWindowInspector {
    private val processNames = WindowsProcessNameResolver()

    override fun current(): ForegroundWindowSnapshot? {
        if (!Platform.isWindows()) return null
        val window = User32.INSTANCE.GetForegroundWindow() ?: return null
        val processId = IntByReference()
        if (User32.INSTANCE.GetWindowThreadProcessId(window, processId) == 0 || processId.value <= 0) {
            return null
        }

        val titleBuffer = CharArray(1_024)
        User32.INSTANCE.GetWindowText(window, titleBuffer, titleBuffer.size)
        val title = Native.toString(titleBuffer)
        val processName = processNames.resolve(processId.value)

        return ForegroundWindowSnapshot(
            handle = Pointer.nativeValue(window.pointer),
            processId = processId.value,
            processName = processName,
            title = title,
        )
    }
}

class WindowsForegroundGuard(
    private val inspector: ForegroundWindowInspector,
    private val target: ForegroundTarget,
) : PlaybackInputGuard {
    fun requireReadyWindow(): ForegroundWindowSnapshot {
        val current = inspector.current()
            ?: throw KeyboardInjectionException("Cannot read the current foreground window")
        if (!target.matches(current)) {
            throw KeyboardInjectionException(
                "Foreground window mismatch. Expected ${target.description()}, " +
                "actual process=${current.processName ?: "unknown"}, title='${current.title}'",
            )
        }
        return current
    }

    override fun requireReady() {
        requireReadyWindow()
    }
}

package dev.gamepiano.autoplay

import com.sun.jna.Platform
import dev.gamepiano.autoplay.input.windows.WindowsProcessNameResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsProcessNameResolverTest {
    @Test
    fun `limited image query returns only the executable and avoids snapshot`() {
        var snapshotCalls = 0
        val resolver = WindowsProcessNameResolver(
            imageName = { "C:\\游戏\\明日之后\\LifeAfter.exe" },
            snapshotName = { snapshotCalls++; null },
        )

        assertEquals("LifeAfter.exe", resolver.resolve(123))
        assertEquals(0, snapshotCalls)
    }

    @Test
    fun `denied process access still resolves name through snapshot`() {
        val resolver = WindowsProcessNameResolver(
            imageName = { null },
            snapshotName = { pid -> if (pid == 123) "lifeafter.exe" else null },
        )

        assertEquals("lifeafter.exe", resolver.resolve(123))
        assertNull(resolver.resolve(456))
    }

    @Test
    fun `failed image query does not prevent snapshot fallback`() {
        val resolver = WindowsProcessNameResolver(
            imageName = { throw IllegalStateException("Access denied") },
            snapshotName = { "lifeafter.exe" },
        )

        assertEquals("lifeafter.exe", resolver.resolve(123))
    }

    @Test
    fun `blank image result falls back and both failures return unknown`() {
        assertEquals("lifeafter.exe", WindowsProcessNameResolver({ "" }, { "lifeafter.exe" }).resolve(123))
        assertNull(WindowsProcessNameResolver({ null }, { throw IllegalStateException() }).resolve(123))
    }

    @Test
    fun `invalid process id never queries native sources`() {
        var calls = 0
        val resolver = WindowsProcessNameResolver(
            imageName = { calls++; null },
            snapshotName = { calls++; null },
        )

        assertNull(resolver.resolve(0))
        assertNull(resolver.resolve(-1))
        assertEquals(0, calls)
    }

    @Test
    fun `native Windows queries resolve current JVM by pid using either route`() {
        if (!Platform.isWindows()) return
        val pid = ProcessHandle.current().pid().toInt()
        val expected = ProcessHandle.current().info().command().orElseThrow()
            .replace('\\', '/').substringAfterLast('/')

        assertEquals(expected, WindowsProcessNameResolver(snapshotName = { null }).resolve(pid))
        assertEquals(expected, WindowsProcessNameResolver(imageName = { null }).resolve(pid))
    }
}

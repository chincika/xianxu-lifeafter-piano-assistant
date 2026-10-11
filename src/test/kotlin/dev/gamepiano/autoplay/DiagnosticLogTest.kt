package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.diagnostics.DiagnosticLog
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class DiagnosticLogTest {
    private fun zipEntries(path: Path): Map<String, String> = ZipFile(path.toFile()).use { zip ->
        zip.entries().asSequence().associate { entry ->
            entry.name to zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    @Test
    fun `disabled by default with no directory or file created`() {
        val root = Files.createTempDirectory("diagnostics-test").resolve("logs")
        DiagnosticLog(root, "test").use { log ->
            log.event("ignored", error = IllegalStateException("ignored"))
            assertFalse(log.snapshot().enabled)
            assertFalse(log.snapshot().canExport)
            assertFalse(Files.exists(root))
            assertFailsWith<IllegalStateException> { log.exportZip(root.resolve("no.zip")) }
        }
        assertFalse(Files.exists(root))
    }

    @Test
    fun `export contains ordered events environment and exception with private paths redacted`() {
        val root = Files.createTempDirectory("diagnostics-test")
        DiagnosticLog(root.resolve("logs"), "test-version").use { log ->
            log.setEnabled(true)
            log.event("first", mapOf("inputPath" to "C:\\Users\\private-user\\Music\\private.mid", "token" to "private-secret", "mail" to "private@example.com"))
            log.event("failure", error = IllegalStateException("bad C:\\Users\\private-user\\secret.txt\nframe details"))
            log.event("last", mapOf("message" to "引号\"换行\n制表\t", "actual" to "process=game.exe, title='private character's name'"))
            val entries = zipEntries(log.exportZip(root.resolve("capture.zip")))
            assertTrue(entries.getValue("environment.txt").contains("test-version"))
            assertTrue(entries.containsKey("README.txt"))
            val events = entries.filterKeys { it.endsWith(".jsonl") }.values.joinToString("")
            assertTrue(events.indexOf("first") < events.indexOf("failure"))
            assertTrue(events.indexOf("failure") < events.indexOf("last"))
            assertTrue(events.contains("IllegalStateException"))
            assertTrue(events.contains("frame details"))
            assertTrue(events.contains("[PATH]"))
            assertTrue(events.contains("[EMAIL]"))
            assertTrue(events.contains("[REDACTED]"))
            assertTrue(events.contains("[OMITTED]"))
            for (privateValue in listOf("private-user", "private-secret", "private@example.com", "private character")) {
                assertFalse(events.contains(privateValue), "Leaked value: $privateValue")
            }
            assertTrue(events.lines().filter { it.isNotEmpty() }.all { it.startsWith("{") && it.endsWith("}") })
        }
    }

    @Test
    fun `disabling keeps existing export available and ignores later events`() {
        val root = Files.createTempDirectory("diagnostics-test")
        DiagnosticLog(root.resolve("logs"), "test").use { log ->
            log.setEnabled(true)
            log.event("before_disable")
            log.setEnabled(false)
            val count = log.snapshot().recordedEvents
            repeat(10) { log.event("must_not_be_recorded") }
            assertEquals(count, log.snapshot().recordedEvents)
            assertFalse(log.snapshot().enabled)
            val content = zipEntries(log.exportZip(root.resolve("stopped.zip"))).values.joinToString("")
            assertTrue(content.contains("before_disable"))
            assertFalse(content.contains("must_not_be_recorded"))
            log.setEnabled(true)
            log.event("after_reenable")
            val resumed = zipEntries(log.exportZip(root.resolve("resumed.zip"))).values.joinToString("")
            assertTrue(resumed.contains("after_reenable"))
        }
        DiagnosticLog(root.resolve("logs"), "test").use { assertFalse(it.snapshot().enabled) }
    }

    @Test
    fun `rotation retains only newest bounded parts`() {
        val root = Files.createTempDirectory("diagnostics-test")
        DiagnosticLog(root.resolve("logs"), "test", maxPartBytes = 1024, maxParts = 2).use { log ->
            log.setEnabled(true)
            repeat(50) { log.event("rotation_$it", mapOf("detail" to "x".repeat(300))) }
            val entries = zipEntries(log.exportZip(root.resolve("rotated.zip")))
            val parts = entries.filterKeys { it.endsWith(".jsonl") }
            assertEquals(2, parts.size)
            assertTrue(parts.values.joinToString("").contains("rotation_49"))
            assertFalse(parts.values.joinToString("").contains("rotation_0\""))
            assertTrue(parts.values.all { it.toByteArray().size <= 1024 })
        }
    }

    @Test
    fun `failed enable does not turn logging on and never overwrites export targets`() {
        val root = Files.createTempDirectory("diagnostics-test")
        val file = root.resolve("not-a-directory")
        Files.writeString(file, "keep-me")
        DiagnosticLog(file, "test").use { log ->
            assertFailsWith<Exception> { log.setEnabled(true) }
            assertFalse(log.snapshot().enabled)
            log.event("ignored")
            assertEquals("keep-me", Files.readString(file))
        }
        DiagnosticLog(root.resolve("logs"), "test").use { log ->
            log.setEnabled(true)
            assertFailsWith<IllegalArgumentException> { log.exportZip(file) }
            assertEquals("keep-me", Files.readString(file))
            assertFailsWith<Exception> { log.exportZip(root.resolve("missing/capture.zip")) }
            assertTrue(Files.exists(log.exportZip(root.resolve("recovery.zip"))))
        }
    }

    @Test
    fun `concurrent producers cannot corrupt exported records`() {
        val root = Files.createTempDirectory("diagnostics-test")
        DiagnosticLog(root.resolve("logs"), "test", queueCapacity = 4096).use { log ->
            log.setEnabled(true)
            val done = CountDownLatch(4)
            repeat(4) { producer ->
                Thread {
                    repeat(100) { log.event("producer", mapOf("producer" to producer, "index" to it)) }
                    done.countDown()
                }.start()
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            val lines = zipEntries(log.exportZip(root.resolve("concurrent.zip")))
                .filterKeys { it.endsWith(".jsonl") }.values.joinToString("").lines().filter(String::isNotEmpty)
            assertEquals(400, lines.count { it.contains("\"event\":\"producer\"") })
            assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
            assertEquals(0L, log.snapshot().droppedEvents)
        }
    }

    @Test
    fun `saturated queue drops events but export still succeeds`() {
        val root = Files.createTempDirectory("diagnostics-test")
        DiagnosticLog(root.resolve("logs"), "test", queueCapacity = 1).use { log ->
            log.setEnabled(true)
            repeat(10_000) { log.event("busy", mapOf("index" to it, "detail" to "x".repeat(1024))) }
            assertTrue(log.snapshot().droppedEvents > 0)
            val entries = zipEntries(log.exportZip(root.resolve("saturated.zip")))
            assertTrue(entries.getValue("capture-summary.txt").contains("Dropped events:"))
            assertTrue(entries.keys.any { it.endsWith(".jsonl") })
        }
    }

    @Test
    fun `disk failure disables logging without propagating to event producer`() {
        val root = Files.createTempDirectory("diagnostics-test")
        val logs = root.resolve("logs")
        DiagnosticLog(logs, "test", maxPartBytes = 1024).use { log ->
            log.setEnabled(true)
            log.exportZip(root.resolve("initial.zip")) // drain initial event
            val session = Files.list(logs).use { it.findFirst().orElseThrow() }
            Files.createDirectory(session.resolve("events-2.jsonl")) // simulate an unwritable next part
            log.event("force_rotation", mapOf("detail" to "x".repeat(1024)))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (log.snapshot().error == null && System.nanoTime() < deadline) Thread.sleep(10)
            assertFalse(log.snapshot().enabled)
            assertTrue(log.snapshot().error != null)
            log.event("ignored_after_failure")
            assertTrue(Files.exists(log.exportZip(root.resolve("partial.zip"))))
        }
    }

    @Test
    fun `redaction handles UNC unix paths credentials and email without damaging process names`() {
        assertEquals("[PATH]", DiagnosticLog.redact("\\\\server\\share\\private.mid"))
        assertEquals("[PATH]", DiagnosticLog.redact("/home/private/Music/song.mid"))
        assertEquals("lifeafter.exe", DiagnosticLog.redact("lifeafter.exe"))
        assertEquals("token=[REDACTED]", DiagnosticLog.redact("token=super-secret"))
        assertEquals("[EMAIL]", DiagnosticLog.redact("user@example.com"))
    }
}

package dev.gamepiano.autoplay.diagnostics

import dev.gamepiano.autoplay.app.APP_VERSION

import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.io.BufferedWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DiagnosticSnapshot(
    val enabled: Boolean = false,
    val recordedEvents: Long = 0,
    val droppedEvents: Long = 0,
    val error: String? = null,
) {
    val canExport: Boolean get() = recordedEvents > 0
}

/** Opt-in only. The playback thread never waits for disk I/O or a full logging queue. */
class DiagnosticLog(
    private val root: Path,
    private val version: String,
    private val maxPartBytes: Long = 2L * 1024 * 1024,
    private val maxParts: Int = 4,
    queueCapacity: Int = 2048,
) : AutoCloseable {
    init {
        require(maxPartBytes >= 1024 && maxParts >= 1 && queueCapacity >= 1)
    }

    private val control = Any()
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(queueCapacity),
        { task -> Thread(task, "xianxu-diagnostic-writer").apply { isDaemon = true } },
    )
    @Volatile private var enabled = false
    @Volatile private var closed = false
    @Volatile private var failure: String? = null
    private val accepted = AtomicLong()
    private val dropped = AtomicLong()
    // Writer-thread owned from this point down.
    private var sessionDirectory: Path? = null
    private val parts = ArrayDeque<Path>()
    private var writer: BufferedWriter? = null
    private var partBytes = 0L
    private var partNumber = 0
    private var environment = ""

    fun snapshot() = DiagnosticSnapshot(enabled, accepted.get(), dropped.get(), failure)

    /** Call from a background worker: enabling can touch the filesystem. Never persisted. */
    fun setEnabled(value: Boolean) = synchronized(control) {
        check(!closed) { "日志系统已关闭" }
        if (value == enabled) return@synchronized
        if (value) {
            command(Callable {
                if (sessionDirectory == null) {
                    Files.createDirectories(root)
                    val name = "session-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())}-${UUID.randomUUID()}"
                    sessionDirectory = Files.createDirectory(root.resolve(name))
                    environment = environmentReport(version)
                }
                if (writer == null) openPart()
            })
            failure = null
            enabled = true
            event("diagnostics.enabled", mapOf("version" to version))
        } else {
            event("diagnostics.disabled")
            enabled = false
            // Export also flushes; a full queue must not make disabling fail.
            runCatching { executor.execute { runCatching { writer?.flush() } } }
        }
    }

    fun event(name: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        if (!enabled || closed) return
        val timestamp = Instant.now().toString()
        val thread = Thread.currentThread().name
        // Copy mutable caller maps. Only small scalar values belong here, never a score or audio buffer.
        val values = fields.entries.take(24).associate { (key, value) -> key to value?.toString()?.take(1024) }
        try {
            executor.execute {
                try {
                    val line = buildString {
                        append("{\"time\":").append(json(timestamp))
                        append(",\"event\":").append(json(redact(name.take(256))))
                        append(",\"thread\":").append(json(redact(thread.take(128))))
                        append(",\"level\":").append(json(if (error == null) "DEBUG" else "ERROR"))
                        values.forEach { (key, value) ->
                            append(',').append(json(key.take(100))).append(':')
                            append(if (value == null) "null" else json(if (sensitiveKey.containsMatchIn(key)) "[REDACTED]" else redact(value)))
                        }
                        if (error != null) append(",\"exception\":").append(json(redact(error.stackTraceToString().take(24_000))))
                        append("}\n")
                    }
                    write(line)
                } catch (throwable: Exception) {
                    failure = redact(throwable.message ?: throwable.javaClass.simpleName)
                    enabled = false
                    runCatching { writer?.close() }
                    writer = null
                }
            }
            accepted.incrementAndGet()
        } catch (_: RejectedExecutionException) {
            dropped.incrementAndGet()
        }
    }

    /** Serializes after all preceding events, so exporting while recording is a consistent snapshot. */
    fun exportZip(destination: Path): Path = synchronized(control) {
        check(!closed && snapshot().canExport) { "暂无可导出的日志，请先开启日志并复现问题" }
        val target = destination.toAbsolutePath().normalize()
        require(!Files.exists(target)) { "目标文件已存在，请选择新的文件名" }
        command(Callable {
            writer?.flush()
            check(parts.isNotEmpty()) { "暂无可导出的日志" }
            val temporary = Files.createTempFile(target.parent, ".xianxu-export-", ".tmp")
            try {
                ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                    fun entry(name: String, bytes: ByteArray) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                    entry("environment.txt", environment.toByteArray(Charsets.UTF_8))
                    entry("README.txt", EXPORT_README.toByteArray(Charsets.UTF_8))
                    entry("capture-summary.txt", "Accepted events: ${accepted.get()}\nDropped events: ${dropped.get()}\nRetained parts: ${parts.size}\nDisk error: ${failure.orEmpty()}\n".toByteArray())
                    parts.forEach { entry(it.fileName.toString(), Files.readAllBytes(it)) }
                }
                // Never overwrite a user file, including one created after the initial existence check.
                Files.move(temporary, target)
            } finally {
                Files.deleteIfExists(temporary)
            }
            target
        })
    }

    private fun openPart() {
        writer?.close()
        val file = requireNotNull(sessionDirectory).resolve("events-${++partNumber}.jsonl")
        writer = Files.newBufferedWriter(file, Charsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        parts.addLast(file)
        partBytes = 0
        while (parts.size > maxParts) Files.delete(parts.removeFirst())
    }

    private fun write(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8).size
        if (partBytes > 0 && partBytes + bytes > maxPartBytes) openPart()
        requireNotNull(writer).write(line)
        partBytes += bytes
        // Flush on the I/O worker, including the last event before an idle period.
        writer?.flush()
    }

    override fun close() = synchronized(control) {
        if (closed) return@synchronized
        event("diagnostics.shutdown")
        enabled = false
        closed = true
        executor.shutdown()
        if (executor.awaitTermination(3, TimeUnit.SECONDS)) {
            runCatching { writer?.close() }
        }
    }

    private fun <T> command(action: Callable<T>): T {
        val future = FutureTask(action)
        try {
            executor.execute(future)
        } catch (exception: RejectedExecutionException) {
            if (executor.isShutdown) throw exception
            // Control actions run off the UI/playback thread and may wait for space.
            executor.queue.put(future)
        }
        return try {
            future.get()
        } catch (exception: ExecutionException) {
            throw exception.cause ?: exception
        }
    }

    companion object {
        private val sensitiveKey = Regex("password|secret|token|cookie|authorization", RegexOption.IGNORE_CASE)
        private val windowsPath = Regex("(?i)(?:[a-z]:[\\\\/]|\\\\\\\\)[^\\r\\n\\t\"<>|]*")
        private val unixPath = Regex("(?<![a-zA-Z0-9:/])/(?:Users|home|tmp|var|opt)/[^\\r\\n\\t\"<>]*")
        private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val title = Regex("title(?: contains)?[= ]'[^\\r\\n]*", RegexOption.IGNORE_CASE)
        private val credential = Regex("(?i)(password|secret|token|cookie|authorization)(\\s*[:=]\\s*)([^\\s,;]+)")
        internal fun redact(value: String): String = credential.replace(
            title.replace(email.replace(unixPath.replace(windowsPath.replace(value, "[PATH]"), "[PATH]"), "[EMAIL]"), "title=[OMITTED]"),
            "$1$2[REDACTED]",
        )

        private fun json(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 32) append("\\u%04x".format(character.code)) else append(character)
                }
            }
            append('"')
        }

        private fun environmentReport(version: String): String = buildString {
            appendLine("Xianxu version: $version")
            appendLine("Captured at: ${Instant.now()}")
            appendLine("Timezone: ${ZoneId.systemDefault()}")
            for (key in listOf("os.name", "os.version", "os.arch", "java.version", "java.vendor", "file.encoding")) {
                appendLine("$key: ${redact(System.getProperty(key).orEmpty())}")
            }
            appendLine("Processors: ${Runtime.getRuntime().availableProcessors()}")
            runCatching {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.forEachIndexed { index, device ->
                    val config = device.defaultConfiguration
                    appendLine("Display $index: ${config.bounds.width}x${config.bounds.height}; scale=${config.defaultTransform.scaleX}x${config.defaultTransform.scaleY}")
                }
            }
        }

        private const val EXPORT_README = """弦序诊断日志 / Xianxu diagnostics

events-*.jsonl 为按时间记录的事件，environment.txt 为运行环境。
日志仅在用户开启后记录，不会补录开启前的事件。重启后默认关闭。
每次运行最多保留 4 个约 2 MiB 的日志分片；更早事件会被轮换删除。
队列满时会丢弃日志，不阻塞演奏；capture-summary.txt 会注明丢弃数量。
窗口标题、音乐文件内容、全局键盘输入、账号和完整环境变量不采集。
绝对路径和邮箱会尽力脱敏，但不能保证所有自由文本都不含个人信息。
分享前请自行打开检查；不要上传音乐文件、账号信息或其他私人数据。

反馈方法：打开以下页面，填写版本、复现步骤、预期和实际结果，手动附加本 ZIP。
https://github.com/chincika/xianxu-lifeafter-piano-assistant/issues/new?template=bug_report.yml
本软件不自动上传日志。
"""
    }
}

object AppDiagnostics {
    private val states = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any?>>()
    val log: DiagnosticLog by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(Path::of)
            ?: Path.of(System.getProperty("java.io.tmpdir"))
        DiagnosticLog(base.resolve("GamePianoAutoplay/logs"), APP_VERSION)
    }

    fun event(name: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) =
        log.event(name, fields, error)

    fun state(name: String, fields: Map<String, Any?>) {
        if (!log.snapshot().enabled) return
        if (states.put(name, fields) != fields) log.event(name, fields)
    }

    fun resetObservedStates() = states.clear()
}

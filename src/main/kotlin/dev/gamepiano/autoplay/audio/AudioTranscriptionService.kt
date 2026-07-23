package dev.gamepiano.autoplay.audio

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.io.path.nameWithoutExtension

enum class AudioTranscriptionMode(val argument: String) {
    HIGH_FIDELITY_VOCALS("high-fidelity-vocals"),
    HIGH_FIDELITY_ACCOMPANIMENT("high-fidelity-accompaniment"),
    MELODY_ONLY("melody"),
    MELODY_WITH_CHORDS("balanced"),
    ACCOMPANIMENT_ONLY("accompaniment"),
}

data class AudioEngineAvailability(
    val available: Boolean,
    val python: String?,
    val sourceSeparationAvailable: Boolean,
    val message: String,
)

data class AudioTranscriptionResult(
    val midiPath: Path,
    val noteCount: Int,
    val estimatedBpm: Double,
    val usedSourceSeparation: Boolean,
)

internal data class AudioWorkerSummary(
    val noteCount: Int,
    val estimatedBpm: Double,
    val usedSourceSeparation: Boolean,
) {
    companion object {
        fun parse(json: String): AudioWorkerSummary = AudioWorkerSummary(
            noteCount = json.numberValue("notes")?.toInt() ?: 0,
            estimatedBpm = json.numberValue("bpm") ?: 0.0,
            usedSourceSeparation = json.booleanValue("separated") ?: false,
        )

        private fun String.numberValue(name: String): Double? =
            Regex("\\\"$name\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
                .find(this)?.groupValues?.get(1)?.toDoubleOrNull()

        private fun String.booleanValue(name: String): Boolean? =
            Regex("\\\"$name\\\"\\s*:\\s*(true|false)")
                .find(this)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }
}

class AudioTranscriptionException(message: String) : IllegalStateException(message)

class AudioTranscriptionService(
    private val environment: Map<String, String> = System.getenv(),
    private val workingDirectory: Path = Path.of("").toAbsolutePath().normalize(),
) : AutoCloseable {
    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var cachedAvailability: AudioEngineAvailability? = null

    fun availability(): AudioEngineAvailability {
        cachedAvailability?.let { return it }
        val python = findPython() ?: return AudioEngineAvailability(
            available = false,
            python = null,
            sourceSeparationAvailable = false,
            message = "未安装本地音频引擎",
        )
        val probe = runProbe(python)
        val availability = when (probe.exitCode) {
            0 -> AudioEngineAvailability(
                available = true,
                python = python,
                sourceSeparationAvailable = probe.output.contains("demucs=1"),
                message = if (probe.output.contains("demucs=1")) {
                    "音频引擎与人声分离已就绪"
                } else {
                    "音符识别已就绪，人声分离未安装"
                },
            )
            else -> AudioEngineAvailability(
                available = false,
                python = python,
                sourceSeparationAvailable = false,
                message = "Python 可用，但缺少 Basic Pitch",
            )
        }
        if (availability.available) cachedAvailability = availability
        return availability
    }

    fun transcribe(
        audioPath: Path,
        mode: AudioTranscriptionMode = AudioTranscriptionMode.MELODY_WITH_CHORDS,
        onProgress: (String) -> Unit = {},
    ): AudioTranscriptionResult {
        require(Files.isRegularFile(audioPath)) { "Audio file does not exist: $audioPath" }
        val python = findPython()
            ?: throw AudioTranscriptionException("未安装音频引擎，请先运行音频组件安装脚本")
        val availability = availability()
        if (!availability.available) throw AudioTranscriptionException(availability.message)

        val worker = extractWorker()
        val output = nextOutputPath(audioPath)
        Files.createDirectories(output.parent)

        val process = ProcessBuilder(
            python,
            worker.toString(),
            "--input", audioPath.toAbsolutePath().normalize().toString(),
            "--output", output.toString(),
            "--mode", mode.argument,
        )
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .configureAudioEnvironment(python)
            .start()
        activeProcess = process

        val log = ArrayDeque<String>()
        var resultLine: String? = null
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (log.size == 30) log.removeFirst()
                    log.addLast(line)
                    when {
                        line.startsWith("PROGRESS ") -> onProgress(line.removePrefix("PROGRESS "))
                        line.startsWith("RESULT ") -> resultLine = line.removePrefix("RESULT ")
                    }
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0 || !Files.isRegularFile(output)) {
                throw AudioTranscriptionException(
                    "音频转写失败（exit=$exitCode）：${log.takeLast(8).joinToString(" / ")}",
                )
            }
        } finally {
            activeProcess = null
        }

        val summary = AudioWorkerSummary.parse(resultLine.orEmpty())
        return AudioTranscriptionResult(
            midiPath = output,
            noteCount = summary.noteCount,
            estimatedBpm = summary.estimatedBpm,
            usedSourceSeparation = summary.usedSourceSeparation,
        )
    }

    fun cancel() {
        activeProcess?.destroy()
        activeProcess?.waitFor(2, TimeUnit.SECONDS)
        activeProcess?.takeIf(Process::isAlive)?.destroyForcibly()
    }

    override fun close() = cancel()

    private fun findPython(): String? {
        val candidates = audioPythonCandidates(environment, workingDirectory)
        return candidates.firstOrNull { candidate ->
            runCatching {
                val process = ProcessBuilder(candidate, "--version").redirectErrorStream(true).start()
                process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0
            }.getOrDefault(false)
        }
    }

    private fun runProbe(python: String): ProbeResult {
        val code = """
            import importlib.util, os, pathlib, sys
            from basic_pitch.inference import predict
            root = pathlib.Path(sys.executable).resolve().parent / 'ffmpeg'
            bins = [item.parent for item in root.glob('**/avcodec-62.dll')] if root.is_dir() else []
            if bins:
                os.environ['PATH'] = str(bins[0]) + os.pathsep + os.environ.get('PATH', '')
            separation = importlib.util.find_spec('demucs') is not None
            if separation:
                try:
                    import torchcodec
                except Exception:
                    separation = False
            print('demucs=' + ('1' if separation else '0'))
        """.trimIndent()
        return runCatching {
            val process = ProcessBuilder(python, "-c", code)
                .redirectErrorStream(true)
                .configureAudioEnvironment(python)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                ProbeResult(-1, "probe timeout")
            } else {
                ProbeResult(process.exitValue(), output)
            }
        }.getOrElse { ProbeResult(-1, it.message.orEmpty()) }
    }

    private fun extractWorker(): Path {
        val cacheRoot = environment["LOCALAPPDATA"]
            ?.let { Path.of(it, "GamePianoAutoplay", "worker") }
            ?: Path.of(System.getProperty("java.io.tmpdir"), "GamePianoAutoplay", "worker")
        Files.createDirectories(cacheRoot)
        val target = cacheRoot.resolve("transcribe.py")
        val resource = requireNotNull(javaClass.getResourceAsStream("/audio/transcribe.py")) {
            "Bundled audio transcription worker is missing"
        }
        resource.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    private fun nextOutputPath(source: Path): Path {
        val outputDirectory = source.toAbsolutePath().normalize().parent.resolve("game-midi")
        val safeStem = source.nameWithoutExtension.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        val suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        return outputDirectory.resolve("$safeStem-game-$suffix.mid")
    }

    private data class ProbeResult(val exitCode: Int, val output: String)

    private fun ProcessBuilder.configureAudioEnvironment(python: String): ProcessBuilder = apply {
        val runtimeRoot = runCatching { Path.of(python).toAbsolutePath().normalize().parent }.getOrNull()
        val bundledModelCache = runtimeRoot?.resolve("model-cache")
        if (bundledModelCache != null && Files.isDirectory(bundledModelCache)) {
            environment()["TORCH_HOME"] = bundledModelCache.toString()
        }
    }

}

internal fun audioPythonCandidates(
    environment: Map<String, String>,
    workingDirectory: Path,
    javaHome: Path = Path.of(System.getProperty("java.home")),
    applicationPath: String? = System.getProperty("jpackage.app-path"),
): List<String> = buildList {
    environment["GAME_PIANO_AUDIO_PYTHON"]?.takeIf(String::isNotBlank)?.let(::add)

    val applicationRoots = buildList {
        applicationPath?.takeIf(String::isNotBlank)?.let { path ->
            runCatching { Path.of(path).toAbsolutePath().normalize().parent }.getOrNull()?.let(::add)
        }
        javaHome.toAbsolutePath().normalize().parent?.let(::add)
        add(workingDirectory.toAbsolutePath().normalize())
    }.distinct()
    applicationRoots.forEach { root ->
        add(root.resolve("audio-runtime/python.exe").toString())
        add(root.resolve("audio-runtime/Scripts/python.exe").toString())
    }

    environment["LOCALAPPDATA"]?.let { localAppData ->
        add(Path.of(localAppData, "GamePianoAutoplay", "audio-runtime", "python.exe").toString())
        add(Path.of(localAppData, "GamePianoAutoplay", "audio-runtime", "Scripts", "python.exe").toString())
    }
    add(workingDirectory.resolve(".audio-runtime/python.exe").toString())
    add("python")
}.distinct()

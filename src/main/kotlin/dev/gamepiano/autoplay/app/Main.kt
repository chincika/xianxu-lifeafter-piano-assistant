package dev.gamepiano.autoplay.app

import dev.gamepiano.autoplay.adapter.LifeAfterPianoProfile
import dev.gamepiano.autoplay.application.SongPlanService
import dev.gamepiano.autoplay.audio.AudioTranscriptionMode
import dev.gamepiano.autoplay.audio.AudioTranscriptionService
import dev.gamepiano.autoplay.input.Piano88MouseInputSink
import dev.gamepiano.autoplay.input.ScanCodeInputSink
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.WindowsClientAreaProvider
import dev.gamepiano.autoplay.input.windows.WindowsForegroundGuard
import dev.gamepiano.autoplay.input.windows.WindowsForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.WindowsMouseInputGateway
import dev.gamepiano.autoplay.input.windows.WindowsPlaybackHotkeyMonitor
import dev.gamepiano.autoplay.input.windows.WindowsSendInputGateway
import dev.gamepiano.autoplay.input.windows.WindowsWindowMessageMouseGateway
import dev.gamepiano.autoplay.midi.ExampleMidiGenerator
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.MouseInputMode
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.playback.InputSink
import dev.gamepiano.autoplay.playback.PlaybackEngine
import dev.gamepiano.autoplay.playback.PlaybackOptions
import dev.gamepiano.autoplay.ui.launchDesktopApp
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        launchDesktopApp()
        return
    }

    if (args.contentEquals(arrayOf("--help"))) {
        printUsage()
        return
    }

    if (args.contentEquals(arrayOf("--inspect-window"))) {
        val window = WindowsForegroundWindowInspector().current()
        if (window == null) {
            println("无法读取当前前台窗口。")
        } else {
            println("Foreground process: ${window.processName ?: "unknown"}")
            println("Foreground PID: ${window.processId}")
            println("Foreground title: ${window.title}")
            println("Foreground HWND: 0x${window.handle.toString(16).uppercase()}")
        }
        return
    }

    if (args.firstOrNull() == "--generate-example") {
        val output = args.getOrNull(1)?.let(Path::of)
            ?: Path.of("examples", "twinkle-twinkle.mid")
        val written = ExampleMidiGenerator.writeTwinkleTwinkle(output)
        println("Example MIDI written to: $written")
        return
    }

    if (args.firstOrNull() == "--transcribe-audio") {
        val audioPath = args.getOrNull(1)?.let(Path::of)
            ?: error("--transcribe-audio requires an audio file path")
        val mode = when (args.getOrNull(2)?.lowercase() ?: "balanced") {
            "high-fidelity-vocals", "hifi-vocals" -> AudioTranscriptionMode.HIGH_FIDELITY_VOCALS
            "high-fidelity-accompaniment", "high-fidelity-88", "high-fidelity", "hifi88" ->
                AudioTranscriptionMode.HIGH_FIDELITY_ACCOMPANIMENT
            "melody" -> AudioTranscriptionMode.MELODY_ONLY
            "balanced" -> AudioTranscriptionMode.MELODY_WITH_CHORDS
            "accompaniment" -> AudioTranscriptionMode.ACCOMPANIMENT_ONLY
            else -> error(
                "Audio mode must be high-fidelity-vocals, high-fidelity-accompaniment, " +
                    "melody, balanced, or accompaniment",
            )
        }
        AudioTranscriptionService().use { service ->
            val availability = service.availability()
            println(availability.message)
            val result = service.transcribe(audioPath, mode, ::println)
            println(
                "Generated MIDI: ${result.midiPath} " +
                    "(${result.noteCount} notes, ${result.estimatedBpm} BPM, " +
                    "source-separation=${result.usedSourceSeparation})",
            )
        }
        return
    }

    val options = parseOptions(args)
    val profile = LifeAfterPianoProfile.provisional()
    val plan = SongPlanService(profile).prepare(
        path = options.midiPath,
        range = options.range,
        performanceMode = options.performanceMode,
    )

    println("Score: ${plan.score.name}")
    println("Notes: ${plan.score.notes.size}, duration: ${plan.score.durationMicros / 1_000_000.0}s")
    println(
        when (options.performanceMode) {
            PerformanceMode.KEYBOARD_23 -> "Input: 23-key keyboard, range: ${options.range}, batches: ${plan.batches.size}"
            PerformanceMode.PIANO_88_MOUSE -> "Input: 88-key mouse, range: A0-C8, batches: ${plan.batches.size}"
        },
    )
    println("Folded: ${plan.projection.foldedNotes}, dropped: ${plan.projection.droppedNotes}")
    plan.batches.take(20).forEach { batch ->
        println("${batch.atMicros / 1_000.0}ms -> ${batch.keys.joinToString { it.keyboardLabel }}")
    }

    if (!options.play) {
        println("Dry-run complete: no game input was sent. Add --play and a target constraint to play.")
        return
    }

    val target = ForegroundTarget(options.processName, options.titleContains)
    val inspector = WindowsForegroundWindowInspector()
    val guard = WindowsForegroundGuard(inspector, target)
    val current = inspector.current()
    println(
        "Current foreground: process=${current?.processName ?: "unknown"}, " +
            "title='${current?.title.orEmpty()}'",
    )
    guard.requireReady()

    if (options.countdownSeconds > 0) {
        for (second in options.countdownSeconds downTo 1) {
            println("Playing in $second...")
            Thread.sleep(1_000)
        }
    }

    val sink: InputSink = when (options.performanceMode) {
        PerformanceMode.KEYBOARD_23 -> ScanCodeInputSink(WindowsSendInputGateway(), guard)
        PerformanceMode.PIANO_88_MOUSE -> Piano88MouseInputSink(
            inspector = inspector,
            target = target,
            gateway = when (options.mouseInputMode) {
                MouseInputMode.SEND_INPUT -> WindowsMouseInputGateway()
                MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL -> WindowsWindowMessageMouseGateway()
            },
            areaProvider = WindowsClientAreaProvider(),
        )
    }
    val engine = PlaybackEngine()
    val shutdownHook = Thread({
        engine.stop()
        runCatching(sink::releaseAll)
    }, "game-piano-shutdown-release")
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val hotkeyMonitor = WindowsPlaybackHotkeyMonitor(
        onStop = {
            println("F8 emergency stop requested.")
            engine.stop()
        },
        onTogglePause = {
            when (engine.togglePaused()) {
                true -> {
                    runCatching(sink::releaseAll)
                    println("Playback paused. Press F9 to resume.")
                }
                false -> println("Playback resumed.")
                null -> Unit
            }
        },
    ).start()

    try {
        println("Playback started. Keep the target game in front; press F9 to pause/resume or F8 to stop.")
        val report = engine.play(
            plan.batches,
            sink,
            PlaybackOptions(tapDurationMillis = options.tapDurationMillis),
        )
        println(
            "Playback finished: executed=${report.executedBatches}, " +
                "late-dropped=${report.droppedLateBatches}, cancelled=${report.cancelled}",
        )
    } finally {
        hotkeyMonitor.close()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }
}

private data class CliOptions(
    val midiPath: Path,
    val range: GameToneRange,
    val play: Boolean,
    val processName: String?,
    val titleContains: String?,
    val countdownSeconds: Int,
    val tapDurationMillis: Long,
    val performanceMode: PerformanceMode,
    val mouseInputMode: MouseInputMode,
)

private fun parseOptions(args: Array<String>): CliOptions {
    require(!args[0].startsWith("--")) { "The first argument must be a MIDI file path" }
    var index = 1
    val range = args.getOrNull(index)
        ?.uppercase()
        ?.takeIf { candidate -> GameToneRange.entries.any { it.name == candidate } }
        ?.let(GameToneRange::valueOf)
        ?.also { index += 1 }
        ?: GameToneRange.G3

    var play = false
    var processName: String? = null
    var titleContains: String? = null
    var countdownSeconds = 3
    var tapDurationMillis = 0L
    var performanceMode = PerformanceMode.PIANO_88_MOUSE
    var mouseInputMode = MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL

    while (index < args.size) {
        val argument = args[index++]
        when {
            argument == "--play" -> play = true
            argument.startsWith("--process=") -> processName = argument.substringAfter('=').ifBlank { null }
            argument.startsWith("--title=") -> titleContains = argument.substringAfter('=').ifBlank { null }
            argument.startsWith("--countdown=") -> {
                countdownSeconds = argument.substringAfter('=').toInt()
                require(countdownSeconds in 0..10) { "Countdown must be in 0..10 seconds" }
            }
            argument.startsWith("--tap-ms=") -> {
                tapDurationMillis = argument.substringAfter('=').toLong()
                require(tapDurationMillis in 0..200) { "Tap duration must be in 0..200ms" }
            }
            argument.startsWith("--input=") -> {
                performanceMode = when (argument.substringAfter('=').lowercase()) {
                    "keyboard23" -> PerformanceMode.KEYBOARD_23
                    "mouse88" -> PerformanceMode.PIANO_88_MOUSE
                    else -> error("Input mode must be keyboard23 or mouse88")
                }
            }
            argument.startsWith("--mouse-input=") -> {
                mouseInputMode = when (argument.substringAfter('=').lowercase()) {
                    "sendinput" -> MouseInputMode.SEND_INPUT
                    "window-message" -> MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL
                    else -> error("Mouse input mode must be sendinput or window-message")
                }
            }
            else -> error("Unknown argument: $argument")
        }
    }

    if (play) {
        require(!processName.isNullOrBlank() || !titleContains.isNullOrBlank()) {
            "--play requires --process=<game.exe> or --title=<window title text>"
        }
    }

    return CliOptions(
        midiPath = Path.of(args[0]),
        range = range,
        play = play,
        processName = processName,
        titleContains = titleContains,
        countdownSeconds = countdownSeconds,
        tapDurationMillis = tapDurationMillis,
        performanceMode = performanceMode,
        mouseInputMode = mouseInputMode,
    )
}

private fun printUsage() {
    println("Game Piano Autoplay")
    println("Dry run: gradlew run --args='<song.mid> [G1|G2|G3|G4|G5|G6]'")
    println(
        "Play: gradlew run --args='<song.mid> G3 --play " +
            "--process=Game.exe [--input=keyboard23|mouse88] " +
            "[--mouse-input=sendinput|window-message] [--title=Game] [--countdown=3] [--tap-ms=0]'",
    )
    println("Inspect the current target: gradlew run --args='--inspect-window'")
    println("Generate a test score: gradlew run --args='--generate-example [output.mid]'")
    println(
        "Transcribe audio: gradlew run --args='--transcribe-audio <song.mp3> " +
            "[high-fidelity-vocals|high-fidelity-accompaniment|melody|balanced|accompaniment]'",
    )
    println(
        "Real input is only enabled with --play and a matching foreground target. " +
            "F9 pauses/resumes and F8 stops playback.",
    )
}

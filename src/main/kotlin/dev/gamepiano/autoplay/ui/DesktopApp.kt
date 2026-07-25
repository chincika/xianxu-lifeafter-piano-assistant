package dev.gamepiano.autoplay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.gamepiano.autoplay.application.GamePlaybackCoordinator
import dev.gamepiano.autoplay.application.LocalMidiLibraryPreferences
import dev.gamepiano.autoplay.application.LocalMidiLibraryScanner
import dev.gamepiano.autoplay.application.LocalMidiTrack
import dev.gamepiano.autoplay.application.PlaybackProgress
import dev.gamepiano.autoplay.application.PlaybackStatus
import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.application.SongPlanService
import dev.gamepiano.autoplay.application.midiNoteName
import dev.gamepiano.autoplay.audio.AudioEngineAvailability
import dev.gamepiano.autoplay.audio.AudioTranscriptionMode
import dev.gamepiano.autoplay.audio.AudioTranscriptionService
import dev.gamepiano.autoplay.input.windows.ForegroundTarget
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.input.windows.WindowsForegroundWindowInspector
import dev.gamepiano.autoplay.input.windows.WindowsStartHotkeyMonitor
import dev.gamepiano.autoplay.legal.PreferencesDisclaimerAcceptanceStore
import dev.gamepiano.autoplay.legal.RiskDisclaimer
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.MouseInputMode
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.model.Piano23Layout
import dev.gamepiano.autoplay.model.RangeCalibration
import dev.gamepiano.autoplay.preview.GameMappedPreviewSequence
import dev.gamepiano.autoplay.preview.MidiPreviewPlayer
import dev.gamepiano.autoplay.preview.PreviewMode
import dev.gamepiano.autoplay.preview.PreviewSnapshot
import java.awt.Dimension
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window as AwtWindow
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFileChooser
import javax.swing.UIManager

private const val APP_VERSION = "0.1.0-beta.5"

fun launchDesktopApp() = application {
    val windowState = rememberWindowState(width = 1_260.dp, height = 840.dp)
    val appIcon = painterResource("icons/xianxu-256.png")
    Window(
        onCloseRequest = ::exitApplication,
        title = "弦序 · 明日之后乐器工作台",
        state = windowState,
        icon = appIcon,
    ) {
        SideEffect {
            window.minimumSize = Dimension(1_080, 720)
        }
        PianoWorkbench(owner = window, onExit = ::exitApplication)
    }
}

@Composable
private fun PianoWorkbench(owner: AwtWindow, onExit: () -> Unit) {
    val planService = remember { SongPlanService() }
    val coordinator = remember { GamePlaybackCoordinator() }
    val previewPlayer = remember { MidiPreviewPlayer() }
    val audioTranscriber = remember { AudioTranscriptionService() }
    val inspector = remember { WindowsForegroundWindowInspector() }
    val disclaimerStore = remember { PreferencesDisclaimerAcceptanceStore() }
    val libraryScanner = remember { LocalMidiLibraryScanner() }
    val libraryPreferences = remember { LocalMidiLibraryPreferences() }
    val worker = remember {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "game-piano-song-loader").apply { isDaemon = true }
        }
    }

    var selectedPath by remember { mutableStateOf<Path?>(null) }
    var plan by remember { mutableStateOf<SongPlan?>(null) }
    var performanceMode by remember { mutableStateOf(PerformanceMode.PIANO_88_MOUSE) }
    var range by remember { mutableStateOf(GameToneRange.G3) }
    var g3LowestMidi by remember { mutableIntStateOf(54) }
    var tapDurationMillis by remember { mutableLongStateOf(0L) }
    var mouseInputMode by remember { mutableStateOf(MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL) }
    var processName by remember { mutableStateOf("lifeafter.exe") }
    var titleContains by remember { mutableStateOf("") }
    var foregroundWindow by remember { mutableStateOf<ForegroundWindowSnapshot?>(null) }
    var playbackStatus by remember { mutableStateOf<PlaybackStatus>(PlaybackStatus.Idle) }
    var loading by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var transcriptionMode by remember { mutableStateOf(AudioTranscriptionMode.MELODY_WITH_CHORDS) }
    var audioAvailability by remember {
        mutableStateOf(AudioEngineAvailability(false, null, false, "正在检查音频组件…"))
    }
    var message by remember { mutableStateOf("选择 MIDI 或音乐文件开始准备乐谱") }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var previewMode by remember { mutableStateOf(PreviewMode.ORIGINAL_MIDI) }
    var previewSnapshot by remember { mutableStateOf(PreviewSnapshot(false, false, 0, 0)) }
    var gamePlaybackProgress by remember { mutableStateOf(PlaybackProgress(0, 0)) }
    var resumeCursorActive by remember { mutableStateOf(false) }
    var scoreGuideOpen by remember { mutableStateOf(false) }
    var disclaimerOpen by remember { mutableStateOf(false) }
    var startupDisclaimerRequired by remember {
        mutableStateOf(!disclaimerStore.hasAcceptedCurrentVersion())
    }
    var pendingRestartPosition by remember { mutableStateOf<Long?>(null) }
    var miniPlayerOpen by remember { mutableStateOf(false) }
    var miniPlayerAlwaysOnTop by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var libraryFolder by remember { mutableStateOf(libraryPreferences.loadFolder()) }
    var libraryTracks by remember { mutableStateOf(emptyList<LocalMidiTrack>()) }
    var libraryLoading by remember { mutableStateOf(false) }
    var librarySearch by remember { mutableStateOf("") }
    var libraryLoop by remember { mutableStateOf(true) }
    var libraryAutoNext by remember { mutableStateOf(false) }
    var pendingLibraryAutoStartPath by remember { mutableStateOf<Path?>(null) }
    var libraryAutoStartRequest by remember { mutableIntStateOf(0) }

    fun prepareSong(
        path: Path,
        nextRange: GameToneRange,
        nextG3Lowest: Int,
        nextPerformanceMode: PerformanceMode,
        sourceMessage: String? = null,
        reloadPreview: Boolean = false,
        keepPreviewPosition: Boolean = false,
    ) {
        loadGeneration += 1
        val generation = loadGeneration
        val requestedPreviewMode = previewMode
        val requestedPosition = if (!reloadPreview || keepPreviewPosition) previewSnapshot.positionMicros else 0L
        val shouldLoadPreview = reloadPreview || requestedPreviewMode == PreviewMode.GAME_MAPPING
        loading = true
        message = when (nextPerformanceMode) {
            PerformanceMode.PIANO_88_MOUSE -> "正在解析 MIDI 与生成 88 键原音高映射…"
            PerformanceMode.KEYBOARD_23 -> "正在解析 MIDI 与生成 23 键映射…"
        }
        worker.submit {
            val result = runCatching {
                val calibration = RangeCalibration.fromCenterRangeLowest(nextG3Lowest)
                val prepared = planService.prepare(
                    path = path,
                    range = nextRange,
                    calibration = calibration,
                    performanceMode = nextPerformanceMode,
                )
                val previewFailure = if (shouldLoadPreview) {
                    previewPlayer.close()
                    runCatching {
                        when (requestedPreviewMode) {
                            PreviewMode.ORIGINAL_MIDI -> previewPlayer.load(path)
                            PreviewMode.GAME_MAPPING -> previewPlayer.load(
                                GameMappedPreviewSequence.build(prepared, calibration),
                            )
                        }
                        previewPlayer.seek(requestedPosition)
                    }.exceptionOrNull()
                } else {
                    null
                }
                prepared to previewFailure
            }
            EventQueue.invokeLater {
                if (generation != loadGeneration) return@invokeLater
                loading = false
                result.fold(
                    onSuccess = { (prepared, previewFailure) ->
                        plan = prepared
                        val deviceSnapshot = previewPlayer.snapshot()
                        previewSnapshot = if (deviceSnapshot.loaded) {
                            deviceSnapshot
                        } else {
                            PreviewSnapshot(
                                loaded = false,
                                playing = false,
                                positionMicros = requestedPosition.coerceIn(0L, prepared.score.durationMicros),
                                durationMicros = prepared.score.durationMicros,
                            )
                        }
                        val mappingMessage = if (prepared.playable) {
                            when (prepared.performanceMode) {
                                PerformanceMode.PIANO_88_MOUSE ->
                                    "88 键直映完成 · ${prepared.batches.size} 个触发批次 · 不做八度折叠"
                                PerformanceMode.KEYBOARD_23 ->
                                    "23 键映射完成 · ${prepared.batches.size} 个触发批次 · 推荐 ${prepared.recommendedRange.name}"
                            }
                        } else {
                            "没有可播放音符，请检查轨道或音域设置"
                        }
                        val previewMessage = previewFailure?.let {
                            "试听不可用：${it.message ?: "系统 MIDI 设备打开失败"}"
                        }
                        message = listOfNotNull(sourceMessage, mappingMessage, previewMessage).joinToString(" · ")
                        if (pendingLibraryAutoStartPath == prepared.source.toAbsolutePath().normalize()) {
                            pendingLibraryAutoStartPath = null
                            libraryAutoStartRequest += 1
                        }
                    },
                    onFailure = { throwable ->
                        if (pendingLibraryAutoStartPath == path.toAbsolutePath().normalize()) {
                            pendingLibraryAutoStartPath = null
                        }
                        plan = null
                        message = "读取失败：${throwable.message ?: "未知错误"}"
                    },
                )
            }
        }
    }

    fun chooseMidi() {
        chooseMidiFile(owner)?.let { path ->
            selectedPath = path
            gamePlaybackProgress = PlaybackProgress(0, 0)
            resumeCursorActive = false
            prepareSong(path, range, g3LowestMidi, performanceMode, reloadPreview = true)
        }
    }

    fun chooseAudio() {
        if (!audioAvailability.available) {
            message = "${audioAvailability.message}；请先运行 tools/audio-transcription/setup-audio-engine.ps1"
            return
        }
        val audioPath = chooseAudioFile(owner) ?: return
        transcribing = true
        plan = null
        gamePlaybackProgress = PlaybackProgress(0, 0)
        resumeCursorActive = false
        previewPlayer.stop()
        previewSnapshot = previewPlayer.snapshot()
        message = "正在启动本地音频转谱…"
        worker.submit {
            val result = runCatching {
                audioTranscriber.transcribe(audioPath, transcriptionMode) { progress ->
                    EventQueue.invokeLater { message = progress }
                }
            }
            EventQueue.invokeLater {
                transcribing = false
                result.fold(
                    onSuccess = { transcription ->
                        selectedPath = transcription.midiPath
                        val method = if (transcription.usedSourceSeparation) "人声分离" else "整曲简化"
                        prepareSong(
                            path = transcription.midiPath,
                            nextRange = range,
                            nextG3Lowest = g3LowestMidi,
                            nextPerformanceMode = performanceMode,
                            sourceMessage = "音频转谱完成 · $method · ${transcription.noteCount} 音符 · " +
                                "${transcription.estimatedBpm.toInt()} BPM",
                            reloadPreview = true,
                        )
                    },
                    onFailure = { throwable ->
                        message = "音频转谱失败：${throwable.message ?: "未知错误"}"
                    },
                )
            }
        }
    }

    fun refreshLibrary(folder: Path? = libraryFolder) {
        val root = folder ?: run {
            message = "请先选择本地 MIDI 音乐库文件夹"
            return
        }
        libraryLoading = true
        message = "正在扫描本地音乐库…"
        worker.submit {
            val result = runCatching { libraryScanner.scan(root) }
            EventQueue.invokeLater {
                libraryLoading = false
                result.fold(
                    onSuccess = { tracks ->
                        libraryFolder = root.toAbsolutePath().normalize()
                        libraryTracks = tracks
                        message = if (tracks.isEmpty()) {
                            "音乐库中没有找到 .mid 或 .midi 文件"
                        } else {
                            "本地音乐库已更新 · 共 ${tracks.size} 首 MIDI"
                        }
                    },
                    onFailure = { throwable ->
                        message = "扫描音乐库失败：${throwable.message ?: "未知错误"}"
                    },
                )
            }
        }
    }

    fun chooseLibraryFolder() {
        chooseDirectory(owner, libraryFolder)?.let { folder ->
            libraryPreferences.saveFolder(folder)
            refreshLibrary(folder)
        }
    }

    fun loadLibraryTrack(path: Path, autoStart: Boolean = false) {
        if (coordinator.isRunning()) coordinator.stop()
        pendingLibraryAutoStartPath = if (autoStart) path.toAbsolutePath().normalize() else null
        selectedPath = path
        gamePlaybackProgress = PlaybackProgress(0, 0)
        resumeCursorActive = false
        prepareSong(
            path = path,
            nextRange = range,
            nextG3Lowest = g3LowestMidi,
            nextPerformanceMode = performanceMode,
            sourceMessage = "已从本地音乐库载入 ${path.fileName}",
            reloadPreview = true,
        )
    }

    fun visibleLibraryTracks(): List<LocalMidiTrack> {
        val query = librarySearch.trim()
        return if (query.isBlank()) {
            libraryTracks
        } else {
            libraryTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.relativeFolder.contains(query, ignoreCase = true)
            }
        }
    }

    fun stepLibrary(delta: Int, autoStart: Boolean = false) {
        val visible = visibleLibraryTracks()
        if (visible.isEmpty()) {
            message = "当前音乐库列表为空"
            return
        }
        val current = visible.indexOfFirst {
            it.path.toAbsolutePath().normalize() == selectedPath?.toAbsolutePath()?.normalize()
        }
        val rawNext = when {
            current < 0 && delta >= 0 -> 0
            current < 0 -> visible.lastIndex
            else -> current + delta
        }
        val nextIndex = when {
            rawNext in visible.indices -> rawNext
            libraryLoop -> Math.floorMod(rawNext, visible.size)
            else -> {
                message = if (delta > 0) "已经是列表最后一首" else "已经是列表第一首"
                return
            }
        }
        loadLibraryTrack(visible[nextIndex].path, autoStart)
    }

    fun openScoreResource(resource: ExternalScoreResource) {
        openExternalScoreResource(resource).fold(
            onSuccess = {
                message = "已在系统浏览器打开 ${resource.displayName} · 下载完成后返回弦序导入 MIDI"
            },
            onFailure = { throwable ->
                message = "无法打开 ${resource.displayName}：${throwable.message ?: "请检查默认浏览器设置"}"
            },
        )
    }

    fun updateRange(next: GameToneRange) {
        if (range == next) return
        range = next
        selectedPath?.let { prepareSong(it, next, g3LowestMidi, performanceMode) }
    }

    fun updateCalibration(next: Int) {
        val value = next.coerceIn(24, 69)
        if (value == g3LowestMidi) return
        g3LowestMidi = value
        selectedPath?.let { prepareSong(it, range, value, performanceMode) }
    }

    fun updatePerformanceMode(next: PerformanceMode) {
        if (performanceMode == next) return
        performanceMode = next
        selectedPath?.let { path ->
            prepareSong(
                path = path,
                nextRange = range,
                nextG3Lowest = g3LowestMidi,
                nextPerformanceMode = next,
                sourceMessage = "已切换为${next.displayName()}",
                reloadPreview = previewMode == PreviewMode.GAME_MAPPING,
                keepPreviewPosition = true,
            )
        }
    }

    fun updatePreviewMode(next: PreviewMode) {
        if (previewMode == next) return
        previewPlayer.pause()
        val deviceSnapshot = previewPlayer.snapshot()
        if (deviceSnapshot.loaded) previewSnapshot = deviceSnapshot
        previewMode = next
        selectedPath?.let { path ->
            prepareSong(
                path = path,
                nextRange = range,
                nextG3Lowest = g3LowestMidi,
                nextPerformanceMode = performanceMode,
                sourceMessage = "已切换为${next.displayName()}试听",
                reloadPreview = true,
                keepPreviewPosition = true,
            )
        }
    }

    fun togglePreview() {
        if (playbackStatus is PlaybackStatus.Countdown ||
            playbackStatus is PlaybackStatus.Playing ||
            playbackStatus is PlaybackStatus.Paused ||
            playbackStatus is PlaybackStatus.WaitingForTarget
        ) return
        runCatching {
            if (resumeCursorActive) {
                previewPlayer.seek(gamePlaybackProgress.positionMicros)
                resumeCursorActive = false
            }
            if (previewPlayer.snapshot().playing) {
                previewPlayer.pause()
                message = "试听已暂停 · 可拖动进度条定位"
            } else {
                previewPlayer.play()
                message = "正在${previewMode.displayName()}试听 · 游戏不会收到按键"
            }
            previewSnapshot = previewPlayer.snapshot()
        }.onFailure { throwable ->
            message = "无法试听：${throwable.message ?: "系统 MIDI 设备错误"}"
        }
    }

    fun stopPreview() {
        val timelineDuration = previewSnapshot.durationMicros
        previewPlayer.stop()
        val deviceSnapshot = previewPlayer.snapshot()
        previewSnapshot = if (deviceSnapshot.loaded) {
            deviceSnapshot
        } else {
            PreviewSnapshot(false, false, 0, timelineDuration)
        }
        gamePlaybackProgress = PlaybackProgress(0, timelineDuration)
        resumeCursorActive = false
        message = "试听已停止"
    }

    fun seekPreview(positionMicros: Long) {
        if (playbackStatus is PlaybackStatus.Countdown ||
            playbackStatus is PlaybackStatus.Playing ||
            playbackStatus is PlaybackStatus.Paused ||
            playbackStatus is PlaybackStatus.WaitingForTarget
        ) return
        previewPlayer.seek(positionMicros)
        gamePlaybackProgress = PlaybackProgress(positionMicros, previewSnapshot.durationMicros)
        resumeCursorActive = false
        val deviceSnapshot = previewPlayer.snapshot()
        previewSnapshot = if (deviceSnapshot.loaded) {
            deviceSnapshot
        } else {
            previewSnapshot.copy(
                playing = false,
                positionMicros = positionMicros.coerceIn(0L, previewSnapshot.durationMicros),
            )
        }
    }

    fun startPlaybackAt(positionOverrideMicros: Long? = null, countdownSeconds: Int = 3) {
        val prepared = plan ?: run {
            message = "请先导入 MIDI 或音乐文件，再按 F7 开始演奏"
            return
        }
        val selectedPosition = positionOverrideMicros ?: if (resumeCursorActive) {
                gamePlaybackProgress.positionMicros
            } else {
                previewSnapshot.positionMicros
            }
        previewPlayer.pause()
        val deviceSnapshot = previewPlayer.snapshot()
        previewSnapshot = if (deviceSnapshot.loaded) deviceSnapshot else previewSnapshot.copy(playing = false)
        val playbackPlan = prepared.startingAt(selectedPosition)
        if (!playbackPlan.playable) {
            message = "当前位置之后没有可演奏音符，请向前拖动进度条"
            return
        }
        val target = runCatching {
            ForegroundTarget(
                processName = processName.trim().ifBlank { null },
                titleContains = titleContains.trim().ifBlank { null },
            )
        }.getOrElse { throwable ->
            message = throwable.message ?: "请填写目标进程名或窗口标题"
            return
        }

        runCatching {
            gamePlaybackProgress = PlaybackProgress(selectedPosition, prepared.score.durationMicros)
            resumeCursorActive = true
            coordinator.start(
                plan = playbackPlan,
                target = target,
                tapDurationMillis = tapDurationMillis,
                mouseInputMode = mouseInputMode,
                countdownSeconds = countdownSeconds,
                onProgress = { progress ->
                    EventQueue.invokeLater {
                        gamePlaybackProgress = progress
                        resumeCursorActive = true
                    }
                },
            ) { status ->
                EventQueue.invokeLater {
                    playbackStatus = status
                    message = status.userMessage()
                    if (status is PlaybackStatus.Stopped) {
                        val restartPosition = pendingRestartPosition
                        if (restartPosition != null) {
                            pendingRestartPosition = null
                            message = "已定位到 ${formatTimestamp(restartPosition)} · 正在从新位置继续"
                            startPlaybackAt(restartPosition, countdownSeconds = 0)
                        }
                    } else if (status is PlaybackStatus.Completed && libraryAutoNext) {
                        stepLibrary(delta = 1, autoStart = true)
                    }
                }
            }
        }.onFailure { throwable ->
            message = throwable.message ?: "无法启动播放"
        }
    }

    fun startPlayback() = startPlaybackAt()

    LaunchedEffect(libraryAutoStartRequest) {
        if (libraryAutoStartRequest > 0) startPlaybackAt(countdownSeconds = 0)
    }

    fun seekTimeline(positionMicros: Long, commit: Boolean) {
        val duration = plan?.score?.durationMicros ?: previewSnapshot.durationMicros
        val position = positionMicros.coerceIn(0L, duration.coerceAtLeast(0L))
        val activePlayback = playbackStatus is PlaybackStatus.Countdown ||
            playbackStatus is PlaybackStatus.Playing ||
            playbackStatus is PlaybackStatus.Paused ||
            playbackStatus is PlaybackStatus.WaitingForTarget
        if (!activePlayback) {
            seekPreview(position)
            return
        }

        gamePlaybackProgress = PlaybackProgress(position, duration)
        resumeCursorActive = true
        if (playbackStatus is PlaybackStatus.Playing) {
            coordinator.togglePause()
        }
        if (commit) {
            pendingRestartPosition = position
            message = "正在定位到 ${formatTimestamp(position)} · 已安全暂停输入"
            coordinator.stop()
        }
    }

    val pollerRunning = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) {
        val startHotkeyMonitor = runCatching {
            WindowsStartHotkeyMonitor {
                EventQueue.invokeLater {
                    if (!coordinator.isRunning()) startPlayback()
                }
            }.start()
        }.getOrNull()
        worker.submit {
            val availability = audioTranscriber.availability()
            EventQueue.invokeLater {
                if (pollerRunning.get()) audioAvailability = availability
            }
        }
        libraryFolder?.let(::refreshLibrary)
        val poller = Thread({
            while (pollerRunning.get()) {
                val snapshot = runCatching(inspector::current).getOrNull()
                EventQueue.invokeLater { foregroundWindow = snapshot }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "game-piano-window-inspector").apply {
            isDaemon = true
            start()
        }
        val previewPoller = Thread({
            while (pollerRunning.get()) {
                val snapshot = previewPlayer.snapshot()
                if (snapshot.loaded) EventQueue.invokeLater { previewSnapshot = snapshot }
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "game-piano-preview-progress").apply {
            isDaemon = true
            start()
        }
        onDispose {
            pollerRunning.set(false)
            poller.interrupt()
            previewPoller.interrupt()
            startHotkeyMonitor?.close()
            coordinator.close()
            previewPlayer.close()
            audioTranscriber.close()
            worker.shutdownNow()
        }
    }

    val targetMatches = remember(foregroundWindow, processName, titleContains) {
        runCatching {
            ForegroundTarget(
                processName.trim().ifBlank { null },
                titleContains.trim().ifBlank { null },
            ).matches(requireNotNull(foregroundWindow))
        }.getOrDefault(false)
    }
    val playing = playbackStatus is PlaybackStatus.Countdown ||
        playbackStatus is PlaybackStatus.Playing ||
        playbackStatus is PlaybackStatus.Paused ||
        playbackStatus is PlaybackStatus.WaitingForTarget
    val modernState = ModernWorkbenchState(
        version = APP_VERSION,
        plan = plan,
        selectedPath = selectedPath,
        performanceMode = performanceMode,
        range = range,
        g3LowestMidi = g3LowestMidi,
        tapDurationMillis = tapDurationMillis,
        mouseInputMode = mouseInputMode,
        processName = processName,
        titleContains = titleContains,
        foregroundWindow = foregroundWindow,
        targetMatches = targetMatches,
        playbackStatus = playbackStatus,
        loading = loading,
        transcribing = transcribing,
        transcriptionMode = transcriptionMode,
        audioAvailability = audioAvailability,
        preview = previewSnapshot,
        playbackProgress = gamePlaybackProgress,
        resumeCursorActive = resumeCursorActive,
        message = message,
        miniPlayerOpen = miniPlayerOpen,
        miniPlayerAlwaysOnTop = miniPlayerAlwaysOnTop,
        themeMode = themeMode,
        libraryFolder = libraryFolder,
        libraryTracks = libraryTracks,
        libraryLoading = libraryLoading,
        librarySearch = librarySearch,
        libraryLoop = libraryLoop,
        libraryAutoNext = libraryAutoNext,
    )
    val modernActions = ModernWorkbenchActions(
        chooseMidi = ::chooseMidi,
        chooseAudio = ::chooseAudio,
        openScoreGuide = { scoreGuideOpen = true },
        openDisclaimer = { disclaimerOpen = true },
        openExternalResource = ::openScoreResource,
        exit = onExit,
        togglePreview = ::togglePreview,
        stopPreview = ::stopPreview,
        seekTimeline = ::seekTimeline,
        startPlayback = ::startPlayback,
        togglePause = { coordinator.togglePause() },
        stopPlayback = coordinator::stop,
        changePerformanceMode = ::updatePerformanceMode,
        changeRange = ::updateRange,
        changeCalibration = ::updateCalibration,
        changeTapDuration = { tapDurationMillis = it },
        changeMouseInputMode = {
            mouseInputMode = it
            message = when (it) {
                MouseInputMode.SEND_INPUT ->
                    "已使用兼容点击 · 坐标按游戏窗口比例计算，系统光标会移动"
                MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL ->
                    "已使用实验性窗口消息 · 光标不移动，但部分游戏版本可能不接收点击"
            }
        },
        changeProcessName = { processName = it },
        changeTitleContains = { titleContains = it },
        changeTranscriptionMode = { transcriptionMode = it },
        toggleMiniPlayer = { miniPlayerOpen = !miniPlayerOpen },
        toggleMiniPlayerAlwaysOnTop = { miniPlayerAlwaysOnTop = !miniPlayerAlwaysOnTop },
        chooseLibraryFolder = ::chooseLibraryFolder,
        refreshLibrary = { refreshLibrary() },
        loadLibraryTrack = { loadLibraryTrack(it) },
        previousLibraryTrack = { stepLibrary(-1) },
        nextLibraryTrack = { stepLibrary(1) },
        changeLibrarySearch = { librarySearch = it },
        toggleLibraryLoop = { libraryLoop = !libraryLoop },
        toggleLibraryAutoNext = { libraryAutoNext = !libraryAutoNext },
        cycleThemeMode = {
            themeMode = when (themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.SYSTEM
            }
        },
    )

    if (true) {
        WorkbenchTheme {
            Box(Modifier.fillMaxSize()) {
                ModernWorkbench(
                    state = modernState,
                    actions = modernActions,
                )
                if (scoreGuideOpen) {
                    ModernScoreGuideDialog(
                        themeMode = themeMode,
                        onClose = { scoreGuideOpen = false },
                        onOpenResource = ::openScoreResource,
                        onImportMidi = {
                            scoreGuideOpen = false
                            chooseMidi()
                        },
                    )
                }
                if (startupDisclaimerRequired) {
                    ModernRiskDisclaimerDialog(
                        themeMode = themeMode,
                        mandatory = true,
                        onAccept = {
                            val persisted = disclaimerStore.acceptCurrentVersion()
                            startupDisclaimerRequired = false
                            message = if (persisted) {
                                "免责声明已确认 · F7 开始，F9 暂停/继续，F8 紧急停止"
                            } else {
                                "免责声明已确认，但无法保存确认状态；下次启动仍会提示"
                            }
                        },
                        onClose = onExit,
                    )
                } else if (disclaimerOpen) {
                    ModernRiskDisclaimerDialog(
                        themeMode = themeMode,
                        mandatory = false,
                        onAccept = { disclaimerOpen = false },
                        onClose = { disclaimerOpen = false },
                    )
                }
            }
            if (miniPlayerOpen) {
                CompactPlayerWindow(
                    state = modernState,
                    actions = modernActions,
                    onClose = { miniPlayerOpen = false },
                )
            }
        }
    } else WorkbenchTheme {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(WorkbenchColors.BackgroundTop, WorkbenchColors.BackgroundBottom),
                ),
            ),
        ) {
            val compactLayout = maxWidth < 1_160.dp
            val controlDeckWidth = if (compactLayout) 326.dp else 360.dp
            Box(
                Modifier.fillMaxWidth().height(2.dp).background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            WorkbenchColors.Amber.copy(alpha = 0.8f),
                            WorkbenchColors.Safe.copy(alpha = 0.5f),
                            Color.Transparent,
                        ),
                    ),
                ),
            )
            Column(
                Modifier.fillMaxSize().padding(
                    horizontal = if (compactLayout) 22.dp else 30.dp,
                    vertical = 22.dp,
                ),
            ) {
                Header(
                    foreground = foregroundWindow,
                    targetMatches = targetMatches,
                    onOpenScoreGuide = { scoreGuideOpen = true },
                    onOpenDisclaimer = { disclaimerOpen = true },
                    onExit = onExit,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 14.dp else 18.dp),
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SongCard(
                            plan = plan,
                            selectedPath = selectedPath,
                            busy = loading || transcribing,
                            transcribing = transcribing,
                            audioAvailability = audioAvailability,
                            transcriptionMode = transcriptionMode,
                            onTranscriptionModeChange = { transcriptionMode = it },
                            onChooseMidi = ::chooseMidi,
                            onChooseAudio = ::chooseAudio,
                        )
                        KeyboardRail(performanceMode, range, g3LowestMidi)
                        ScoreSummary(
                            plan = plan,
                            previewMode = previewMode,
                            preview = previewSnapshot,
                            playbackProgress = gamePlaybackProgress,
                            resumeCursorActive = resumeCursorActive,
                            playbackStatus = playbackStatus,
                            enabled = !playing && !loading && !transcribing,
                            onTogglePreview = ::togglePreview,
                            onStopPreview = ::stopPreview,
                            onSeekPreview = ::seekPreview,
                            onPreviewModeChange = ::updatePreviewMode,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ControlDeck(
                        modifier = Modifier.width(controlDeckWidth).fillMaxHeight(),
                        performanceMode = performanceMode,
                        onPerformanceModeChange = ::updatePerformanceMode,
                        range = range,
                        onRangeChange = ::updateRange,
                        recommendedRange = plan?.recommendedRange,
                        g3LowestMidi = g3LowestMidi,
                        onCalibrationChange = ::updateCalibration,
                        tapDurationMillis = tapDurationMillis,
                        onTapDurationChange = { tapDurationMillis = it },
                        mouseInputMode = mouseInputMode,
                        onMouseInputModeChange = {
                            mouseInputMode = it
                            message = when (it) {
                                MouseInputMode.SEND_INPUT -> "已使用兼容点击；演奏时系统光标会移动"
                                MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL ->
                                    "已使用实验性窗口消息；光标不移动，但游戏可能不接收点击"
                            }
                        },
                        processName = processName,
                        onProcessNameChange = { processName = it },
                        titleContains = titleContains,
                        onTitleContainsChange = { titleContains = it },
                        targetMatches = targetMatches,
                        playing = playing,
                        canPlay = plan?.playable == true && !loading,
                        playbackStatus = playbackStatus,
                        onPlay = ::startPlayback,
                        onTogglePause = { coordinator.togglePause() },
                        onStop = coordinator::stop,
                    )
                }
                Spacer(Modifier.height(14.dp))
                StatusBar(message, playbackStatus)
            }
            if (scoreGuideOpen) {
                ScoreAcquisitionGuide(
                    onClose = { scoreGuideOpen = false },
                    onOpenResource = ::openScoreResource,
                    onImportMidi = {
                        scoreGuideOpen = false
                        chooseMidi()
                    },
                )
            }
            if (startupDisclaimerRequired) {
                RiskDisclaimerDialog(
                    mandatory = true,
                    onAccept = {
                        val persisted = disclaimerStore.acceptCurrentVersion()
                        startupDisclaimerRequired = false
                        message = if (persisted) {
                            "免责声明已确认 · F7 开始，F9 暂停/继续，F8 紧急停止"
                        } else {
                            "免责声明已确认，但无法保存确认状态；下次启动仍会提示"
                        }
                    },
                    onClose = onExit,
                )
            } else if (disclaimerOpen) {
                RiskDisclaimerDialog(
                    mandatory = false,
                    onAccept = { disclaimerOpen = false },
                    onClose = { disclaimerOpen = false },
                )
            }
        }
    }
}

@Composable
private fun Header(
    foreground: ForegroundWindowSnapshot?,
    targetMatches: Boolean,
    onOpenScoreGuide: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onExit: () -> Unit,
) {
    var helpMenuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(3.dp).height(45.dp).clip(CircleShape).background(WorkbenchColors.Amber),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "弦序",
                    fontFamily = FontFamily.Serif,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = WorkbenchColors.Ivory,
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    color = WorkbenchColors.Amber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WorkbenchColors.Amber.copy(alpha = 0.28f)),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "XIANXU",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = WorkbenchColors.Amber,
                    )
                }
            }
            Text(
                "明日之后钢琴助手  ·  23 / 88 键演奏工作台  ·  v$APP_VERSION",
                fontSize = 12.sp,
                color = WorkbenchColors.TextMuted,
                letterSpacing = 0.6.sp,
            )
        }
        Surface(
            color = WorkbenchColors.Amber.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, WorkbenchColors.Amber.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(10.dp),
        ) {
            TextButton(onClick = onOpenScoreGuide) {
                Text("获取乐谱", color = WorkbenchColors.Amber, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            TextButton(onClick = { helpMenuOpen = true }) {
                Text("帮助", color = WorkbenchColors.TextMuted)
            }
            DropdownMenu(
                expanded = helpMenuOpen,
                onDismissRequest = { helpMenuOpen = false },
            ) {
                DropdownMenuItem(
                    onClick = {
                        helpMenuOpen = false
                        onOpenDisclaimer()
                    },
                ) {
                    Text("用户协议与隐私声明")
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ForegroundBadge(foreground, targetMatches)
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onExit) {
            Text("退出", color = WorkbenchColors.TextMuted)
        }
    }
}

@Composable
private fun RiskDisclaimerDialog(
    mandatory: Boolean,
    onAccept: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            .clickable(onClick = { if (!mandatory) onClose() })
            .padding(horizontal = 72.dp, vertical = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 780.dp).heightIn(max = 640.dp).clickable(onClick = {}),
            color = WorkbenchColors.GuidePanel,
            border = BorderStroke(1.dp, WorkbenchColors.Danger.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            elevation = 16.dp,
        ) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow(if (mandatory) "首次使用确认" else "帮助 · 安全与责任")
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (mandatory) "在继续前，请确认使用风险" else RiskDisclaimer.TITLE,
                            fontFamily = FontFamily.Serif,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            color = WorkbenchColors.Ivory,
                        )
                        Text(
                            "声明版本 ${RiskDisclaimer.DISPLAY_VERSION} · 更新于 ${RiskDisclaimer.LAST_UPDATED}",
                            fontSize = 10.sp,
                            color = WorkbenchColors.TextFaint,
                        )
                    }
                    if (!mandatory) {
                        TextButton(onClick = onClose) {
                            Text("关闭", color = WorkbenchColors.TextMuted)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = WorkbenchColors.Danger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WorkbenchColors.Danger.copy(alpha = 0.34f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        RiskDisclaimer.SUMMARY,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WorkbenchColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RiskDisclaimer.sections.forEach { section ->
                        Column {
                            Text(
                                section.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WorkbenchColors.Amber,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                section.body,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = WorkbenchColors.TextMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(WorkbenchColors.Divider))
                Spacer(Modifier.height(14.dp))
                if (mandatory) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onClose) {
                            Text("不同意并退出", color = WorkbenchColors.TextMuted)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onAccept,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = WorkbenchColors.Amber,
                                contentColor = WorkbenchColors.Ink,
                            ),
                            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                        ) {
                            Text("我已阅读并同意", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = WorkbenchColors.Control,
                            contentColor = WorkbenchColors.TextPrimary,
                        ),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    ) {
                        Text("我知道了", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreAcquisitionGuide(
    onClose: () -> Unit,
    onOpenResource: (ExternalScoreResource) -> Unit,
    onImportMidi: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(onClick = onClose)
            .padding(horizontal = 48.dp, vertical = 38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClick = {}),
            color = WorkbenchColors.GuidePanel,
            border = BorderStroke(1.dp, WorkbenchColors.Amber.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(20.dp),
            elevation = 12.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(26.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("曲谱阅览室")
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "获取乐谱",
                            fontFamily = FontFamily.Serif,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = WorkbenchColors.Ivory,
                        )
                        Text(
                            "开放授权曲库优先；外部网站与工具由用户自行选择。",
                            fontSize = 11.sp,
                            color = WorkbenchColors.TextMuted,
                        )
                    }
                    TextButton(onClick = onClose) {
                        Text("关闭", color = WorkbenchColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    OpenLibraryPanel(
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                        onOpenResource = onOpenResource,
                    )
                    ExternalMidiTutorial(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onOpenResource = onOpenResource,
                        onImportMidi = onImportMidi,
                    )
                }
                Spacer(Modifier.height(16.dp))
                TrustNotice()
            }
        }
    }
}

@Composable
private fun OpenLibraryPanel(
    modifier: Modifier,
    onOpenResource: (ExternalScoreResource) -> Unit,
) {
    Surface(
        modifier = modifier,
        color = WorkbenchColors.Amber.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, WorkbenchColors.Amber.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "从许可清晰的作品开始",
                    fontFamily = FontFamily.Serif,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = WorkbenchColors.Ivory,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Mutopia 收录公版及 Creative Commons 乐谱，并提供可下载的 MIDI。每首作品仍需查看其具体许可和署名要求。",
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = WorkbenchColors.TextPrimary,
                )
                Spacer(Modifier.height(18.dp))
                GuideTag("公版古典")
                Spacer(Modifier.height(7.dp))
                GuideTag("开放许可")
                Spacer(Modifier.height(7.dp))
                GuideTag("适合建立个人曲库")
            }
            Button(
                onClick = { onOpenResource(ExternalScoreResource.MUTOPIA) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = WorkbenchColors.Amber,
                    contentColor = WorkbenchColors.Ink,
                ),
                shape = RoundedCornerShape(11.dp),
                elevation = ButtonDefaults.elevation(0.dp),
            ) {
                Text("打开 Mutopia", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExternalMidiTutorial(
    modifier: Modifier,
    onOpenResource: (ExternalScoreResource) -> Unit,
    onImportMidi: () -> Unit,
) {
    WorkbenchSurface(modifier) {
        Column(
            Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "在自己的浏览器中获取外部 MIDI",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = WorkbenchColors.TextPrimary,
            )
            Text(
                "弦序不会自动安装、运行或更新第三方脚本，也不会接触登录信息。",
                fontSize = 10.sp,
                color = WorkbenchColors.TextMuted,
            )
            TutorialStep(
                number = "01",
                title = "选择脚本管理器",
                description = "在浏览器中自行安装一个可信的用户脚本管理器。建议安装前阅读其权限说明。",
            ) {
                CompactGuideButton("Violentmonkey") {
                    onOpenResource(ExternalScoreResource.VIOLENTMONKEY)
                }
                Spacer(Modifier.width(7.dp))
                CompactGuideButton("Tampermonkey") {
                    onOpenResource(ExternalScoreResource.TAMPERMONKEY)
                }
            }
            TutorialStep(
                number = "02",
                title = "阅读并安装第三方脚本",
                description = "前往项目说明页检查源码、适用网站和使用方式；是否安装由你决定。",
            ) {
                CompactGuideButton("查看脚本项目") {
                    onOpenResource(ExternalScoreResource.USERSCRIPT_GUIDE)
                }
            }
            TutorialStep(
                number = "03",
                title = "搜索并确认作品许可",
                description = "登录 MidiShow 搜索曲目，确认页面标注的许可、积分及原作品权利要求。",
            ) {
                CompactGuideButton("打开 MidiShow") {
                    onOpenResource(ExternalScoreResource.MIDISHOW)
                }
            }
            TutorialStep(
                number = "04",
                title = "返回弦序导入",
                description = "下载完成后回到这里选择 .mid 或 .midi 文件，随后即可试听与生成游戏映射。",
            ) {
                CompactGuideButton("导入下载的 MIDI", emphasized = true, onClick = onImportMidi)
            }
        }
    }
}

@Composable
private fun TutorialStep(
    number: String,
    title: String,
    description: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WorkbenchColors.Control.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, WorkbenchColors.Divider),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top) {
            Text(
                number,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WorkbenchColors.Amber,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WorkbenchColors.Ivory,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    color = WorkbenchColors.TextMuted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(content = actions)
        }
    }
}

@Composable
private fun CompactGuideButton(
    text: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (emphasized) WorkbenchColors.Amber else WorkbenchColors.Control,
            contentColor = if (emphasized) WorkbenchColors.Ink else WorkbenchColors.Ivory,
        ),
        border = if (emphasized) null else BorderStroke(1.dp, WorkbenchColors.Divider),
        shape = RoundedCornerShape(9.dp),
        elevation = ButtonDefaults.elevation(0.dp),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun GuideTag(text: String) {
    Surface(
        color = WorkbenchColors.GuidePanel.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, WorkbenchColors.Amber.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 10.sp,
            color = WorkbenchColors.Amber,
        )
    }
}

@Composable
private fun TrustNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WorkbenchColors.Warning.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, WorkbenchColors.Warning.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("来源说明", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WorkbenchColors.Warning)
            Spacer(Modifier.width(12.dp))
            Text(
                "外部网站和脚本与弦序无隶属关系。请仅获取你有权使用的作品，遵守来源网站规则；不要批量抓取、转售或再次传播。",
                fontSize = 9.sp,
                color = WorkbenchColors.TextMuted,
            )
        }
    }
}

@Composable
private fun ForegroundBadge(foreground: ForegroundWindowSnapshot?, targetMatches: Boolean) {
    val color by animateColorAsState(
        if (targetMatches) WorkbenchColors.Safe else WorkbenchColors.Warning,
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 8.dp).widthIn(max = 300.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    if (targetMatches) "游戏已在前台" else "等待切回游戏",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    foreground?.processName ?: "未读取到窗口",
                    fontSize = 10.sp,
                    color = WorkbenchColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SongCard(
    plan: SongPlan?,
    selectedPath: Path?,
    busy: Boolean,
    transcribing: Boolean,
    audioAvailability: AudioEngineAvailability,
    transcriptionMode: AudioTranscriptionMode,
    onTranscriptionModeChange: (AudioTranscriptionMode) -> Unit,
    onChooseMidi: () -> Unit,
    onChooseAudio: () -> Unit,
) {
    WorkbenchSurface(Modifier.fillMaxWidth().height(184.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    WorkbenchColors.Amber.copy(alpha = 0.22f),
                                    WorkbenchColors.Amber.copy(alpha = 0.08f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♪", fontFamily = FontFamily.Serif, fontSize = 30.sp, color = WorkbenchColors.Amber)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            transcribing -> "正在把音频转换为乐谱…"
                            busy -> "正在准备乐谱…"
                            plan != null -> plan.score.name
                            else -> "导入 MIDI 或音乐文件"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WorkbenchColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        selectedPath?.toString() ?: "支持 MIDI、MP3、WAV、FLAC、M4A、OGG",
                        fontSize = 11.sp,
                        color = WorkbenchColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                SongActionButton("导入 MIDI", enabled = !busy, emphasized = true, onClick = onChooseMidi)
                Spacer(Modifier.width(8.dp))
                SongActionButton("音频转谱（测试）", enabled = !busy, onClick = onChooseAudio)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("转谱模式", fontSize = 11.sp, color = WorkbenchColors.TextMuted)
                Spacer(Modifier.width(10.dp))
                AudioTranscriptionMode.entries.forEach { mode ->
                    ChoiceChip(
                        text = mode.displayName(),
                        selected = transcriptionMode == mode,
                        enabled = !busy,
                        onClick = { onTranscriptionModeChange(mode) },
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "测试功能",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WorkbenchColors.Warning,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "机械转谱仅用于初步编配，原生 MIDI 通常更准确",
                    fontSize = 10.sp,
                    color = WorkbenchColors.TextMuted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        audioAvailability.sourceSeparationAvailable -> "人声分离已就绪"
                        audioAvailability.available -> "整曲简化模式"
                        else -> audioAvailability.message
                    },
                    fontSize = 10.sp,
                    color = if (audioAvailability.available) WorkbenchColors.Safe else WorkbenchColors.Warning,
                )
            }
        }
    }
}

@Composable
private fun SongActionButton(
    text: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 42.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (emphasized) WorkbenchColors.Amber else WorkbenchColors.Control,
            contentColor = if (emphasized) WorkbenchColors.Ink else WorkbenchColors.Ivory,
            disabledBackgroundColor = WorkbenchColors.Control.copy(alpha = 0.7f),
            disabledContentColor = WorkbenchColors.TextFaint,
        ),
        border = if (emphasized) null else BorderStroke(1.dp, WorkbenchColors.Divider),
        shape = RoundedCornerShape(11.dp),
        elevation = ButtonDefaults.elevation(0.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KeyboardRail(performanceMode: PerformanceMode, range: GameToneRange, g3LowestMidi: Int) {
    if (performanceMode == PerformanceMode.PIANO_88_MOUSE) {
        Piano88Rail()
        return
    }
    val calibration = remember(g3LowestMidi) { RangeCalibration.fromCenterRangeLowest(g3LowestMidi) }
    val lowest = calibration.lowestMidiNote(range)
    WorkbenchSurface(Modifier.fillMaxWidth().height(170.dp)) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "23 键映射",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WorkbenchColors.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${midiNoteName(lowest)} — ${midiNoteName(lowest + 22)}",
                    fontSize = 11.sp,
                    color = WorkbenchColors.Amber,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(108.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Piano23Layout.keys.forEach { key ->
                    PianoKey(
                        modifier = Modifier.weight(1f),
                        label = key.keyboardLabel,
                        note = midiNoteName(lowest + key.index),
                        dark = key.keyboardLabel in darkKeyLabels,
                    )
                }
            }
        }
    }
}

@Composable
private fun Piano88Rail() {
    WorkbenchSurface(Modifier.fillMaxWidth().height(170.dp)) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "88 键高保真映射",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WorkbenchColors.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text("A0 — C8", fontSize = 11.sp, color = WorkbenchColors.Safe)
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(108.dp),
                color = WorkbenchColors.Control.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, WorkbenchColors.Divider),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "MIDI 21–108 一键对应游戏钢琴 · 不做逐音符八度折叠",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WorkbenchColors.Ivory,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("A0", "C2", "C4", "C6", "C8").forEach { note ->
                            Text(note, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = WorkbenchColors.Amber)
                        }
                    }
                    Text(
                        "请先在游戏中切换到截图所示的 88 键通用模式",
                        fontSize = 11.sp,
                        color = WorkbenchColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PianoKey(modifier: Modifier = Modifier, label: String, note: String, dark: Boolean) {
    val background = if (dark) WorkbenchColors.BlackKey else WorkbenchColors.Ivory
    val foreground = if (dark) WorkbenchColors.Ivory else WorkbenchColors.Ink
    Column(
        modifier.height(if (dark) 90.dp else 112.dp)
            .clip(RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp))
            .background(background)
            .padding(vertical = 8.dp, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            note,
            fontSize = 8.sp,
            color = foreground.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = foreground)
    }
}

@Composable
private fun ScoreSummary(
    plan: SongPlan?,
    previewMode: PreviewMode,
    preview: PreviewSnapshot,
    playbackProgress: PlaybackProgress,
    resumeCursorActive: Boolean,
    playbackStatus: PlaybackStatus,
    enabled: Boolean,
    onTogglePreview: () -> Unit,
    onStopPreview: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onPreviewModeChange: (PreviewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkbenchSurface(modifier.fillMaxWidth()) {
        if (plan == null) {
            EmptyScoreStage()
            return@WorkbenchSurface
        }
        val timelinePosition = if (resumeCursorActive) {
            playbackProgress.positionMicros
        } else {
            preview.positionMicros
        }
        val timelineDuration = plan.score.durationMicros
        val activePlayback = playbackStatus is PlaybackStatus.Countdown ||
            playbackStatus is PlaybackStatus.Playing ||
            playbackStatus is PlaybackStatus.Paused ||
            playbackStatus is PlaybackStatus.WaitingForTarget
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (resumeCursorActive) "游戏演奏进度" else "弹奏预览",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (activePlayback) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = when (playbackStatus) {
                            PlaybackStatus.WaitingForTarget -> WorkbenchColors.Warning.copy(alpha = 0.14f)
                            PlaybackStatus.Paused -> WorkbenchColors.Warning.copy(alpha = 0.14f)
                            else -> WorkbenchColors.Safe.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            when (playbackStatus) {
                                PlaybackStatus.WaitingForTarget -> "切屏等待"
                                PlaybackStatus.Paused -> "已暂停"
                                is PlaybackStatus.Countdown -> "准备中"
                                else -> "演奏中"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            color = when (playbackStatus) {
                                PlaybackStatus.WaitingForTarget, PlaybackStatus.Paused -> WorkbenchColors.Warning
                                else -> WorkbenchColors.Safe
                            },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                SongActionButton(
                    text = if (preview.playing) "暂停" else "试听",
                    enabled = enabled && preview.loaded,
                    onClick = onTogglePreview,
                )
                Spacer(Modifier.width(7.dp))
                SongActionButton(
                    text = "停止",
                    enabled = enabled && (preview.playing || preview.positionMicros > 0),
                    onClick = onStopPreview,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("试听内容", fontSize = 11.sp, color = WorkbenchColors.TextMuted)
                Spacer(Modifier.weight(1f))
                PreviewMode.entries.forEach { mode ->
                    ChoiceChip(
                        text = mode.displayName(),
                        selected = previewMode == mode,
                        enabled = enabled,
                        onClick = { onPreviewModeChange(mode) },
                    )
                    if (mode != PreviewMode.entries.last()) Spacer(Modifier.width(6.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTimestamp(timelinePosition),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = if (resumeCursorActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (resumeCursorActive) WorkbenchColors.Amber else WorkbenchColors.TextMuted,
                )
                Slider(
                    value = if (timelineDuration > 0) {
                        (timelinePosition.toDouble() / timelineDuration).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onValueChange = { fraction ->
                        onSeekPreview((timelineDuration * fraction.coerceIn(0f, 1f)).toLong())
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    enabled = enabled && timelineDuration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = WorkbenchColors.Amber,
                        activeTrackColor = WorkbenchColors.Amber,
                        inactiveTrackColor = WorkbenchColors.Divider,
                    ),
                )
                Text(
                    formatTimestamp(timelineDuration),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = WorkbenchColors.TextMuted,
                )
            }
            Text(
                when {
                    playbackStatus is PlaybackStatus.WaitingForTarget ->
                        "已停止发送输入并冻结时间轴 · 切回游戏后将从 ${formatTimestamp(timelinePosition)} 自动继续"
                    playbackStatus is PlaybackStatus.Paused ->
                        "手动暂停在 ${formatTimestamp(timelinePosition)} · 按 F9 从此处继续"
                    playbackStatus is PlaybackStatus.Stopped && resumeCursorActive ->
                        "断点已保留 · 再次开始将从 ${formatTimestamp(timelinePosition)} 续弹，也可拖动调整"
                    playbackStatus is PlaybackStatus.Failed && resumeCursorActive ->
                        "演奏中止位置已保留 · 修正问题后可从 ${formatTimestamp(timelinePosition)} 续弹"
                    else -> "进度条位置也会作为游戏演奏的起点"
                },
                fontSize = 10.sp,
                color = when (playbackStatus) {
                    PlaybackStatus.WaitingForTarget, PlaybackStatus.Paused -> WorkbenchColors.Warning
                    else -> WorkbenchColors.TextFaint
                },
            )
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("有效音符", plan.projection.events.size.toString(), Modifier.weight(1f))
                Stat("时长", formatDuration(plan.score.durationMicros), Modifier.weight(1f))
                Stat("批次", plan.batches.size.toString(), Modifier.weight(1f))
                Stat(
                    if (plan.performanceMode == PerformanceMode.PIANO_88_MOUSE) "范围外丢弃" else "八度折叠",
                    if (plan.performanceMode == PerformanceMode.PIANO_88_MOUSE) {
                        plan.projection.droppedNotes.toString()
                    } else {
                        plan.projection.foldedNotes.toString()
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        color = WorkbenchColors.Control.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, WorkbenchColors.Divider.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WorkbenchColors.Ivory)
            Text(label, fontSize = 10.sp, color = WorkbenchColors.TextMuted)
        }
    }
}

@Composable
private fun EmptyScoreStage() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow("快速开始")
        Spacer(Modifier.height(5.dp))
        Text(
            "一条乐谱，三步开演",
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WorkbenchColors.Ivory,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WorkflowStep("01", "导入乐谱", "选择 MIDI，或用测试功能从音频生成", Modifier.weight(1f))
            WorkflowStep("02", "试听校验", "确认原始音高或游戏映射是否自然", Modifier.weight(1f))
            WorkflowStep("03", "切回游戏", "倒计时后自动演奏，F8 随时停止", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorkflowStep(number: String, title: String, description: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = WorkbenchColors.Control.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, WorkbenchColors.Divider),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                number,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WorkbenchColors.Amber,
            )
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WorkbenchColors.Ivory)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = WorkbenchColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ControlDeck(
    modifier: Modifier,
    performanceMode: PerformanceMode,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    range: GameToneRange,
    onRangeChange: (GameToneRange) -> Unit,
    recommendedRange: GameToneRange?,
    g3LowestMidi: Int,
    onCalibrationChange: (Int) -> Unit,
    tapDurationMillis: Long,
    onTapDurationChange: (Long) -> Unit,
    mouseInputMode: MouseInputMode,
    onMouseInputModeChange: (MouseInputMode) -> Unit,
    processName: String,
    onProcessNameChange: (String) -> Unit,
    titleContains: String,
    onTitleContainsChange: (String) -> Unit,
    targetMatches: Boolean,
    playing: Boolean,
    canPlay: Boolean,
    playbackStatus: PlaybackStatus,
    onPlay: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    WorkbenchSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Eyebrow("CONTROL DECK")
                Spacer(Modifier.height(4.dp))
                Text(
                    "演奏控制台",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WorkbenchColors.Ivory,
                )
                Text(
                    "配置输入方式与目标窗口",
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextMuted,
                )
                Spacer(Modifier.height(18.dp))
                Text("输入方式", fontSize = 12.sp, color = WorkbenchColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PerformanceMode.entries.forEach { mode ->
                        ChoiceChip(
                            text = mode.displayName(),
                            selected = performanceMode == mode,
                            enabled = !playing,
                            onClick = { onPerformanceModeChange(mode) },
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    when (performanceMode) {
                        PerformanceMode.PIANO_88_MOUSE -> "原音高 A0–C8 · 需要游戏 88 键通用模式"
                        PerformanceMode.KEYBOARD_23 -> "兼容 23 键快捷键 · 超范围音符会八度折叠"
                    },
                    fontSize = 10.sp,
                    color = if (performanceMode == PerformanceMode.PIANO_88_MOUSE) {
                        WorkbenchColors.Safe
                    } else {
                        WorkbenchColors.TextFaint
                    },
                )

                if (performanceMode == PerformanceMode.KEYBOARD_23) {
                    Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音调区间", fontSize = 12.sp, color = WorkbenchColors.TextMuted)
                    Spacer(Modifier.weight(1f))
                    if (recommendedRange != null) {
                        TextButton(
                            onClick = { onRangeChange(recommendedRange) },
                            enabled = !playing && range != recommendedRange,
                        ) {
                            Text("推荐 ${recommendedRange.name}", fontSize = 10.sp, color = WorkbenchColors.Amber)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                SegmentedRange(range, onRangeChange, enabled = !playing)

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("G3 基准最低音", fontSize = 12.sp, color = WorkbenchColors.TextMuted)
                        Text(
                            "${midiNoteName(g3LowestMidi)} · MIDI $g3LowestMidi",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WorkbenchColors.TextPrimary,
                        )
                    }
                    StepButton("−", enabled = !playing) { onCalibrationChange(g3LowestMidi - 1) }
                    Spacer(Modifier.width(6.dp))
                    StepButton("+", enabled = !playing) { onCalibrationChange(g3LowestMidi + 1) }
                }

                Spacer(Modifier.height(18.dp))
                Text("短触发停留", fontSize = 12.sp, color = WorkbenchColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L, 5L, 15L, 35L).forEach { value ->
                        ChoiceChip(
                            text = if (value == 0L) "即时" else "${value}ms",
                            selected = tapDurationMillis == value,
                            enabled = !playing,
                            onClick = { onTapDurationChange(value) },
                        )
                    }
                }
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text("鼠标触发", fontSize = 12.sp, color = WorkbenchColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MouseInputMode.entries.forEach { mode ->
                            ChoiceChip(
                                text = mode.displayName(),
                                selected = mouseInputMode == mode,
                                enabled = !playing,
                                onClick = { onMouseInputModeChange(mode) },
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        when (mouseInputMode) {
                            MouseInputMode.SEND_INPUT ->
                                "兼容性更高；演奏期间系统光标会随音符移动"
                            MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL ->
                                "实验功能 · 光标不移动；若游戏无声音，请切回兼容点击"
                        },
                        fontSize = 10.sp,
                        color = if (mouseInputMode == MouseInputMode.SEND_INPUT) {
                            WorkbenchColors.TextPrimary
                        } else {
                            WorkbenchColors.Warning
                        },
                    )
                }

                Spacer(Modifier.height(18.dp))
                Eyebrow("TARGET WINDOW")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = processName,
                        onValueChange = onProcessNameChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("进程名") },
                        singleLine = true,
                        enabled = !playing,
                    )
                    OutlinedTextField(
                        value = titleContains,
                        onValueChange = onTitleContainsChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("标题包含") },
                        placeholder = { Text("可选") },
                        singleLine = true,
                        enabled = !playing,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(WorkbenchColors.Divider))
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (targetMatches) {
                    WorkbenchColors.Safe.copy(alpha = 0.08f)
                } else {
                    WorkbenchColors.Control.copy(alpha = 0.62f)
                },
                border = BorderStroke(
                    1.dp,
                    if (targetMatches) WorkbenchColors.Safe.copy(alpha = 0.3f) else WorkbenchColors.Divider,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape).background(
                            if (targetMatches) WorkbenchColors.Safe else WorkbenchColors.TextFaint,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            playbackStatus is PlaybackStatus.Countdown ->
                                "请在 ${playbackStatus.secondsRemaining} 秒内切回游戏"
                            playbackStatus is PlaybackStatus.WaitingForTarget ->
                                "已安全暂停 · 切回游戏后从断点自动继续"
                            playbackStatus is PlaybackStatus.Paused -> "演奏已暂停 · F9 继续，F8 停止"
                            targetMatches -> "目标窗口已匹配，可以开始演奏"
                            else -> "开始后有 3 秒时间切回游戏"
                        },
                        fontSize = 11.sp,
                        color = if (targetMatches) WorkbenchColors.Safe else WorkbenchColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (playbackStatus is PlaybackStatus.Playing ||
                playbackStatus is PlaybackStatus.Paused ||
                playbackStatus is PlaybackStatus.WaitingForTarget
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onTogglePause,
                        enabled = playbackStatus !is PlaybackStatus.WaitingForTarget,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = WorkbenchColors.Control,
                            contentColor = WorkbenchColors.Amber,
                        ),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    ) {
                        Text(
                            when (playbackStatus) {
                                PlaybackStatus.Paused -> "继续 · F9"
                                PlaybackStatus.WaitingForTarget -> "等待游戏"
                                else -> "暂停 · F9"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = WorkbenchColors.Danger,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    ) {
                        Text("停止 · F8", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = if (playing) onStop else onPlay,
                    enabled = playing || canPlay,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (playing) WorkbenchColors.Danger else WorkbenchColors.Amber,
                        contentColor = if (playing) Color.White else WorkbenchColors.Ink,
                        disabledBackgroundColor = WorkbenchColors.Control,
                        disabledContentColor = WorkbenchColors.TextMuted,
                    ),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                ) {
                    Text(
                        when (playbackStatus) {
                            is PlaybackStatus.Countdown -> "取消倒计时"
                            else -> "开始演奏 · F7"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedRange(
    selected: GameToneRange,
    onSelected: (GameToneRange) -> Unit,
    enabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(WorkbenchColors.Control)
            .padding(3.dp),
    ) {
        GameToneRange.entries.forEach { range ->
            val active = selected == range
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (active) WorkbenchColors.Ivory else Color.Transparent)
                    .selectable(
                        selected = active,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(range) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    range.name,
                    fontWeight = FontWeight.Bold,
                    color = if (active) WorkbenchColors.Ink else WorkbenchColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> WorkbenchColors.Focus
        selected -> WorkbenchColors.Amber.copy(alpha = 0.7f)
        else -> WorkbenchColors.Divider
    }
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        color = if (selected) WorkbenchColors.Amber.copy(alpha = 0.15f) else WorkbenchColors.Control,
        border = BorderStroke(if (focused) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(9.dp),
    ) {
        Box(Modifier.heightIn(min = 38.dp).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) WorkbenchColors.Amber else WorkbenchColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = WorkbenchColors.Control,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, WorkbenchColors.Divider),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 18.sp, color = WorkbenchColors.Ivory)
        }
    }
}

@Composable
private fun StatusBar(message: String, playbackStatus: PlaybackStatus) {
    val tone = when (playbackStatus) {
        is PlaybackStatus.Failed -> WorkbenchColors.Danger
        PlaybackStatus.Playing, is PlaybackStatus.Countdown -> WorkbenchColors.Amber
        PlaybackStatus.Paused, PlaybackStatus.WaitingForTarget -> WorkbenchColors.Warning
        is PlaybackStatus.Completed -> WorkbenchColors.Safe
        else -> WorkbenchColors.TextMuted
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WorkbenchColors.Panel.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, WorkbenchColors.Divider.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
            Spacer(Modifier.width(8.dp))
            Text(message, fontSize = 11.sp, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("F7 开始  ·  F9 暂停 / 继续  ·  F8 停止", fontSize = 10.sp, color = WorkbenchColors.TextFaint)
        }
    }
}

@Composable
private fun WorkbenchSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = WorkbenchColors.Panel,
        border = BorderStroke(1.dp, WorkbenchColors.PanelBorder),
        shape = RoundedCornerShape(18.dp),
        elevation = 0.dp,
        content = content,
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = WorkbenchColors.Amber,
    )
}

@Composable
private fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = WorkbenchColors.Amber,
            background = WorkbenchColors.BackgroundBottom,
            surface = WorkbenchColors.Panel,
            onPrimary = WorkbenchColors.Ink,
            onBackground = WorkbenchColors.TextPrimary,
            onSurface = WorkbenchColors.TextPrimary,
            error = WorkbenchColors.Danger,
        ),
        content = content,
    )
}

private fun chooseMidiFile(owner: AwtWindow): Path? {
    return chooseNativeFile(
        owner = owner,
        title = "选择 MIDI 乐谱（.mid / .midi）",
        extensions = setOf("mid", "midi"),
    )
}

private fun chooseAudioFile(owner: AwtWindow): Path? {
    return chooseNativeFile(
        owner = owner,
        title = "选择音乐文件（MP3 / WAV / FLAC / M4A / OGG）",
        extensions = setOf("mp3", "wav", "flac", "m4a", "ogg"),
    )
}

private fun chooseDirectory(owner: AwtWindow, initialDirectory: Path?): Path? {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser(initialDirectory?.toFile()).apply {
        dialogTitle = "选择本地 MIDI 音乐库文件夹"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        approveButtonText = "选择文件夹"
    }
    return chooser.takeIf { it.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION }
        ?.selectedFile
        ?.toPath()
        ?.toAbsolutePath()
        ?.normalize()
}

private fun chooseNativeFile(owner: AwtWindow, title: String, extensions: Set<String>): Path? {
    val dialog = when (owner) {
        is Frame -> FileDialog(owner, title, FileDialog.LOAD)
        is Dialog -> FileDialog(owner, title, FileDialog.LOAD)
        else -> FileDialog(null as Frame?, title, FileDialog.LOAD)
    }
    dialog.isMultipleMode = false
    dialog.filenameFilter = java.io.FilenameFilter { _, name ->
        name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT) in extensions
    }
    return try {
        dialog.isVisible = true
        dialog.files.firstOrNull()?.toPath()
    } finally {
        dialog.dispose()
    }
}

private fun AudioTranscriptionMode.displayName(): String = when (this) {
    AudioTranscriptionMode.HIGH_FIDELITY_VOCALS -> "88键·人声"
    AudioTranscriptionMode.HIGH_FIDELITY_ACCOMPANIMENT -> "88键·伴奏"
    AudioTranscriptionMode.MELODY_ONLY -> "人声主旋律"
    AudioTranscriptionMode.MELODY_WITH_CHORDS -> "主旋律＋和弦"
    AudioTranscriptionMode.ACCOMPANIMENT_ONLY -> "纯伴奏"
}

private fun PreviewMode.displayName(): String = when (this) {
    PreviewMode.ORIGINAL_MIDI -> "原始 MIDI"
    PreviewMode.GAME_MAPPING -> "游戏映射"
}

private fun PerformanceMode.displayName(): String = when (this) {
    PerformanceMode.PIANO_88_MOUSE -> "88 键鼠标（推荐）"
    PerformanceMode.KEYBOARD_23 -> "23 键键盘"
}

private fun MouseInputMode.displayName(): String = when (this) {
    MouseInputMode.SEND_INPUT -> "兼容点击（光标移动）"
    MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL -> "窗口消息（实验·光标静止）"
}

private fun PlaybackStatus.userMessage(): String = when (this) {
    PlaybackStatus.Idle -> "已就绪"
    is PlaybackStatus.Countdown -> "倒计时 $secondsRemaining 秒 · 请切回游戏窗口"
    PlaybackStatus.Playing -> "正在演奏 · F9 暂停，F8 紧急停止"
    PlaybackStatus.Paused -> "演奏已暂停 · F9 继续，F8 停止"
    PlaybackStatus.WaitingForTarget -> "游戏不在前台 · 已冻结进度，切回游戏后自动继续"
    is PlaybackStatus.Completed -> "演奏完成 · 已发送 ${report.executedBatches} 个批次"
    PlaybackStatus.Stopped -> "演奏已停止，按键已释放"
    is PlaybackStatus.Failed -> "演奏中止：$message"
}

private fun formatDuration(micros: Long): String {
    val totalSeconds = micros / 1_000_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatTimestamp(micros: Long): String {
    val totalMillis = micros / 1_000
    return "%d:%02d.%03d".format(totalMillis / 60_000, totalMillis / 1_000 % 60, totalMillis % 1_000)
}

private object WorkbenchColors {
    val BackgroundTop = Color(0xFF171B25)
    val BackgroundBottom = Color(0xFF090B10)
    val Panel = Color(0xF21A1E27)
    val PanelBorder = Color(0xFF353B49)
    val Control = Color(0xFF252A35)
    val Divider = Color(0xFF3A4150)
    val Ivory = Color(0xFFFFF3DF)
    val Ink = Color(0xFF17130D)
    val BlackKey = Color(0xFF10131A)
    val Amber = Color(0xFFF2B84B)
    val Safe = Color(0xFF62D4B3)
    val Warning = Color(0xFFF0A85B)
    val Danger = Color(0xFFE56F74)
    val Focus = Color(0xFF8AB4FF)
    val TextPrimary = Color(0xFFF1EAE0)
    val TextMuted = Color(0xFFB7B0A6)
    val TextFaint = Color(0xFF918C84)
    val GuidePanel = Color(0xFF151922)
}

private val darkKeyLabels = setOf("1", "2", "3", "5", "6", "7", "8", "9", "-", "=")

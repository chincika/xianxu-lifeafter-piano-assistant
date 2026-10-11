package dev.gamepiano.autoplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.gamepiano.autoplay.application.PlaybackProgress
import dev.gamepiano.autoplay.application.PlaybackStatus
import dev.gamepiano.autoplay.application.LocalMidiTrack
import dev.gamepiano.autoplay.application.SongPlan
import dev.gamepiano.autoplay.audio.AudioEngineAvailability
import dev.gamepiano.autoplay.audio.AudioTranscriptionMode
import dev.gamepiano.autoplay.input.windows.ForegroundWindowSnapshot
import dev.gamepiano.autoplay.legal.RiskDisclaimer
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.MouseInputMode
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.preview.PreviewSnapshot
import java.nio.file.Path as NioPath

internal data class ModernWorkbenchState(
    val version: String,
    val plan: SongPlan?,
    val selectedPath: NioPath?,
    val performanceMode: PerformanceMode,
    val range: GameToneRange,
    val g3LowestMidi: Int,
    val tapDurationMillis: Long,
    val mouseInputMode: MouseInputMode,
    val processName: String,
    val titleContains: String,
    val foregroundWindow: ForegroundWindowSnapshot?,
    val targetMatches: Boolean,
    val playbackStatus: PlaybackStatus,
    val loading: Boolean,
    val transcribing: Boolean,
    val transcriptionMode: AudioTranscriptionMode,
    val audioAvailability: AudioEngineAvailability,
    val preview: PreviewSnapshot,
    val playbackProgress: PlaybackProgress,
    val resumeCursorActive: Boolean,
    val message: String,
    val miniPlayerOpen: Boolean,
    val miniPlayerAlwaysOnTop: Boolean,
    val themeMode: ThemeMode,
    val libraryFolder: NioPath?,
    val libraryTracks: List<LocalMidiTrack>,
    val libraryLoading: Boolean,
    val librarySearch: String,
    val libraryLoop: Boolean,
    val libraryAutoNext: Boolean,
    val diagnostics: dev.gamepiano.autoplay.diagnostics.DiagnosticSnapshot = dev.gamepiano.autoplay.diagnostics.DiagnosticSnapshot(),
    val diagnosticsBusy: Boolean = false,
    val diagnosticsMessage: String = "",
)

internal data class ModernWorkbenchActions(
    val chooseMidi: () -> Unit,
    val chooseAudio: () -> Unit,
    val openScoreGuide: () -> Unit,
    val openDisclaimer: () -> Unit,
    val openExternalResource: (ExternalScoreResource) -> Unit,
    val exit: () -> Unit,
    val togglePreview: () -> Unit,
    val stopPreview: () -> Unit,
    val seekTimeline: (Long, Boolean) -> Unit,
    val startPlayback: () -> Unit,
    val togglePause: () -> Unit,
    val stopPlayback: () -> Unit,
    val changePerformanceMode: (PerformanceMode) -> Unit,
    val changeRange: (GameToneRange) -> Unit,
    val changeCalibration: (Int) -> Unit,
    val changeTapDuration: (Long) -> Unit,
    val changeMouseInputMode: (MouseInputMode) -> Unit,
    val changeProcessName: (String) -> Unit,
    val changeTitleContains: (String) -> Unit,
    val changeTranscriptionMode: (AudioTranscriptionMode) -> Unit,
    val toggleMiniPlayer: () -> Unit,
    val toggleMiniPlayerAlwaysOnTop: () -> Unit,
    val chooseLibraryFolder: () -> Unit,
    val refreshLibrary: () -> Unit,
    val loadLibraryTrack: (NioPath) -> Unit,
    val previousLibraryTrack: () -> Unit,
    val nextLibraryTrack: () -> Unit,
    val changeLibrarySearch: (String) -> Unit,
    val toggleLibraryLoop: () -> Unit,
    val toggleLibraryAutoNext: () -> Unit,
    val cycleThemeMode: () -> Unit,
    val toggleDiagnostics: () -> Unit = {},
    val exportDiagnostics: () -> Unit = {},
    val openBugReport: () -> Unit = {},
)

private enum class WorkbenchPage(val label: String, val subtitle: String, val glyph: Glyph) {
    PLAYBACK("演奏", "准备乐谱、查看进度并控制演奏", Glyph.PLAY),
    LIBRARY("音乐库", "管理本机 MIDI，像播放列表一样连续选曲", Glyph.LIBRARY),
    SCORES("乐谱", "导入 MIDI 或查看乐谱获取指南", Glyph.SCORE),
    TRANSCRIBE("音频转谱", "将本地音乐转换为可编辑 MIDI", Glyph.AUDIO),
    SETTINGS("设置", "调整输入方式、目标窗口与兼容模式", Glyph.SETTINGS),
    SAFETY("帮助与安全", "快捷键、风险声明与使用说明", Glyph.SHIELD),
}

internal enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    DARK("深色"),
    LIGHT("浅色"),
}

private enum class Glyph {
    PLAY,
    LIBRARY,
    SCORE,
    AUDIO,
    SETTINGS,
    SHIELD,
    THEME,
}

private data class FluentPalette(
    val appBackground: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val control: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val danger: Color,
)

private val DarkPalette = FluentPalette(
    appBackground = Color(0xFF0C1422),
    sidebar = Color(0xE8141D2B),
    surface = Color(0xF21A2535),
    surfaceRaised = Color(0xFF202D40),
    control = Color(0xFF263449),
    border = Color(0xFF34445B),
    textPrimary = Color(0xFFF6F8FC),
    textSecondary = Color(0xFFB7C1D1),
    textTertiary = Color(0xFF8390A4),
    accent = Color(0xFF7667F5),
    accentSoft = Color(0x337667F5),
    success = Color(0xFF56D69B),
    successSoft = Color(0x2256D69B),
    warning = Color(0xFFF2B45E),
    danger = Color(0xFFE8646A),
)

private val LightPalette = FluentPalette(
    appBackground = Color(0xFFF2F5FA),
    sidebar = Color(0xF5F8FAFD),
    surface = Color(0xF9FFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    control = Color(0xFFE9EDF5),
    border = Color(0xFFD5DCE8),
    textPrimary = Color(0xFF162033),
    textSecondary = Color(0xFF4E5B70),
    textTertiary = Color(0xFF748198),
    accent = Color(0xFF6554E8),
    accentSoft = Color(0x1F6554E8),
    success = Color(0xFF16865D),
    successSoft = Color(0x1516865D),
    warning = Color(0xFF9A6418),
    danger = Color(0xFFC83E48),
)

private val LocalFluentPalette = staticCompositionLocalOf { DarkPalette }

@Composable
internal fun ModernWorkbench(
    state: ModernWorkbenchState,
    actions: ModernWorkbenchActions,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(WorkbenchPage.PLAYBACK) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    FluentTheme(darkTheme) {
        val palette = LocalFluentPalette.current
        Row(
            modifier.fillMaxSize().background(palette.appBackground),
        ) {
            Sidebar(
                page = page,
                onPageChange = { page = it },
                themeMode = state.themeMode,
                onThemeModeChange = actions.cycleThemeMode,
                targetMatches = state.targetMatches,
                version = state.version,
            )
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    page = page,
                    state = state,
                    onExit = actions.exit,
                )
                Box(
                    Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 18.dp),
                ) {
                    when (page) {
                        WorkbenchPage.PLAYBACK -> PlaybackPage(
                            state,
                            actions,
                            onOpenSettings = { page = WorkbenchPage.SETTINGS },
                            onOpenLibrary = { page = WorkbenchPage.LIBRARY },
                        )
                        WorkbenchPage.LIBRARY -> LocalMusicLibraryPage(state, actions)
                        WorkbenchPage.SCORES -> ScoresPage(state, actions)
                        WorkbenchPage.TRANSCRIBE -> TranscriptionPage(state, actions)
                        WorkbenchPage.SETTINGS -> SettingsPage(state, actions)
                        WorkbenchPage.SAFETY -> SafetyPage(state, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    page: WorkbenchPage,
    onPageChange: (WorkbenchPage) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: () -> Unit,
    targetMatches: Boolean,
    version: String,
) {
    val palette = LocalFluentPalette.current
    Column(
        Modifier.width(232.dp).fillMaxHeight().background(palette.sidebar)
            .border(BorderStroke(0.5.dp, palette.border.copy(alpha = 0.75f)))
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource("icons/xianxu-64.png"),
                contentDescription = "弦序图标",
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column {
                Text("弦序", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text("钢琴助手", fontSize = 11.sp, color = palette.textTertiary)
            }
        }
        Spacer(Modifier.height(12.dp))
        WorkbenchPage.entries.forEach { item ->
            val selected = item == page
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) palette.accentSoft else Color.Transparent)
                    .clickable(role = Role.Tab) { onPageChange(item) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(3.dp).height(24.dp).clip(CircleShape)
                        .background(if (selected) palette.accent else Color.Transparent),
                )
                Spacer(Modifier.width(10.dp))
                LineGlyph(item.glyph, if (selected) palette.accent else palette.textSecondary)
                Spacer(Modifier.width(11.dp))
                Text(
                    item.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) palette.textPrimary else palette.textSecondary,
                )
                if (item == WorkbenchPage.TRANSCRIBE) {
                    Tag("BETA", palette.accent, palette.accentSoft, compact = true)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        StatusStrip(
            text = if (targetMatches) "游戏在前台" else "等待游戏前台",
            positive = targetMatches,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onThemeModeChange).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineGlyph(Glyph.THEME, palette.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text(themeMode.label, fontSize = 12.sp, color = palette.textSecondary)
        }
        Text(
            "v$version",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = palette.textTertiary,
        )
    }
}

@Composable
private fun PageHeader(
    page: WorkbenchPage,
    state: ModernWorkbenchState,
    onExit: () -> Unit,
) {
    val palette = LocalFluentPalette.current
    Row(
        Modifier.fillMaxWidth().height(106.dp).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(page.label, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(page.subtitle, fontSize = 12.sp, color = palette.textSecondary)
        }
        val status = when (state.playbackStatus) {
            PlaybackStatus.Playing -> "正在演奏"
            PlaybackStatus.Paused -> "演奏已暂停"
            PlaybackStatus.WaitingForTarget -> "等待游戏前台"
            is PlaybackStatus.Countdown -> "${state.playbackStatus.secondsRemaining} 秒后开始"
            else -> if (state.targetMatches) "游戏在前台" else "等待游戏前台"
        }
        StatusStrip(
            text = status,
            positive = state.playbackStatus == PlaybackStatus.Playing || state.targetMatches,
            warning = state.playbackStatus == PlaybackStatus.Paused ||
                state.playbackStatus == PlaybackStatus.WaitingForTarget ||
                state.playbackStatus is PlaybackStatus.Countdown,
        )
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onExit) {
            Text("退出", color = palette.textSecondary)
        }
    }
}

@Composable
private fun PlaybackPage(
    state: ModernWorkbenchState,
    actions: ModernWorkbenchActions,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CurrentScoreCard(state, actions)
        PerformanceProgressCard(state, actions)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 920.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PlaybackQueueCard(
                            state,
                            actions,
                            onOpenLibrary,
                            Modifier.fillMaxWidth().height(126.dp),
                        )
                        CompactPerformanceConfig(
                            state,
                            actions,
                            onOpenSettings,
                            Modifier.fillMaxWidth().height(194.dp),
                        )
                    }
                    Column(
                        Modifier.width(280.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TargetSummary(state, Modifier.fillMaxWidth().height(126.dp))
                        MiniPlayerCard(state, actions, Modifier.fillMaxWidth().height(194.dp))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PlaybackQueueCard(
                        state,
                        actions,
                        onOpenLibrary,
                        Modifier.fillMaxWidth().height(126.dp),
                    )
                    CompactPerformanceConfig(
                        state,
                        actions,
                        onOpenSettings,
                        Modifier.fillMaxWidth().height(194.dp),
                    )
                    TargetSummary(state, Modifier.fillMaxWidth().height(126.dp))
                    MiniPlayerCard(state, actions, Modifier.fillMaxWidth().height(194.dp))
                }
            }
        }
        MessageBar(state.message, state.playbackStatus)
    }
}

@Composable
private fun PlaybackQueueCard(
    state: ModernWorkbenchState,
    actions: ModernWorkbenchActions,
    onOpenLibrary: () -> Unit,
    modifier: Modifier,
) {
    val palette = LocalFluentPalette.current
    val currentPath = state.selectedPath?.toAbsolutePath()?.normalize()
    val currentIndex = state.libraryTracks.indexOfFirst {
        it.path.toAbsolutePath().normalize() == currentPath
    }
    val queueSubtitle = when {
        state.libraryTracks.isEmpty() -> "本地音乐库尚未建立"
        currentIndex >= 0 -> "本地音乐库 · 第 ${currentIndex + 1} / ${state.libraryTracks.size} 首"
        else -> "当前乐谱未加入本地音乐库"
    }
    MicaCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("播放队列", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenLibrary) {
                    Text("打开音乐库  ›", color = palette.accent, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = palette.control,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("MIDI", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.plan?.score?.name ?: "尚未选择乐谱",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(queueSubtitle, fontSize = 9.sp, color = palette.textTertiary)
                }
                TrackStepButton(
                    direction = TrackStepDirection.PREVIOUS,
                    label = "上一首",
                    enabled = state.libraryTracks.isNotEmpty() && !state.loading,
                    onClick = actions.previousLibraryTrack,
                )
                Spacer(Modifier.width(8.dp))
                TrackStepButton(
                    direction = TrackStepDirection.NEXT,
                    label = "下一首",
                    enabled = state.libraryTracks.isNotEmpty() && !state.loading,
                    onClick = actions.nextLibraryTrack,
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.height(48.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    QueueToggleChoice("列表循环", state.libraryLoop, actions.toggleLibraryLoop)
                    QueueToggleChoice("完成后自动下一首", state.libraryAutoNext, actions.toggleLibraryAutoNext)
                }
            }
        }
    }
}

private enum class TrackStepDirection {
    PREVIOUS,
    NEXT,
}

@Composable
private fun TrackStepButton(
    direction: TrackStepDirection,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalFluentPalette.current
    val color = if (enabled) palette.textPrimary else palette.textTertiary
    Surface(
        modifier = Modifier.width(90.dp).height(40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = palette.control.copy(alpha = if (enabled) 1f else 0.52f),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val stroke = 1.6f
                val leftToRight = direction == TrackStepDirection.NEXT
                val barX = if (leftToRight) size.width * 0.78f else size.width * 0.22f
                drawLine(
                    color,
                    Offset(barX, size.height * 0.22f),
                    Offset(barX, size.height * 0.78f),
                    stroke,
                    StrokeCap.Round,
                )
                val path = Path().apply {
                    if (leftToRight) {
                        moveTo(size.width * 0.24f, size.height * 0.22f)
                        lineTo(size.width * 0.67f, size.height * 0.5f)
                        lineTo(size.width * 0.24f, size.height * 0.78f)
                    } else {
                        moveTo(size.width * 0.76f, size.height * 0.22f)
                        lineTo(size.width * 0.33f, size.height * 0.5f)
                        lineTo(size.width * 0.76f, size.height * 0.78f)
                    }
                    close()
                }
                drawPath(path, color)
            }
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 10.sp, color = color)
        }
    }
}

@Composable
private fun CurrentScoreCard(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    MicaCard {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                color = palette.control,
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("MIDI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                }
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("当前乐谱", fontSize = 11.sp, color = palette.textTertiary)
                Spacer(Modifier.height(3.dp))
                Text(
                    state.plan?.score?.name ?: "尚未导入乐谱",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val details = state.plan?.let {
                        "MIDI · ${modernTime(it.score.durationMicros)} · ${it.score.notes.size} 个音符"
                    } ?: "支持 .mid 与 .midi 文件"
                    Text(details, fontSize = 11.sp, color = palette.textSecondary)
                    if (state.plan != null) {
                        Spacer(Modifier.width(10.dp))
                        Tag(
                            if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) "88 键原音高" else "23 键兼容",
                            palette.textSecondary,
                            palette.control,
                        )
                        Spacer(Modifier.width(6.dp))
                        Tag(
                            if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) "A0–C8" else state.range.name,
                            palette.textSecondary,
                            palette.control,
                        )
                    }
                }
            }
            SecondaryButton(
                text = if (state.plan == null) "导入 MIDI" else "更换乐谱",
                enabled = !state.loading && !state.transcribing,
                onClick = actions.chooseMidi,
            )
            Spacer(Modifier.width(8.dp))
            SecondaryButton(
                text = if (state.preview.playing) "暂停试听" else "试听原始 MIDI",
                enabled = state.preview.loaded && !isActivePlayback(state.playbackStatus),
                onClick = actions.togglePreview,
            )
        }
    }
}

@Composable
private fun PerformanceProgressCard(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    val active = isActivePlayback(state.playbackStatus)
    val duration = state.plan?.score?.durationMicros ?: state.preview.durationMicros
    val reportedPosition = when {
        state.resumeCursorActive -> state.playbackProgress.positionMicros
        else -> state.preview.positionMicros
    }.coerceIn(0L, duration.coerceAtLeast(0L))
    var dragFraction by remember(duration) { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (duration > 0L) {
        (reportedPosition.toDouble() / duration).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownPosition = if (duration > 0L) (duration * fraction).toLong() else 0L
    MicaCard(
        border = if (active) palette.accent.copy(alpha = 0.72f) else palette.border,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("演奏进度", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Spacer(Modifier.width(10.dp))
                val statusText = when (state.playbackStatus) {
                    PlaybackStatus.Playing -> "正在演奏"
                    PlaybackStatus.Paused -> "已暂停"
                    PlaybackStatus.WaitingForTarget -> "切屏等待"
                    is PlaybackStatus.Countdown -> "准备中"
                    is PlaybackStatus.Completed -> "已完成"
                    PlaybackStatus.Stopped -> if (state.resumeCursorActive) "断点已保留" else "已停止"
                    is PlaybackStatus.Failed -> "已中止"
                    else -> "准备就绪"
                }
                Tag(
                    statusText,
                    when (state.playbackStatus) {
                        PlaybackStatus.Playing, is PlaybackStatus.Completed -> palette.success
                        PlaybackStatus.Paused, PlaybackStatus.WaitingForTarget,
                        is PlaybackStatus.Countdown -> palette.warning
                        is PlaybackStatus.Failed -> palette.danger
                        else -> palette.textSecondary
                    },
                    when (state.playbackStatus) {
                        PlaybackStatus.Playing, is PlaybackStatus.Completed -> palette.successSoft
                        else -> palette.control
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${modernTime(shownPosition)}  /  ${modernTime(duration)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = palette.textPrimary,
                )
            }
            Spacer(Modifier.height(16.dp))
            NoteDensityStrip(
                plan = state.plan,
                progress = fraction,
                modifier = Modifier.fillMaxWidth().height(34.dp),
            )
            Slider(
                value = fraction,
                onValueChange = { next ->
                    dragFraction = next
                    actions.seekTimeline((duration * next.coerceIn(0f, 1f)).toLong(), false)
                },
                onValueChangeFinished = {
                    val finalFraction = dragFraction ?: fraction
                    dragFraction = null
                    actions.seekTimeline((duration * finalFraction.coerceIn(0f, 1f)).toLong(), true)
                },
                enabled = state.plan != null && duration > 0L && !state.loading && !state.transcribing,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.control,
                    disabledThumbColor = palette.textTertiary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("0:00", fontSize = 10.sp, color = palette.textTertiary)
                Spacer(Modifier.weight(1f))
                Text("拖动进度条可定位 · 拖动时自动暂停", fontSize = 10.sp, color = palette.textTertiary)
                Spacer(Modifier.weight(1f))
                Text(modernTime(duration), fontSize = 10.sp, color = palette.textTertiary)
            }
            Spacer(Modifier.height(17.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    state.playbackStatus == PlaybackStatus.Playing ||
                        state.playbackStatus == PlaybackStatus.Paused ||
                        state.playbackStatus == PlaybackStatus.WaitingForTarget -> {
                        PrimaryButton(
                            text = if (state.playbackStatus == PlaybackStatus.Paused) "继续  F9" else "暂停  F9",
                            enabled = state.playbackStatus != PlaybackStatus.WaitingForTarget,
                            onClick = actions.togglePause,
                            modifier = Modifier.width(180.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        DangerButton(
                            text = "停止  F8",
                            onClick = actions.stopPlayback,
                            modifier = Modifier.width(150.dp),
                        )
                    }
                    state.playbackStatus is PlaybackStatus.Countdown -> {
                        DangerButton(
                            text = "取消倒计时",
                            onClick = actions.stopPlayback,
                            modifier = Modifier.width(180.dp),
                        )
                    }
                    else -> {
                        PrimaryButton(
                            text = if (state.resumeCursorActive && shownPosition > 0L) {
                                "从 ${modernTime(shownPosition)} 继续  F7"
                            } else {
                                "开始演奏  F7"
                            },
                            enabled = state.plan?.playable == true && !state.loading && !state.transcribing,
                            onClick = actions.startPlayback,
                            modifier = Modifier.width(230.dp),
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        when (state.playbackStatus) {
                            PlaybackStatus.Playing -> "正在向游戏发送 88 键鼠标输入"
                            PlaybackStatus.Paused -> "已释放按键，当前位置不会丢失"
                            PlaybackStatus.WaitingForTarget -> "时间轴已冻结，切回游戏后自动继续"
                            else -> "断点自动保留 · F9 暂停/继续 · F8 停止"
                        },
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                    )
                    if (active) {
                        Spacer(Modifier.height(3.dp))
                        Text("拖动松手后将从新位置继续", fontSize = 10.sp, color = palette.textTertiary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactPlayerWindow(
    state: ModernWorkbenchState,
    actions: ModernWorkbenchActions,
    onClose: () -> Unit,
) {
    val windowState = rememberWindowState(width = 430.dp, height = 190.dp)
    val icon = painterResource("icons/xianxu-64.png")
    Window(
        onCloseRequest = onClose,
        title = "弦序 · 迷你演奏",
        state = windowState,
        icon = icon,
        alwaysOnTop = state.miniPlayerAlwaysOnTop,
        resizable = false,
    ) {
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (state.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        FluentTheme(darkTheme) {
            val palette = LocalFluentPalette.current
            val duration = state.plan?.score?.durationMicros ?: 0L
            val position = if (state.resumeCursorActive) {
                state.playbackProgress.positionMicros
            } else {
                state.preview.positionMicros
            }.coerceIn(0L, duration.coerceAtLeast(0L))
            var localFraction by remember(duration) { mutableStateOf<Float?>(null) }
            val fraction = localFraction ?: if (duration > 0L) {
                (position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            Column(
                Modifier.fillMaxSize().background(palette.appBackground)
                    .padding(horizontal = 17.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = icon,
                        contentDescription = "弦序",
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.plan?.score?.name ?: "尚未导入乐谱",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) "88 键鼠标" else "23 键键盘",
                            fontSize = 9.sp,
                            color = palette.success,
                        )
                    }
                    StatusStrip(
                        when (state.playbackStatus) {
                            PlaybackStatus.Playing -> "演奏中"
                            PlaybackStatus.Paused -> "已暂停"
                            PlaybackStatus.WaitingForTarget -> "等待游戏"
                            else -> "已就绪"
                        },
                        positive = state.playbackStatus == PlaybackStatus.Playing,
                        warning = state.playbackStatus == PlaybackStatus.Paused ||
                            state.playbackStatus == PlaybackStatus.WaitingForTarget,
                    )
                }
                Slider(
                    value = fraction,
                    onValueChange = { next ->
                        localFraction = next
                        actions.seekTimeline((duration * next.coerceIn(0f, 1f)).toLong(), false)
                    },
                    onValueChangeFinished = {
                        val finalFraction = localFraction ?: fraction
                        localFraction = null
                        actions.seekTimeline((duration * finalFraction.coerceIn(0f, 1f)).toLong(), true)
                    },
                    enabled = state.plan != null && duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.control,
                    ),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${modernTime((duration * fraction).toLong())} / ${modernTime(duration)}",
                        modifier = Modifier.width(90.dp),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    when (state.playbackStatus) {
                        PlaybackStatus.Playing, PlaybackStatus.Paused, PlaybackStatus.WaitingForTarget -> {
                            SecondaryButton(
                                text = if (state.playbackStatus == PlaybackStatus.Paused) "继续 F9" else "暂停 F9",
                                enabled = state.playbackStatus != PlaybackStatus.WaitingForTarget,
                                onClick = actions.togglePause,
                            )
                            Spacer(Modifier.width(8.dp))
                            DangerButton("停止 F8", actions.stopPlayback, Modifier.width(105.dp))
                        }
                        else -> PrimaryButton(
                            text = "开始 F7",
                            enabled = state.plan?.playable == true,
                            onClick = actions.startPlayback,
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModernScoreGuideDialog(
    themeMode: ThemeMode,
    onClose: () -> Unit,
    onOpenResource: (ExternalScoreResource) -> Unit,
    onImportMidi: () -> Unit,
) {
    ModernOverlayTheme(themeMode) {
        val palette = LocalFluentPalette.current
        OverlayScrim {
            MicaCard(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.82f)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 650.dp)
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("获取乐谱", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text(
                                "外部网站与脚本不由弦序运营，请自行核对授权、来源与使用范围。",
                                fontSize = 10.sp,
                                color = palette.textTertiary,
                            )
                        }
                        TextButton(onClick = onClose) {
                            Text("关闭", color = palette.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    SectionTitle("推荐流程", "所有链接都在系统默认浏览器中打开")
                    Spacer(Modifier.height(9.dp))
                    GuideStep("1", "优先使用开放授权曲库", "Mutopia 等开放曲库更适合学习和公开分享。")
                    GuideStep("2", "自行下载 MIDI", "从其他站点获取时，请确认作品和文件的授权条件。")
                    GuideStep("3", "返回弦序导入", "选择下载的 .mid 或 .midi 文件并先试听原始 MIDI。")
                    Spacer(Modifier.height(16.dp))
                    ExternalScoreResource.entries.forEach { resource ->
                        Row(
                            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp))
                                .background(palette.control.copy(alpha = 0.62f))
                                .clickable { onOpenResource(resource) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                resource.displayName,
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp,
                                color = palette.textPrimary,
                            )
                            Text("在浏览器打开  ↗", fontSize = 10.sp, color = palette.accent)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton("取消", true, onClose)
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton("导入 MIDI", true, onImportMidi, Modifier.width(140.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModernRiskDisclaimerDialog(
    themeMode: ThemeMode,
    mandatory: Boolean,
    onAccept: () -> Unit,
    onClose: () -> Unit,
) {
    ModernOverlayTheme(themeMode) {
        val palette = LocalFluentPalette.current
        OverlayScrim {
            MicaCard(
                Modifier.widthIn(max = 780.dp).fillMaxWidth(0.84f),
                border = palette.warning.copy(alpha = 0.52f),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = palette.warning.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.warning)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (mandatory) "使用前请确认风险" else RiskDisclaimer.TITLE,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                            )
                            Text(
                                "协议版本 ${RiskDisclaimer.DISPLAY_VERSION} · 更新于 ${RiskDisclaimer.LAST_UPDATED}",
                                fontSize = 9.sp,
                                color = palette.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Surface(
                        color = palette.warning.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.28f)),
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Text(
                            RiskDisclaimer.SUMMARY,
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            fontSize = 11.sp,
                            color = palette.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        RiskDisclaimer.sections.forEach { section ->
                            Text(
                                section.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(section.body, fontSize = 10.sp, color = palette.textSecondary)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton(
                            if (mandatory) "不同意并退出" else "关闭",
                            true,
                            onClose,
                        )
                        Spacer(Modifier.width(9.dp))
                        PrimaryButton(
                            if (mandatory) "我已阅读并同意" else "我知道了",
                            true,
                            onAccept,
                            Modifier.width(170.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, body: String) {
    val palette = LocalFluentPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accent)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text(body, fontSize = 9.sp, color = palette.textTertiary)
        }
    }
}

@Composable
private fun OverlayScrim(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ModernOverlayTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    FluentTheme(
        when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        },
        content,
    )
}

@Composable
private fun CompactPerformanceConfig(
    state: ModernWorkbenchState,
    actions: ModernWorkbenchActions,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val palette = LocalFluentPalette.current
    MicaCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("演奏配置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) {
                    Text("调整  ›", color = palette.accent, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) "88 键鼠标" else "23 键键盘",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.width(7.dp))
                if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) {
                    Tag("推荐", palette.accent, palette.accentSoft, compact = true)
                }
                Spacer(Modifier.weight(1f))
                Tag("琴键区域自动适配", palette.success, palette.successSoft, compact = true)
            }
            if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) {
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MouseInputMode.entries.reversed().forEach { mode ->
                        val selected = mode == state.mouseInputMode
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp)
                                .clickable { actions.changeMouseInputMode(mode) },
                            color = if (selected) palette.accentSoft else palette.control,
                            border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
                            shape = RoundedCornerShape(9.dp),
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(15.dp).border(
                                        1.5.dp,
                                        if (selected) palette.accent else palette.textTertiary,
                                        CircleShape,
                                    ).padding(3.dp),
                                ) {
                                    if (selected) {
                                        Box(Modifier.fillMaxSize().clip(CircleShape).background(palette.accent))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    mode.modernName(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) palette.accent else palette.textSecondary,
                                )
                                if (mode == MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL) {
                                    Tag("默认", palette.accent, palette.accentSoft, compact = true)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable {
                        actions.changePerformanceMode(
                            if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) {
                                PerformanceMode.KEYBOARD_23
                            } else {
                                PerformanceMode.PIANO_88_MOUSE
                            },
                        )
                    }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("其他演奏方式", fontSize = 11.sp, color = palette.textTertiary)
                Spacer(Modifier.width(12.dp))
                Text("23 键键盘兼容模式", fontSize = 11.sp, color = palette.textSecondary)
                Spacer(Modifier.weight(1f))
                Text("›", fontSize = 18.sp, color = palette.textTertiary)
            }
        }
    }
}

@Composable
private fun TargetSummary(state: ModernWorkbenchState, modifier: Modifier) {
    val palette = LocalFluentPalette.current
    MicaCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("目标游戏", fontSize = 12.sp, color = palette.textTertiary)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(palette.control),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("游", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("明日之后", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text(state.processName.ifBlank { "未设置进程" }, fontSize = 10.sp, color = palette.textTertiary)
                }
                StatusStrip(if (state.targetMatches) "前台就绪" else "等待前台", state.targetMatches)
            }
        }
    }
}

@Composable
private fun MiniPlayerCard(
    state: ModernWorkbenchState,
    actions: ModernWorkbenchActions,
    modifier: Modifier,
) {
    val palette = LocalFluentPalette.current
    MicaCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(palette.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("▣", fontSize = 15.sp, color = palette.accent)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("迷你悬浮窗", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text("同步进度与演奏控制", fontSize = 9.sp, color = palette.textTertiary)
                }
                StatusStrip(if (state.miniPlayerOpen) "已开启" else "未开启", state.miniPlayerOpen)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToggleChoice(
                    label = "始终置顶",
                    checked = state.miniPlayerAlwaysOnTop,
                    onClick = actions.toggleMiniPlayerAlwaysOnTop,
                )
                Spacer(Modifier.weight(1f))
                SecondaryButton(
                    text = if (state.miniPlayerOpen) "关闭悬浮窗" else "打开悬浮窗",
                    enabled = state.plan != null,
                    onClick = actions.toggleMiniPlayer,
                )
            }
        }
    }
}

@Composable
private fun LocalMusicLibraryPage(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    val query = state.librarySearch.trim()
    val visibleTracks = remember(state.libraryTracks, query) {
        if (query.isBlank()) {
            state.libraryTracks
        } else {
            state.libraryTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.relativeFolder.contains(query, ignoreCase = true)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MicaCard {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(palette.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("▱", fontSize = 22.sp, color = palette.accent)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.libraryFolder?.toString() ?: "尚未指定本地音乐库",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            state.libraryLoading -> "正在递归扫描 .mid / .midi…"
                            state.libraryFolder == null -> "选择一个文件夹后，弦序会记住它"
                            else -> "已找到 ${state.libraryTracks.size} 首 MIDI · 递归扫描子文件夹"
                        },
                        fontSize = 10.sp,
                        color = palette.textTertiary,
                    )
                }
                SecondaryButton("更换文件夹", true, actions.chooseLibraryFolder)
                Spacer(Modifier.width(8.dp))
                SecondaryButton(
                    "刷新",
                    state.libraryFolder != null && !state.libraryLoading,
                    actions.refreshLibrary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.librarySearch,
                onValueChange = actions.changeLibrarySearch,
                label = { Text("搜索本地乐谱") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "显示 ${visibleTracks.size} / ${state.libraryTracks.size} 首",
                fontSize = 10.sp,
                color = palette.textTertiary,
            )
        }
        MicaCard {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("曲名", Modifier.weight(1f), fontSize = 10.sp, color = palette.textTertiary)
                    Text("时长", Modifier.width(70.dp), fontSize = 10.sp, color = palette.textTertiary)
                    Text("音符", Modifier.width(70.dp), fontSize = 10.sp, color = palette.textTertiary)
                    Text("文件夹", Modifier.width(150.dp), fontSize = 10.sp, color = palette.textTertiary)
                }
                if (visibleTracks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.libraryFolder == null) "选择文件夹以建立本地音乐库" else "没有匹配的 MIDI",
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                        )
                    }
                } else {
                    visibleTracks.take(100).forEach { track ->
                        LibraryTrackRow(
                            track = track,
                            selected = state.selectedPath?.toAbsolutePath()?.normalize() ==
                                track.path.toAbsolutePath().normalize(),
                            onClick = { actions.loadLibraryTrack(track.path) },
                        )
                    }
                    if (visibleTracks.size > 100) {
                        Text(
                            "为保持界面流畅，仅显示前 100 首；可使用搜索快速定位。",
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            fontSize = 9.sp,
                            color = palette.textTertiary,
                        )
                    }
                }
            }
        }
        Text(
            "在此选择歌曲；上一首、下一首与循环控制已移至“演奏”主界面的播放队列。",
            fontSize = 10.sp,
            color = palette.textTertiary,
        )
        MessageBar(state.message, state.playbackStatus)
    }
}

@Composable
private fun LibraryTrackRow(track: LocalMidiTrack, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalFluentPalette.current
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .background(if (selected) palette.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (selected) palette.accentSoft else palette.control,
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                "M",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) palette.accent else palette.textSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            track.title,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(modernTime(track.durationMicros), Modifier.width(70.dp), fontSize = 10.sp, color = palette.textSecondary)
        Text(track.noteCount.toString(), Modifier.width(70.dp), fontSize = 10.sp, color = palette.textSecondary)
        Text(
            track.relativeFolder,
            Modifier.width(150.dp),
            fontSize = 10.sp,
            color = palette.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScoresPage(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("导入乐谱", "弦序不会内置或分发受版权保护的 MIDI 文件")
        MicaCard {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("从本机选择 MIDI", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        state.selectedPath?.toString() ?: "支持 .mid 与 .midi；导入后可试听原始 MIDI 并开始演奏",
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PrimaryButton("选择 MIDI", !state.loading, actions.chooseMidi, Modifier.width(140.dp))
            }
        }
        SectionTitle("获取乐谱", "外部资源将在系统默认浏览器中打开，由用户自行判断授权和用途")
        ExternalScoreResource.entries.forEach { resource ->
            MicaCard(
                Modifier.fillMaxWidth().clickable { actions.openExternalResource(resource) },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(resource.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text(resource.url, fontSize = 10.sp, color = palette.textTertiary, maxLines = 1)
                    }
                    Text("在浏览器打开  ↗", fontSize = 11.sp, color = palette.accent)
                }
            }
        }
        SecondaryButton("查看完整获取教程", true, actions.openScoreGuide)
    }
}

@Composable
private fun TranscriptionPage(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = palette.accentSoft,
            border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(11.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("BETA", palette.accent, palette.surface)
                Spacer(Modifier.width(10.dp))
                Text(
                    "机械转谱是测试功能，结果通常不如人工制作的原生 MIDI；请在演奏前先试听检查。",
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                )
            }
        }
        MicaCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("本地音频引擎", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text(state.audioAvailability.message, fontSize = 11.sp, color = palette.textSecondary)
                    }
                    StatusStrip(
                        if (state.audioAvailability.available) "已就绪" else "不可用",
                        state.audioAvailability.available,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("转谱模式", fontSize = 12.sp, color = palette.textTertiary)
                Spacer(Modifier.height(8.dp))
                AudioTranscriptionMode.entries.forEach { mode ->
                    val selected = mode == state.transcriptionMode
                    Row(
                        Modifier.fillMaxWidth().height(47.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (selected) palette.accentSoft else palette.control.copy(alpha = 0.55f))
                            .clickable { actions.changeTranscriptionMode(mode) }.padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(16.dp).border(
                                2.dp,
                                if (selected) palette.accent else palette.textTertiary,
                                CircleShape,
                            ).padding(3.dp),
                        ) {
                            if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(palette.accent))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.modernName(), fontSize = 12.sp, color = palette.textPrimary)
                            Text(mode.modernDescription(), fontSize = 9.sp, color = palette.textTertiary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = if (state.transcribing) "正在转谱…" else "选择音乐并开始转谱",
                    enabled = state.audioAvailability.available && !state.transcribing,
                    onClick = actions.chooseAudio,
                    modifier = Modifier.width(230.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("演奏方式", "88 键鼠标为默认推荐；23 键键盘仅用于兼容旧界面")
        MicaCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceTile(
                        title = "88 键鼠标",
                        subtitle = "完整 A0–C8 音域",
                        selected = state.performanceMode == PerformanceMode.PIANO_88_MOUSE,
                        onClick = { actions.changePerformanceMode(PerformanceMode.PIANO_88_MOUSE) },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceTile(
                        title = "23 键键盘",
                        subtitle = "G1–G6 兼容模式",
                        selected = state.performanceMode == PerformanceMode.KEYBOARD_23,
                        onClick = { actions.changePerformanceMode(PerformanceMode.KEYBOARD_23) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.performanceMode == PerformanceMode.PIANO_88_MOUSE) {
                    Spacer(Modifier.height(14.dp))
                    Text("鼠标点击方式", fontSize = 11.sp, color = palette.textTertiary)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MouseInputMode.entries.forEach { mode ->
                            ChoicePill(
                                text = mode.modernName(),
                                selected = mode == state.mouseInputMode,
                                onClick = { actions.changeMouseInputMode(mode) },
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (state.mouseInputMode == MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL) {
                            "实验模式不会移动系统光标，但部分游戏版本可能拒绝窗口消息。"
                        } else {
                            "兼容模式按游戏窗口客户区比例计算坐标，可适配不同窗口大小和 DPI。"
                        },
                        fontSize = 10.sp,
                        color = palette.textTertiary,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                    Text("游戏音调区间", fontSize = 11.sp, color = palette.textTertiary)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameToneRange.entries.forEach { candidate ->
                            ChoicePill(
                                text = candidate.name,
                                selected = candidate == state.range,
                                onClick = { actions.changeRange(candidate) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G3 基准最低音", fontSize = 11.sp, color = palette.textSecondary)
                        Spacer(Modifier.weight(1f))
                        SecondaryButton("−", state.g3LowestMidi > 24, {
                            actions.changeCalibration(state.g3LowestMidi - 1)
                        })
                        Spacer(Modifier.width(7.dp))
                        Text("MIDI ${state.g3LowestMidi}", fontFamily = FontFamily.Monospace, color = palette.textPrimary)
                        Spacer(Modifier.width(7.dp))
                        SecondaryButton("+", state.g3LowestMidi < 69, {
                            actions.changeCalibration(state.g3LowestMidi + 1)
                        })
                    }
                }
            }
        }
        SectionTitle("目标窗口", "默认兼容 lifeafter.exe / mingrizhihou.exe；切回游戏后匹配前台窗口")
        MicaCard {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.processName,
                    onValueChange = actions.changeProcessName,
                    label = { Text("进程名") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.titleContains,
                    onValueChange = actions.changeTitleContains,
                    label = { Text("标题包含（可选）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
        SectionTitle("触发设置", "短触发停留用于兼容不同游戏和设备")
        MicaCard {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("按键/点击停留", fontSize = 12.sp, color = palette.textSecondary)
                Spacer(Modifier.width(16.dp))
                listOf(0L, 5L, 15L, 35L).forEach { value ->
                    ChoicePill(
                        text = if (value == 0L) "即时" else "${value}ms",
                        selected = state.tapDurationMillis == value,
                        onClick = { actions.changeTapDuration(value) },
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
    }
}

@Composable
internal fun SafetyPage(state: ModernWorkbenchState, actions: ModernWorkbenchActions) {
    val palette = LocalFluentPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("快捷键", "快捷键在演奏期间全局生效")
        MicaCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                HotkeyRow("F7", "开始演奏 / 从保留断点继续")
                HotkeyRow("F9", "暂停或继续当前演奏")
                HotkeyRow("F8", "紧急停止并释放全部按键")
            }
        }
        SectionTitle("诊断日志", "测试时开启，重启后自动关闭")
        MicaCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("记录详细诊断日志", fontSize = 14.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("窗口识别 · 演奏与转谱 · 错误详情", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    androidx.compose.material.Switch(
                        checked = state.diagnostics.enabled,
                        onCheckedChange = { actions.toggleDiagnostics() },
                        enabled = !state.diagnosticsBusy,
                        modifier = Modifier.semantics { contentDescription = "记录详细诊断日志" },
                        colors = androidx.compose.material.SwitchDefaults.colors(checkedThumbColor = palette.accent),
                    )
                }
                val captureStatus = when {
                    state.diagnosticsBusy -> "正在处理…"
                    state.diagnostics.error != null -> "记录异常：${state.diagnostics.error}"
                    state.diagnostics.enabled -> "正在记录 · ${state.diagnostics.recordedEvents} 条事件"
                    state.diagnostics.canExport -> "已停止记录 · 已有 ${state.diagnostics.recordedEvents} 条事件可导出"
                    else -> "未开启 · 暂无日志"
                }
                Text(captureStatus, fontSize = 12.sp, color = if (state.diagnostics.enabled) palette.textPrimary else palette.textSecondary)
                if (state.diagnostics.droppedEvents > 0) {
                    Text("记录繁忙，已跳过 ${state.diagnostics.droppedEvents} 条事件，未阻塞演奏。", fontSize = 12.sp, color = palette.warning)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("导出日志 ZIP", state.diagnostics.canExport && !state.diagnosticsBusy, actions.exportDiagnostics)
                    SecondaryButton("GitHub 反馈", !state.diagnosticsBusy, actions.openBugReport)
                }
                Text("仅本地记录，不自动上传；分享前请检查日志内容。", fontSize = 12.sp, color = palette.textSecondary)
                if (state.diagnosticsMessage.isNotBlank()) {
                    Text(state.diagnosticsMessage, fontSize = 12.sp, color = palette.textPrimary)
                }
            }
        }
        SectionTitle("游戏输入风险", "使用前请了解自动输入可能带来的账号风险")
        Surface(
            color = palette.warning.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.32f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    "本软件会向游戏窗口发送键盘或鼠标输入，可能被游戏识别为异常行为。用户应自行判断并承担账号处罚风险。",
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton("阅读完整免责声明", true, actions.openDisclaimer)
            }
        }
        SectionTitle("当前环境", "用于排查目标窗口和输入状态")
        MicaCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                InfoRow("当前前台进程", state.foregroundWindow?.processName ?: "未知")
                InfoRow("当前窗口标题", state.foregroundWindow?.title?.ifBlank { "无标题" } ?: "未知")
                InfoRow("目标匹配", if (state.targetMatches) "是" else "否")
                InfoRow("应用版本", state.version)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("乐谱获取教程", true, actions.openScoreGuide)
            SecondaryButton("退出弦序", true, actions.exit)
        }
    }
}

@Composable
private fun MicaCard(
    modifier: Modifier = Modifier,
    border: Color = LocalFluentPalette.current.border,
    content: @Composable () -> Unit,
) {
    val palette = LocalFluentPalette.current
    Surface(
        modifier = modifier,
        color = palette.surface,
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(12.dp),
        elevation = 0.dp,
        content = content,
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalFluentPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = palette.accent,
            contentColor = Color.White,
            disabledBackgroundColor = palette.control,
            disabledContentColor = palette.textTertiary,
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalFluentPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = palette.control,
            contentColor = palette.textPrimary,
            disabledBackgroundColor = palette.control.copy(alpha = 0.5f),
            disabledContentColor = palette.textTertiary,
        ),
        border = BorderStroke(1.dp, palette.border),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 13.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalFluentPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = palette.danger,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChoiceTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val palette = LocalFluentPalette.current
    Surface(
        modifier = modifier.height(68.dp).clickable(onClick = onClick),
        color = if (selected) palette.accentSoft else palette.control,
        border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text(subtitle, fontSize = 10.sp, color = palette.textTertiary)
        }
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalFluentPalette.current
    Surface(
        modifier = Modifier.height(37.dp).clickable(onClick = onClick),
        color = if (selected) palette.accentSoft else palette.control,
        border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(Modifier.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) palette.accent else palette.textSecondary,
            )
        }
    }
}

@Composable
private fun ToggleChoice(label: String, checked: Boolean, onClick: () -> Unit) {
    val palette = LocalFluentPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.width(31.dp).height(18.dp),
            color = if (checked) palette.accent else palette.control,
            border = BorderStroke(1.dp, if (checked) palette.accent else palette.border),
            shape = RoundedCornerShape(50),
        ) {
            Box(Modifier.fillMaxSize().padding(2.dp)) {
                Box(
                    Modifier.size(12.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(if (checked) Color.White else palette.textTertiary),
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontSize = 9.sp,
            color = if (checked) palette.textPrimary else palette.textSecondary,
        )
    }
}

@Composable
private fun QueueToggleChoice(label: String, checked: Boolean, onClick: () -> Unit) {
    val palette = LocalFluentPalette.current
    Row(
        Modifier.height(22.dp).clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.width(27.dp).height(15.dp),
            color = if (checked) palette.accent else palette.control,
            border = BorderStroke(1.dp, if (checked) palette.accent else palette.border),
            shape = RoundedCornerShape(50),
        ) {
            Box(Modifier.fillMaxSize().padding(2.dp)) {
                Box(
                    Modifier.size(9.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(if (checked) Color.White else palette.textTertiary),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 8.sp, color = palette.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Tag(
    text: String,
    foreground: Color,
    background: Color,
    compact: Boolean = false,
) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
        )
    }
}

@Composable
private fun StatusStrip(text: String, positive: Boolean, warning: Boolean = false) {
    val palette = LocalFluentPalette.current
    val color = when {
        warning -> palette.warning
        positive -> palette.success
        else -> palette.textTertiary
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(7.dp))
            Text(text, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun MessageBar(message: String, playbackStatus: PlaybackStatus) {
    val palette = LocalFluentPalette.current
    val color = when (playbackStatus) {
        is PlaybackStatus.Failed -> palette.danger
        PlaybackStatus.Paused, PlaybackStatus.WaitingForTarget, is PlaybackStatus.Countdown -> palette.warning
        PlaybackStatus.Playing, is PlaybackStatus.Completed -> palette.success
        else -> palette.textSecondary
    }
    Surface(
        color = palette.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f), fontSize = 10.sp, color = color, maxLines = 2)
            Text("F7 开始 · F9 暂停/继续 · F8 停止", fontSize = 9.sp, color = palette.textTertiary)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    val palette = LocalFluentPalette.current
    Column {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(subtitle, fontSize = 10.sp, color = palette.textTertiary)
    }
}

@Composable
private fun HotkeyRow(key: String, description: String) {
    val palette = LocalFluentPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = palette.control, border = BorderStroke(1.dp, palette.border), shape = RoundedCornerShape(7.dp)) {
            Text(
                key,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(description, fontSize = 11.sp, color = palette.textSecondary)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalFluentPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, modifier = Modifier.width(130.dp), fontSize = 11.sp, color = palette.textTertiary)
        Text(value, modifier = Modifier.weight(1f), fontSize = 11.sp, color = palette.textPrimary, maxLines = 1)
    }
}

@Composable
private fun DividerDot() {
    val palette = LocalFluentPalette.current
    Box(Modifier.size(3.dp).clip(CircleShape).background(palette.textTertiary))
}

@Composable
private fun NoteDensityStrip(plan: SongPlan?, progress: Float, modifier: Modifier = Modifier) {
    val palette = LocalFluentPalette.current
    val density = remember(plan) { noteDensityBuckets(plan) }
    Canvas(modifier) {
        val count = density.size
        val gap = size.width / count
        val currentIndex = (progress.coerceIn(0f, 1f) * count)
            .toInt()
            .coerceIn(0, count - 1)
        density.forEachIndexed { index, value ->
            val height = size.height * (0.16f + value * 0.82f)
            val x = index * gap + gap / 2
            val color = when {
                index < currentIndex -> palette.accent.copy(alpha = 0.74f)
                index == currentIndex -> palette.accent
                else -> palette.border.copy(alpha = 0.70f)
            }
            if (index == currentIndex && plan != null) {
                drawLine(
                    color = palette.accent.copy(alpha = 0.18f),
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - height),
                    strokeWidth = (gap * 1.45f).coerceAtLeast(5f),
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x, size.height - height),
                strokeWidth = (gap * if (index == currentIndex) 0.62f else 0.46f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
        }
    }
}

internal fun noteDensityBuckets(plan: SongPlan?, count: Int = 64): List<Float> {
    if (plan == null) {
        return List(count) { 0f }
    }
    return noteDensityBuckets(plan.batches, plan.score.durationMicros, count)
}

internal fun noteDensityBuckets(
    batches: List<dev.gamepiano.autoplay.model.KeyBatch>,
    durationMicros: Long,
    count: Int = 64,
): List<Float> {
    require(count > 0)
    if (durationMicros <= 0L || batches.isEmpty()) return List(count) { 0f }
    val duration = durationMicros.toDouble()
    val totals = FloatArray(count)
    batches.forEach { batch ->
        val index = ((batch.atMicros / duration) * count).toInt().coerceIn(0, count - 1)
        totals[index] += 1f + (batch.keys.size - 1).coerceAtLeast(0) * 0.42f
    }
    val peak = totals.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    return totals.map { raw ->
        if (raw <= 0f) 0f else kotlin.math.sqrt(raw / peak).coerceIn(0f, 1f)
    }
}

@Composable
private fun LineGlyph(glyph: Glyph, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * 0.34f, size.height * 0.23f)
                    lineTo(size.width * 0.78f, size.height * 0.5f)
                    lineTo(size.width * 0.34f, size.height * 0.77f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            Glyph.LIBRARY -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.25f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f),
                    style = stroke,
                )
                val tab = Path().apply {
                    moveTo(size.width * 0.20f, size.height * 0.25f)
                    lineTo(size.width * 0.31f, size.height * 0.13f)
                    lineTo(size.width * 0.51f, size.height * 0.13f)
                    lineTo(size.width * 0.60f, size.height * 0.25f)
                }
                drawPath(tab, color, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.48f, size.height * 0.55f))
                drawLine(
                    color,
                    Offset(size.width * 0.55f, size.height * 0.42f),
                    Offset(size.width * 0.55f, size.height * 0.63f),
                    1.7f,
                    StrokeCap.Round,
                )
            }
            Glyph.SCORE -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.14f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f),
                    style = stroke,
                )
                repeat(3) { index ->
                    drawLine(
                        color,
                        Offset(size.width * 0.34f, size.height * (0.38f + index * 0.15f)),
                        Offset(size.width * 0.66f, size.height * (0.38f + index * 0.15f)),
                        1.5f,
                        StrokeCap.Round,
                    )
                }
            }
            Glyph.AUDIO -> {
                val heights = listOf(0.38f, 0.72f, 0.95f, 0.58f, 0.82f, 0.34f)
                heights.forEachIndexed { index, value ->
                    val x = size.width * (0.16f + index * 0.135f)
                    drawLine(
                        color,
                        Offset(x, size.height * (0.5f - value / 2)),
                        Offset(x, size.height * (0.5f + value / 2)),
                        1.7f,
                        StrokeCap.Round,
                    )
                }
            }
            Glyph.SETTINGS -> {
                drawCircle(color, radius = size.minDimension * 0.28f, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.08f, style = stroke)
                repeat(8) { index ->
                    val angle = Math.PI * index / 4
                    drawLine(
                        color,
                        Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * size.width * 0.33f,
                            center.y + kotlin.math.sin(angle).toFloat() * size.height * 0.33f,
                        ),
                        Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * size.width * 0.43f,
                            center.y + kotlin.math.sin(angle).toFloat() * size.height * 0.43f,
                        ),
                        1.6f,
                        StrokeCap.Round,
                    )
                }
            }
            Glyph.SHIELD -> {
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.12f)
                    lineTo(size.width * 0.78f, size.height * 0.24f)
                    lineTo(size.width * 0.73f, size.height * 0.66f)
                    quadraticTo(size.width * 0.64f, size.height * 0.82f, size.width * 0.5f, size.height * 0.9f)
                    quadraticTo(size.width * 0.36f, size.height * 0.82f, size.width * 0.27f, size.height * 0.66f)
                    lineTo(size.width * 0.22f, size.height * 0.24f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            Glyph.THEME -> {
                drawCircle(color, radius = size.minDimension * 0.34f, style = stroke)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    style = Stroke(width = 4f),
                )
            }
        }
    }
}

@Composable
internal fun FluentTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colors = if (darkTheme) {
        darkColors(
            primary = palette.accent,
            secondary = palette.success,
            background = palette.appBackground,
            surface = palette.surface,
            onPrimary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            error = palette.danger,
        )
    } else {
        lightColors(
            primary = palette.accent,
            secondary = palette.success,
            background = palette.appBackground,
            surface = palette.surface,
            onPrimary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            error = palette.danger,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalFluentPalette provides palette) {
        MaterialTheme(colors = colors, content = content)
    }
}

private fun isActivePlayback(status: PlaybackStatus): Boolean =
    status is PlaybackStatus.Countdown ||
        status == PlaybackStatus.Playing ||
        status == PlaybackStatus.Paused ||
        status == PlaybackStatus.WaitingForTarget

private fun modernTime(micros: Long): String {
    val totalSeconds = micros.coerceAtLeast(0L) / 1_000_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun MouseInputMode.modernName(): String = when (this) {
    MouseInputMode.SEND_INPUT -> "移动鼠标"
    MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL -> "无光标移动（实验）"
}

private fun AudioTranscriptionMode.modernName(): String = when (this) {
    AudioTranscriptionMode.HIGH_FIDELITY_VOCALS -> "88 键 · 人声"
    AudioTranscriptionMode.HIGH_FIDELITY_ACCOMPANIMENT -> "88 键 · 伴奏"
    AudioTranscriptionMode.MELODY_ONLY -> "人声主旋律"
    AudioTranscriptionMode.MELODY_WITH_CHORDS -> "主旋律 + 和弦"
    AudioTranscriptionMode.ACCOMPANIMENT_ONLY -> "纯伴奏"
}

private fun AudioTranscriptionMode.modernDescription(): String = when (this) {
    AudioTranscriptionMode.HIGH_FIDELITY_VOCALS -> "先分离人声，再保留完整音高范围"
    AudioTranscriptionMode.HIGH_FIDELITY_ACCOMPANIMENT -> "先分离伴奏，再保留完整音高范围"
    AudioTranscriptionMode.MELODY_ONLY -> "提取较清晰的单声部旋律"
    AudioTranscriptionMode.MELODY_WITH_CHORDS -> "兼顾主旋律与主要和声"
    AudioTranscriptionMode.ACCOMPANIMENT_ONLY -> "弱化人声并提取节奏伴奏"
}

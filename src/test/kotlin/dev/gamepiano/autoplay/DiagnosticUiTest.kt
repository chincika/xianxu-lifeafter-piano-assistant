package dev.gamepiano.autoplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.gamepiano.autoplay.application.PlaybackProgress
import dev.gamepiano.autoplay.application.PlaybackStatus
import dev.gamepiano.autoplay.audio.AudioEngineAvailability
import dev.gamepiano.autoplay.audio.AudioTranscriptionMode
import dev.gamepiano.autoplay.diagnostics.DiagnosticSnapshot
import dev.gamepiano.autoplay.model.GameToneRange
import dev.gamepiano.autoplay.model.MouseInputMode
import dev.gamepiano.autoplay.model.PerformanceMode
import dev.gamepiano.autoplay.preview.PreviewSnapshot
import dev.gamepiano.autoplay.ui.FluentTheme
import dev.gamepiano.autoplay.ui.ModernWorkbenchActions
import dev.gamepiano.autoplay.ui.ModernWorkbenchState
import dev.gamepiano.autoplay.ui.SafetyPage
import dev.gamepiano.autoplay.ui.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class DiagnosticUiTest {
    @Test
    fun `help page renders diagnostics in both themes and scaled viewport`() {
        for ((name, dark, scale) in listOf(Triple("light", false, 1f), Triple("dark", true, 1f), Triple("scaled", true, 1.5f))) {
            val state = sampleState().copy(diagnostics = DiagnosticSnapshot(enabled = dark, recordedEvents = if (dark) 126 else 0))
            val scene = ImageComposeScene(width = (1000 * scale).toInt(), height = (900 * scale).toInt(), density = Density(scale)) {
                FluentTheme(dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(24.dp)) {
                        SafetyPage(state, actions())
                    }
                }
            }
            try {
                scene.render().close()
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                        val out = Path.of("build/reports/diagnostic-ui/$name.png")
                        Files.createDirectories(out.parent)
                        Files.write(out, data.bytes)
                    }
                }
                val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
                assertTrue(texts.contains("诊断日志"))
                assertTrue(texts.contains("导出日志 ZIP"))
                assertTrue(texts.contains("GitHub 反馈"))
                assertTrue(nodes.any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("记录详细诊断日志") == true })
            } finally {
                scene.close()
            }
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun sampleState() = ModernWorkbenchState(
        version = "0.1.0-beta.5", plan = null, selectedPath = null,
        performanceMode = PerformanceMode.PIANO_88_MOUSE, range = GameToneRange.G3, g3LowestMidi = 54,
        tapDurationMillis = 0, mouseInputMode = MouseInputMode.WINDOW_MESSAGE_EXPERIMENTAL,
        processName = "lifeafter.exe", titleContains = "", foregroundWindow = null, targetMatches = false,
        playbackStatus = PlaybackStatus.Idle, loading = false, transcribing = false,
        transcriptionMode = AudioTranscriptionMode.MELODY_WITH_CHORDS,
        audioAvailability = AudioEngineAvailability(false, null, false, "未安装本地音频引擎"),
        preview = PreviewSnapshot(false, false, 0, 0), playbackProgress = PlaybackProgress(0, 0),
        resumeCursorActive = false, message = "", miniPlayerOpen = false, miniPlayerAlwaysOnTop = true,
        themeMode = ThemeMode.LIGHT, libraryFolder = null, libraryTracks = emptyList(),
        libraryLoading = false, librarySearch = "", libraryLoop = true, libraryAutoNext = false,
    )

    private fun actions() = ModernWorkbenchActions(
        chooseMidi = {}, chooseAudio = {}, openScoreGuide = {}, openDisclaimer = {}, openExternalResource = {}, exit = {},
        togglePreview = {}, stopPreview = {}, seekTimeline = { _, _ -> }, startPlayback = {}, togglePause = {}, stopPlayback = {},
        changePerformanceMode = {}, changeRange = {}, changeCalibration = {}, changeTapDuration = {}, changeMouseInputMode = {},
        changeProcessName = {}, changeTitleContains = {}, changeTranscriptionMode = {}, toggleMiniPlayer = {}, toggleMiniPlayerAlwaysOnTop = {},
        chooseLibraryFolder = {}, refreshLibrary = {}, loadLibraryTrack = {}, previousLibraryTrack = {}, nextLibraryTrack = {},
        changeLibrarySearch = {}, toggleLibraryLoop = {}, toggleLibraryAutoNext = {}, cycleThemeMode = {},
    )
}

package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.audio.AudioTranscriptionMode
import dev.gamepiano.autoplay.audio.AudioWorkerSummary
import dev.gamepiano.autoplay.audio.audioPythonCandidates
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTranscriptionServiceTest {
    @Test
    fun `parses the worker result without depending on output path characters`() {
        val summary = AudioWorkerSummary.parse(
            """{"output":"C:\\\\音乐\\\\测试.mid","notes":137,"bpm":98.44,"separated":true}""",
        )

        assertEquals(137, summary.noteCount)
        assertEquals(98.44, summary.estimatedBpm)
        assertTrue(summary.usedSourceSeparation)
    }

    @Test
    fun `audio modes expose stable worker arguments`() {
        assertEquals("high-fidelity-vocals", AudioTranscriptionMode.HIGH_FIDELITY_VOCALS.argument)
        assertEquals(
            "high-fidelity-accompaniment",
            AudioTranscriptionMode.HIGH_FIDELITY_ACCOMPANIMENT.argument,
        )
        assertEquals("melody", AudioTranscriptionMode.MELODY_ONLY.argument)
        assertEquals("balanced", AudioTranscriptionMode.MELODY_WITH_CHORDS.argument)
        assertEquals("accompaniment", AudioTranscriptionMode.ACCOMPANIMENT_ONLY.argument)
    }

    @Test
    fun `bundled audio runtime is preferred over per-user installation`() {
        val candidates = audioPythonCandidates(
            environment = mapOf("LOCALAPPDATA" to "C:\\Users\\tester\\AppData\\Local"),
            workingDirectory = Path.of("C:\\work"),
            javaHome = Path.of("C:\\portable\\GamePianoAutoplay\\runtime"),
            applicationPath = "C:\\portable\\GamePianoAutoplay\\GamePianoAutoplay.exe",
        )

        assertEquals(
            "C:\\portable\\GamePianoAutoplay\\audio-runtime\\python.exe",
            candidates.first(),
        )
        assertTrue(
            candidates.indexOf("C:\\portable\\GamePianoAutoplay\\audio-runtime\\python.exe") <
                candidates.indexOf("C:\\Users\\tester\\AppData\\Local\\GamePianoAutoplay\\audio-runtime\\python.exe"),
        )
    }
}

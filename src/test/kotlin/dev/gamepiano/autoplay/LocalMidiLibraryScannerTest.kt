package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.application.LocalMidiLibraryScanner
import dev.gamepiano.autoplay.midi.ExampleMidiGenerator
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalMidiLibraryScannerTest {
    @Test
    fun scansMidiRecursivelyAndIgnoresOtherFiles() {
        val root = Files.createTempDirectory("xianxu-library-test")
        val nested = root.resolve("收藏").createDirectories()
        val first = root.resolve("b.mid")
        val second = nested.resolve("a.midi")
        ExampleMidiGenerator.writeTwinkleTwinkle(first)
        ExampleMidiGenerator.writeTwinkleTwinkle(second)
        Files.writeString(root.resolve("broken.mid"), "not a MIDI file")
        root.resolve("readme.txt").createFile()

        val tracks = LocalMidiLibraryScanner().scan(root)

        assertEquals(listOf("a", "b"), tracks.map { it.title })
        assertEquals("收藏", tracks.first().relativeFolder)
        assertEquals("/", tracks.last().relativeFolder)
        assertEquals(true, tracks.all { it.noteCount > 0 && it.durationMicros > 0 })
    }
}

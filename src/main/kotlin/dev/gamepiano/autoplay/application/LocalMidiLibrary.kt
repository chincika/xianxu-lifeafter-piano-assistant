package dev.gamepiano.autoplay.application

import dev.gamepiano.autoplay.midi.MidiFileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.prefs.Preferences
import kotlin.io.path.extension

data class LocalMidiTrack(
    val path: Path,
    val title: String,
    val durationMicros: Long,
    val noteCount: Int,
    val relativeFolder: String,
)

class LocalMidiLibraryScanner {
    fun scan(folder: Path): List<LocalMidiTrack> {
        val root = folder.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "音乐库文件夹不存在：$root" }

        val midiPaths = Files.walk(root).use { paths ->
            paths
                .filter { path ->
                    Files.isRegularFile(path) &&
                        path.extension.lowercase(Locale.ROOT) in MIDI_EXTENSIONS
                }
                .toList()
        }
        return midiPaths
            .mapNotNull { path ->
                runCatching {
                    val normalized = path.toAbsolutePath().normalize()
                    val score = MidiFileReader.read(normalized)
                    val parent = normalized.parent
                    val relativeFolder = if (parent == null || parent == root) {
                        "/"
                    } else {
                        root.relativize(parent).toString()
                    }
                    LocalMidiTrack(
                        path = normalized,
                        title = normalized.fileName.toString().substringBeforeLast('.'),
                        durationMicros = score.durationMicros,
                        noteCount = score.notes.size,
                        relativeFolder = relativeFolder,
                    )
                }.getOrNull()
            }
            .sortedWith(
                compareBy<LocalMidiTrack>(
                    { it.title.lowercase(Locale.ROOT) },
                    { it.path.toString().lowercase(Locale.ROOT) },
                ),
            )
    }

    private companion object {
        val MIDI_EXTENSIONS = setOf("mid", "midi")
    }
}

class LocalMidiLibraryPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(
        LocalMidiLibraryPreferences::class.java,
    ),
) {
    fun loadFolder(): Path? = preferences.get(FOLDER_KEY, null)
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?.toAbsolutePath()
        ?.normalize()
        ?.takeIf(Files::isDirectory)

    fun saveFolder(folder: Path) {
        preferences.put(FOLDER_KEY, folder.toAbsolutePath().normalize().toString())
        runCatching(preferences::flush)
    }

    private companion object {
        const val FOLDER_KEY = "local-midi-library-folder"
    }
}

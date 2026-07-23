import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.11.0"
}

group = "dev.gamepiano"
version = "0.1.0-beta.2"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material:1.11.0")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val stagePortableApp by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("portable-input"))
    from(tasks.jar)
    from(configurations.runtimeClasspath)
}

val cleanPortableAppImage by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("portable/GamePianoAutoplay"))
}

tasks.register<Exec>("createPortableAppImage") {
    dependsOn(stagePortableApp, cleanPortableAppImage)
    executable = file("${System.getProperty("java.home")}/bin/jpackage.exe").absolutePath
    args(
        "--type", "app-image",
        "--input", layout.buildDirectory.dir("portable-input").get().asFile.absolutePath,
        "--dest", layout.buildDirectory.dir("portable").get().asFile.absolutePath,
        "--name", "GamePianoAutoplay",
        "--main-jar", "game-piano-autoplay-${project.version}.jar",
        "--main-class", "dev.gamepiano.autoplay.app.MainKt",
        "--app-version", "0.1.0",
        "--vendor", "Game Piano Learning Project",
        "--description", "LifeAfter 23/88-key MIDI autoplay workstation",
    )
}

compose.desktop {
    application {
        mainClass = "dev.gamepiano.autoplay.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "GamePianoAutoplay"
            packageVersion = "0.1.0"
            description = "明日之后 23/88 键乐器 MIDI 自动演奏工具"
            vendor = "Game Piano Learning Project"
        }
    }
}

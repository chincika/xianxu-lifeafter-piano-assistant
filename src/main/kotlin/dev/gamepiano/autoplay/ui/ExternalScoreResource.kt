package dev.gamepiano.autoplay.ui

import java.awt.Desktop
import java.net.URI

internal enum class ExternalScoreResource(
    val displayName: String,
    val url: String,
) {
    MUTOPIA(
        displayName = "Mutopia 开放曲库",
        url = "https://www.mutopiaproject.org/",
    ),
    MIDISHOW(
        displayName = "MidiShow",
        url = "https://www.midishow.com/",
    ),
    USERSCRIPT_GUIDE(
        displayName = "第三方脚本项目",
        url = "https://github.com/Saobby/midishow-downloader-js",
    ),
    VIOLENTMONKEY(
        displayName = "Violentmonkey",
        url = "https://violentmonkey.github.io/",
    ),
    TAMPERMONKEY(
        displayName = "Tampermonkey",
        url = "https://www.tampermonkey.net/",
    ),
}

internal fun openExternalScoreResource(
    resource: ExternalScoreResource,
    browse: (URI) -> Unit = ::browseInSystemBrowser,
): Result<URI> = runCatching {
    val uri = URI(resource.url)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
        "外部链接必须使用 HTTPS"
    }
    browse(uri)
    uri
}

private fun browseInSystemBrowser(uri: URI) {
    check(Desktop.isDesktopSupported()) { "当前系统不支持打开浏览器" }
    val desktop = Desktop.getDesktop()
    check(desktop.isSupported(Desktop.Action.BROWSE)) { "当前系统未配置默认浏览器" }
    desktop.browse(uri)
}

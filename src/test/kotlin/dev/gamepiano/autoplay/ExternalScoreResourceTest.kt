package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.ui.ExternalScoreResource
import dev.gamepiano.autoplay.ui.openExternalScoreResource
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalScoreResourceTest {
    @Test
    fun `all curated resources use unique HTTPS links`() {
        val resources = ExternalScoreResource.entries

        assertEquals(resources.size, resources.map { it.url }.toSet().size)
        resources.forEach { resource ->
            val uri = URI(resource.url)
            assertEquals("https", uri.scheme)
            assertTrue(uri.host.isNotBlank())
        }
    }

    @Test
    fun `launcher opens only the selected curated resource`() {
        var opened: URI? = null

        val result = openExternalScoreResource(ExternalScoreResource.MIDISHOW) { opened = it }

        assertTrue(result.isSuccess)
        assertEquals(URI(ExternalScoreResource.MIDISHOW.url), opened)
    }
}

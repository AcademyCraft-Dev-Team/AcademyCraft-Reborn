package org.academy.api.client.gui.imgui

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UiDebugLocalizationTest {
    @Test
    fun `ui debug translations match between English and Chinese`() {
        val english = languageKeys("en_us").filter { it.contains(".ui_debug.") }.toSet()
        val chinese = languageKeys("zh_cn").filter { it.contains(".ui_debug.") }.toSet()

        assertFalse(english.isEmpty(), "UI debug translation set is unexpectedly empty")
        assertEquals(english, chinese)
        for (key in listOf(
            "screen.academy.ui_debug.inspector.title",
            "screen.academy.ui_debug.inspector.close",
            "screen.academy.ui_debug.inspector.live_title",
            "screen.academy.ui_debug.inspector.hud_title"
        )) {
            assertTrue(english.contains(key), "Missing UI debug translation $key")
        }
    }

    @Test
    fun `imgui Chinese font is bundled`() {
        val font = javaClass.getResourceAsStream("/assets/academy/fonts/wqy-microhei-modified.ttf")
        assertNotNull(font)
        font!!.use { assertTrue(it.readAllBytes().size > 100_000) }
    }

    private fun languageKeys(language: String): Set<String> {
        val stream = javaClass.getResourceAsStream("/assets/academy/lang/$language.json")
        assertNotNull(stream)
        return stream!!.reader().use { reader ->
            JsonParser.parseReader(reader).asJsonObject.keySet()
        }
    }
}

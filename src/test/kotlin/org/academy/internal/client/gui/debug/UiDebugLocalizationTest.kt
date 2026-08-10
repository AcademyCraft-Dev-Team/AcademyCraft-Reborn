package org.academy.internal.client.gui.debug

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UiDebugLocalizationTest {
    @Test
    fun `ui debug translations match between English and Chinese`() {
        val english = languageKeys("en_us")
        val chinese = languageKeys("zh_cn")
        val prefixes = listOf("screen.academy.ui_debug.", "message.academy.ui_debug.")
        val englishDebug = english.filter { key -> prefixes.any(key::startsWith) }.toSet()
        val chineseDebug = chinese.filter { key -> prefixes.any(key::startsWith) }.toSet()

        assertTrue(englishDebug.size >= 230, "UI debug translation set is unexpectedly incomplete")
        assertEquals(englishDebug, chineseDebug)
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

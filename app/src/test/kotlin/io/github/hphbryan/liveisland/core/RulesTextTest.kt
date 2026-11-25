package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTextTest {
    private val full = AppRules(
        packageName = "com.example.delivery",
        oneOffs = true,
        seconds = 15,
        chip = ChipSource.NUMBER,
        first = true,
        skip = listOf("coupon", "優惠"),
        types = mapOf("orders" to true, "marketing" to false),
    )

    @Test fun `rules survive a round trip`() {
        val other = AppRules("com.example.bus", oneOffs = false)
        assertEquals(listOf(full, other), RulesText.parse(RulesText.format(listOf(full, other))))
    }

    @Test fun `defaults are left out of the text`() {
        val text = RulesText.format(listOf(AppRules("com.example.bus")))
        assertEquals("LiveIsland rules\n\napp: com.example.bus", text)
    }

    @Test fun `text that is not rules gives nothing`() {
        assertTrue(RulesText.parse("app: com.example.bus\nchip: title").isEmpty())
        assertTrue(RulesText.parse("hello").isEmpty())
    }

    @Test fun `pasted from a chat with extra spacing and unknown lines`() {
        val text = """
            Here are mine:
            LiveIsland rules
              app:  com.example.bus
              chip: title
              colour: blue
              seconds: 999
        """.trimIndent()
        assertEquals(listOf(AppRules("com.example.bus", chip = ChipSource.TITLE)), RulesText.parse(text))
    }

    @Test fun `lines before any app and bad package names are ignored`() {
        val text = "LiveIsland rules\nchip: title\napp: not a package\nfirst: on\napp: com.example.a\nfirst: on"
        assertEquals(listOf(AppRules("com.example.a", first = true)), RulesText.parse(text))
    }
}

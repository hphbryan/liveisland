package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChipRuleTest {
    private fun pick(source: ChipSource, title: String, text: String? = null) =
        (ChipRule.pick(source, title, text) as? ChipText.Headline)?.text

    @Test fun `automatic leaves the choice to the classifier`() = assertNull(ChipRule.pick(ChipSource.AUTO, "Title", "Text"))

    @Test fun `title and text`() {
        assertEquals("Order ready", pick(ChipSource.TITLE, "Order ready", "Counter 3"))
        assertEquals("Counter 3", pick(ChipSource.TEXT, "Order ready", "Counter 3"))
    }

    @Test fun `text when there is none falls back`() = assertNull(ChipRule.pick(ChipSource.TEXT, "Order ready", null))

    @Test fun `a number with its Chinese unit`() {
        assertEquals("3 位", pick(ChipSource.NUMBER, "目前叫號", "前面還有 3 位"))
        assertEquals("12站", pick(ChipSource.NUMBER, "還有12站到達", null))
        assertEquals("15 分鐘", pick(ChipSource.NUMBER, "外送中", "預計 15 分鐘送達"))
    }

    @Test fun `a number with a word or percent`() {
        assertEquals("3 stops", pick(ChipSource.NUMBER, "Next bus", "3 stops away"))
        assertEquals("42%", pick(ChipSource.NUMBER, "Uploading", "42% done"))
        assertEquals("1.5 km", pick(ChipSource.NUMBER, "Run", "1.5 km so far"))
    }

    @Test fun `a number with a unit beats a bare one such as a route`() {
        assertEquals("3 站", pick(ChipSource.NUMBER, "12 往市政府", "還有 3 站，約 5 分鐘"))
        assertEquals("20 min", pick(ChipSource.NUMBER, "Gate 7", "Boarding in 20 min"))
    }

    @Test fun `with no unit anywhere, the first number`() = assertEquals("7", pick(ChipSource.NUMBER, "Gate 7", "Boarding soon"))

    @Test fun `order numbers and other long digit runs are skipped`() {
        assertEquals("2 items", pick(ChipSource.NUMBER, "Order #123456", "2 items"))
        assertNull(ChipRule.pick(ChipSource.NUMBER, "Order 98765432", "on its way"))
    }

    @Test fun `ordinary words are not units`() {
        assertEquals("3 stops", pick(ChipSource.NUMBER, "Bus 12 to Central", "3 stops away"))
        assertEquals("2", pick(ChipSource.NUMBER, "Step 2 of 4", null))
        assertEquals("2", pick(ChipSource.NUMBER, "2 new messages", null))
    }

    @Test fun `part of a longer number is not taken`() = assertNull(ChipRule.pick(ChipSource.NUMBER, "Run", "1,234 km total"))

    @Test fun `no number falls back`() = assertNull(ChipRule.pick(ChipSource.NUMBER, "Preparing", "Soon"))
}

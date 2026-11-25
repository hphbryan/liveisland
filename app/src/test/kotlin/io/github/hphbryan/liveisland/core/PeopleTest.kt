package io.github.hphbryan.liveisland.core

import io.github.hphbryan.liveisland.core.Classifier.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleTest {
    private val line = "jp.naver.line.android"
    private fun message(sender: String, text: String = "到了嗎？", group: String? = null) = snapshot(
        packageName = line,
        title = group ?: sender,
        text = text,
        isOngoing = false,
        sender = sender,
        conversationTitle = group,
        isConversation = true,
        channelId = "messages",
    )

    private fun policy(vararg rules: PersonRule, flash: Boolean = false) = FakePolicy(
        enabled = setOf(line),
        flash = if (flash) setOf(line) else emptySet(),
        people = mapOf(line to rules.toList()),
    )

    @Test fun `exact name wins over a partial match`() {
        val amy = PersonRule("Amy")
        val amyChen = PersonRule("Amy Chen", untilRead = false)
        assertEquals(amyChen, PersonMatcher.match(listOf(amy, amyChen), message("amy chen")))
        assertEquals(amy, PersonMatcher.match(listOf(amyChen, amy), message("Amy")))
    }

    @Test fun `matches the sender, the group or a partial name`() {
        assertEquals("媽媽", PersonMatcher.match(listOf(PersonRule("媽媽")), message("媽媽"))?.name)
        assertEquals("家人", PersonMatcher.match(listOf(PersonRule("家人")), message("爸爸", group = "家人群組"))?.name)
        assertNull(PersonMatcher.match(listOf(PersonRule("A")), message("Alice")))
    }

    @Test fun `a picked person is shown until read even with flash off`() {
        val c = (Classifier.classify(message("老闆"), policy(PersonRule("老闆"))) as Decision.Relay).content
        assertEquals(LiveContent.Kind.STICKY, c.kind)
        assertEquals(ChipText.Message("老闆", "到了嗎？"), c.chip)
        assertNull(c.timeoutMillis)
        assertEquals("relay:person-until-read", Classifier.explain(message("老闆"), policy(PersonRule("老闆"))))
    }

    @Test fun `a picked person can be shown briefly instead`() {
        val c = (Classifier.classify(message("Bob"), policy(PersonRule("Bob", untilRead = false))) as Decision.Relay).content
        assertEquals(LiveContent.Kind.FLASH, c.kind)
        assertEquals(SourcePolicy.DEFAULT_FLASH_MILLIS, c.timeoutMillis)
    }

    @Test fun `others are skipped unless flash is on, then show the sender`() {
        assertEquals(Decision.Remove, Classifier.classify(message("Someone"), policy(PersonRule("老闆"))))
        val c = (Classifier.classify(message("Someone"), policy(PersonRule("老闆"), flash = true)) as Decision.Relay).content
        assertEquals(ChipText.Message("Someone", "到了嗎…"), c.chip)
    }

    @Test fun `ignored words still win over a picked person`() {
        val p = FakePolicy(enabled = setOf(line), people = mapOf(line to listOf(PersonRule("店家"))), keywords = mapOf(line to listOf("優惠")))
        assertEquals(Decision.Remove, Classifier.classify(message("店家", text = "限時優惠"), p))
    }

    @Test fun `a group message shows who wrote it, not the group`() {
        val c = (Classifier.classify(message("爸爸", text = "晚餐吃什麼", group = "家人群組"), policy(PersonRule("家人"))) as Decision.Relay).content
        assertEquals(ChipText.Message("爸爸", "晚餐吃什麼"), c.chip)
    }

    @Test fun `the name is not repeated when the app puts it in the text`() {
        val c = (Classifier.classify(message("Amy", text = "Amy: on my way"), policy(PersonRule("Amy"))) as Decision.Relay).content
        assertEquals(ChipText.Message("Amy", "on my way"), c.chip)
    }

    @Test fun `a long message is cut to fit the chip`() {
        val c = (Classifier.classify(message("小明", text = "我已經到餐廳門口了你們還要多久"), policy(PersonRule("小明"))) as Decision.Relay).content
        val chip = c.chip as ChipText.Message
        assertEquals("我已經到…", chip.preview)
        assertTrue(TextShortener.width("${chip.sender}：${chip.preview}") <= TextShortener.CHIP_COLUMNS)
    }
}

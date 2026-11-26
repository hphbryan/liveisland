package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageHistoryTest {
    @Test fun `an app showing only the latest message keeps the earlier ones`() {
        val h = MessageHistory()
        h.merge("line", listOf("在嗎"))
        h.merge("line", listOf("我到了"))
        assertEquals(listOf("在嗎", "我到了", "門口等你"), h.merge("line", listOf("門口等你")))
    }

    @Test fun `the same message posted again is not repeated`() {
        val h = MessageHistory()
        h.merge("line", listOf("在嗎"))
        assertEquals(listOf("在嗎"), h.merge("line", listOf("在嗎")))
    }

    @Test fun `with the app's ids, two messages saying the same are both kept`() {
        val h = MessageHistory()
        h.merge("line", listOf("好"), id = "m1")
        assertEquals(listOf("好", "好"), h.merge("line", listOf("好"), id = "m2"))
    }

    @Test fun `with the app's ids, a message posted again is not repeated`() {
        val h = MessageHistory()
        h.merge("line", listOf("在嗎"), id = "m1")
        h.merge("line", listOf("我到了"), id = "m2")
        assertEquals(listOf("在嗎", "我到了"), h.merge("line", listOf("在嗎"), id = "m1"))
        assertEquals(listOf("在嗎", "我到了"), h.merge("line", listOf("我到了"), id = "m2"))
    }

    @Test fun `an app listing its messages is taken as it is`() {
        val h = MessageHistory()
        h.merge("ig", listOf("hi"))
        assertEquals(listOf("a", "b", "c"), h.merge("ig", listOf("a", "b", "c")))
    }

    @Test fun `only the newest are kept`() {
        val h = MessageHistory(maxLines = 3)
        (1..5).forEach { h.merge("k", listOf("m$it")) }
        assertEquals(listOf("m3", "m4", "m5"), h.merge("k", emptyList()))
    }

    @Test fun `chats are kept apart and forgotten once read`() {
        val h = MessageHistory()
        h.merge("a", listOf("1"))
        h.merge("b", listOf("x"))
        h.forget("a")
        assertEquals(listOf("2"), h.merge("a", listOf("2")))
        assertEquals(listOf("x", "y"), h.merge("b", listOf("y")))
    }

    @Test fun `after a dismiss only newer messages show`() {
        val h = MessageHistory()
        h.merge("line", listOf("在嗎"), id = "m1")
        h.merge("line", listOf("我到了"), id = "m2")
        h.dismissed("line")
        // The same message posted again is nothing new.
        assertEquals(emptyList<String>(), h.merge("line", listOf("我到了"), id = "m2"))
        assertEquals(listOf("門口等你"), h.merge("line", listOf("門口等你"), id = "m3"))
    }

    @Test fun `after a dismiss, without ids, the same last message is nothing new`() {
        val h = MessageHistory()
        h.merge("k", listOf("hi"))
        h.dismissed("k")
        assertEquals(emptyList<String>(), h.merge("k", listOf("hi")))
        assertEquals(listOf("there?"), h.merge("k", listOf("there?")))
    }

    @Test fun `after a dismiss, a listed conversation leaves out what was dismissed`() {
        val h = MessageHistory()
        h.merge("ig", listOf("a", "b"))
        h.dismissed("ig")
        assertEquals(listOf("c"), h.merge("ig", listOf("a", "b", "c")))
    }

    @Test fun `what is known of a chat`() {
        val h = MessageHistory()
        assertEquals(false, h.knows("k"))
        h.merge("k", listOf("hi"))
        h.dismissed("k")
        assertEquals(true, h.knows("k"))
        h.forget("k")
        assertEquals(false, h.knows("k"))
    }

    @Test fun `lines name the sender in a group, not in a one-to-one chat`() {
        assertEquals("on my way", MessageHistory.format(ChatLine("Amy", "on my way"), chat = "Amy"))
        assertEquals("Bob: hi", MessageHistory.format(ChatLine("Bob", "hi"), chat = "Family"))
        assertEquals("Bob: hi", MessageHistory.format(ChatLine("Bob", "Bob: hi"), chat = "Family"))
        assertEquals("Bob：hi", MessageHistory.format(ChatLine(null, "Bob：hi"), chat = "Family"))
    }
}

package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextShortenerTest {
    @Test fun `wide characters and emoji take two columns`() {
        assertEquals(6, TextShortener.width("ab中文"))
        assertEquals(2, TextShortener.width("👨‍👩‍👧"))
        assertEquals(2, TextShortener.width("："))
    }

    @Test fun `short text is kept, on one line`() {
        assertEquals("短名字", TextShortener.fit("短名字"))
        assertEquals("on my way", TextShortener.fit(" on my\nway "))
    }

    @Test fun `long text is cut with an ellipsis inside the budget`() {
        assertEquals("12345678…", TextShortener.fit("1234567890", columns = 9))
        assertEquals("一二三四…", TextShortener.fit("一二三四五六", columns = 9))
        assertEquals("👨‍👩‍👧…", TextShortener.fit("👨‍👩‍👧👨‍👩‍👧👨‍👩‍👧", columns = 4))
    }
}

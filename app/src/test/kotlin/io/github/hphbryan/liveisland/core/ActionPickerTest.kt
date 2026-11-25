package io.github.hphbryan.liveisland.core

import io.github.hphbryan.liveisland.core.ActionPicker.SourceAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPickerTest {
    @Test fun `no actions`() = assertEquals(emptyList<Int>(), ActionPicker.pick(emptyList()))

    @Test fun `keeps titled actions in order`() =
        assertEquals(listOf(0, 1), ActionPicker.pick(listOf(SourceAction("Reply", false), SourceAction("Mark read", false))))

    @Test fun `caps at two to leave room for dismiss`() =
        assertEquals(listOf(0, 1), ActionPicker.pick(List(3) { SourceAction("A$it", false) }))

    @Test fun `skips contextual and untitled actions`() =
        assertEquals(
            listOf(2),
            ActionPicker.pick(listOf(SourceAction("Smart reply", true), SourceAction(" ", false), SourceAction("Call driver", false))),
        )
}

package io.github.hphbryan.liveisland.platform

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTest {
    @After fun tearDown() = ActivityLog.clear()

    @Test fun `undo after clear keeps one entry per notification and outcome`() {
        ActivityLog.record("a", "pkg", "relay:ongoing", now = 1)
        ActivityLog.record("b", "pkg", "skip:keyword", now = 2)
        val old = ActivityLog.entries.value
        ActivityLog.clear()
        // The same notification updates again before Undo.
        ActivityLog.record("a", "pkg", "relay:ongoing", now = 3)
        ActivityLog.restore(old)
        val entries = ActivityLog.entries.value
        assertEquals(listOf("a" to 3L, "b" to 2L), entries.map { it.key to it.timeMillis })
    }
}

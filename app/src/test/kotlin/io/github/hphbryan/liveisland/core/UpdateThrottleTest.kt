package io.github.hphbryan.liveisland.core

import io.github.hphbryan.liveisland.core.UpdateThrottle.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateThrottleTest {
    private var now = 10_000L
    private val throttle = UpdateThrottle(minIntervalMillis = 1_000, clock = { now })

    private fun content(title: String = "A", key: String = "k") = LiveContent(
        sourceKey = key, packageName = "p", title = title, text = null, chip = null,
        progress = null, chronometer = null, kind = LiveContent.Kind.ONGOING,
    )

    private fun post(c: LiveContent): Action = throttle.offer(c).also { if (it == Action.Post) throttle.markPosted(c) }

    @Test fun `first offer posts`() = assertEquals(Action.Post, post(content()))

    @Test fun `unchanged content is skipped`() {
        post(content())
        now += 5_000
        assertEquals(Action.Skip, post(content()))
    }

    @Test fun `rapid change is deferred and the latest wins`() {
        post(content("A"))
        now += 200
        assertEquals(Action.Defer(800), throttle.offer(content("B")))
        assertEquals(Action.Skip, throttle.offer(content("C")))
        assertEquals("C", throttle.takeDeferred("k")?.title)
    }

    @Test fun `deferred change back to the posted content is dropped`() {
        post(content("A"))
        now += 200
        throttle.offer(content("B"))
        throttle.offer(content("A"))
        assertNull(throttle.takeDeferred("k"))
    }

    @Test fun `change after the interval posts immediately`() {
        post(content("A"))
        now += 1_000
        assertEquals(Action.Post, post(content("B")))
    }

    @Test fun `forget clears tracking`() {
        post(content())
        assertTrue(throttle.isTracked("k"))
        throttle.forget("k")
        assertFalse(throttle.isTracked("k"))
        assertEquals(Action.Post, post(content()))
    }
}

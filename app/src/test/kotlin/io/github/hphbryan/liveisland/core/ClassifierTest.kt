package io.github.hphbryan.liveisland.core

import io.github.hphbryan.liveisland.core.Classifier.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    private val app = "com.example.delivery"
    private val enabled = FakePolicy(enabled = setOf(app))

    private fun relayed(d: Decision): LiveContent = (d as Decision.Relay).content

    @Test fun `ongoing notification from an enabled app is relayed`() {
        val c = relayed(Classifier.classify(snapshot(), enabled))
        assertEquals(LiveContent.Kind.ONGOING, c.kind)
        assertEquals("Courier is on the way", c.title)
        assertEquals(ChipText.Minutes(12), c.chip)
    }

    @Test fun `apps the user did not enable are not relayed`() =
        assertEquals(Decision.Remove, Classifier.classify(snapshot(), FakePolicy()))

    @Test fun `progress makes a non-ongoing notification an activity`() {
        val c = relayed(Classifier.classify(snapshot(isOngoing = false, progress = 3, progressMax = 10), enabled))
        assertEquals(LiveContent.Progress.Determinate(30, 100), c.progress)
    }

    @Test fun `progress is clamped`() {
        val c = relayed(Classifier.classify(snapshot(progress = 15, progressMax = 10), enabled))
        assertEquals(LiveContent.Progress.Determinate(100, 100), c.progress)
    }

    @Test fun `byte-level progress does not change what is posted until a whole percent passes`() {
        val a = relayed(Classifier.classify(snapshot(progress = 1_000_000, progressMax = 300_000_000), enabled))
        val b = relayed(Classifier.classify(snapshot(progress = 1_500_000, progressMax = 300_000_000), enabled))
        assertEquals(a.fingerprint, b.fingerprint)
        val big = relayed(Classifier.classify(snapshot(progress = 150_000_000, progressMax = 300_000_000), enabled))
        assertEquals(LiveContent.Progress.Determinate(50, 100), big.progress)
    }

    @Test fun `indeterminate progress`() {
        val c = relayed(Classifier.classify(snapshot(isOngoing = false, progressIndeterminate = true), enabled))
        assertEquals(LiveContent.Progress.Indeterminate, c.progress)
    }

    @Test fun `chronometer is carried over`() {
        val c = relayed(Classifier.classify(snapshot(showsChronometer = true, chronometerCountsDown = true, whenMillis = 42), enabled))
        assertEquals(LiveContent.Chronometer(42, countsDown = true), c.chronometer)
    }

    @Test fun `big text is preferred over text`() {
        val c = relayed(Classifier.classify(snapshot(text = "short", bigText = "the long version"), enabled))
        assertEquals("the long version", c.text)
    }

    @Test fun `an ended activity removes the relayed copy`() =
        assertEquals(Decision.Remove, Classifier.classify(snapshot(isOngoing = false), enabled))

    @Test fun `one-off notification flashes when flash is on`() {
        val policy = FakePolicy(enabled = setOf(app), flash = setOf(app))
        val c = relayed(Classifier.classify(snapshot(isOngoing = false, title = "訂單已送達", text = "祝您用餐愉快"), policy))
        assertEquals(LiveContent.Kind.FLASH, c.kind)
        assertEquals(ChipText.Headline("訂單已送達 祝您…"), c.chip)
    }

    @Test fun `a value in the chip comes with what it is about`() {
        val bus = relayed(Classifier.classify(snapshot(title = "12 往火車站", text = "約 3 分鐘到站"), enabled))
        assertEquals(ChipText.Minutes(3), bus.chip)
        assertEquals("12 往火車站", bus.chipDetail)
        val ride = relayed(Classifier.classify(snapshot(title = "Driver arrives in 4 min", text = "Toyota Altis ABC-1234"), enabled))
        assertEquals(ChipText.Minutes(4), ride.chip)
        assertEquals("Toyota Alti…", ride.chipDetail)
        val download = relayed(Classifier.classify(snapshot(title = "report.pdf", text = null, progress = 45, progressMax = 100), enabled))
        assertEquals(ChipText.Percent(45), download.chip)
        assertEquals("report.pdf", download.chipDetail)
    }

    @Test fun `without a value the chip shows the title, unless a timer is running`() {
        val c = relayed(Classifier.classify(snapshot(title = "外送員正在取餐", text = "餐廳正在準備"), enabled))
        assertEquals(ChipText.Headline("外送員正在取餐"), c.chip)
        assertNull(c.chipDetail)
        val timer = relayed(Classifier.classify(snapshot(title = "Workout", text = "Running", showsChronometer = true), enabled))
        assertNull(timer.chip)
    }

    @Test fun `ai reply flashes as replied`() {
        val claude = "com.anthropic.claude"
        val policy = FakePolicy(enabled = setOf(claude), flash = setOf(claude))
        val c = relayed(Classifier.classify(snapshot(packageName = claude, isOngoing = false, title = "旅行打包清單"), policy))
        assertEquals(ChipText.Replied, c.chip)
    }

    @Test fun `google app only relays gemini channels`() {
        val g = KnownApps.GOOGLE_APP
        val policy = FakePolicy(enabled = setOf(g), flash = setOf(g))
        assertEquals(Decision.Remove, Classifier.classify(snapshot(packageName = g, channelId = "77", isOngoing = false, title = "今天天氣晴"), policy))
        assertTrue(Classifier.classify(snapshot(packageName = g, channelId = "gemini_sync_chat_channel", isOngoing = false, title = "Gemini"), policy) is Decision.Relay)
    }

    @Test fun `notifications the system already shows live are not duplicated`() {
        assertFalse(Classifier.isCandidate(snapshot(isPromoted = true)))
        assertFalse(Classifier.isCandidate(snapshot(isSamsungLiveNotification = true)))
    }

    @Test fun `notifications the user hid in system settings are not brought back`() {
        assertEquals(Decision.Remove, Classifier.classify(snapshot(isHiddenBySystem = true), enabled))
        assertEquals("skip:hidden", Classifier.explain(snapshot(isHiddenBySystem = true), enabled))
    }

    @Test fun `do not disturb silences one-offs but not what is in progress`() {
        val flash = FakePolicy(enabled = setOf(app), flash = setOf(app))
        val quiet = snapshot(isOngoing = false, interruptsNow = false)
        assertEquals(Decision.Remove, Classifier.classify(quiet, flash))
        assertEquals("skip:dnd", Classifier.explain(quiet, flash))
        assertTrue(Classifier.classify(snapshot(interruptsNow = false), enabled) is Decision.Relay)
    }

    @Test fun `the copy keeps the source's lock screen treatment`() {
        assertEquals(LockScreen.HIDDEN, relayed(Classifier.classify(snapshot(lockScreen = LockScreen.HIDDEN), enabled)).lockScreen)
        val flash = FakePolicy(enabled = setOf(app), flash = setOf(app))
        val c = relayed(Classifier.classify(snapshot(isOngoing = false, lockScreen = LockScreen.APP_NAME_ONLY), flash))
        assertEquals(LockScreen.APP_NAME_ONLY, c.lockScreen)
    }

    @Test fun `promotions and recommendations are skipped`() {
        assertEquals("skip:promo", Classifier.explain(snapshot(category = Classifier.CATEGORY_PROMO), enabled))
        assertEquals("skip:promo", Classifier.explain(snapshot(category = Classifier.CATEGORY_RECOMMENDATION), enabled))
        assertEquals("skip:promo", Classifier.explain(snapshot(isClassifiedAsBulk = true), enabled))
    }

    @Test fun `a running-in-background notice is not an activity, but a service with progress or a timer is`() {
        val service = Classifier.CATEGORY_SERVICE
        assertEquals("skip:background", Classifier.explain(snapshot(category = service, title = "Sync is running"), enabled))
        assertTrue(Classifier.classify(snapshot(category = service, progress = 4, progressMax = 10), enabled) is Decision.Relay)
        assertTrue(Classifier.classify(snapshot(category = service, showsChronometer = true), enabled) is Decision.Relay)
    }

    @Test fun `pausing leaves everything out`() {
        val paused = FakePolicy(enabled = setOf(app), paused = true)
        assertEquals(Decision.Remove, Classifier.classify(snapshot(), paused))
        assertEquals("skip:paused", Classifier.explain(snapshot(), paused))
    }

    @Test fun `a notification type the user turned off is left out`() {
        val policy = FakePolicy(enabled = setOf(app), channels = mapOf("orders" to false))
        assertEquals("skip:channel", Classifier.explain(snapshot(channelId = "orders"), policy))
        assertTrue(Classifier.classify(snapshot(channelId = "rides"), policy) is Decision.Relay)
    }

    @Test fun `types named like promotions are left out until the user turns them on`() {
        val promo = snapshot(channelId = "mkt", channelName = "優惠活動")
        assertEquals("skip:promo", Classifier.explain(promo, enabled))
        assertEquals("skip:promo", Classifier.explain(snapshot(channelId = "m", channelName = "Promotions and offers"), enabled))
        assertTrue(Classifier.classify(snapshot(channelId = "o", channelName = "Order updates"), enabled) is Decision.Relay)
        val chosen = FakePolicy(enabled = setOf(app), channels = mapOf("mkt" to true))
        assertTrue(Classifier.classify(promo, chosen) is Decision.Relay)
    }

    @Test fun `explain gives the reason`() {
        val flash = FakePolicy(enabled = setOf(app), flash = setOf(app))
        assertEquals("relay:ongoing", Classifier.explain(snapshot(), enabled))
        assertEquals("relay:flash", Classifier.explain(snapshot(isOngoing = false), flash))
        assertEquals("skip:not-ongoing", Classifier.explain(snapshot(isOngoing = false), enabled))
        assertEquals("skip:app-off", Classifier.explain(snapshot(), FakePolicy()))
        assertEquals("skip:already-live-update", Classifier.explain(snapshot(isPromoted = true), enabled))
        assertEquals("skip:not-relevant", Classifier.explain(snapshot(packageName = KnownApps.GOOGLE_APP, channelId = "71"), FakePolicy(enabled = setOf(KnownApps.GOOGLE_APP))))
    }

    @Test fun `media, calls, summaries, system and untitled notifications are skipped`() {
        assertFalse(Classifier.isCandidate(snapshot(isMedia = true)))
        assertFalse(Classifier.isCandidate(snapshot(category = Classifier.CATEGORY_CALL)))
        assertFalse(Classifier.isCandidate(snapshot(isGroupSummary = true)))
        assertFalse(Classifier.isCandidate(snapshot(packageName = "android")))
        assertFalse(Classifier.isCandidate(snapshot(title = "  ")))
        assertFalse(Classifier.isCandidate(snapshot(title = null)))
    }

    @Test fun `ignored keywords skip a notification and remove an existing copy`() {
        val policy = FakePolicy(enabled = setOf(app), keywords = mapOf(app to listOf("coupon")))
        val ad = snapshot(title = "Your COUPON is here", isOngoing = true)
        assertEquals(Decision.Remove, Classifier.classify(ad, policy))
        assertEquals("skip:keyword", Classifier.explain(ad, policy))
        assertTrue(Classifier.classify(snapshot(), policy) is Decision.Relay)
    }

    @Test fun `flash uses the app's chosen duration`() {
        val policy = FakePolicy(enabled = setOf(app), flash = setOf(app), flashMillis = mapOf(app to 15_000L))
        val c = relayed(Classifier.classify(snapshot(isOngoing = false), policy))
        assertEquals(LiveContent.Kind.FLASH, c.kind)
        assertEquals(15_000L, c.timeoutMillis)
    }

    @Test fun `ongoing content has no timeout`() =
        assertEquals(null, relayed(Classifier.classify(snapshot(), enabled)).timeoutMillis)
}

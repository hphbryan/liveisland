package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownAppsTest {
    private val packageName = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    @Test fun `package names are well formed`() {
        (KnownApps.ALL + KnownApps.SYSTEM - "android").forEach {
            assertTrue("bad package name: $it", packageName.matches(it))
        }
    }

    @Test fun `each app is in one category only and none is a system app`() {
        val all = KnownApps.BY_CATEGORY.values.flatten()
        assertEquals(all.size, all.toSet().size)
        assertEquals(emptySet<String>(), all.toSet() intersect KnownApps.SYSTEM)
    }

    @Test fun `every category has apps`() =
        AppCategory.entries.forEach { assertTrue(it.name, KnownApps.BY_CATEGORY[it].orEmpty().isNotEmpty()) }

    @Test fun `apps with their own Live Updates are not listed`() {
        val native = setOf(
            "com.google.android.apps.maps", "com.ubercab", "com.ubercab.eats", "me.lyft.android",
            "com.grabtaxi.passenger", "hearsilent.busplus", "com.google.android.apps.photos",
        )
        assertEquals(emptySet<String>(), KnownApps.ALL intersect native)
    }

    @Test fun `gogoro is relayed only while charging`() {
        assertTrue(KnownApps.isRelevant(snapshot(packageName = KnownApps.GOGORO, title = "充電中", text = "1 小時 20 分")))
        assertTrue(KnownApps.isRelevant(snapshot(packageName = KnownApps.GOGORO, title = "GoCharger", text = "Charging")))
        assertFalse(KnownApps.isRelevant(snapshot(packageName = KnownApps.GOGORO, title = "Gogoro", text = "已連線到你的車")))
    }

    @Test fun `category defaults`() {
        assertTrue(KnownApps.flashByDefault("com.anthropic.claude"))
        assertTrue(KnownApps.flashByDefault(KnownApps.FOODPANDA)) // app override in a non-flash category
        assertFalse(KnownApps.flashByDefault("jp.naver.line.android"))
        assertFalse(KnownApps.flashByDefault("com.unknown.app"))
        assertTrue(KnownApps.hasPeople("jp.naver.line.android"))
        assertFalse(KnownApps.hasPeople(KnownApps.FOODPANDA))
        assertEquals(PROMOTION_WORDS, KnownApps.DEFAULT_IGNORED_KEYWORDS[KnownApps.FOODPANDA])
        assertEquals(listOf("優惠", "活動", "折扣", "promo"), KnownApps.DEFAULT_IGNORED_KEYWORDS[KnownApps.TAIWAN_TAXI])
        assertFalse("com.anthropic.claude" in KnownApps.DEFAULT_IGNORED_KEYWORDS)
    }

    @Test fun `shared promotion words leave out everyday ones`() {
        // Instagram's stories are "限時動態"; a chat message or AI reply can say "deal" or "sale".
        assertFalse(KeywordFilter.matches(SHARED_PROMOTION_WORDS, "小明 新增了限時動態"))
        assertFalse(KeywordFilter.matches(SHARED_PROMOTION_WORDS, "Deal with it later, the sale ended"))
        assertTrue(KeywordFilter.matches(SHARED_PROMOTION_WORDS, "今日免運，領券再折 50"))
        assertTrue(SHARED_PROMOTION_WORDS.all { it in PROMOTION_WORDS })
    }

    @Test fun `taipei bus apps are transit`() {
        assertEquals(AppCategory.TRANSIT, KnownApps.categoryOf("tw.skystar.tpbus"))
        assertEquals(AppCategory.TRANSIT, KnownApps.categoryOf("tw.com.trtc.is.android05"))
    }

    @Test fun `only apps about something in progress are listed`() {
        // One-off push apps (shopping, travel, mail) are out of scope; they can still be added by hand.
        val oneOff = setOf("com.shopee.tw", "com.google.android.gm", "com.booking", "com.exptech.dpip")
        assertEquals(emptySet<String>(), KnownApps.ALL intersect oneOff)
    }
}

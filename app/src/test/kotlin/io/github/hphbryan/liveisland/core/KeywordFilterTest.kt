package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordFilterTest {
    @Test fun `parse accepts ASCII, full-width and ideographic commas and new lines`() =
        assertEquals(listOf("優惠", "折扣", "coupon", "sale"), KeywordFilter.parse(" 優惠，折扣、coupon,\nsale ,, "))

    @Test fun `parse drops case-insensitive duplicates`() =
        assertEquals(listOf("Sale"), KeywordFilter.parse("Sale, sale, SALE"))

    @Test fun `format round-trips`() =
        assertEquals("優惠, coupon", KeywordFilter.format(KeywordFilter.parse("優惠、coupon")))

    @Test fun `matches title, text or big text ignoring case`() {
        assertTrue(KeywordFilter.matches(listOf("優惠"), snapshot(title = "限時優惠")))
        assertTrue(KeywordFilter.matches(listOf("promo"), snapshot(text = "New PROMO inside")))
        assertTrue(KeywordFilter.matches(listOf("free"), snapshot(bigText = "Free delivery")))
    }

    @Test fun `latin words match at the start of a word only`() {
        assertFalse(KeywordFilter.matches(listOf("sale"), snapshot(title = "Wholesale order on its way")))
        assertTrue(KeywordFilter.matches(listOf("sale"), snapshot(title = "Big SALE today")))
        assertTrue(KeywordFilter.matches(listOf("promo"), snapshot(title = "Promotion: 20% off")))
    }

    @Test fun `chinese words match anywhere`() =
        assertTrue(KeywordFilter.matches(listOf("優惠"), snapshot(title = "今日限定優惠券")))

    @Test fun `no words never matches`() = assertFalse(KeywordFilter.matches(emptyList(), snapshot()))

    @Test fun `unrelated words do not match`() =
        assertFalse(KeywordFilter.matches(listOf("優惠"), snapshot(title = "外送員正在路上", text = "預計 5 分鐘")))
}

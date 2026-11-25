package io.github.hphbryan.liveisland.core

import io.github.hphbryan.liveisland.core.ChipText.ClockTime
import io.github.hphbryan.liveisland.core.ChipText.Distance
import io.github.hphbryan.liveisland.core.ChipText.Minutes
import io.github.hphbryan.liveisland.core.ChipText.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChipTextExtractorTest {
    private fun chip(title: String?, text: String? = null, progress: LiveContent.Progress? = null) =
        ChipTextExtractor.extract(title, text, progress)

    @Test fun `chinese minutes`() = assertEquals(Minutes(12), chip("外送員正在路上", "預計 12 分鐘後抵達"))

    @Test fun `short chinese minutes`() = assertEquals(Minutes(3), chip("公車即將到站", "約3分"))

    @Test fun `english minutes`() = assertEquals(Minutes(5), chip("Arriving in 5 min"))

    @Test fun `hours and minutes become total minutes`() = assertEquals(Minutes(80), chip("剩餘 1 小時 20 分鐘"))

    @Test fun `minutes win over a clock time`() = assertEquals(Minutes(8), chip("預計 12:30 送達", "還有 8 分鐘"))

    @Test fun `distance in metres`() = assertEquals(Distance("300", "m"), chip("300 公尺後右轉"))

    @Test fun `distance in kilometres`() = assertEquals(Distance("2.5", "km"), chip("2.5 km to destination"))

    @Test fun `metre unit is not confused with minutes`() = assertEquals(Minutes(4), chip("4 mins"))

    @Test fun `clock time`() = assertEquals(ClockTime("12:30"), chip("預計 12:30 送達"))

    @Test fun `a date-time pair is not a clock time`() = assertNull(chip("Updated 12:30:45"))

    @Test fun `percent in text`() = assertEquals(Percent(45), chip("下載中", "45%"))

    @Test fun `percent above 100 is ignored`() = assertNull(chip("Up 250%"))

    @Test fun `percent from determinate progress`() =
        assertEquals(Percent(50), chip("Uploading", "photo.jpg", LiveContent.Progress.Determinate(30, 60)))

    @Test fun `words containing 分 are not minutes`() = assertNull(chip("分店資訊", "5分店開幕"))

    @Test fun `nothing useful`() = assertNull(chip("同步完成", "所有資料都是最新的"))

    @Test fun `bus arriving has no number but still gets a chip`() {
        assertEquals(ChipText.Arriving, ChipTextExtractor.extract("12 往火車站", "即將進站", null))
        assertEquals(ChipText.Arriving, ChipTextExtractor.extract("12", "進站中", null))
        assertEquals(ChipText.Arriving, ChipTextExtractor.extract("Bus 22", "Arriving at Main St", null))
    }

    @Test fun `minutes win over arriving words and stops`() =
        assertEquals(ChipText.Minutes(4), ChipTextExtractor.extract("12 將到站", "還有 2 站，約 4 分", null))

    @Test fun `stops to go`() {
        assertEquals(ChipText.Stops(2), ChipTextExtractor.extract("12 往火車站", "還有 2 站", null))
        assertEquals(ChipText.Stops(3), ChipTextExtractor.extract("Bus 22", "3 stops away", null))
    }

    @Test fun `station names and stop signs are not stops`() {
        assertEquals(null, ChipTextExtractor.extract("台北車站", "1號站牌", null))
        assertEquals(null, ChipTextExtractor.extract("下一站：2號出口", null, null))
    }
}

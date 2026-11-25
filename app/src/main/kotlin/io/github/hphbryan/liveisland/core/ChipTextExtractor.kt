package io.github.hphbryan.liveisland.core

/**
 * Finds something worth showing in the status bar chip in a notification's text, in order of
 * usefulness: remaining minutes, "arriving now", stops to go, distance, a clock time, then a percentage.
 */
object ChipTextExtractor {
    private val HOURS_MINUTES = Regex(
        """(?<!\d)(\d{1,2})\s*(?:小時|小时|hours?|hrs?|h)\s*(\d{1,2})\s*(?:分鐘|分钟|分|minutes?|mins?|m)(?![a-zA-Z])""",
        RegexOption.IGNORE_CASE,
    )
    private val MINUTES = Regex(
        """(?<!\d)(\d{1,3})\s*(?:分鐘|分钟|分(?!店|行|享|類|类)|minutes?|mins?)(?![a-zA-Z])""",
        RegexOption.IGNORE_CASE,
    )
    private val DISTANCE = Regex(
        """(?<![\d.])(\d+(?:\.\d+)?)\s*(公里|公尺|米|km|m)(?![a-zA-Z])""",
        RegexOption.IGNORE_CASE,
    )
    /** A bus or train at the stop, or about to be; no number to show. */
    private val ARRIVING = Regex(
        """即將進站|即將到站|進站中|將到站|即將抵達|arriving|approaching""",
        RegexOption.IGNORE_CASE,
    )

    /** Stops to go, as transit apps put it: "還有 2 站", "3 stops away". Not "台北車站" or "站牌". */
    private val STOPS = Regex(
        """(?<!\d)(\d{1,2})\s*(?:站(?![牌名點位內外])|stops?(?![a-zA-Z]))""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOCK = Regex("""(?<![\d:])([01]?\d|2[0-3]):[0-5]\d(?![\d:])""")
    private val PERCENT = Regex("""(?<!\d)(\d{1,3})\s*%""")

    fun extract(title: String?, text: String?, progress: LiveContent.Progress?): ChipText? {
        val haystack = listOfNotNull(title, text).joinToString(" ")
        HOURS_MINUTES.find(haystack)?.let {
            return ChipText.Minutes(it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt())
        }
        MINUTES.find(haystack)?.let { return ChipText.Minutes(it.groupValues[1].toInt()) }
        if (ARRIVING.containsMatchIn(haystack)) return ChipText.Arriving
        STOPS.find(haystack)?.let { return ChipText.Stops(it.groupValues[1].toInt()) }
        DISTANCE.find(haystack)?.let { return ChipText.Distance(it.groupValues[1], normaliseUnit(it.groupValues[2])) }
        CLOCK.find(haystack)?.let { return ChipText.ClockTime(it.value) }
        PERCENT.find(haystack)?.let { m ->
            val pct = m.groupValues[1].toInt()
            if (pct <= 100) return ChipText.Percent(pct)
        }
        if (progress is LiveContent.Progress.Determinate && progress.max > 0) {
            return ChipText.Percent(progress.value * 100 / progress.max)
        }
        return null
    }

    private fun normaliseUnit(unit: String): String = when (unit.lowercase()) {
        "公里", "km" -> "km"
        else -> "m"
    }
}

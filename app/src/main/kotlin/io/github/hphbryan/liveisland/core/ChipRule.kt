package io.github.hphbryan.liveisland.core

/** What the user chose to show in an app's chip, instead of the automatic choice. */
enum class ChipSource(val key: String) {
    /** LiveIsland picks: a time, distance or percentage if there is one, otherwise the title. */
    AUTO("auto"),

    /** The notification's title, as the app writes it. */
    TITLE("title"),

    /** The notification's text (the line under the title). */
    TEXT("text"),

    /**
     * The first number with a unit, such as "3 站" or "12 km", from the title or else the text; with no
     * unit anywhere, the first number on its own.
     */
    NUMBER("number");

    companion object {
        fun of(key: String?): ChipSource = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** Applies a [ChipSource] to a notification's words. */
object ChipRule {
    /**
     * A short number on its own. Long digit runs (order numbers, phone numbers) and pieces of a longer number
     * ("234" of "1,234") are skipped: what is worth a glance is short.
     */
    private const val DIGITS = """(?<![\d#.,:])\d{1,5}(?:[.:,]\d{1,2})?(?![.,:]?\d)"""

    /**
     * A number, then its unit: "%", a Chinese measure word, or a known English unit ("min", "km", "stops"),
     * not any word, so "Bus 12 to Central" is not "12 to".
     */
    private val WITH_UNIT = Regex(
        DIGITS + """\s*(?:%|分鐘|分钟|小時|小时|公里|公尺|公斤|[位站分秒元個个件人號号樓楼天次台輛辆杯份張张]|""" +
            """(?i:mins?|minutes?|hrs?|hours?|h|secs?|s|km|mi|m|ft|kg|lbs?|[kmg]b|stops?|items?)(?![a-zA-Z]))""",
    )
    private val BARE = Regex(DIGITS)

    /**
     * The chip [source] asks for, or null when it is [ChipSource.AUTO] or the notification has nothing for
     * it (no text, no number); then the automatic chip is used.
     */
    fun pick(source: ChipSource, title: String, text: String?): ChipText? = when (source) {
        ChipSource.AUTO -> null
        ChipSource.TITLE -> title.ifBlank { null }?.let { ChipText.Headline(TextShortener.fit(it)) }
        ChipSource.TEXT -> text?.ifBlank { null }?.let { ChipText.Headline(TextShortener.fit(it)) }
        ChipSource.NUMBER -> listOf(WITH_UNIT, BARE).firstNotNullOfOrNull { r -> r.find(title) ?: text?.let(r::find) }
            ?.let { ChipText.Headline(TextShortener.fit(it.value.trim())) }
    }
}

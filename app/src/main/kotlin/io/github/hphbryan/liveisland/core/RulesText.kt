package io.github.hphbryan.liveisland.core

/**
 * One app's choices, as shared between people: whether one-offs are shown and for how long, what the chip
 * shows, whether it comes first, skipped words and notification types. Never people (their names are
 * personal), never anything a notification said.
 */
data class AppRules(
    val packageName: String,
    val oneOffs: Boolean? = null,
    val seconds: Int? = null,
    val chip: ChipSource = ChipSource.AUTO,
    val first: Boolean = false,
    /** Null when the app's words were never changed, so pasting keeps the receiver's built-in ones. */
    val skip: List<String>? = null,
    /** Notification type (channel) id to shown or left out. */
    val types: Map<String, Boolean> = emptyMap(),
)

/**
 * Plain text for [AppRules], to copy into a chat or a note and paste on another phone:
 *
 *     LiveIsland rules
 *     app: com.example.delivery
 *     one-offs: on
 *     seconds: 8
 *     chip: number
 *     first: on
 *     skip: coupon, 優惠
 *     show type: orders
 *     hide type: marketing
 *
 * Several apps follow one another, each starting at its "app:" line. Unknown lines are ignored, so a
 * newer version's rules still paste, minus what this one does not know.
 */
object RulesText {
    const val HEADER = "LiveIsland rules"

    fun format(rules: List<AppRules>): String = buildString {
        append(HEADER)
        rules.forEach { r ->
            append("\n\napp: ").append(r.packageName)
            r.oneOffs?.let { append("\none-offs: ").append(onOff(it)) }
            r.seconds?.let { append("\nseconds: ").append(it) }
            if (r.chip != ChipSource.AUTO) append("\nchip: ").append(r.chip.key)
            if (r.first) append("\nfirst: on")
            r.skip?.let { append("\nskip: ").append(KeywordFilter.format(it)) }
            r.types.forEach { (id, on) -> append(if (on) "\nshow type: " else "\nhide type: ").append(id) }
        }
    }

    /** The apps in [text], or an empty list if it is not LiveIsland rules. */
    fun parse(text: String): List<AppRules> {
        val lines = text.lines().map { it.trim() }
        if (lines.none { it.equals(HEADER, ignoreCase = true) }) return emptyList()
        val out = mutableListOf<AppRules>()
        var current: AppRules? = null
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            if (key == "app") {
                current?.let(out::add)
                current = value.takeIf(::isPackageName)?.let { AppRules(it) }
                continue
            }
            val r = current ?: continue
            current = when (key) {
                "one-offs" -> r.copy(oneOffs = value == "on")
                "seconds" -> value.toIntOrNull()?.takeIf { it in SourcePolicy.FLASH_SECONDS_CHOICES }?.let { r.copy(seconds = it) } ?: r
                "chip" -> r.copy(chip = ChipSource.of(value))
                "first" -> r.copy(first = value == "on")
                "skip" -> r.copy(skip = KeywordFilter.parse(value))
                "show type" -> if (value.isEmpty()) r else r.copy(types = r.types + (value to true))
                "hide type" -> if (value.isEmpty()) r else r.copy(types = r.types + (value to false))
                else -> r
            }
        }
        current?.let(out::add)
        return out.distinctBy { it.packageName }
    }

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    private val PACKAGE = Regex("""[a-zA-Z][\w]*(\.[a-zA-Z_][\w]*)+""")

    private fun isPackageName(s: String) = PACKAGE.matches(s)
}

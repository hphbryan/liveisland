package io.github.hphbryan.liveisland.core

import java.text.BreakIterator

/**
 * Fits text into the status bar chip without splitting an emoji or a combined character.
 *
 * Widths are in columns: CJK characters and emoji take two, most others one. One UI 9 shows about 13
 * columns in the chip (six or seven CJK characters, a dozen Latin ones) and fades out the rest; other
 * launchers cut off sooner. Text is fitted a little past that so One UI fades rather than shows "…".
 */
object TextShortener {
    const val CHIP_COLUMNS = 16

    /** A sender's name in "name: message"; leaves room for the message. */
    const val NAME_COLUMNS = 8

    private val WHITESPACE = Regex("""\s+""")

    /** [text] on one line, cut to at most [columns] columns with a trailing "…" if it had to be cut. */
    fun fit(text: String, columns: Int = CHIP_COLUMNS): String {
        val clean = text.replace(WHITESPACE, " ").trim()
        if (width(clean) <= columns) return clean
        var used = 0
        var cut = 0
        for ((start, end) in graphemes(clean)) {
            val w = graphemeWidth(clean, start)
            if (used + w > columns - 1) break // One column for "…".
            used += w
            cut = end
        }
        return clean.substring(0, cut).trimEnd() + "…"
    }

    fun width(text: String): Int = graphemes(text).sumOf { (start, _) -> graphemeWidth(text, start) }

    private fun graphemes(text: String): List<Pair<Int, Int>> {
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        val out = mutableListOf<Pair<Int, Int>>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += start to end
            start = end
            end = it.next()
        }
        return out
    }

    private fun graphemeWidth(text: String, start: Int): Int = if (isWide(text.codePointAt(start))) 2 else 1

    /** East Asian wide and fullwidth characters, and emoji. */
    private fun isWide(cp: Int): Boolean =
        cp in 0x1100..0x115F || cp in 0x2E80..0xA4CF || cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF ||
            cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFF60 || cp in 0xFFE0..0xFFE6 || cp in 0x1F300..0x1FAFF ||
            cp in 0x20000..0x3FFFD
}

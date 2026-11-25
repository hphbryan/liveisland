package io.github.hphbryan.liveisland.core

import java.util.Locale

/** User-defined words that make a notification be skipped. Matching is case-insensitive substring. */
object KeywordFilter {
    /** Separators accepted between words: comma (ASCII, full-width, ideographic) and new lines. */
    private val SEPARATORS = Regex("[,，、\\n]+")

    /** Splits what the user typed into distinct, non-blank words, keeping their order. */
    fun parse(input: String): List<String> =
        input.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }

    /** Canonical text for storing [parse]d words. */
    fun format(words: List<String>): String = words.joinToString(", ")

    fun matches(words: List<String>, s: NotificationSnapshot): Boolean =
        matches(words, listOfNotNull(s.title, s.text, s.bigText).joinToString("\n"))

    fun matches(words: List<String>, text: String): Boolean {
        if (words.isEmpty() || text.isEmpty()) return false
        val haystack = text.lowercase(Locale.ROOT)
        return words.any { matchesWord(it.lowercase(Locale.ROOT), haystack) }
    }

    /**
     * Words in Latin letters must start a word, so "sale" does not skip "wholesale" (but "promo" still
     * skips "promotion"). Chinese and other scripts without spaces match anywhere.
     */
    private fun matchesWord(word: String, haystack: String): Boolean {
        if (!word.first().isLetterOrDigit() || word.first().code > 0x24F) return word in haystack
        var from = 0
        while (true) {
            val i = haystack.indexOf(word, from)
            if (i < 0) return false
            if (i == 0 || !haystack[i - 1].isLetterOrDigit()) return true
            from = i + 1
        }
    }
}

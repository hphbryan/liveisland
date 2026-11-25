package io.github.hphbryan.liveisland.platform

import android.graphics.Paint
import android.graphics.Typeface
import java.text.BreakIterator

/**
 * Fits chip text for Pixel and other non-Samsung phones. Their status bar chip shows the text only when it
 * fits whole and otherwise shows the icon alone, so a long chip text means no text at all. The limit is a
 * width, not a number of characters: on Android 17 "1 min On its" and "還有3分鐘" were shown, while
 * "1 min On its " and "ChatGPT D…" were not. Widths are compared in the system font, so the limit holds at
 * any text size; a small margin covers phones whose status bar font is a little wider.
 */
object ChipFit {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 100f
    }
    private val budget = paint.measureText("1 min On its") * 0.95f
    private const val ELLIPSIS = "…"

    fun fits(text: String): Boolean = paint.measureText(text) <= budget

    /** [text] on one line, cut at a character boundary with a trailing "…" until it fits. */
    fun fit(text: String): String {
        val clean = text.replace(WHITESPACE, " ").trim()
        if (fits(clean)) return clean
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(clean) }
        var best = ""
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            val candidate = clean.substring(0, end).trimEnd() + ELLIPSIS
            if (!fits(candidate)) break
            best = candidate
            end = boundaries.next()
        }
        return best
    }

    private val WHITESPACE = Regex("""\s+""")
}

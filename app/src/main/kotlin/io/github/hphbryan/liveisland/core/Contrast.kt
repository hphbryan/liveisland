package io.github.hphbryan.liveisland.core

import kotlin.math.pow

/** WCAG contrast between colours given as 0xAARRGGBB, and the nearest colour that has enough of it. */
object Contrast {
    fun ratio(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * [color] made darker on a light [background], or lighter on a dark one, in small steps until it has at
     * least [minimum] contrast. Already-sufficient colours come back unchanged.
     */
    fun ensure(color: Int, background: Int, minimum: Double): Int {
        val toward = if (luminance(background) > 0.5) 0x000000 else 0xFFFFFF
        var out = color
        var step = 0
        while (ratio(out, background) < minimum && step < STEPS) {
            step++
            out = mix(color, toward, step.toDouble() / STEPS)
        }
        return out
    }

    private fun mix(from: Int, to: Int, amount: Double): Int {
        fun ch(shift: Int) = (((from shr shift) and 0xFF) * (1 - amount) + ((to shr shift) and 0xFF) * amount).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun luminance(color: Int): Double {
        fun lin(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
    }

    private const val STEPS = 50
}

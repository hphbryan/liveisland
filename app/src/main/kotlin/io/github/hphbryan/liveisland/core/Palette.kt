package io.github.hphbryan.liveisland.core

/**
 * Picks the colour an icon is known by: the most common vivid hue, ignoring transparent, near-white,
 * near-black and grey pixels, so a white background or a black outline does not win. Null when the icon
 * has too little colour to call it a brand colour (ChatGPT's, say), and the system's colour is used.
 */
object Palette {
    data class Pixel(val a: Int, val r: Int, val g: Int, val b: Int)

    fun dominant(pixels: List<Pixel>): Pixel? {
        val opaque = pixels.filter { it.a >= OPAQUE }
        if (opaque.isEmpty()) return null
        val vivid = opaque.filter { saturation(it) >= MIN_SATURATION && value(it) in MIN_VALUE..MAX_VALUE }
        if (vivid.size < opaque.size * MIN_SHARE) return null
        // The hue bucket with the most pixels, then the average of those pixels.
        val bucket = vivid.groupBy { (hue(it) / BUCKET_DEGREES).toInt() }.maxBy { it.value.size }.value
        return Pixel(255, bucket.sumOf { it.r } / bucket.size, bucket.sumOf { it.g } / bucket.size, bucket.sumOf { it.b } / bucket.size)
    }

    private fun value(p: Pixel) = maxOf(p.r, p.g, p.b) / 255f

    private fun saturation(p: Pixel): Float {
        val max = maxOf(p.r, p.g, p.b)
        return if (max == 0) 0f else (max - minOf(p.r, p.g, p.b)) / max.toFloat()
    }

    private fun hue(p: Pixel): Float {
        val max = maxOf(p.r, p.g, p.b)
        val d = (max - minOf(p.r, p.g, p.b)).toFloat()
        if (d == 0f) return 0f
        val h = when (max) {
            p.r -> ((p.g - p.b) / d).mod(6f)
            p.g -> (p.b - p.r) / d + 2
            else -> (p.r - p.g) / d + 4
        }
        return h * 60
    }

    private const val OPAQUE = 128
    private const val MIN_SATURATION = 0.35f
    private const val MIN_VALUE = 0.25f
    private const val MAX_VALUE = 1f
    /** Below this share of colourful pixels the icon is black and white with an accent at most. */
    private const val MIN_SHARE = 0.08f
    private const val BUCKET_DEGREES = 20f
}

package io.github.hphbryan.liveisland.platform

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import io.github.hphbryan.liveisland.core.Palette

/** An app's brand colour, read from its icon once per app (see [Palette]). */
object BrandColor {
    fun of(icon: Bitmap): Int? {
        val step = maxOf(1, icon.width / SAMPLES_PER_SIDE)
        val pixels = buildList {
            for (y in 0 until icon.height step step) for (x in 0 until icon.width step step) add(icon[x, y])
        }
        return Palette.dominant(pixels.map { Palette.Pixel(Color.alpha(it), Color.red(it), Color.green(it), Color.blue(it)) })
            ?.let { Color.rgb(it.r, it.g, it.b) }
    }

    private const val SAMPLES_PER_SIDE = 32
}

package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteTest {
    private fun px(r: Int, g: Int, b: Int, a: Int = 255) = Palette.Pixel(a, r, g, b)

    @Test fun `vivid colour wins over white background and black outline`() {
        val pink = px(0xD7, 0x0F, 0x64)
        val icon = List(50) { px(255, 255, 255) } + List(10) { px(0, 0, 0) } + List(40) { pink }
        assertEquals(pink, Palette.dominant(icon))
    }

    @Test fun `most common hue wins`() {
        val green = px(0x06, 0xC7, 0x55)
        val icon = List(30) { green } + List(10) { px(0xFF, 0xC1, 0x07) }
        assertEquals(green, Palette.dominant(icon))
    }

    @Test fun `black and white icon has no brand colour`() {
        assertNull(Palette.dominant(List(60) { px(255, 255, 255) } + List(40) { px(10, 10, 10) }))
        assertNull(Palette.dominant(List(10) { px(255, 0, 0, a = 0) }))
    }
}

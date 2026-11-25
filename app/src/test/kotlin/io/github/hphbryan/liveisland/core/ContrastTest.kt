package io.github.hphbryan.liveisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val light = 0xFFFBF9FE.toInt()
    private val dark = 0xFF16181E.toInt()

    @Test fun `black on white is 21 to 1`() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test fun `a light green is darkened on a light card and kept on a dark one`() {
        val green = 0xFF01C57C.toInt()
        val onLight = Contrast.ensure(green, light, 3.2)
        assertTrue(Contrast.ratio(onLight, light) >= 3.2)
        assertEquals(green, Contrast.ensure(green, dark, 3.2))
    }

    @Test fun `a dark blue is lightened on a dark card`() {
        val navy = 0xFF1A237E.toInt()
        assertTrue(Contrast.ratio(Contrast.ensure(navy, dark, 3.2), dark) >= 3.2)
        assertEquals(navy, Contrast.ensure(navy, light, 3.2))
    }
}

package com.eltonkola.comfyflux.app.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class FittedSizeTest {

    @Test
    fun keepsAspectWithinTheCap() {
        // Portrait photo: full height, width narrowed - the latent match
        // stops the model from filling a square latent with a crop.
        assertEquals(672 to 1024, fittedSize(1200, 1830))
        assertEquals(1024 to 672, fittedSize(1830, 1200))
        assertEquals(1024 to 1024, fittedSize(1024, 1024))
    }

    @Test
    fun neverBelowMinimumAndAlways32Aligned() {
        val (w, h) = fittedSize(100, 4000)
        assertEquals(256, w)
        assertEquals(0, w % 32)
        assertEquals(1024, h)
    }
}

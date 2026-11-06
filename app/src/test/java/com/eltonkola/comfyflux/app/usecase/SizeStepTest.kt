package com.eltonkola.comfyflux.app.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeStepTest {

    private val sizes = listOf(256, 320, 384, 448, 512, 576, 640, 704, 768, 832, 896, 960, 1024)

    @Test
    fun photoFittedSizesLandOnTheNearestSliderStep() {
        // 928 is a fitted portrait width and is NOT one of the steps; the old
        // exact lookup returned -1 and the Size/Batch tab crashed.
        val index = nearestSizeIndex(sizes, 928)
        assertEquals(10, index)
        assertEquals(896, sizes[index])
    }

    @Test
    fun exactStepsAndOutOfRangeValuesStayInsideTheList() {
        assertEquals(4, nearestSizeIndex(sizes, 512))
        assertEquals(0, nearestSizeIndex(sizes, 64))
        assertEquals(sizes.lastIndex, nearestSizeIndex(sizes, 4096))
        assertEquals(sizes.indices.toList(), sizes.map { nearestSizeIndex(sizes, it) })
    }
}

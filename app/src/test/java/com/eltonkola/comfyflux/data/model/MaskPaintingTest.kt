package com.eltonkola.comfyflux.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mask painting is stored as normalized strokes and rasterized at the photo's
 * own resolution, so the geometry cannot drift between editor and upload.
 */
class MaskPaintingTest {

    @Test
    fun emptyStrokesPaintNothing() {
        val painted = rasterizeMask(emptyList(), 10, 10)

        assertEquals(100, painted.size)
        assertFalse(painted.any { it })
    }

    @Test
    fun aSinglePointPaintsADiscAndNothingElse() {
        val painted = rasterizeMask(
            listOf(MaskStroke(points = listOf(0.5f, 0.5f), radius = 0.1f)),
            width = 100,
            height = 100
        )

        assertTrue(painted[50 * 100 + 50])
        assertFalse(painted[0])
        assertFalse(painted[99 * 100 + 99])
    }

    @Test
    fun consecutivePointsLeaveAContinuousLine() {
        val painted = rasterizeMask(
            listOf(MaskStroke(points = listOf(0.1f, 0.5f, 0.9f, 0.5f), radius = 0.02f)),
            width = 100,
            height = 100
        )

        val row = 50 * 100
        var paintedOnRow = 0
        for (x in 0 until 100) if (painted[row + x]) paintedOnRow++
        assertTrue("line should cover most of the middle row, got $paintedOnRow", paintedOnRow >= 79)
    }

    @Test
    fun compositingKeepsRGBAndWritesAlphaOnlyForPaintedPixels() {
        val photo = intArrayOf(0x112233, 0x445566)
        val painted = booleanArrayOf(true, false)

        val out = compositeMaskPixels(photo, painted)

        assertEquals(0x112233, out[0])
        assertEquals(0xFF445566.toInt(), out[1])
    }

    @Test
    fun paintedMaskReportsEmptiness() {
        assertTrue(PaintedMask("slot", emptyList()).isEmpty)
        assertFalse(PaintedMask("slot", listOf(MaskStroke(listOf(0.5f, 0.5f), 0.1f))).isEmpty)
    }
}

package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.jsonObject

/**
 * Mask painting, modelled as normalized strokes instead of Android bitmaps:
 * the editor draws them, the upload path rasterizes them at the photo's own
 * resolution. That keeps the geometry exact (no two-scale problem) and makes
 * the mask math testable off-device.
 */
data class MaskStroke(
    /** Normalized points, x/y pairs, 0..1 of the image's own size. */
    val points: List<Float>,
    /** Brush radius, normalized to the image width. */
    val radius: Float
)

/** Which image slot the mask belongs to, plus the painted area. */
data class PaintedMask(val slotId: String, val strokes: List<MaskStroke>) {
    val isEmpty: Boolean get() = strokes.isEmpty()
}

/**
 * Rasterize the strokes to a per-pixel "painted" map at the photo's resolution
 * (discs stamped along each segment, so a slow drag leaves a continuous line).
 */
internal fun rasterizeMask(strokes: List<MaskStroke>, width: Int, height: Int): BooleanArray {
    val painted = BooleanArray(width * height)
    if (width <= 0 || height <= 0) return painted
    strokes.forEach { stroke ->
        val radius = (stroke.radius * width).coerceAtLeast(0.5f)
        val r2 = radius * radius
        val pts = stroke.points
        var i = 0
        var prevX = Float.NaN
        var prevY = Float.NaN
        while (i + 1 < pts.size) {
            val x = pts[i] * width
            val y = pts[i + 1] * height
            if (prevX.isNaN()) {
                stampDisc(painted, width, height, x, y, radius, r2)
            } else {
                // Pointer events can arrive far apart on a fast drag; stamp the
                // whole segment so the line stays continuous instead of dotted.
                val dx = x - prevX
                val dy = y - prevY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val stride = (radius * 0.5f).coerceAtLeast(0.5f)
                val steps = kotlin.math.ceil(dist / stride).toInt()
                for (s in 1 until steps) {
                    val t = s.toFloat() / steps.toFloat()
                    stampDisc(painted, width, height, prevX + dx * t, prevY + dy * t, radius, r2)
                }
                stampDisc(painted, width, height, x, y, radius, r2)
            }
            prevX = x
            prevY = y
            i += 2
        }
    }
    return painted
}

private fun stampDisc(
    painted: BooleanArray, width: Int, height: Int, cx: Float, cy: Float, radius: Float, r2: Float
) {
    val x0 = (cx - radius).toInt().coerceAtLeast(0)
    val x1 = (cx + radius).toInt().coerceAtMost(width - 1)
    val y0 = (cy - radius).toInt().coerceAtLeast(0)
    val y1 = (cy + radius).toInt().coerceAtMost(height - 1)
    for (y in y0..y1) {
        val dy = y - cy
        for (x in x0..x1) {
            val dx = x - cx
            if (dx * dx + dy * dy <= r2) painted[y * width + x] = true
        }
    }
}

/**
 * ComfyUI's LoadImage reads a mask off the image's alpha channel (`mask = 1 -
 * alpha`), so "the area to inpaint" has to become alpha 0 in the uploaded PNG.
 * Painted pixels lose their alpha, everything else is opaque.
 */
internal fun compositeMaskPixels(photoPixels: IntArray, painted: BooleanArray): IntArray {
    val out = IntArray(photoPixels.size)
    for (i in photoPixels.indices) {
        out[i] = if (painted.getOrNull(i) == true) photoPixels[i] and 0x00FFFFFF
        else (photoPixels[i] or 0xFF000000.toInt())
    }
    return out
}

/**
 * True when the graph consumes a MASK somewhere (SetLatentNoiseMask,
 * InpaintModelConditioning, LoadImageMask's channel path). Only then does the
 * Create screen offer painting, instead of showing a button that does nothing.
 */
fun Workflow.hasMaskConsumer(): Boolean =
    any { (_, node) -> node.inputs.jsonObject["mask"] != null }

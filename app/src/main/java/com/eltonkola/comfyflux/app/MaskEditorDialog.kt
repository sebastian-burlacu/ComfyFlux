package com.eltonkola.comfyflux.app

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eltonkola.comfyflux.data.model.MaskStroke
import com.eltonkola.comfyflux.data.model.PaintedMask

/**
 * Paint the area to regenerate over the picked photo. Strokes are recorded as
 * normalized points, so the mask rasterizes at the photo's own resolution when
 * Create uploads it - no two-scale drift.
 */
@Composable
fun MaskEditorDialog(
    photoUri: String,
    slotId: String,
    initial: PaintedMask?,
    onDismiss: () -> Unit,
    onApply: (PaintedMask) -> Unit
) {
    // Reopening keeps the strokes already painted for this slot.
    var strokes by remember(initial) { mutableStateOf(initial?.strokes.orEmpty()) }
    var brush by remember { mutableFloatStateOf(0.04f) }
    var canvasBounds by remember { mutableStateOf(IntSize.Zero) }
    var photoSize by remember { mutableStateOf(IntSize.Zero) }
    var pending by remember { mutableStateOf(emptyList<Float>()) }

    // The photo is drawn with ContentScale.Fit inside a black box, so the
    // paintable rect is smaller than the canvas; points must map to that rect.
    val photoRect = remember(canvasBounds, photoSize) { fittedRect(canvasBounds, photoSize) }
    // Translucent accent so the photo stays visible under the paint; resolved
    // outside Canvas because a DrawScope is not a composable context.
    val ink = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Dialog(
        onDismissRequest = onDismiss,
        // Full-window dialog: the photo needs the whole screen width to paint.
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text("Paint the mask", fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .background(Color.Black)
                        .onSizeChanged { canvasBounds = it }
                ) {
                    AsyncImage(
                        model = Uri.parse(photoUri),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { success ->
                            val width = success.result.drawable.intrinsicWidth
                            val height = success.result.drawable.intrinsicHeight
                            if (width > 0 && height > 0) photoSize = IntSize(width, height)
                        }
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(brush, photoRect) {
                                val rect = photoRect
                                if (rect != null) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            pending = normalizedPair(rect, start)
                                        },
                                        onDrag = { change, _ ->
                                            pending = pending + normalizedPair(rect, change.position)
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            // A stroke needs two recorded x/y points.
                                            if (pending.size >= 4) {
                                                strokes = strokes + MaskStroke(pending.toList(), brush)
                                            }
                                            pending = emptyList()
                                        },
                                        onDragCancel = { pending = emptyList() }
                                    )
                                }
                            }
                    ) {
                        val rect = photoRect
                        if (rect != null) {
                            strokes.forEach { stroke ->
                                drawMaskStroke(rect, stroke.points, stroke.radius, ink)
                            }
                            // Live preview while the finger is still down.
                            if (pending.size >= 2) {
                                drawMaskStroke(rect, pending, brush, ink)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Brush", fontSize = 13.sp)
                    Slider(
                        value = brush,
                        onValueChange = { brush = it },
                        valueRange = 0.01f..0.15f,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            strokes = emptyList()
                            pending = emptyList()
                        },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Clear", fontSize = 13.sp) }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 13.sp) }
                    TextButton(
                        onClick = { onApply(PaintedMask(slotId, strokes)) },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Apply", fontSize = 13.sp) }
                }

                Text(
                    text = "Painted areas are the parts to regenerate. ComfyUI inpaint " +
                            "workflows take this mask from the photo's alpha channel.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** Offset inside the canvas -> x/y pairs relative to the photo, clamped to 0..1. */
private fun normalizedPair(rect: Rect, position: Offset): List<Float> = listOf(
    ((position.x - rect.left) / rect.width).coerceIn(0f, 1f),
    ((position.y - rect.top) / rect.height).coerceIn(0f, 1f)
)

/** Rect the photo occupies under ContentScale.Fit; the rest is black letterbox. */
private fun fittedRect(bounds: IntSize, photo: IntSize): Rect? {
    if (bounds.width <= 0 || bounds.height <= 0 || photo.width <= 0 || photo.height <= 0) {
        return null
    }
    val scale = minOf(
        bounds.width.toFloat() / photo.width,
        bounds.height.toFloat() / photo.height
    )
    val width = photo.width * scale
    val height = photo.height * scale
    val left = (bounds.width - width) / 2f
    val top = (bounds.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/** One stroke as a path; radius is normalized to the photo's width. */
private fun DrawScope.drawMaskStroke(rect: Rect, points: List<Float>, radius: Float, ink: Color) {
    if (points.size < 4) return
    val path = Path()
    path.moveTo(rect.left + points[0] * rect.width, rect.top + points[1] * rect.height)
    var i = 2
    while (i + 1 < points.size) {
        path.lineTo(rect.left + points[i] * rect.width, rect.top + points[i + 1] * rect.height)
        i += 2
    }
    drawPath(
        path = path,
        color = ink,
        style = Stroke(
            width = 2f * radius * rect.width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.eltonkola.comfyflux.data.model.MaskStroke
import com.eltonkola.comfyflux.data.model.PaintedMask

/**
 * Paint the area to regenerate over the picked photo. Points are stored
 * normalized (0..1 of the image bounds) so the mask rasterizes at the photo's
 * own resolution when Create uploads it - no two-scale drift.
 */
@Composable
fun MaskEditorDialog(
    photoUri: String,
    slotId: String,
    initial: PaintedMask?,
    onDismiss: () -> Unit,
    onApply: (PaintedMask) -> Unit
) {
    var strokes by remember { mutableStateOf(initial?.strokes.orEmpty()) }
    var brush by remember { mutableFloatStateOf(0.04f) }
    // Hoisted: draw code runs in a DrawScope, not a composable context.
    val ink = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Paint the mask", fontSize = 18.sp)
                Text(
                    text = "Painted areas are the parts to regenerate. ComfyUI inpaint " +
                            "workflows read this mask from the photo's alpha channel.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = Uri.parse(photoUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(brush, strokes) {
                                var points = mutableListOf<Float>()
                                fun clampX(x: Float) = (x / size.width).coerceIn(0f, 1f)
                                fun clampY(y: Float) = (y / size.height).coerceIn(0f, 1f)
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        points = mutableListOf(clampX(pos.x), clampY(pos.y))
                                    },
                                    onDrag = { change, _ ->
                                        points += clampX(change.position.x)
                                        points += clampY(change.position.y)
                                    },
                                    onDragEnd = {
                                        if (points.isNotEmpty()) {
                                            strokes = strokes + MaskStroke(points.toList(), brush)
                                        }
                                    }
                                )
                            }
                    ) {
                        strokes.forEach { stroke ->
                            val pts = stroke.points
                            var prev: Offset? = null
                            var i = 0
                            while (i + 1 < pts.size) {
                                val point = Offset(pts[i] * size.width, pts[i + 1] * size.height)
                                prev?.let { start ->
                                    drawLine(
                                        color = ink,
                                        start = start,
                                        end = point,
                                        strokeWidth = stroke.radius * 2 * size.width,
                                        cap = StrokeCap.Round
                                    )
                                }
                                prev = point
                                i += 2
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Brush", fontSize = 13.sp)
                    Slider(
                        value = brush,
                        onValueChange = { brush = it },
                        valueRange = 0.01f..0.15f,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) {
                        Text("Clear", fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = { onApply(PaintedMask(slotId, strokes)) },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Apply") }
                }
            }
        }
    }
}

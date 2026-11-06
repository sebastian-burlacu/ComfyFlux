package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * A workflow whose size is produced by a ResolutionSelector node
 * (aspect-ratio + megapixels -> width/height for the latent). Templates built
 * this way ignore width/height written on the latent, because those inputs
 * are linked, so the app drives the selector instead.
 */
data class SizeSlot(
    val nodeId: String,
    val aspectOptions: List<String>,
    val currentAspect: String,
    val currentMegapixels: Float
)

private const val RESOLUTION_SELECTOR = "ResolutionSelector"

/** The size steps the Create screen slider offers. */
val SIZE_STEPS = listOf(256, 320, 384, 448, 512, 576, 640, 704, 768, 832, 896, 960, 1024)

/** Find the resolution driver, if the workflow has one whose inputs are live. */
fun detectResolutionSelector(workflow: Workflow, objectInfo: JsonObject): SizeSlot? {
    val (id, node) = workflow.entries.firstOrNull { it.value.classType == RESOLUTION_SELECTOR }
        ?: return null
    val spec = objectInfo[RESOLUTION_SELECTOR]?.jsonObject ?: return null
    val groups = listOfNotNull(
        spec["input"]?.jsonObject?.get("required")?.jsonObject,
        spec["input"]?.jsonObject?.get("optional")?.jsonObject
    )
    val aspects = groups.firstOrNull { it.containsKey("aspect_ratio") }
        ?.get("aspect_ratio")?.comboStringOptions() ?: return null
    val inputs = node.inputs.jsonObject
    return SizeSlot(
        nodeId = id,
        aspectOptions = aspects,
        currentAspect = (inputs["aspect_ratio"] as? JsonPrimitive)?.contentOrNull ?: "",
        currentMegapixels = (inputs["megapixels"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: 1f
    )
}

/**
 * Ratio of an aspect label ("16:9 (Landscape)", "3:2", "1:1"), or null when
 * the label can't be read (custom/free-form options are left alone).
 */
fun aspectRatioOf(label: String): Float? {
    val match = Regex("""(\d+(?:\.\d+)?)\s*:\s*(\d+(?:\.\d+)?)""").find(label) ?: return null
    val w = match.groupValues[1].toFloatOrNull() ?: return null
    val h = match.groupValues[2].toFloatOrNull() ?: return null
    return if (h == 0f) null else w / h
}

/** The offered label whose ratio is closest to [width]/[height]. */
fun nearestAspect(options: List<String>, width: Int, height: Int): String? {
    if (height <= 0 || options.isEmpty()) return null
    val wanted = width.toFloat() / height
    return options
        .mapNotNull { label -> aspectRatioOf(label)?.let { label to it } }
        .minByOrNull { (_, ratio) -> kotlin.math.abs(ratio - wanted) }
        ?.first
}

/**
 * Point the selector node at [aspect] for the given megapixel count. Both
 * values are only written when the node still has literal widgets there.
 */
fun Workflow.applyResolutionSize(nodeId: String, aspect: String, megapixels: Float) {
    val node = this[nodeId] ?: return
    if (node.classType != RESOLUTION_SELECTOR) return
    val current = node.inputs.jsonObject
    if (current["aspect_ratio"] !is JsonPrimitive || current["megapixels"] !is JsonPrimitive) return
    replaceInputs(
        nodeId,
        mapOf(
            "aspect_ratio" to JsonPrimitive(aspect),
            "megapixels" to JsonPrimitive((megapixels * 100).toInt() / 100f)
        )
    )
}

/**
 * Width/height for [aspect] holding roughly the current pixel area, snapped
 * to slider steps. Keeps the preview box honest when an aspect is picked.
 */
fun fitToAspect(currentW: Int, currentH: Int, aspect: String, steps: List<Int>): Pair<Int, Int> {
    val ratio = aspectRatioOf(aspect) ?: return currentW to currentH
    if (steps.isEmpty()) return currentW to currentH
    val area = (currentW.toLong() * currentH).toFloat()
    // Pick the slider pair whose *shape* is nearest, breaking ties by pixel
    // area: snapping each side independently can produce a 2:1 box when the
    // user asked for 16:9.
    fun score(w: Int, h: Int): Float {
        val shape = kotlin.math.abs(kotlin.math.ln((w.toFloat() / h) / ratio))
        val size = kotlin.math.abs(kotlin.math.ln((w.toLong() * h).toFloat() / area))
        return 2f * shape + size
    }
    var best = steps.first() to steps.first()
    var bestScore = Float.MAX_VALUE
    steps.forEach { w ->
        steps.forEach { h ->
            val s = score(w, h)
            if (s < bestScore) {
                bestScore = s
                best = w to h
            }
        }
    }
    return best
}

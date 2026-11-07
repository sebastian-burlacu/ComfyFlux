package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-node graph tweaks: widgets the app has no dedicated control for
 * (FluxGuidance.guidance, ModelSamplingSD3.shift, custom-node knobs) are listed
 * per node in the Advanced tab. Detection is generic over the server's own
 * specs, so a custom node declaring a FLOAT/INT/BOOLEAN/COMBO widget shows
 * up without a whitelist; anything another panel already owns is filtered out
 * so two controls never fight over one input.
 */
enum class GraphKnobKind { Float, Int, Boolean, Choice }

data class GraphKnob(
    val nodeId: String,
    val classType: String,
    val input: String,
    val kind: GraphKnobKind,
    val min: Float?,
    val max: Float?,
    /** Current workflow value, string-encoded (numbers as plain text). */
    val current: String,
    /** COMBO options straight from the server. */
    val options: List<String> = emptyList()
) {
    val key: String get() = "$nodeId|$input"
}

/** Input names other panels drive (prompt, models, samplers, sizes, cfg/steps). */
internal val OWNED_INPUTS: Set<String> =
    setOf(
        "text", "prompt", "prompt_upsampling", "image", "mask", "pixels", "images",
        "filename_prefix", "model", "clip", "vae", "samples", "latent_image",
        "conditioning", "positive", "negative", "sigmas", "sampler_name",
        "scheduler", "denoise", "seed", "noise_seed", "cfg", "steps",
        "width", "height", "batch_size", "add_noise", "noise", "latent",
        "control_net", "image1", "image2"
    ) + MODEL_INPUTS + STRENGTH_INPUTS.keys + (0..3).map { "strength_$it" }

/** Plumbing and sinks: no useful knob, or a knob that does nothing. */
private val SKIP_CLASSES = setOf(
    "ComfySwitchNode", "ComfyMathNode", "Reroute", "Note", "MarkdownNote",
    "PreviewAny", "PreviewImage", "VAEDecode", "VAEEncode", "ConditioningZeroOut",
    "SaveImage", "SaveImageAdvanced", "SaveVideo", "SaveAudio"
) + SIGMA_LIST_NODES

/**
 * Every knob the app can safely write: literal values, or links ending in a
 * PrimitiveInt/PrimitiveFloat/String node (see numericWriteTargets).
 */
fun Workflow.detectGraphKnobs(objectInfo: JsonObject): List<GraphKnob> =
    entries.mapNotNull { (id, node) ->
        if (node.classType in SKIP_CLASSES) return@mapNotNull null
        val spec = objectInfo[node.classType]?.jsonObjectOrNull ?: return@mapNotNull null
        val groups = listOfNotNull(
            spec["input"]?.jsonObjectOrNull?.get("required")?.jsonObjectOrNull,
            spec["input"]?.jsonObjectOrNull?.get("optional")?.jsonObjectOrNull
        )
        // File-loading nodes keep their picks in the Models panel.
        if (groups.any { group -> group.keys.any { it in MODEL_INPUTS } }) return@mapNotNull null
        val knobs = mutableListOf<GraphKnob>()
        groups.forEach { group ->
            group.forEach { (input, valueSpec) ->
                if (input in OWNED_INPUTS) return@forEach
                if (!takesNumericInput(id, input)) return@forEach
                val shape = knobShape(valueSpec, spec.declaredOptionsOrNull(input)) ?: return@forEach
                knobs += GraphKnob(
                    nodeId = id,
                    classType = node.classType,
                    input = input,
                    kind = shape.first,
                    min = shape.second.firstOrNull(),
                    max = shape.second.lastOrNull(),
                    current = widgetValue(id, input) ?: "",
                    options = shape.third
                )
            }
        }
        knobs.takeIf { it.isNotEmpty() }
    }.flatten().sortedBy { it.nodeId.toIntOrNull() ?: 0 }

/** Server spec of one input, as a (kind, bounds, options) triple. */
private fun knobShape(
    spec: kotlinx.serialization.json.JsonElement,
    declaredOptions: List<String>?
): Triple<GraphKnobKind, List<Float>, List<String>>? {
    val array = spec as? JsonArray ?: return null
    val typeName = (array.firstOrNull() as? JsonPrimitive)?.contentOrNull
    val meta = array.getOrNull(1) as? JsonObject
    val min = meta?.get("min")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
    val max = meta?.get("max")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
    return when (typeName) {
        "INT" -> Triple(GraphKnobKind.Int, listOfNotNull(min, max), emptyList())
        "FLOAT" -> Triple(GraphKnobKind.Float, listOfNotNull(min, max), emptyList())
        "BOOLEAN" -> Triple(GraphKnobKind.Boolean, emptyList(), emptyList())
        else -> {
            val options = declaredOptions ?: return null
            if (options.isEmpty()) return null
            Triple(GraphKnobKind.Choice, emptyList(), options)
        }
    }
}

/** The spec of `input` for the given node class, or null when undeclared. */
private fun JsonObject.declaredOptionsOrNull(input: String): List<String>? {
    val groups = listOfNotNull(
        this["input"]?.jsonObjectOrNull?.get("required")?.jsonObjectOrNull,
        this["input"]?.jsonObjectOrNull?.get("optional")?.jsonObjectOrNull
    )
    val spec = groups.firstOrNull { it.containsKey(input) }?.get(input) ?: return null
    return spec.declaredOptions()
}

/** Write the picked knobs; linked values that are not primitives stay untouched. */
fun Workflow.applyGraphKnobs(overrides: Map<String, String>) {
    overrides.forEach { (key, value) ->
        val parts = key.split('|', limit = 2)
        if (parts.size != 2) return@forEach
        val (id, input) = parts
        val node = this[id] ?: return@forEach
        val existing = node.inputs.jsonObject[input] ?: return@forEach
        val primitive = when {
            value.equals("true", ignoreCase = true) -> JsonPrimitive(true)
            value.equals("false", ignoreCase = true) -> JsonPrimitive(false)
            // Keep the workflow's own numeric type: INT stays INT.
            existing is JsonPrimitive && existing.contentOrNull?.contains('.') == false ->
                value.toFloatOrNull()?.toInt()?.let { JsonPrimitive(it) } ?: JsonPrimitive(value)
            existing is JsonPrimitive && existing.contentOrNull?.toFloatOrNull() != null ->
                JsonPrimitive(value.toFloat())
            else -> JsonPrimitive(value)
        }
        writeNumeric(id, input, primitive)
    }
}

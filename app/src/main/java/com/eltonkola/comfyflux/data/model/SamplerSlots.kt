package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Sampler knobs the phone can drive on the selected workflow: which sampler
 * and scheduler names the server accepts, and which nodes expose a denoise
 * widget. Mirrors the web UI's KSampler dropdowns without the graph.
 */
data class SamplerSlots(
    val samplerOptions: List<String> = emptyList(),
    val schedulerOptions: List<String> = emptyList(),
    val currentSampler: String = "",
    val currentScheduler: String = "",
    /** True when at least one sampler node has a (unlinked) denoise input. */
    val hasDenoise: Boolean = false,
    val currentDenoise: Float = 1f
) {
    val isEmpty: Boolean
        get() = samplerOptions.isEmpty() && schedulerOptions.isEmpty() && !hasDenoise
}

// class_type -> the combo/float inputs the app may override on it. The guide
// graphs (RandomNoise -> CFGGuider/BasicGuider -> BasicScheduler) spread the
// same three knobs over two nodes: the sampler name sits on KSamplerSelect,
// the scheduler and denoise on BasicScheduler.
private val SAMPLER_INPUTS = mapOf(
    "KSampler" to setOf("sampler_name", "scheduler", "denoise"),
    "KSamplerAdvanced" to setOf("sampler_name", "scheduler", "denoise"),
    "KSamplerSelect" to setOf("sampler_name"),
    "BasicScheduler" to setOf("scheduler", "denoise"),
    // These schedulers also carry the img2img strength in guide graphs.
    "SDTurboScheduler" to setOf("denoise"),
    "AlignYourStepsScheduler" to setOf("denoise"),
    "OptimalStepsScheduler" to setOf("denoise"),
    "GITSScheduler" to setOf("denoise")
)

/**
 * Read the available sampler/scheduler names from the server's own
 * object_info, so a custom sampler that only exists on this server still
 * shows up. A choice is offered only when the workflow actually has such a
 * node AND the server lists options for it.
 */
fun detectSamplerSlots(workflow: Workflow, objectInfo: JsonObject): SamplerSlots {
    var samplerOptions: List<String> = emptyList()
    var schedulerOptions: List<String> = emptyList()
    var currentSampler = ""
    var currentScheduler = ""
    var hasDenoise = false
    var currentDenoise = 1f

    workflow.entries.forEach { (_, node) ->
        val allowed = SAMPLER_INPUTS[node.classType] ?: return@forEach
        val spec = objectInfo[node.classType]?.jsonObject
        val groups = listOfNotNull(
            spec?.get("input")?.jsonObject?.get("required")?.jsonObject,
            spec?.get("input")?.jsonObject?.get("optional")?.jsonObject
        )
        val inputs = node.inputs.jsonObject

        listOf("sampler_name", "scheduler").forEach { name ->
            if (name !in allowed) return@forEach
            val options = groups.firstOrNull { it.containsKey(name) }?.get(name)?.comboStringOptions()
                ?: return@forEach
            if (options.isEmpty()) return@forEach
            if (name == "sampler_name") {
                samplerOptions = mergeOptions(samplerOptions, options)
                if (currentSampler.isEmpty()) currentSampler = inputs[name].stringOrNull() ?: ""
            } else {
                schedulerOptions = mergeOptions(schedulerOptions, options)
                if (currentScheduler.isEmpty()) currentScheduler = inputs[name].stringOrNull() ?: ""
            }
        }

        if ("denoise" in allowed && inputs["denoise"] is JsonPrimitive) {
            hasDenoise = true
            if (currentDenoise == 1f) {
                currentDenoise = inputs["denoise"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 1f
            }
        }
    }

    return SamplerSlots(samplerOptions, schedulerOptions, currentSampler, currentScheduler, hasDenoise, currentDenoise)
}

private fun mergeOptions(existing: List<String>, more: List<String>): List<String> =
    if (existing.isEmpty()) more else existing.intersect(more.toSet()).toList().ifEmpty { more }

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * JsonElement -> the accepted strings for a combo input, covering every form
 * ComfyUI uses: [["a","b"], {meta}] (older servers), ["COMBO", {options:
 * ["a","b"]}] (0.43+), and a plain ["a","b"]. Anything else (type names, link
 * specs) yields null: not a choice we can offer.
 */
internal fun JsonElement?.comboStringOptions(): List<String>? {
    val array = this as? JsonArray ?: return null
    if (array.isEmpty()) return null
    val first = array.first()
    // 0.43 style: the list lives in the metadata object.
    if (first is JsonPrimitive && first.isString && first.content == "COMBO" && array.size > 1) {
        val options = (array[1] as? JsonObject)?.get("options") as? JsonArray ?: return null
        val strings = options.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        return strings.takeIf { it.isNotEmpty() && strings.size == options.size }
    }
    val candidates: List<JsonElement> = if (first is JsonArray) array.first() as JsonArray else array
    val strings = candidates.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    return strings.takeIf { it.isNotEmpty() && strings.size == candidates.size }
}

/**
 * Write the chosen sampler/scheduler/denoise into every sampler node that
 * accepts them. Null means "keep what the workflow says". Linked inputs are
 * never overwritten (replaceInputs protects them).
 */
fun Workflow.applySamplerChoices(sampler: String?, scheduler: String?, denoise: Float?) {
    if (sampler == null && scheduler == null && denoise == null) return
    entries.toList().forEach { (id, node) ->
        val allowed = SAMPLER_INPUTS[node.classType] ?: return@forEach
        // Some graphs feed these widgets from primitive nodes; writeNumeric
        // follows those links instead of breaking them.
        sampler?.let { if ("sampler_name" in allowed) writeNumeric(id, "sampler_name", JsonPrimitive(it)) }
        scheduler?.let { if ("scheduler" in allowed) writeNumeric(id, "scheduler", JsonPrimitive(it)) }
        denoise?.let { if ("denoise" in allowed) writeNumeric(id, "denoise", JsonPrimitive(it)) }
    }
}

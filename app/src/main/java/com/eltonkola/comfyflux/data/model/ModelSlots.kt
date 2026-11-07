package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One model choice the app can drive from the phone: a workflow node whose
 * input is a file list the server can fill in (checkpoint, lora, vae...).
 * Mirrors the web UI's widget dropdown without showing the graph.
 */
data class ModelSlot(
    val nodeId: String,
    val classType: String,
    val input: String,
    val current: String,
    val options: List<String>,
    /** Strength widgets sitting next to this file choice (LoRA strength...). */
    val strengths: List<StrengthSlot> = emptyList(),
) {
    /** Key for the override map: unique per node input. */
    val key: String get() = "$nodeId|$input"

    /** Key for the strength override map ("nodeId|strength_model"). */
    fun strengthKey(strength: StrengthSlot) = "$nodeId|${strength.input}"

    val label: String get() = SLOT_LABELS[classType] ?: classType
}

/**
 * A strength float the node offers, with the range the server declares.
 * ComfyUI advertises -100..100 for LoRA strength, which is technically true
 * and useless as a slider, so the UI picks a usable window around the
 * workflow's own value (see strengthRange).
 */
data class StrengthSlot(
    val input: String,
    val label: String,
    val current: Float,
    val min: Float,
    val max: Float
) {
    /** Slider bounds: the server range, narrowed to something draggable. */
    fun range(override: Float?): ClosedFloatingPointRange<Float> {
        val values = listOf(current, override ?: current)
        val lo = minOf(-1f, values.minOrNull() ?: 0f).coerceAtLeast(min)
        val hi = maxOf(2f, values.maxOrNull() ?: 1f).coerceAtMost(max)
        return if (hi > lo) lo..hi else 0f..1f
    }
}

// Strength widgets the app exposes beside a model file choice. "strength" is
// the bare knob apply nodes use (ControlNet applies, adapters); the stacked
// forms belong to multi-lora loaders, where lora_0 pairs with strength_0.
internal val STRENGTH_INPUTS = mapOf(
    "strength_model" to "model",
    "strength_clip" to "CLIP",
    "strength" to "strength"
)

// Strength of a stacked loader slot: lora_<i> -> strength_<i>.
private fun stackedStrengthFor(fileInput: String): String? =
    STACKED_LORA_INPUTS[fileInput]?.let { "strength_$it" }

private val STACKED_LORA_INPUTS = mapOf(
    "lora_0" to 0, "lora_1" to 1, "lora_2" to 2, "lora_3" to 3
)

// Every strength input name applyStrengthOverrides may write: keeps a stale
// draft from rewriting an unrelated float.
private val WRITABLE_STRENGTH_INPUTS: Set<String> =
    STRENGTH_INPUTS.keys + (0..3).map { "strength_$it" }

// Input names that hold model files. Anything not in this set is a graph
// choice we deliberately do not touch (samplers, schedulers, node toggles).
internal val MODEL_INPUTS = setOf(
    "ckpt_name",
    "unet_name",
    "lora_name",
    "lora_0", // ComfyUI-KJ/rgl3-style multi-lora stacks
    "lora_1",
    "lora_2",
    "lora_3",
    "vae_name",
    "clip_name",
    "clip_name1",
    "clip_name2",
    "clip_name3",
    "control_net_name",
    "upscale_model",
    "model_name",
)

private val SLOT_LABELS = mapOf(
    "CheckpointLoaderSimple" to "Checkpoint",
    "CheckpointLoader" to "Checkpoint",
    "UnetLoaderGGUF" to "UNET (GGUF)",
    "UnetLoaderGGUFAdvanced" to "UNET (GGUF)",
    "UNETLoader" to "UNET",
    "UnetLoader" to "UNET",
    "LoraLoader" to "LoRA",
    "LoraLoaderModelOnly" to "LoRA (model only)",
    "VAELoader" to "VAE",
    "CLIPLoader" to "CLIP",
    "CLIPLoaderGGUF" to "CLIP (GGUF)",
    "DualCLIPLoader" to "CLIP pair",
    "DualCLIPLoaderGGUF" to "CLIP pair (GGUF)",
    "TripleCLIPLoader" to "CLIP trio",
    "CLIPVisionLoader" to "CLIP Vision",
    "ControlNetLoader" to "ControlNet",
    "InpaintModelConditioning" to "Inpaint model",
    "UpscaleModelLoader" to "Upscale model",
)

/**
 * Detect the model choices of a converted (API format) workflow using the
 * server's own /object_info: an input qualifies only when the server
 * actually advertises a non-empty list of file choices for it, so unknown
 * custom nodes with free-text inputs are left alone.
 */
fun detectModelSlots(workflow: Workflow, objectInfo: JsonObject): List<ModelSlot> {
    return workflow.entries.mapNotNull { (id, node) ->
        val spec = objectInfo[node.classType]?.jsonObjectOrNull ?: return@mapNotNull null
        val inputs = listOfNotNull(
            spec["input"]?.jsonObjectOrNull?.get("required")?.jsonObjectOrNull,
            spec["input"]?.jsonObjectOrNull?.get("optional")?.jsonObjectOrNull,
        )
        val current = node.inputs.jsonObject
        inputs.flatMap { group ->
            group.entries.mapNotNull { (name, descriptor) ->
                if (name !in MODEL_INPUTS) return@mapNotNull null
                val options = descriptor.comboOptions() ?: return@mapNotNull null
                val value = current[name]?.jsonPrimitiveOrNull?.takeIf { it.isString }
                    ?.content ?: ""
                ModelSlot(id, node.classType, name, value, options, nodeStrengths(inputs, current, name))
            }
        }.takeIf { it.isNotEmpty() }
    }.flatten().sortedWith(
        // Stable, human order: checkpoints first, then loras, then helpers.
        compareBy({ slotOrder(it.classType) }, { it.nodeId.toIntOrNull() ?: 0 }, { it.input })
    )
}

/**
 * The strength widgets of a node: only when the node itself has the input as
 * a live number (a linked value belongs to its source node) and the server
 * declares a range for it. [fileInput] is the model input the widgets sit
 * beside, so a stacked loader (lora_2) offers its own strength (strength_2).
 */
private fun nodeStrengths(
    groups: List<JsonObject>,
    current: JsonObject,
    fileInput: String = ""
): List<StrengthSlot> {
    val extra: Map<String, String> = stackedStrengthFor(fileInput)?.let { mapOf(it to "strength") }
        ?: emptyMap()
    val wanted: Map<String, String> = STRENGTH_INPUTS + extra
    return wanted.mapNotNull { (name, label) ->
        if (current[name] !is JsonPrimitive) return@mapNotNull null
        val spec = groups.firstOrNull { it.containsKey(name) }?.get(name)
        val meta = ((spec as? JsonArray)?.getOrNull(1)) as? JsonObject ?: return@mapNotNull null
        val value = (current[name] as JsonPrimitive).contentOrNull?.toFloatOrNull() ?: 1f
        StrengthSlot(
            input = name,
            label = label,
            current = value,
            min = meta["min"]?.jsonPrimitiveOrNull?.contentOrNull?.toFloatOrNull() ?: -100f,
            max = meta["max"]?.jsonPrimitiveOrNull?.contentOrNull?.toFloatOrNull() ?: 100f
        )
    }
}

/**
 * Strength knobs of nodes that carry no model file choice of their own -
 * ControlNet/adapter apply nodes, whose `strength` decides how hard the
 * conditioning pushes. [coveredNodeIds] are the nodes already shown as model
 * slots, so their strengths are never offered twice.
 */
data class StrengthGroup(
    val nodeId: String,
    val classType: String,
    val label: String,
    val strengths: List<StrengthSlot>
)

private val APPLY_LABELS = mapOf(
    "ControlNetApply" to "ControlNet",
    "ControlNetApplyAdvanced" to "ControlNet",
    "ControlNetApplySD3" to "ControlNet",
    "MiniMaxH3FunControlNetApply" to "ControlNet",
    "AnimaLLLiteApply" to "LLLite",
    "ConditioningSetAreaStrength" to "Conditioning area",
    "DifferentialDiffusion" to "Differential diffusion"
)

fun detectStrengthGroups(
    workflow: Workflow,
    objectInfo: JsonObject,
    coveredNodeIds: Set<String> = emptySet()
): List<StrengthGroup> =
    workflow.entries.mapNotNull { (id, node) ->
        if (id in coveredNodeIds) return@mapNotNull null
        val spec = objectInfo[node.classType]?.jsonObjectOrNull ?: return@mapNotNull null
        val inputs = listOfNotNull(
            spec["input"]?.jsonObjectOrNull?.get("required")?.jsonObjectOrNull,
            spec["input"]?.jsonObjectOrNull?.get("optional")?.jsonObjectOrNull,
        )
        // A node with its own file choices is already a model slot.
        if (inputs.any { group -> group.keys.any { it in MODEL_INPUTS } }) return@mapNotNull null
        val strengths = nodeStrengths(inputs, node.inputs.jsonObject)
        strengths.takeIf { it.isNotEmpty() }?.let {
            StrengthGroup(id, node.classType, APPLY_LABELS[node.classType] ?: node.classType, it)
        }
    }.sortedBy { it.nodeId.toIntOrNull() ?: 0 }

private val SLOT_ORDER = listOf(
    "CheckpointLoaderSimple", "CheckpointLoader",
    "UnetLoaderGGUF", "UnetLoaderGGUFAdvanced", "UNETLoader", "UnetLoader",
    "CLIPLoader", "CLIPLoaderGGUF", "DualCLIPLoader", "DualCLIPLoaderGGUF", "TripleCLIPLoader",
    "VAELoader", "LoraLoader", "LoraLoaderModelOnly", "CLIPVisionLoader",
    "ControlNetLoader", "UpscaleModelLoader",
)

private fun slotOrder(classType: String) = SLOT_ORDER.indexOf(classType).let { if (it < 0) 99 else it }

/**
 * Parse an object_info input descriptor into its file options. Accepts both
 * classic shapes: ["a.safetensors", "b.safetensors"] and the modern
 * [["a.safetensors", "b.safetensors"], {metadata}]. Anything else (type
 * names, free text, link specs) yields null: not a choice we can offer.
 */
private fun JsonElement.comboOptions(): List<String>? = comboStringOptions()

/**
 * Point the chosen loader inputs at the picked files. Keys are ModelSlot.key
 * ("nodeId|input"); unknown ids/inputs are ignored so stale drafts can never
 * corrupt unrelated inputs.
 */
fun Workflow.applyModelOverrides(overrides: Map<String, String>) {
    overrides.forEach { (key, value) ->
        val parts = key.split('|', limit = 2)
        if (parts.size != 2) return@forEach
        val (id, input) = parts
        if (input !in MODEL_INPUTS) return@forEach
        replaceInputs(id, mapOf(input to JsonPrimitive(value)))
    }
}

/**
 * Strength overrides, keyed like the model overrides ("nodeId|strength_model").
 * Kept separate from applyModelOverrides on purpose: that one is gated on
 * file-input names, and a strength is not a file. Unknown ids, linked values
 * and inputs the node does not have are ignored.
 */
fun Workflow.applyStrengthOverrides(overrides: Map<String, Float>) {
    overrides.forEach { (key, value) ->
        val parts = key.split('|', limit = 2)
        if (parts.size != 2) return@forEach
        val (id, input) = parts
        if (input !in WRITABLE_STRENGTH_INPUTS) return@forEach
        if (this[id] == null) return@forEach
        // Follows PrimitiveFloat links templates use for strengths; a link
        // that ends in a real node output is left completely alone.
        writeNumeric(id, input, JsonPrimitive(value))
    }
}

internal val JsonElement.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

private val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

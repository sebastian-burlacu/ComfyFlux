package com.eltonkola.comfyflux.data.model

import com.eltonkola.comfyflux.data.network.ComfyJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WorkflowSerializer : KSerializer<Workflow> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Workflow") {
            element<JsonObject>("content")
        }

    override fun serialize(encoder: Encoder, value: Workflow) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("This serializer can be used only with Json format")
        val jsonObject = JsonObject(value.mapValues { (_, node) -> Json.encodeToJsonElement(node) })
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Workflow {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer can be used only with Json format")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        return Workflow().apply {
            putAll(jsonObject.mapValues { (_, value) ->
                ComfyJson.decodeFromJsonElement<Node<JsonElement>>(value)
            })
        }
    }
}

@Serializable(with = WorkflowSerializer::class)
class Workflow : MutableMap<String, Node<JsonElement>> by mutableMapOf() {

    fun toJson(): String {
        return Json.encodeToString(this)
    }

    /** Prompt text of the positive text encoder; "" when the workflow has none. */
    fun getPrompt(): String {
        val (id, key) = promptTextTargets().firstOrNull() ?: return ""
        return this[id]?.inputs?.jsonObject?.get(key)?.jsonPrimitiveOrNull?.contentOrNull ?: ""
    }
}

@Serializable
data class Node<T>(
    val inputs: T,
    @SerialName("class_type")
    val classType: String,
    @SerialName("_meta")
    val meta: Meta = Meta("")
)

@Serializable
data class Meta(
    val title: String
)

private val CLIP_TEXT_ENCODE = "CLIPTextEncode"

// Nodes that consume conditioning; walking their conditioning links leads to
// the text encoder that holds the positive prompt.
private val CONDITIONING_CONSUMERS = setOf(
    "KSampler",
    "KSamplerAdvanced",
    "SamplerCustom",
    "SamplerCustomAdvanced",
    "CFGGuider",
    "BasicGuider"
)

// Input names that carry conditioning links.
private val CONDITIONING_INPUTS = setOf("positive", "positive_input", "conditioning", "guider")

// Input names where the user's prompt text can live, in preference order.
internal val TEXT_LITERAL_KEYS = listOf("text", "prompt", "value", "string")

// Link names to never follow when hunting the positive prompt: negative
// conditioning, and LLM system/instruction slots that must keep their text.
private val NON_USER_LINKS = setOf(
    "negative", "negative_input",
    "system", "system_prompt", "instruction", "template", "message", "messages"
)

private fun looksLikeSystemSlot(key: String): Boolean =
    NON_USER_LINKS.contains(key.lowercase()) || key.lowercase().startsWith("system")

// class_type -> name of the input that holds its seed
private val SEED_INPUTS = mapOf(
    "KSampler" to "seed",
    "KSamplerAdvanced" to "noise_seed",
    "RandomNoise" to "noise_seed"
)

// A link is a JsonArray [nodeId, outputSlot]; return the referenced node id.
private fun JsonElement.linkedNodeId(): String? {
    val array = this as? JsonArray ?: return null
    return array.firstOrNull()?.jsonPrimitive?.contentOrNull
}

private fun Node<JsonElement>.conditioningLinks(): List<String> =
    inputs.jsonObject
        .filterKeys { it in CONDITIONING_INPUTS }
        .values
        .mapNotNull { it.linkedNodeId() }

/**
 * Ids of the CLIPTextEncode nodes that feed the sampler chain (positive
 * conditioning), so only the positive prompt is replaced. Falls back to the
 * first text encoder when nothing is linked, so simple or foreign workflows
 * still receive the prompt instead of silently keeping their placeholder.
 */
fun Workflow.promptNodeIds(): List<String> {
    val visited = mutableSetOf<String>()
    val found = mutableListOf<String>()
    val queue = ArrayDeque<String>()

    filterValues { it.classType in CONDITIONING_CONSUMERS }
        .forEach { (_, node) -> queue.addAll(node.conditioningLinks()) }

    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!visited.add(id)) continue
        val node = this[id] ?: continue
        if (node.classType == CLIP_TEXT_ENCODE) found += id
        queue.addAll(node.conditioningLinks())
    }

    if (found.isEmpty()) {
        entries.firstOrNull { it.value.classType == CLIP_TEXT_ENCODE }?.let { found += it.key }
    }
    return found
}

/**
 * Where a numeric widget actually lives. Templates route step counts, CFG and
 * strengths through PrimitiveInt / PrimitiveFloat nodes - and sometimes
 * through a ComfySwitchNode that offers two of them - so the scheduler node's
 * own input holds a link that [replaceInputs] must never overwrite. The write
 * goes to the primitive at the other end of the link instead.
 *
 * Returns the (nodeId, input) pairs to write. A link that ends somewhere else
 * (a math node, a model output) has no safe widget and yields nothing.
 */
private val PRIMITIVE_VALUE_INPUTS = mapOf(
    "PrimitiveInt" to "value",
    "PrimitiveFloat" to "value",
    "PrimitiveString" to "value",
    "PrimitiveStringMultiline" to "value"
)

internal fun Workflow.numericWriteTargets(
    id: String,
    input: String,
    depth: Int = 0
): List<Pair<String, String>> {
    val node = this[id] ?: return emptyList()
    val value = node.inputs.jsonObject[input] ?: return emptyList()
    if (value !is JsonArray) return listOf(id to input)
    val sourceId = value.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return emptyList()
    val source = this[sourceId] ?: return emptyList()
    if (depth >= 2) return emptyList()
    PRIMITIVE_VALUE_INPUTS[source.classType]?.let { return listOf(sourceId to it) }
    if (source.classType == "ComfySwitchNode") {
        // Both branches are written: the override holds whichever way the
        // switch is set, which is what a user typing a step count expects.
        return listOf("on_true", "on_false").flatMap { branch ->
            numericWriteTargets(sourceId, branch, depth + 1)
        }
    }
    return emptyList()
}

/** True when a numeric override for this input can be written somewhere. */
internal fun Workflow.takesNumericInput(id: String, input: String): Boolean =
    numericWriteTargets(id, input).isNotEmpty()

internal fun Workflow.replaceInputs(id: String, replacements: Map<String, JsonPrimitive>) {
    val node = this[id] ?: return
    val updated = buildJsonObject {
        node.inputs.jsonObject.forEach { (key, value) ->
            // Never clobber a link [nodeId, slot] with a literal: linked
            // values (e.g. size from a ResolutionSelector) belong to their source node.
            put(key, if (value is JsonArray) value else replacements[key] ?: value)
        }
    }
    this[id] = node.copy(inputs = updated)
}

private val EMPTY_LATENT_NODES = setOf("EmptyLatentImage", "EmptySD3LatentImage")

fun Workflow.updateSizeAndBatch(width: Int, height: Int, batchSize: Int) {
    filterValues { it.classType in EMPTY_LATENT_NODES }
        .keys.toList()
        .forEach { id ->
            replaceInputs(
                id,
                mapOf(
                    "width" to JsonPrimitive(width),
                    "height" to JsonPrimitive(height),
                    "batch_size" to JsonPrimitive(batchSize)
                )
            )
        }
}

/**
 * (nodeId, inputKey) pairs holding the positive prompt text. Prefers the
 * conditioning chain of samplers/guiders; when the graph has no classic
 * text encoder (subgraphs, custom encoders), falls back to a link walk
 * from the consumers. A text input that is a link (e.g. fed by a
 * PrimitiveStringMultiline widget node) resolves to the literal node the
 * user would edit in the web UI - patching the encoder link itself would
 * do nothing, which is the "app ignores my prompt" bug this fixes.
 */
fun Workflow.promptTextTargets(): List<Pair<String, String>> {
    val targets = mutableListOf<Pair<String, String>>()

    conditioningTextSources().forEach { (id, key) ->
        resolveLiteralTarget(id, key)?.forEach { t -> if (t !in targets) targets += t }
    }

    // The broad pass keeps sibling slots fed by the same subgraph input in
    // sync (e.g. the Qwen template wires one prompt box to both the switch's
    // on_false branch and TextGenerate.prompt), matching what the web UI
    // sends. Deduped, never replacing the precise link targets found first.
    broadTextSearch().forEach { t -> if (t !in targets) targets += t }

    // Last resort (simple graphs with an unlinked CLIPTextEncode): patch the
    // encoder directly so the prompt is never silently dropped.
    if (targets.isEmpty()) {
        promptNodeIds().forEach { id ->
            val node = this[id] ?: return@forEach
            TEXT_LITERAL_KEYS.firstOrNull { it in node.inputs.jsonObject }?.let { targets += id to it }
        }
    }
    return targets
}

/**
 * (node, key) text slots along the positive conditioning chain, whatever
 * class they sit on: custom encoders (TextEncodeQwenImage21.prompt) feed the
 * sampler just like CLIPTextEncode and must be recognized.
 */
private fun Workflow.conditioningTextSources(): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<String>()

    filterValues { it.classType in CONDITIONING_CONSUMERS }
        .forEach { (_, node) -> queue.addAll(node.conditioningLinks()) }

    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!visited.add(id)) continue
        val node = this[id] ?: continue
        TEXT_LITERAL_KEYS.firstOrNull { it in node.inputs.jsonObject }?.let { out += id to it }
        queue.addAll(node.conditioningLinks())
    }
    return out
}

/** Nodes that route one of two inputs based on a boolean "switch". */
private fun isSwitchLike(node: Node<JsonElement>): Boolean =
    node.classType == "ComfySwitchNode" ||
        (node.inputs.jsonObject.containsKey("switch") &&
            (node.inputs.jsonObject.containsKey("on_true") || node.inputs.jsonObject.containsKey("on_false")))

/** Where the actual text lives for input [key] of node [id]: literal or behind a link. */
private fun Workflow.resolveLiteralTarget(id: String, key: String): List<Pair<String, String>> {
    val node = this[id] ?: return emptyList()
    var value: JsonElement = node.inputs.jsonObject[key] ?: return emptyList()
    if (value !is JsonArray) return listOf(id to key)

    repeat(4) {
        val srcId = value.linkedNodeId() ?: return emptyList()
        val src = this[srcId] ?: return emptyList()

        if (isSwitchLike(src)) {
            // Only the ACTIVE branch carries the text that runs. When the
            // branch is an inline string, target it (and the inline sibling,
            // so flipping the switch in a saved run stays coherent).
            val on = src.inputs.jsonObject["switch"]?.jsonPrimitiveOrNull?.booleanOrNull == true
            val active = if (on) "on_true" else "on_false"
            val alt = if (on) "on_false" else "on_true"
            val branch = src.inputs.jsonObject[active]
            if (branch is JsonArray) {
                value = branch
                return@repeat
            }
            if (branch?.jsonPrimitiveOrNull?.isString == true) {
                val out = mutableListOf(srcId to active)
                if (src.inputs.jsonObject[alt]?.jsonPrimitiveOrNull?.isString == true) {
                    out += srcId to alt
                }
                return out
            }
            return emptyList()
        }

        val srcKey = TEXT_LITERAL_KEYS.firstOrNull { k ->
            src.inputs.jsonObject[k]?.jsonPrimitiveOrNull?.isString == true
        }
        if (srcKey != null) return listOf(srcId to srcKey)

        // Pass-through nodes (PreviewAny etc.): a single linked input means
        // the text comes from it; hop over the node instead of giving up.
        val passthrough = src.inputs.jsonObject.values
            .filterIsInstance<JsonArray>()
            .singleOrNull() ?: return emptyList()
        value = passthrough
    }
    return emptyList()
}

/**
 * Last resort: walk every (non-system, non-negative) input link out of the
 * conditioning consumers and collect string inputs on the way. Catches
 * graphs like the Qwen-image templates where the prompt sits on custom
 * nodes (TextEncode.../TextGenerate) behind switch nodes.
 */
private fun Workflow.broadTextSearch(): List<Pair<String, String>> {
    val found = mutableListOf<Pair<String, String>>()
    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<String>()

    filterValues { it.classType in CONDITIONING_CONSUMERS }
        .forEach { (id, _) -> queue.add(id) }

    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!visited.add(id)) continue
        val node = this[id] ?: continue

        TEXT_LITERAL_KEYS.firstOrNull { key ->
            !looksLikeSystemSlot(key) && node.inputs.jsonObject[key]?.jsonPrimitiveOrNull?.isString == true
        }?.let { key -> found += id to key }

        node.inputs.jsonObject.forEach { (key, value) ->
            if (!looksLikeSystemSlot(key)) {
                value.linkedNodeId()?.let { queue.add(it) }
            }
        }
    }
    return found
}

private val JsonElement.jsonPrimitiveOrNull: kotlinx.serialization.json.JsonPrimitive?
    get() = this as? kotlinx.serialization.json.JsonPrimitive

fun Workflow.updatePrompt(prompt: String) {
    promptTextTargets().forEach { (id, key) ->
        replaceInputs(id, mapOf(key to JsonPrimitive(prompt)))
    }
}

/** Input names where a negative prompt can live, preferred over generic ones. */
private val NEGATIVE_TEXT_KEYS = listOf("negative_prompt", "negative_text", "negative")

/**
 * Where a user negative prompt belongs: walk the NEGATIVE conditioning
 * links and land on the node holding that text (a dedicated negative
 * encoder, a negative_prompt slot, or its linked literal). Nodes already
 * patched as the positive prompt are skipped, so graphs that derive the
 * negative from the positive (ConditioningZeroOut) are left alone.
 */
fun Workflow.negativeTextTargets(): List<Pair<String, String>> {
    val positiveTargets = promptTextTargets()
    val found = mutableListOf<Pair<String, String>>()
    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<String>()

    filterValues { it.classType in CONDITIONING_CONSUMERS }
        .forEach { (_, node) ->
            node.inputs.jsonObject
                .filterKeys { it == "negative" || it == "negative_input" }
                .values
                .mapNotNull { it.linkedNodeId() }
                .forEach { queue.add(it) }
        }

    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!visited.add(id)) continue
        val node = this[id] ?: continue

        val key = (NEGATIVE_TEXT_KEYS + TEXT_LITERAL_KEYS).firstOrNull { k ->
            node.inputs.jsonObject[k]?.let { v ->
                v.jsonPrimitiveOrNull?.isString == true || v is JsonArray
            } == true
        }
        if (key != null) {
            // Never patch the positive target through the negative walk
            // (graphs that derive the negative via ConditioningZeroOut).
            resolveLiteralTarget(id, key)
                ?.filter { it !in positiveTargets }
                ?.forEach { t -> if (t !in found) found += t }
            continue
        }
        queue.addAll(node.conditioningLinks())
    }
    return found
}

/** Write the user's negative prompt; no-op when the graph has nowhere for it. */
fun Workflow.updateNegativePrompt(prompt: String) {
    if (prompt.isBlank()) return
    negativeTextTargets().forEach { (id, key) ->
        replaceInputs(id, mapOf(key to JsonPrimitive(prompt)))
    }
}

fun Workflow.updateSeed(seed: Long) {
    entries.toList().forEach { (id, node) ->
        SEED_INPUTS[node.classType]?.let { key ->
            // Templates often feed the seed from a "Seed" PrimitiveInt node;
            // write there instead of leaving the link pointing elsewhere.
            writeNumeric(id, key, JsonPrimitive(seed))
        }
    }
}

// class_type -> the sampler inputs the app is allowed to override. KSamplerAdvanced
// names its step count "steps" as well; the newer guide graphs split the same
// knobs across nodes: the guider holds cfg, BasicScheduler holds steps.
private val CFG_STEPS_INPUTS = mapOf(
    "KSampler" to mapOf("cfg" to "cfg", "steps" to "steps"),
    "KSamplerAdvanced" to mapOf("cfg" to "cfg", "steps" to "steps"),
    "CFGGuider" to mapOf("cfg" to "cfg"),
    // Two CFGs: the cond CFG is the one the slider means.
    "DualCFGGuider" to mapOf("cfg" to "cfg_conds"),
    // Guide graphs keep the step count on the node that builds the sigma
    // schedule, and ComfyUI ships a family of them. Writing the same number
    // the web UI widget would write is what makes the slider work there.
    "BasicScheduler" to mapOf("steps" to "steps"),
    "Flux2Scheduler" to mapOf("steps" to "steps"),
    "SDTurboScheduler" to mapOf("steps" to "steps"),
    "AlignYourStepsScheduler" to mapOf("steps" to "steps"),
    "OptimalStepsScheduler" to mapOf("steps" to "steps"),
    "GITSScheduler" to mapOf("steps" to "steps"),
    "BetaSamplingScheduler" to mapOf("steps" to "steps"),
    "KarrasScheduler" to mapOf("steps" to "steps"),
    "ExponentialScheduler" to mapOf("steps" to "steps"),
    "PolyexponentialScheduler" to mapOf("steps" to "steps"),
    "LaplaceScheduler" to mapOf("steps" to "steps"),
    "VPScheduler" to mapOf("steps" to "steps"),
    "LTXVScheduler" to mapOf("steps" to "steps"),
    "Ideogram4Scheduler" to mapOf("steps" to "steps")
)

// Sigma-list nodes: the step count is baked into a literal sigma string, so
// no widget exists to write. ExtendIntermediateSigmas takes a schedule and
// stretches it, which is why it is not in the table above.
internal val SIGMA_LIST_NODES = setOf(
    "ManualSigmas", "ExtendIntermediateSigmas", "FlipSigmas", "SplitSigmas",
    "SplitSigmasDenoise", "SetFirstSigma", "NormalizeSigmas"
)

/**
 * Where the Steps / CFG sliders can actually land in this graph. Empty lists
 * are the honest answer for guided models (FLUX.2 has no CFG widget) and for
 * sigma-list workflows (steps are fixed), and the UI says so instead of
 * letting a slider do nothing.
 */
data class GuidanceTargets(
    val stepsNodeIds: List<String>,
    val cfgNodeIds: List<String>,
    /** Steps are typed in as a sigma list somewhere in the graph. */
    val fixedSigmaSchedule: Boolean
) {
    val takesSteps: Boolean get() = stepsNodeIds.isNotEmpty()
    val takesCfg: Boolean get() = cfgNodeIds.isNotEmpty()

}

/**
 * Override the sampler settings with the values picked in the More tab.
 * cfg <= 0 / steps <= 0 mean "keep whatever the workflow itself specifies",
 * so each is skipped independently; a node only ever receives what it exposes.
 */
/** Literal value, or the value of the primitive a link points at. */
internal fun Workflow.widgetValue(id: String, input: String): String? {
    val value = this[id]?.inputs?.jsonObject?.get(input) ?: return null
    if (value is JsonPrimitive) return value.contentOrNull
    numericWriteTargets(id, input).firstOrNull()?.let { (targetId, targetInput) ->
        return this[targetId]?.inputs?.jsonObject?.get(targetInput)?.jsonPrimitive?.contentOrNull
    }
    return null
}

fun Workflow.guidanceTargets(): GuidanceTargets {
    val steps = mutableListOf<String>()
    val cfg = mutableListOf<String>()
    forEach { (id, node) ->
        val names = CFG_STEPS_INPUTS[node.classType] ?: return@forEach
        if (names["steps"] != null && takesNumericInput(id, names.getValue("steps"))) steps += id
        if (names["cfg"] != null && takesNumericInput(id, names.getValue("cfg"))) cfg += id
    }
    return GuidanceTargets(
        stepsNodeIds = steps,
        cfgNodeIds = cfg,
        fixedSigmaSchedule = any { it.value.classType in SIGMA_LIST_NODES }
    )
}

fun Workflow.updateCfgAndSteps(cfg: Float, steps: Int) {
    if (cfg <= 0f && steps <= 0) return
    entries.toList().forEach { (id, node) ->
        val names = CFG_STEPS_INPUTS[node.classType] ?: return@forEach
        if (cfg > 0f) names["cfg"]?.let { input -> writeNumeric(id, input, JsonPrimitive(cfg)) }
        if (steps > 0) names["steps"]?.let { input -> writeNumeric(id, input, JsonPrimitive(steps)) }
    }
}

/** Write one numeric widget, following the primitive links templates use. */
internal fun Workflow.writeNumeric(id: String, input: String, value: JsonPrimitive) {
    numericWriteTargets(id, input).forEach { (targetId, targetInput) ->
        replaceInputs(targetId, mapOf(targetInput to value))
    }
}

/**
 * img2img hook: ComfyUI's standard image input node. When the graph has a
 * LoadImage, the app uploads the phone photo chosen on the Create screen
 * and writes the server-side filename into it.
 */
fun Workflow.hasImageInput(): Boolean = any { it.value.classType == "LoadImage" }

/** One LoadImage node the workflow expects, plus the file it ships with. */
data class ImageInput(val id: String, val file: String, val primary: Boolean)

/**
 * Every LoadImage the workflow loads, lowest-id first. Edit templates keep
 * separate *reference* images in extra nodes (the qwen image-edit template
 * loads a portrait AND a shirt), so the UI has to show one slot per node
 * instead of assuming a single input photo. The primary node - the one an
 * img2img photo belongs in - is conventionally the lowest-id LoadImage.
 */
fun Workflow.imageInputs(): List<ImageInput> {
    val nodes = filterValues { it.classType == "LoadImage" }
    val primaryId = nodes.keys.minByOrNull { it.toIntOrNull() ?: Int.MAX_VALUE }
    return nodes.entries
        .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
        .map { (id, node) ->
            ImageInput(
                id = id,
                file = node.inputs.jsonObject["image"]?.jsonPrimitive?.contentOrNull ?: "",
                primary = id == primaryId
            )
        }
}

/**
 * Point each assigned LoadImage node at its uploaded server file (names are
 * already subfolder-prefixed when the server put them in a subfolder).
 * Unassigned nodes keep the file the workflow shipped with.
 */
fun Workflow.applyInputImages(assignments: Map<String, String>) {
    assignments.forEach { (id, name) ->
        if (name.isNotBlank() && containsKey(id)) {
            replaceInputs(id, mapOf("image" to JsonPrimitive(name)))
        }
    }
}

/** How a submitted run was configured, read back from its API-format prompt. */
data class RunParams(
    val seed: Long?,
    val steps: Int?,
    val cfg: Float?,
    val sampler: String?,
    val scheduler: String?,
    val denoise: Float?,
    val models: List<String>
) {
    fun summary(): String = buildList {
        seed?.let { add("seed $it") }
        steps?.let { add("$it steps") }
        cfg?.let { add("cfg ${if (it % 1f == 0f) it.toInt().toString() else it.toString()}") }
        sampler?.let { add(it) }
        scheduler?.let { add(it) }
        denoise?.let { if (it < 1f) add("denoise $it") }
    }.joinToString(" - ")
}

fun Workflow.runParams(): RunParams {
    val sampler = values.firstOrNull { it.classType.contains("KSampler") }
        ?.inputs?.jsonObject
    val modelNodes = filterValues {
        it.classType in setOf(
            "CheckpointLoaderSimple", "CheckpointLoader", "UNETLoader",
            "LoraLoader", "LoraLoaderModelOnly", "UnetLoaderGGUF"
        )
    }
    val models = modelNodes.values.mapNotNull { node ->
        listOf("ckpt_name", "unet_name", "lora_name")
            .firstNotNullOfOrNull { node.inputs.jsonObject[it]?.jsonPrimitive?.contentOrNull }
    }
    return RunParams(
        seed = sampler?.get("seed")?.jsonPrimitive?.longOrNull,
        steps = sampler?.get("steps")?.jsonPrimitive?.intOrNull,
        cfg = sampler?.get("cfg")?.jsonPrimitive?.floatOrNull
            ?: sampler?.get("cfg_2")?.jsonPrimitive?.floatOrNull,
        sampler = sampler?.get("sampler_name")?.jsonPrimitive?.contentOrNull,
        scheduler = sampler?.get("scheduler")?.jsonPrimitive?.contentOrNull,
        denoise = sampler?.get("denoise")?.jsonPrimitive?.floatOrNull,
        models = models
    )
}

/** What a workflow consumes, for the "This workflow needs" card. */
data class WorkflowNeeds(
    val imageCount: Int,
    /** Empty latent node: the size sliders drive output size. */
    val hasLatent: Boolean,
    /** No latent to size: the output follows an encoded input image. */
    val usesImageLatent: Boolean,
    /** Class of the node the prompt text lands in. */
    val encoder: String?
)

fun Workflow.needs(): WorkflowNeeds {
    val targets = promptTextTargets().mapNotNull { (id, _) -> this[id]?.classType }
    return WorkflowNeeds(
        imageCount = filterValues { it.classType == "LoadImage" }.size,
        hasLatent = values.any { it.classType in EMPTY_LATENT_NODES },
        usesImageLatent = values.any { it.classType == "VAEEncode" },
        encoder = targets.firstOrNull { it.contains("Encode", ignoreCase = true) }
            ?: targets.firstOrNull()
    )
}

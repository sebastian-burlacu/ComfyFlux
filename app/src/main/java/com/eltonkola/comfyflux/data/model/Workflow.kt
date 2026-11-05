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
import kotlinx.serialization.json.contentOrNull
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

    promptNodeIds().forEach { id ->
        val node = this[id] ?: return@forEach
        val key = TEXT_LITERAL_KEYS.firstOrNull { it in node.inputs.jsonObject } ?: return@forEach
        resolveLiteralTarget(id, key)?.let { targets += it }
    }

    if (targets.isEmpty()) targets += broadTextSearch()
    return targets
}

/** Where the actual text lives for input [key] of node [id]: literal or behind a link. */
private fun Workflow.resolveLiteralTarget(id: String, key: String): List<Pair<String, String>> {
    val node = this[id] ?: return emptyList()
    val value = node.inputs.jsonObject[key] ?: return emptyList()
    if (value !is JsonArray) return listOf(id to key)

    val srcId = value.linkedNodeId() ?: return emptyList()
    val src = this[srcId] ?: return emptyList()
    val srcKey = TEXT_LITERAL_KEYS.firstOrNull { k ->
        src.inputs.jsonObject[k]?.jsonPrimitiveOrNull?.isString == true
    } ?: return emptyList()
    return listOf(srcId to srcKey)
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

fun Workflow.updateSeed(seed: Long) {
    entries.toList().forEach { (id, node) ->
        SEED_INPUTS[node.classType]?.let { key ->
            replaceInputs(id, mapOf(key to JsonPrimitive(seed)))
        }
    }
}

// class_type -> the sampler inputs the app is allowed to override. KSamplerAdvanced
// names its step count "steps" as well; CFGGuider only exposes cfg.
private val CFG_STEPS_INPUTS = mapOf(
    "KSampler" to mapOf("cfg" to "cfg", "steps" to "steps"),
    "KSamplerAdvanced" to mapOf("cfg" to "cfg", "steps" to "steps"),
    "CFGGuider" to mapOf("cfg" to "cfg")
)

/**
 * Override the sampler settings with the values picked in the More tab.
 * cfg <= 0 / steps <= 0 mean "keep whatever the workflow itself specifies",
 * so each is skipped independently; CFGGuider only ever receives the cfg.
 */
fun Workflow.updateCfgAndSteps(cfg: Float, steps: Int) {
    if (cfg <= 0f && steps <= 0) return
    entries.toList().forEach { (id, node) ->
        val names = CFG_STEPS_INPUTS[node.classType] ?: return@forEach
        val replacements = buildMap {
            if (cfg > 0f) names["cfg"]?.let { put(it, JsonPrimitive(cfg)) }
            if (steps > 0) names["steps"]?.let { put(it, JsonPrimitive(steps)) }
        }
        if (replacements.isNotEmpty()) replaceInputs(id, replacements)
    }
}

package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What the selected workflow asks for that this server cannot provide,
 * computed from the workflow + /object_info BEFORE a run is queued. The
 * failure sheet used to be the only place this surfaced, so a workflow that
 * obviously cannot run still burned a queue slot to say so.
 */
data class WorkflowGaps(
    /** Node classes the graph uses but the server does not know (custom nodes). */
    val missingNodeTypes: List<String> = emptyList(),
    /** Model files the graph names that the server's own lists do not contain. */
    val missingFiles: List<MissingFile> = emptyList()
) {
    val isEmpty: Boolean get() = missingNodeTypes.isEmpty() && missingFiles.isEmpty()
}

data class MissingFile(
    val classType: String,
    val nodeId: String,
    val input: String,
    val wanted: String
) {
    /** One readable line: what to install, and where the workflow wants it. */
    val note: String
        get() = "$classType node $nodeId: '$wanted' is not on this server (input '$input')."
}

/**
 * Server-declared file choices for one node input, or null when the server
 * says nothing about it (free text, unknown node, custom widget). An empty
 * list is a real answer: the folder exists but holds nothing, which is the
 * most confusing state a template can run into, so it has to be reported.
 */
internal fun objectInfoChoices(objectInfo: JsonObject, classType: String, input: String): List<String>? {
    val spec = objectInfo[classType] as? JsonObject ?: return null
    val groups = listOfNotNull(
        spec["input"]?.jsonObjectOrNull?.get("required")?.jsonObjectOrNull,
        spec["input"]?.jsonObjectOrNull?.get("optional")?.jsonObjectOrNull
    )
    return groups.firstOrNull { it.containsKey(input) }?.get(input)?.declaredOptions()
}

private fun JsonElement?.declaredOptions(): List<String>? {
    val array = this as? JsonArray ?: return null
    if (array.isEmpty()) return null
    val candidates: JsonArray = when (val first = array.first()) {
        // classic form: [["file_a", "file_b"], {meta}]
        is JsonArray -> first
        // metadata-first form: {"options": [...]} straight as the spec
        is JsonObject -> first["options"] as? JsonArray ?: return null
        else -> {
            val text = (first as? JsonPrimitive)?.contentOrNull
            if (text == "COMBO") (array.getOrNull(1) as? JsonObject)?.let { it["options"] as? JsonArray }
                ?: return null
            // flat file list straight from the loader node type
            array
        }
    }
    val strings = candidates.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    return strings.takeIf { it.size == candidates.size }
}

fun Workflow.gaps(objectInfo: JsonObject): WorkflowGaps {
    val nodeTypes = values.map { it.classType }.distinct().filter { objectInfo[it] == null }

    val files = mutableListOf<MissingFile>()
    entries.forEach { (id, node) ->
        node.inputs.jsonObject.forEach { (input, value) ->
            if (input !in MODEL_INPUTS) return@forEach
            val wanted = (value as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }?.contentOrNull ?: return@forEach
            if (wanted.isBlank()) return@forEach
            val choices = objectInfoChoices(objectInfo, node.classType, input) ?: return@forEach
            if (wanted in choices) return@forEach
            files += MissingFile(node.classType, id, input, wanted)
        }
    }

    return WorkflowGaps(nodeTypes, files)
}

package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A boolean control a workflow exposes as a named input - the knobs the
 * ComfyUI web UI renders for template subgraphs (prompt enhance on/off,
 * "thinking", etc.). [index] is the position among the subgraph's declared
 * inputs; [nodeId] is the outer instance node that carries the value.
 */
data class WorkflowToggle(
    val nodeId: String,
    val index: Int,
    val name: String,
    val current: Boolean
) {
    val key: String get() = "$nodeId|$index"

    /** Display name: "negative_prompt" -> "Negative prompt". */
    val label: String
        get() = name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Find boolean inputs on subgraph instance nodes of a graph-format workflow. */
fun detectWorkflowToggles(graph: JsonObject): List<WorkflowToggle> {
    val sgMap = (graph["definitions"]?.jsonObject?.get("subgraphs") as? JsonArray)
        ?.associate { it.jsonObject["id"]!!.jsonPrimitive.content to it.jsonObject }
        ?: return emptyList()
    val nodes = graph["nodes"] as? JsonArray ?: return emptyList()
    val out = ArrayList<WorkflowToggle>()
    nodes.forEach { el ->
        val n = el.jsonObject
        val sg = sgMap[n["type"]?.jsonPrimitive?.contentOrNull] ?: return@forEach
        val nodeId = n["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val wv = n["widgets_values"] as? JsonArray
        (sg["inputs"] as? JsonArray)?.forEachIndexed { k, sgInEl ->
            val sgIn = sgInEl.jsonObject
            if (sgIn["type"]?.jsonPrimitive?.contentOrNull != "BOOLEAN") return@forEachIndexed
            val name = sgIn["name"]?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed
            val current = wv?.getOrNull(k)?.jsonPrimitive?.booleanOrNull
                ?: sgIn["default"]?.jsonPrimitive?.booleanOrNull
                ?: false
            out += WorkflowToggle(nodeId, k, name, current)
        }
    }
    return out
}

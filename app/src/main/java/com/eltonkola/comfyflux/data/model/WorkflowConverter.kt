package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts a graph (frontend/LiteGraph) workflow — the format the ComfyUI web
 * UI saves under user data — into the API format accepted by POST /prompt.
 *
 * Strategy: deterministic ids (main node N -> "N", inner node k of subgraph
 * instance N -> "N_k"). Subgraph instance output maps depend only on the
 * inner graph, so they are computed up front; bindings then resolve outer
 * inputs (link or widget value) for every subgraph input slot, and one
 * expansion pass emits all API nodes.
 */
class WorkflowConverter(private val objectInfo: JsonObject) {

    private val widgetTypes = setOf("INT", "FLOAT", "STRING", "COMBO", "BOOLEAN")

    private data class Spec(val name: String, val type: JsonElement, val opts: JsonObject, val required: Boolean)

    private sealed interface WFValue
    private class WFLiteral(val v: JsonElement) : WFValue
    private class WFRef(val api: String, val slot: Int) : WFValue

    private fun specs(cls: String): List<Spec>? {
        val input = objectInfo[cls]?.jsonObject?.get("input")?.jsonObject ?: return null
        val out = ArrayList<Spec>()
        for (required in booleanArrayOf(true, false)) {
            val group = input[if (required) "required" else "optional"]?.jsonObject ?: continue
            group.forEach { (name, spec) ->
                val arr = spec.jsonArray
                out += Spec(name, arr[0], if (arr.size > 1) arr[1].jsonObject else JsonObject(emptyMap()), required)
            }
        }
        return out
    }

    private fun isWidgetType(t: JsonElement): Boolean =
        t is JsonArray || (t is JsonPrimitive && t.isString && t.content in widgetTypes)

    /** Combo-like custom types (COMFY_DYNAMICCOMBO_V3 etc.) are widgets too. */
    private fun isWidget(s: Spec): Boolean =
        !s.opts.containsKey("forceInput") &&
            (isWidgetType(s.type) || s.opts["options"] is JsonArray)

    /**
     * Widget names in widgets_values order for one UI node: every widgetish
     * spec input (required then optional), each immediately followed by the
     * dotted sub-widget slots the frontend exposes for combo-like inputs
     * (sampling_mode.temperature, format.bit_depth, ...). Subslots belong to
     * widgets_values only under a widgetish parent; plain sockets (autogrow
     * image slots) do not consume values.
     */
    private fun widgetSlots(cls: String, n: JsonObject): List<String> {
        val slots = ArrayList<String>()
        val uiNames = (n["inputs"] as? JsonArray)
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?: emptyList()
        specs(cls)?.forEach { s ->
            if (!isWidget(s)) return@forEach
            slots += s.name
            if (s.opts["control_after_generate"]?.jsonPrimitive?.booleanOrNull == true) {
                slots += s.name + ":cag"
            }
            uiNames.filter { it.startsWith("${s.name}.") }.forEach { slots += it }
        }
        return slots
    }

    private fun normLink(l: JsonElement): Link? = when (l) {
        is JsonArray -> if (l.size >= 5) Link(l[0].jsonPrimitive.int, l[1].jsonPrimitive.int, l[2].jsonPrimitive.int, l[3].jsonPrimitive.int, l[4].jsonPrimitive.int) else null
        is JsonObject -> Link(
            l["id"]!!.jsonPrimitive.int,
            l["origin_id"]!!.jsonPrimitive.int,
            l["origin_slot"]!!.jsonPrimitive.int,
            l["target_id"]!!.jsonPrimitive.int,
            l["target_slot"]!!.jsonPrimitive.int,
        )
        else -> null
    }

    private data class Link(val id: Int, val o: Int, val os: Int, val t: Int, val ts: Int)

    private fun linkMap(links: JsonElement?): Map<Int, Link> {
        val out = HashMap<Int, Link>()
        (links as? JsonArray)?.forEach { l -> normLink(l)?.let { out[it.id] = it } }
        return out
    }

    /** outer output slot -> (inner api id, inner slot); handles chained nesting */
    private fun sgOutputMap(sg: JsonObject, prefix: String, sgMap: Map<String, JsonObject>): Map<Int, Pair<String, Int>> {
        val omap = HashMap<Int, Pair<String, Int>>()
        (sg["links"] as? JsonArray)?.forEach { le ->
            val l = normLink(le) ?: return@forEach
            if (l.t != -20) return@forEach
            val cls = (sg["nodes"] as JsonArray).firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.int == l.o }
                ?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
            if (cls != null && sgMap.containsKey(cls)) {
                sgOutputMap(sgMap.getValue(cls), "${prefix}${l.o}_", sgMap)[l.os]?.let { omap[l.ts] = it }
            } else {
                omap[l.ts] = "${prefix}_${l.o}" to l.os
            }
        }
        return omap
    }

    private fun bindingsFor(
        node: JsonObject,
        sg: JsonObject,
        links: Map<Int, Link>,
        omaps: Map<Int, Map<Int, Pair<String, Int>>>,
    ): Map<Int, WFValue?> {
        val wv = node["widgets_values"] as? JsonArray ?: JsonArray(emptyList())
        val bindings = HashMap<Int, WFValue?>()
        (sg["inputs"] as? JsonArray)?.forEachIndexed { k, sgInEl ->
            val sgIn = sgInEl.jsonObject
            val name = sgIn["name"]?.jsonPrimitive?.contentOrNull
            val ext = (node["inputs"] as? JsonArray)?.firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == name
            }?.jsonObject
            val linkId = ext?.get("link")?.jsonPrimitive?.intOrNull
            if (linkId != null) {
                val lk = links[linkId]
                when {
                    lk == null -> bindings[k] = null
                    omaps.containsKey(lk.o) && omaps.getValue(lk.o).containsKey(lk.os) ->
                        omapTarget(omaps.getValue(lk.o), lk.os)?.let { (api, slot) -> bindings[k] = WFRef(api, slot) }
                    else -> bindings[k] = WFRef(lk.o.toString(), lk.os)
                }
            } else {
                val def = wv.getOrNull(k) ?: sgIn["default"] ?: JsonNull
                bindings[k] = WFLiteral(def)
            }
        }
        return bindings
    }

    /** follow chained subgraph output maps until a concrete inner node */
    private fun omapTarget(omap: Map<Int, Pair<String, Int>>, slot: Int): Pair<String, Int>? = omap[slot]

    private fun nodeEntry(
        n: JsonObject,
        refPrefix: String,
        links: Map<Int, Link>,
        bindings: Map<Int, WFValue?>?,
    ): Node<JsonElement>? {
        val cls = n["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val sp = specs(cls) ?: return null  // not executable (MarkdownNote etc.)
        val inputs = LinkedHashMap<String, JsonElement>()

        (n["inputs"] as? JsonArray)?.forEach { slotEl ->
            val slot = slotEl.jsonObject
            val name = slot["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val linkId = slot["link"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val lk = links[linkId] ?: return@forEach
            val src: WFValue? = if (lk.o == -10) bindings?.get(lk.os)
            else WFRef(refPrefix + lk.o, lk.os)
            when (src) {
                is WFRef -> inputs[name] = buildJsonArray { add(JsonPrimitive(src.api)); add(JsonPrimitive(src.slot)) }
                is WFLiteral -> if (src.v != JsonNull) inputs[name] = src.v
                null -> {}
            }
        }

        val specMap = sp.associateBy { it.name }
        val wv = n["widgets_values"] as? JsonArray ?: JsonArray(emptyList())
        widgetSlots(cls, n).forEachIndexed { k, name ->
            if (name.endsWith(":cag")) {
                val real = name.removeSuffix(":cag")
                if (k < wv.size && specMap[real]?.opts?.containsKey("control_after_generate") == true) {
                    inputs["control_after_generate"] = wv[k]
                }
                return@forEachIndexed
            }
            if (inputs.containsKey(name)) return@forEachIndexed
            when {
                k < wv.size -> inputs[name] = wv[k]
                specMap[name]?.opts?.get("default")?.let { it != JsonNull } == true ->
                    inputs[name] = specMap.getValue(name).opts.getValue("default")
            }
        }

        val title = n["title"]?.jsonPrimitive?.contentOrNull ?: cls
        return Node(JsonObject(inputs), cls, Meta(title))
    }

    fun convert(graph: JsonObject): Workflow {
        val nodes = graph["nodes"] as? JsonArray
            ?: throw IllegalArgumentException("workflow is not a graph (frontend) format")
        val sgMap = (graph["definitions"]?.jsonObject?.get("subgraphs") as? JsonArray)
            ?.associate { it.jsonObject["id"]!!.jsonPrimitive.content to it.jsonObject }
            ?: emptyMap()
        val links = linkMap(graph["links"])
        val w = Workflow()

        // Pass 1: subgraph instances -> output maps + input bindings
        val omaps = HashMap<Int, Map<Int, Pair<String, Int>>>()
        val binds = HashMap<String, Map<Int, WFValue?>>()
        nodes.forEach { el ->
            val n = el.jsonObject
            val cls = n["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val sg = sgMap[cls] ?: return@forEach
            val id = n["id"]!!.jsonPrimitive.int
            omaps[id] = sgOutputMap(sg, id.toString(), sgMap)
            binds[id.toString()] = bindingsFor(n, sg, links, omaps)
        }

        // Pass 2: expand every node
        nodes.forEach { el ->
            val n = el.jsonObject
            val cls = n["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val id = n["id"]!!.jsonPrimitive.int
            val sg = sgMap[cls]
            if (sg != null) {
                val p = id.toString()
                val innerLinks = linkMap(sg["links"])
                (sg["nodes"] as JsonArray).forEach { innerEl ->
                    val inner = innerEl.jsonObject
                    val innerCls = inner["type"]?.jsonPrimitive?.contentOrNull
                    if (sgMap.containsKey(innerCls)) {
                        throw IllegalArgumentException("nested subgraphs are not supported")
                    }
                    nodeEntry(inner, "${p}_", innerLinks, binds[p])?.let { w["${p}_${inner["id"]!!.jsonPrimitive.int}"] = it }
                }
            } else {
                nodeEntry(n, "", links, null)?.let { w[id.toString()] = it }
            }
        }

        // Pass 3: references that point at a subgraph instance become
        // references to the inner node feeding that output slot.
        w.entries.toList().forEach { (id, node) ->
            val updated = buildJsonObject {
                node.inputs.jsonObject.forEach { (key, value) ->
                    if (value is JsonArray && value.size == 2) {
                        val srcId = value[0].jsonPrimitive.contentOrNull
                        val slot = value[1].jsonPrimitive.intOrNull
                        val omap = srcId?.toIntOrNull()?.let { omaps[it] }
                        if (omap != null && slot != null) {
                            val t = omapTarget(omap, slot)
                                ?: throw IllegalArgumentException("node $id.$key references unresolved subgraph output $srcId[$slot]")
                            put(key, buildJsonArray { add(JsonPrimitive(t.first)); add(JsonPrimitive(t.second)) })
                            return@forEach
                        }
                    }
                    put(key, value)
                }
            }
            w[id] = node.copy(inputs = updated)
        }

        // Integrity: every link target must exist in the result.
        w.forEach { (id, node) ->
            node.inputs.jsonObject.forEach { (key, value) ->
                if (value is JsonArray && value.size == 2 && value[0].jsonPrimitive.isString) {
                    val target = value[0].jsonPrimitive.content
                    if (!w.containsKey(target)) {
                        throw IllegalArgumentException("node $id.$key references missing node $target")
                    }
                }
            }
        }
        return w
    }
}

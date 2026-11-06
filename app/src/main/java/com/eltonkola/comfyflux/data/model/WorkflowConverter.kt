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

    /** One consumed widgets_values slot: its API input name plus the spec
     * opts carrying its default (used when an older template predates the
     * widget and simply has no value for it). */
    internal data class Slot(val name: String, val opts: JsonObject, val options: List<String>? = null)

    /** The option list of a COMBO-like spec, if it restricts values. */
    private fun comboOptions(s: Spec): List<String>? = when {
        s.type is JsonArray && (s.type as JsonArray).all { it is JsonPrimitive } ->
            (s.type as JsonArray).map { it.jsonPrimitive.content }
        s.opts["options"] is JsonArray &&
            (s.opts["options"] as JsonArray).all { it is JsonPrimitive } ->
            (s.opts["options"] as JsonArray).map { it.jsonPrimitive.content }
        else -> null
    }

    /**
     * Widget names in widgets_values order for one UI node: every widgetish
     * spec input (required then optional), each immediately followed by the
     * dotted sub-widget slots of its V3 dynamic combo (the API names those
     * as "format.bit_depth", "sampling_mode.temperature", ... - matching
     * the server's finalized input names, validated by name). Which
     * subslots exist depends on the option the parent combo holds at that
     * position in widgets_values, hence [wv]. Plain sockets (autogrow image
     * slots) do not consume values.
     */
    private fun widgetSlots(cls: String, n: JsonObject, wv: JsonArray): List<Slot> {
        val slots = ArrayList<Slot>()
        // Older frontends serialized sub-widgets as dotted entries in the
        // node's inputs array (link null = still a widget); newer ones only
        // declare them in the dynamic combo's per-option spec. Honour both:
        // the spec expansion defines the order, leftover dotted names are
        // appended (a slot must exist for every value that occupies
        // widgets_values, or every later value shifts).
        val uiNames = (n["inputs"] as? JsonArray)
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?: emptyList()
        specs(cls)?.forEach { s ->
            if (!isWidget(s)) return@forEach
            slots += Slot(s.name, s.opts, comboOptions(s))
            if (s.opts["control_after_generate"]?.jsonPrimitive?.booleanOrNull == true) {
                slots += Slot(s.name + ":cag", s.opts)
            }
            // V3 dynamic combos declare per-option subinputs; the frontend
            // renders them right after the parent, in declaration order.
            val expanded = ArrayList<String>()
            val dyn = s.opts["options"] as? JsonArray ?: return@forEach.also { }
            if (dyn.any { it is JsonObject }) {
                val chosenIdx = slots.indexOfLast { it.name == s.name }
                val chosen = wv.getOrNull(chosenIdx)?.jsonPrimitive?.contentOrNull
                val sel = (dyn.filterIsInstance<JsonObject>())
                    .firstOrNull { it["key"]?.jsonPrimitive?.contentOrNull == chosen }
                if (sel != null) {
                    val ins = sel["inputs"]?.jsonObject
                    for (req in booleanArrayOf(true, false)) {
                        (ins?.get(if (req) "required" else "optional")?.jsonObject ?: continue)
                            .forEach { (subName, rawSpec) ->
                                val arr = rawSpec.jsonArray
                                val subOpts = if (arr.size > 1) arr[1].jsonObject else JsonObject(emptyMap())
                                val sub = Spec("${s.name}.$subName", arr[0], subOpts, req)
                                if (!isWidget(sub)) return@forEach
                                slots += Slot("${s.name}.$subName", subOpts, comboOptions(sub))
                                expanded += "${s.name}.$subName"
                            }
                    }
                }
            }
            uiNames.filter { it.startsWith("${s.name}.") && it !in expanded }
                .forEach { name ->
                    slots += Slot(name, JsonObject(emptyMap()))
                }
        }
        return slots
    }

    private fun normLink(l: JsonElement): Link? {
        // Malformed link -> skip it, never crash on a hand-edited file.
        val nums: List<Int?> = when (l) {
            is JsonArray -> if (l.size >= 5) l.take(5).map { it.jsonPrimitive.intOrNull } else return null
            is JsonObject -> listOf("id", "origin_id", "origin_slot", "target_id", "target_slot")
                .map { l[it]?.jsonPrimitive?.intOrNull }
            else -> return null
        }
        val (id, o, os, t, ts) = nums.map { it ?: return null }
        return Link(id, o, os, t, ts)
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

        val wv = n["widgets_values"] as? JsonArray ?: JsonArray(emptyList())
        widgetSlots(cls, n, wv).forEachIndexed { k, slot ->
            val name = slot.name
            if (name.endsWith(":cag")) {
                if (k < wv.size && slot.opts.containsKey("control_after_generate")) {
                    inputs["control_after_generate"] = wv[k]
                }
                return@forEachIndexed
            }
            if (inputs.containsKey(name)) return@forEachIndexed
            when {
                k < wv.size -> inputs[name] = wv[k]
                slot.opts["default"]?.let { it != JsonNull } == true ->
                    inputs[name] = slot.opts.getValue("default")
            }
        }

        // Version drift: required inputs the template's widgets never
        // carried (a newer server added them) get their declared default;
        // socketless required aggregates the frontend auto-provides
        // (ImageCompare's compare_view) get a placeholder the server
        // accepts and ignores at execution.
        sp.forEach { s ->
            if (!s.required || inputs.containsKey(s.name)) return@forEach
            if (s.opts["socketless"]?.jsonPrimitive?.booleanOrNull == true) {
                inputs[s.name] = JsonPrimitive(true)
            } else if (isWidget(s)) {
                s.opts["default"]?.let { if (it != JsonNull) inputs[s.name] = it }
            }
        }

        // Combo values outside the server's accepted list (the template was
        // authored against different options) snap to the default, else the
        // first legal value - otherwise validation rejects the whole prompt.
        sp.forEach { s ->
            val opts = comboOptions(s) ?: return@forEach
            val v = inputs[s.name] as? JsonPrimitive ?: return@forEach
            if (v.isString && v.content in opts) return@forEach
            val d = s.opts["default"]
            if (d is JsonPrimitive && d.content in opts) inputs[s.name] = d
            else inputs[s.name] = JsonPrimitive(opts.first())
        }

        val title = n["title"]?.jsonPrimitive?.contentOrNull ?: cls
        return Node(JsonObject(inputs), cls, Meta(title))
    }

    /**
     * Convert a graph-format workflow. [toggleOverrides] maps
     * "outerNodeId|inputIndex" (see detectWorkflowToggles) to a boolean that
     * must replace the widget value for that subgraph input slot; applied
     * before any patching so prompt targeting sees the ACTIVE branch.
     */
    fun convert(graph: JsonObject, toggleOverrides: Map<String, JsonElement> = emptyMap()): Workflow {
        val nodes = graph["nodes"] as? JsonArray
            ?: throw IllegalArgumentException("workflow is not a graph (frontend) format")
        val sgMap = (graph["definitions"]?.jsonObject?.get("subgraphs") as? JsonArray)
            ?.mapNotNull { sg -> (sg.jsonObject["id"] as? JsonPrimitive)?.contentOrNull
                ?.let { it to sg.jsonObject } }
            ?.toMap()
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
            val id = n["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
            omaps[id] = sgOutputMap(sg, id.toString(), sgMap)
            val bindings = HashMap(bindingsFor(n, sg, links, omaps))
            // App-side flips of the workflow's own boolean inputs.
            toggleOverrides.forEach { (key, value) ->
                val (nodeKey, indexKey) = key.split('|', limit = 2).let { if (it.size == 2) it[0] to it[1] else return@forEach }
                if (nodeKey == id.toString()) {
                    val index = indexKey.toIntOrNull() ?: return@forEach
                    bindings[index] = WFLiteral(value)
                }
            }
            binds[id.toString()] = bindings
        }

        // Pass 2: expand every node
        nodes.forEach { el ->
            val n = el.jsonObject
            val cls = n["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val id = n["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
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
                    nodeEntry(inner, "${p}_", innerLinks, binds[p])?.let { inner["id"]?.jsonPrimitive?.intOrNull?.let { innerId -> w["${p}_$innerId"] = it } }
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

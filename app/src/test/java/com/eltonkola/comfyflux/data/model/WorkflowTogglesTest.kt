package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Boolean inputs a template subgraph exposes (prompt enhance on/off etc.):
 * detection from the raw graph and app-side overrides applied during
 * conversion. Synthetic but shaped exactly like the real templates.
 */
class WorkflowTogglesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val graph = json.parseToJsonElement(
        """
        {
          "nodes": [
            {"id": 40, "type": "SGX", "widgets_values": ["a prompt", false, "bad stuff"]}
          ],
          "links": [],
          "definitions": {"subgraphs": [{
            "id": "SGX",
            "inputs": [
              {"name": "prompt", "type": "STRING"},
              {"name": "enhance", "type": "BOOLEAN"},
              {"name": "negative_prompt", "type": "STRING"}
            ],
            "nodes": [
              {"id": 1, "type": "ComfySwitchNode", "inputs": [
                {"name": "on_false", "link": 5},
                {"name": "on_true", "link": 6},
                {"name": "switch", "link": 7}
              ], "widgets_values": []}
            ],
            "links": [
              {"id": 5, "origin_id": -10, "origin_slot": 0, "target_id": 1, "target_slot": 0},
              {"id": 6, "origin_id": -10, "origin_slot": 2, "target_id": 1, "target_slot": 1},
              {"id": 7, "origin_id": -10, "origin_slot": 1, "target_id": 1, "target_slot": 2}
            ]
          }]}
        }
        """.trimIndent()
    ).jsonObject

    private val objectInfo = json.parseToJsonElement(
        """
        {"ComfySwitchNode": {"input": {"required": {
          "on_false": ["STRING", {}], "on_true": ["STRING", {}], "switch": ["BOOLEAN", {}]
        }}}}
        """.trimIndent()
    ).jsonObject

    @Test
    fun booleanSubgraphInputsAreDetectedWithCurrentValues() {
        val toggles = detectWorkflowToggles(graph)
        assertEquals(1, toggles.size)
        val t = toggles.single()
        assertEquals("40|1", t.key)
        assertEquals("enhance", t.name)
        assertEquals("Enhance", t.label)
        assertEquals(false, t.current)
    }

    @Test
    fun overridesReplaceTheSavedWidgetValue() {
        val converter = WorkflowConverter(objectInfo)

        val asSaved = converter.convert(graph)
        assertEquals(
            false,
            asSaved.getValue("40_1").inputs.jsonObject["switch"]!!.jsonPrimitive.booleanOrNull
        )

        val flipped = converter.convert(graph, mapOf("40|1" to JsonPrimitive(true)))
        assertEquals(
            true,
            flipped.getValue("40_1").inputs.jsonObject["switch"]!!.jsonPrimitive.booleanOrNull
        )
    }
}

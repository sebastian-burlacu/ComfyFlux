package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-node graph tweaks: detection is generic over the server's specs, so a
 * custom node declaring a widget shows up, while inputs other panels own
 * (sampler/cfg/steps/model files) never duplicate a control.
 */
class GraphKnobsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val info = Json.parseToJsonElement(
        """{
          "FluxGuidance": {"input": {"required": {
            "guidance": ["FLOAT", {"default": 3.5, "min": 0.0, "max": 100.0}],
            "conditioning": ["CONDITIONING", {}]
          }}},
          "ModelSamplingSD3": {"input": {"required": {
            "shift": ["FLOAT", {"default": 3.0, "min": 0.0, "max": 20.0}],
            "model": ["MODEL", {}]
          }}},
          "KSampler": {"input": {"required": {
            "seed": ["INT", {"default": 0, "min": 0, "max": 100}],
            "steps": ["INT", {"default": 20, "min": 1, "max": 10000}],
            "cfg": ["FLOAT", {"default": 8.0, "min": 0.0, "max": 100.0}],
            "sampler_name": ["COMBO", {"options": ["euler", "dpmpp_2m"]}]
          }}},
          "CustomToggle": {"input": {"required": {
            "enabled": ["BOOLEAN", {"default": true}]
          }}},
          "CustomChoice": {"input": {"required": {
            "mode": ["COMBO", {"options": ["a", "b", "c"]}]
          }}},
          "CustomSteps": {"input": {"required": {
            "steps": ["INT", {"default": 20, "min": 1, "max": 100}]
          }}},
          "ComfySwitchNode": {"input": {"required": {
            "switch": ["BOOLEAN", {"default": true}]
          }}}
        }"""
    ).jsonObject

    @Test
    fun picksOnlyInputsNoOtherPanelOwns() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "1": {"class_type": "FluxGuidance", "inputs": {"guidance": 3.5}},
              "2": {"class_type": "ModelSamplingSD3", "inputs": {"shift": 3.0}},
              "3": {"class_type": "KSampler", "inputs": {"seed": 0, "steps": 20, "cfg": 8.0, "sampler_name": "euler"}},
              "4": {"class_type": "ComfySwitchNode", "inputs": {"switch": true}}
            }"""
        )

        val knobs = wf.detectGraphKnobs(info)
        val keys = knobs.map { it.key }

        assertEquals(listOf("1|guidance", "2|shift"), keys)
        val guidance = knobs.first { it.key == "1|guidance" }
        assertEquals(GraphKnobKind.Float, guidance.kind)
        assertEquals(0f, guidance.min!!, 0.0001f)
        assertEquals(100f, guidance.max!!, 0.0001f)
        assertEquals("3.5", guidance.current)
    }

    @Test
    fun booleanAndChoiceKnobsCarryServerOptions() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "5": {"class_type": "CustomToggle", "inputs": {"enabled": true}},
              "6": {"class_type": "CustomChoice", "inputs": {"mode": "b"}}
            }"""
        )

        val knobs = wf.detectGraphKnobs(info).associateBy { it.key }

        assertEquals(GraphKnobKind.Boolean, knobs.getValue("5|enabled").kind)
        assertEquals("true", knobs.getValue("5|enabled").current)
        assertEquals(GraphKnobKind.Choice, knobs.getValue("6|mode").kind)
        assertEquals(listOf("a", "b", "c"), knobs.getValue("6|mode").options)
        assertEquals("b", knobs.getValue("6|mode").current)
    }

    @Test
    fun applyWritesIntsAsIntsAndFollowsPrimitiveLinks() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "1": {"class_type": "FluxGuidance", "inputs": {"guidance": 3.5}},
              "2": {"class_type": "PrimitiveFloat", "inputs": {"value": 0.7}},
              "3": {"class_type": "ModelSamplingSD3", "inputs": {"shift": ["2", "value"]}},
              "4": {"class_type": "KSampler", "inputs": {"steps": 20}}
            }"""
        )

        wf.applyGraphKnobs(mapOf("1|guidance" to "7.0"))

        assertEquals(7.0f, wf.getValue("1").inputs.jsonObject["guidance"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(7.0f, wf.getValue("1").inputs.jsonObject["guidance"]!!.jsonPrimitive.float, 0.0001f)

        // A link is resolved to its primitive source instead of clobbered with a literal.
        wf.applyGraphKnobs(mapOf("3|shift" to "1.5"))
        assertEquals(1.5f, wf.getValue("2").inputs.jsonObject["value"]!!.jsonPrimitive.float, 0.0001f)
        assertTrue(wf.getValue("3").inputs.jsonObject["shift"]!! is kotlinx.serialization.json.JsonArray)

        // Unknown ids/inputs are ignored, never throw.
        wf.applyGraphKnobs(mapOf("99|guidance" to "1.0", "bad" to "1.0"))
        assertFalse(wf.containsKey("99"))
    }

    @Test
    fun intOverrideKeepsIntegerWidth() {
        val wf = json.decodeFromString<Workflow>(
            """{"4": {"class_type": "CustomSteps", "inputs": {"steps": 20}}}"""
        )

        wf.applyGraphKnobs(mapOf("4|steps" to "30"))

        val written = wf.getValue("4").inputs.jsonObject["steps"]!!.jsonPrimitive
        assertEquals(30, written.int)
        assertEquals("30", written.content)
    }
}

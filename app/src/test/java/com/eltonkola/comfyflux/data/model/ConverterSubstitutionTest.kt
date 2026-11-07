package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ConverterSubstitutionTest {
    private val info = Json.parseToJsonElement(
        """{
        "Model": {"input": {"required": {
            "unet_name": [["real_a.safetensors", "real_b.safetensors"], {"default": "real_a.safetensors"}]
          }}, "output": ["MODEL"], "output_name": ["MODEL"]},
        "Run": {"input": {"required": {"model": ["MODEL"]}}, "output": [], "output_name": []}
        }"""
    ).jsonObject

    private val graph = Json.parseToJsonElement(
        """{"nodes": [
          {"id": 1, "type": "Model", "widgets_values": ["missing_template_model.safetensors"], "inputs": [], "outputs": []},
          {"id": 2, "type": "Run", "widgets_values": [], "inputs": [{"name": "model", "type": "MODEL", "link": 10}], "outputs": []}
        ], "links": [[10, 1, 0, 2, 0, "MODEL"]]}"""
    ).jsonObject

    // A loader whose folder is empty declares no options at all. Snapping has
    // nothing to pick, and crashing the conversion would make the whole
    // workflow unrunnable instead of telling the user what is missing.
    @Test
    fun anEmptyOptionListIsLeftAloneInsteadOfCrashingTheConversion() {
        val emptyInfo = Json.parseToJsonElement(
            """{
            "ControlNetLoader": {"input": {"required": {
                "control_net_name": [[], {"default": ""}]
              }}, "output": ["CONTROL_NET"], "output_name": ["CONTROL_NET"]},
            "Run": {"input": {"required": {"control_net": ["CONTROL_NET"]}}, "output": [], "output_name": []}
            }"""
        ).jsonObject
        val emptyGraph = Json.parseToJsonElement(
            """{"nodes": [
              {"id": 1, "type": "ControlNetLoader", "widgets_values": ["instantx.safetensors"], "inputs": [], "outputs": []},
              {"id": 2, "type": "Run", "widgets_values": [], "inputs": [{"name": "control_net", "type": "CONTROL_NET", "link": 10}], "outputs": []}
            ], "links": [[10, 1, 0, 2, 0, "CONTROL_NET"]]}"""
        ).jsonObject

        val c = WorkflowConverter(emptyInfo)
        val w = c.convert(emptyGraph)

        assertEquals("instantx.safetensors", w["1"]!!.inputs.jsonObject["control_net_name"]!!.jsonPrimitive.content)
        assertEquals(emptyList<String>(), c.substitutions.map { it.wanted })
        assertEquals(
            listOf("control_net_name" to "instantx.safetensors"),
            w.gaps(emptyInfo).missingFiles.map { it.input to it.wanted }
        )
    }

    @Test
    fun missingModelValueIsSnappedAndReported() {
        val c = WorkflowConverter(info)
        val w = c.convert(graph)
        assertEquals("real_a.safetensors", w["1"]!!.inputs.jsonObject["unet_name"]!!.jsonPrimitive.content)
        val s = c.substitutions.single()
        assertEquals("UNET_NAME".lowercase(), s.input.lowercase())
        assertEquals("missing_template_model.safetensors", s.wanted)
        assertEquals("real_a.safetensors", s.used)
    }
}

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

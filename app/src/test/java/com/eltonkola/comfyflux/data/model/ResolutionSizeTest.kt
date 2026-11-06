package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolutionSizeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val info = Json.parseToJsonElement(
        """{"ResolutionSelector": {"input": {"required": {
            "aspect_ratio": [["1:1 (Square)","4:3 (Standard)","16:9 (Wide)","3:4 (Portrait)","9:16 (Tall)"], {}],
            "megapixels": ["FLOAT", {"default": 1, "min": 0, "max": 10, "step": 0.01}],
            "multiple": ["INT", {"default": 8}]
          }}, "output": ["INT","INT"], "output_name": ["width","height"]}}"""
    ).jsonObject

    private val workflow = json.decodeFromString<Workflow>(
        """{"13": {"class_type": "ResolutionSelector", "inputs": {"aspect_ratio": "1:1 (Square)", "megapixels": 1.0, "multiple": 8}},
           "14": {"class_type": "EmptyLatentImage", "inputs": {"width": ["13", 0], "height": ["13", 1], "batch_size": 1}}}"""
    )

    @Test
    fun theSelectorIsDetectedWithItsServerOptions() {
        val slot = detectResolutionSelector(workflow, info)!!
        assertEquals("13", slot.nodeId)
        assertEquals(5, slot.aspectOptions.size)
        assertEquals("1:1 (Square)", slot.currentAspect)
        assertEquals(1f, slot.currentMegapixels, 0.0001f)
    }

    @Test
    fun aspectLabelsParseAndSnapToTheNearestOfferedShape() {
        assertEquals(1f, aspectRatioOf("1:1 (Square)")!!, 0.001f)
        assertEquals(16f / 9f, aspectRatioOf("16:9 (Wide)")!!, 0.001f)
        assertNull(aspectRatioOf("custom free text"))
        assertEquals("3:4 (Portrait)", nearestAspect(
            listOf("1:1 (Square)", "16:9 (Wide)", "3:4 (Portrait)"), 768, 1024
        ))
        assertEquals("16:9 (Wide)", nearestAspect(
            listOf("1:1 (Square)", "16:9 (Wide)", "3:4 (Portrait)"), 1024, 576
        ))
    }

    @Test
    fun theSelectorTakesTheChosenShapeAndPixelCount() {
        val wf = json.decodeFromString<Workflow>(workflow.toJson())
        wf.applyResolutionSize("13", "3:4 (Portrait)", 0.7864f)
        val inputs = wf["13"]!!.inputs.jsonObject
        assertEquals("3:4 (Portrait)", inputs["aspect_ratio"]!!.jsonPrimitive.content)
        assertEquals(0.78f, inputs["megapixels"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
        // the latent's linked width/height are untouched
        val linked = wf["14"]!!.inputs.jsonObject["width"] as kotlinx.serialization.json.JsonArray
        assertEquals(listOf("13", "0"), linked.map { it.jsonPrimitive.content })
    }

    @Test
    fun fittingAnAspectKeepsThePixelAreaOnSliderSteps() {
        val (w, h) = fitToAspect(1024, 1024, "16:9 (Wide)", SIZE_STEPS)
        assertEquals(1024, w)
        assertEquals(576, h)
        // 288 is not a slider step; the nearest honest shape wins.
        val (sw, sh) = fitToAspect(384, 384, "16:9 (Wide)", SIZE_STEPS)
        assertEquals(576, sw)
        assertEquals(320, sh)
        val (pw, ph) = fitToAspect(1024, 1024, "3:4 (Portrait)", SIZE_STEPS)
        assertEquals(768, pw)
        assertEquals(1024, ph)
    }

    @Test
    fun aspectOptionsComeThroughTheServerComboForm() {
        // The live 0.43.0 server advertises aspect_ratio as
        // ["COMBO", {"options": [...]}]; that is the shape his templates use.
        val liveInfo = Json.parseToJsonElement(
            """{"ResolutionSelector": {"input": {"required": {
                "aspect_ratio": ["COMBO", {"default": "1:1 (Square)", "options":
                    ["1:1 (Square)", "2:3 (Portrait Photo)", "3:2 (Photo)", "3:4 (Portrait Standard)",
                     "4:3 (Standard)", "9:16 (Portrait Widescreen)", "16:9 (Widescreen)", "21:9 (Ultrawide)"]}],
                "megapixels": ["FLOAT", {"default": 1.0, "min": 0.1, "max": 16.0, "step": 0.1}]
            }}}}"""
        ).jsonObject
        val slot = detectResolutionSelector(workflow, liveInfo)!!
        assertEquals(8, slot.aspectOptions.size)
        assertEquals("21:9 (Ultrawide)", slot.aspectOptions.last())
        assertEquals("16:9 (Widescreen)", nearestAspect(slot.aspectOptions, 1024, 576))
    }

    @Test
    fun workflowsWithoutTheSelectorGetNoSizeSlot() {
        val wf = json.decodeFromString<Workflow>(
            """{"5": {"class_type": "EmptyLatentImage", "inputs": {"width": 512, "height": 512}}}"""
        )
        assertNull(detectResolutionSelector(wf, info))
    }
}

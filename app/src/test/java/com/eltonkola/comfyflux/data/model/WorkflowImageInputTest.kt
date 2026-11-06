package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowImageInputTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val graph = """
    {
      "37": {"class_type": "LoadImage", "inputs": {"image": "workflow_saved.png"}},
      "40": {"class_type": "KSampler", "inputs": {"positive": ["41", 0], "negative": ["42", 0]}},
      "41": {"class_type": "CLIPTextEncode", "inputs": {"text": "keep"}},
      "60": {"class_type": "LoadImage", "inputs": {"image": "second.png"}}
    }
    """.trimIndent()

    @Test
    fun detectsImageInputNodes() {
        val wf = json.decodeFromString<Workflow>(graph)
        assertTrue(wf.hasImageInput())
    }

    @Test
    fun noImageInputWhenGraphHasNoLoadImage() {
        val wf = json.decodeFromString<Workflow>(
            """{"40": {"class_type": "KSampler", "inputs": {"positive": ["41", 0]}},
                "41": {"class_type": "CLIPTextEncode", "inputs": {"text": "p"}}}"""
        )
        assertFalse(wf.hasImageInput())
    }

    @Test
    fun applyInputImagePatchesOnlyThePrimaryLoadImage() {
        // Edit templates keep REFERENCE images in extra LoadImage nodes
        // (the qwen image-edit template loads a portrait and a separate
        // shirt); overwriting every node with one photo breaks that
        // premise. Only the lowest-id LoadImage is the workflow's input.
        val wf = json.decodeFromString<Workflow>(graph)
        wf.applyInputImage("phone/IMG_0001.jpg")

        assertEquals(
            "phone/IMG_0001.jpg",
            wf.getValue("37").inputs.jsonObject["image"]!!.jsonPrimitive.content
        )
        assertEquals(
            "second.png",
            wf.getValue("60").inputs.jsonObject["image"]!!.jsonPrimitive.content
        )
        // Nothing else moves.
        assertEquals("keep", wf.getValue("41").inputs.jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun blankUploadNameLeavesWorkflowFilesAlone() {
        val wf = json.decodeFromString<Workflow>(graph)
        wf.applyInputImage("")
        assertEquals(
            "workflow_saved.png",
            wf.getValue("37").inputs.jsonObject["image"]!!.jsonPrimitive.content
        )
    }
}

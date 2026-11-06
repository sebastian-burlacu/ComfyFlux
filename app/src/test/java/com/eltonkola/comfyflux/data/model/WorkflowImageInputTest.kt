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
    fun imageInputsListEveryLoadImageAndMarkThePrimary() {
        // The qwen image-edit template loads a portrait AND a separate
        // shirt reference; the UI needs one labelled slot per node.
        val slots = json.decodeFromString<Workflow>(graph).imageInputs()
        assertEquals(listOf("37", "60"), slots.map { it.id })
        assertEquals(listOf("workflow_saved.png", "second.png"), slots.map { it.file })
        assertEquals(listOf(true, false), slots.map { it.primary })
    }

    @Test
    fun applyInputImagesPatchesOnlyAssignedNodes() {
        val wf = json.decodeFromString<Workflow>(graph)
        wf.applyInputImages(
            mapOf(
                "37" to "phone/IMG_0001.jpg",
                "60" to "",                       // cleared: keeps template file
                "999" to "phone/ghost.png"        // unknown node: ignored
            )
        )

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
}

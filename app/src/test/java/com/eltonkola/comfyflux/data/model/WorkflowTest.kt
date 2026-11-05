package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class WorkflowTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Unit tests run with the module directory (app/) as the working directory.
    private fun load(name: String): Workflow =
        json.decodeFromString(File("src/main/assets/$name").readText())

    private fun textOf(wf: Workflow, id: String): String =
        wf.getValue(id).inputs.jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun patchesPromptOnPositiveConditioningOnly() {
        val wf = load("sdxl_lighting_4steps.json")
        val negative = textOf(wf, "7")

        wf.updatePrompt("a cat in a hat")

        assertEquals("a cat in a hat", textOf(wf, "6"))
        assertEquals(negative, textOf(wf, "7"))
        assertEquals("a cat in a hat", wf.getPrompt())
    }

    @Test
    fun findsPromptThroughGuiderChain() {
        val wf = load("workflow_flux_dev.json")
        // SamplerCustomAdvanced -> BasicGuider.conditioning -> CLIPTextEncode 6
        assertEquals(listOf("6"), wf.promptNodeIds())
    }

    @Test
    fun patchesEveryEmptyLatentImage() {
        val wf = load("workflow_flux_schnell.json")
        wf.updateSizeAndBatch(768, 512, 4)

        val inputs = wf.getValue("5").inputs.jsonObject
        assertEquals(768, inputs["width"]!!.jsonPrimitive.int)
        assertEquals(512, inputs["height"]!!.jsonPrimitive.int)
        assertEquals(4, inputs["batch_size"]!!.jsonPrimitive.int)
    }

    @Test
    fun patchesKSamplerSeed() {
        val wf = load("sdxl_lighting_4steps.json")
        wf.updateSeed(424242L)

        assertEquals(424242L, wf.getValue("3").inputs.jsonObject["seed"]!!.jsonPrimitive.long)
    }

    @Test
    fun patchesRandomNoiseSeed() {
        val wf = load("workflow_flux_schnell.json")
        wf.updateSeed(1234567890123L)

        assertEquals(
            1234567890123L,
            wf.getValue("25").inputs.jsonObject["noise_seed"]!!.jsonPrimitive.long
        )
    }

    @Test
    fun foreignWorkflowWithoutTextEncoderIsSafe() {
        val wf = json.decodeFromString<Workflow>(
            """{"1": {"inputs": {"anything": 1}, "class_type": "SomeLoader", "_meta": {"title": "x"}}}"""
        )

        assertEquals("", wf.getPrompt())
        wf.updatePrompt("ignored")
        wf.updateSeed(1L)
        wf.updateSizeAndBatch(1, 1, 1)

        assertEquals(listOf("1"), wf.keys.toList())
        assertNull(wf.getValue("1").inputs.jsonObject["text"])
    }

    @Test
    fun unlinkedTextEncoderStillReceivesPrompt() {
        val wf = json.decodeFromString<Workflow>(
            """
            {
              "12": {"inputs": {"text": "placeholder", "clip": ["2", 0]}, "class_type": "CLIPTextEncode", "_meta": {"title": "Prompt"}},
              "13": {"inputs": {"images": ["4", 0], "filename_prefix": "ComfyUI"}, "class_type": "SaveImage", "_meta": {"title": "Save"}}
            }
            """.trimIndent()
        )

        wf.updatePrompt("hello world")

        assertEquals("hello world", textOf(wf, "12"))
        assertEquals("hello world", wf.getPrompt())
    }

    @Test
    fun promptWithQuotesAndBackslashesSurvivesRoundTrip() {
        val wf = load("workflow_flux_dev.json")
        val tricky = "a \"quoted\" prompt with \\ backslash and \"newlines\"\nnot escaped twice"

        wf.updatePrompt(tricky)
        val reparsed = json.decodeFromString<Workflow>(wf.toJson())

        assertEquals(tricky, reparsed.getPrompt())
    }
}

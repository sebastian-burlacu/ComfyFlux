package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SamplerSlotsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val info = Json.parseToJsonElement(
        """{
          "KSampler": {"input": {"required": {
            "sampler_name": [["euler","euler_ancestral","dpmpp_2m"], {}],
            "scheduler": [["normal","karras","simple"], {}],
            "denoise": ["FLOAT", {"default": 1.0, "min": 0, "max": 1, "step": 0.01}]
          }}, "output": ["LATENT"], "output_name": ["LATENT"]},
          "KSamplerSelect": {"input": {"required": {
            "sampler_name": [["euler","rmspp"], {}]
          }}, "output": ["SAMPLER"], "output_name": ["SAMPLER"]}
        }"""
    ).jsonObject

    private val workflow = json.decodeFromString<Workflow>(
        """{
          "9": {"class_type": "KSampler", "inputs": {
            "sampler_name": "euler", "scheduler": "normal", "denoise": 1.0, "seed": 1, "steps": 20, "cfg": 4
          }},
          "12": {"class_type": "KSamplerSelect", "inputs": {"sampler_name": "euler"}}
        }"""
    )

    @Test
    fun optionsComeFromTheServer() {
        val slots = detectSamplerSlots(workflow, info)
        // KSamplerSelect only knows euler/rmspp, so offering dpmpp_2m would
        // make the server reject the prompt: the safe offer is the intersection.
        assertEquals(listOf("euler"), slots.samplerOptions)
        assertEquals(listOf("normal", "karras", "simple"), slots.schedulerOptions)
        assertEquals("euler", slots.currentSampler)
        assertEquals("normal", slots.currentScheduler)
        assertEquals(true, slots.hasDenoise)
        assertEquals(1f, slots.currentDenoise, 0.0001f)
    }

    @Test
    fun choicesAreWrittenToEverySamplerThatAcceptsThem() {
        val wf = json.decodeFromString<Workflow>(
            workflow.toJson().also { }
        )
        wf.applySamplerChoices(sampler = "dpmpp_2m", scheduler = "karras", denoise = 0.65f)
        val k = wf["9"]!!.inputs.jsonObject
        assertEquals("dpmpp_2m", k["sampler_name"]!!.jsonPrimitive.content)
        assertEquals("karras", k["scheduler"]!!.jsonPrimitive.content)
        assertEquals(0.65f, k["denoise"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
        assertEquals("dpmpp_2m", wf["12"]!!.inputs.jsonObject["sampler_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun nullChoicesLeaveTheWorkflowAlone() {
        val wf = json.decodeFromString<Workflow>(
            """{"9": {"class_type": "KSampler", "inputs": {"sampler_name": "euler", "denoise": 0.4}}}"""
        )
        wf.applySamplerChoices(null, null, null)
        assertEquals("euler", wf["9"]!!.inputs.jsonObject["sampler_name"]!!.jsonPrimitive.content)
        assertEquals(0.4f, wf["9"]!!.inputs.jsonObject["denoise"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
    }

    @Test
    fun linkedInputsAreNeverOverwritten() {
        val wf = json.decodeFromString<Workflow>(
            """{"9": {"class_type": "KSampler", "inputs": {"sampler_name": ["3", 0]}}}"""
        )
        wf.applySamplerChoices(sampler = "dpmpp_2m", scheduler = null, denoise = null)
        val linked = wf["9"]!!.inputs.jsonObject["sampler_name"] as kotlinx.serialization.json.JsonArray
        assertEquals(listOf("3", "0"), linked.map { it.jsonPrimitive.content })
    }

    @Test
    fun workflowsWithoutSamplersReportNothing() {
        val wf = json.decodeFromString<Workflow>(
            """{"2": {"class_type": "VAEEncode", "inputs": {"pixels": ["1", 0]}}}"""
        )
        assertEquals(true, detectSamplerSlots(wf, info).isEmpty)
    }
}

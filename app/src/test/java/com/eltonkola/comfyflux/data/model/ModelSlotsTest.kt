package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSlotsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val objectInfo = json.parseToJsonElement(
        """
        {
          "CheckpointLoaderSimple": {"input": {"required": {"ckpt_name":
              [["flux.safetensors", "sd_xl base.safetensors"], {"control_after_generate": true}]}}},
          "UnetLoaderGGUF": {"input": {"required": {"unet_name":
              ["krea GGUF.safetensors", "qwen fp8.safetensors"]}}},
          "VAELoader": {"input": {"required": {"vae_name": [["ae.safetensors"]]}}},
          "UNETLoader": {"input": {"required": {"unet_name": [["flux1-dev.safetensors"]]}}},
          "DualCLIPLoader": {"input": {"required": {"clip_name1": [["t5xxl_fp8_e4m3fn.safetensors"]],
              "clip_name2": [["clip_l.safetensors"]]}}},
          "KSampler": {"input": {"required": {"seed": ["INT", {"default": 0}],
              "sampler_name": [["euler", "dpmpp"], {}]}}},
          "MysteryNode": {"input": {"required": {"model_name": "STRING"}}}
        }
        """.trimIndent()
    ).jsonObject

    private fun workflow(): Workflow = json.decodeFromString(
        """
        {
          "7": {"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": "krea GGUF.safetensors"}},
          "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "flux.safetensors"}},
          "3": {"class_type": "KSampler", "inputs": {"seed": 5, "sampler_name": "euler"}},
          "9": {"class_type": "MysteryNode", "inputs": {"model_name": "anything"}}
        }
        """.trimIndent()
    )

    @Test
    fun loraStrengthsComeWithTheirSlotAndRange() {
        val info = json.parseToJsonElement(
            """{"LoraLoader": {"input": {"required": {
                "model": ["MODEL"], "clip": ["CLIP"],
                "lora_name": [["anime.safetensors", "detail.safetensors"], {}],
                "strength_model": ["FLOAT", {"default": 1.0, "min": -100.0, "max": 100.0, "step": 0.01}],
                "strength_clip": ["FLOAT", {"default": 1.0, "min": -100.0, "max": 100.0, "step": 0.01}]
            }}}}"""
        ).jsonObject
        val wf = json.decodeFromString<Workflow>(
            """{"21": {"class_type": "LoraLoader", "inputs": {
                "model": ["1", 0], "clip": ["1", 1], "lora_name": "anime.safetensors",
                "strength_model": 0.8, "strength_clip": 1.0}}}"""
        )
        val slot = detectModelSlots(wf, info).single()
        assertEquals(listOf("strength_model", "strength_clip"), slot.strengths.map { it.input })
        assertEquals(0.8f, slot.strengths.first().current, 0.0001f)
        // the server range is -100..100; the draggable window is not
        assertEquals(-1f..2f, slot.strengths.first().range(null))
        assertEquals(-1f..2.4f, slot.strengths.first().range(2.4f))

        wf.applyStrengthOverrides(mapOf("21|strength_model" to 1.35f))
        assertEquals(1.35f, wf["21"]!!.inputs.jsonObject["strength_model"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
        // linked model/clip inputs are never touched by a strength write
        assertEquals("anime.safetensors", wf["21"]!!.inputs.jsonObject["lora_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun strengthWritesSkipLinkedOrUnknownNodes() {
        val wf = json.decodeFromString<Workflow>(
            """{"21": {"class_type": "LoraLoader", "inputs": {"strength_model": ["20", 0]}}}"""
        )
        wf.applyStrengthOverrides(mapOf("21|strength_model" to 0.5f, "99|strength_model" to 0.5f))
        val value = wf["21"]!!.inputs.jsonObject["strength_model"]
        assertTrue("linked strength must stay a link", value is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun server043ComboFormIsOfferedToo() {
        // ComfyUI 0.43 moved option lists into the metadata object for some
        // loaders (TripleCLIPLoader, UpscaleModelLoader, ...). Missing that
        // form silently drops the picker for those nodes.
        val info = json.parseToJsonElement(
            """{"TripleCLIPLoader": {"input": {"required": {
                "clip_name1": ["COMBO", {"options": ["t5xxl_fp8_e4m3fn.safetensors", "clip_l.safetensors"]}],
                "clip_name2": ["COMBO", {"options": ["clip_g.safetensors"]}],
                "clip_name3": ["COMBO", {"options": ["clip_h.safetensors"]}]
            }}}}"""
        ).jsonObject
        val wf = json.decodeFromString<Workflow>(
            """{"11": {"class_type": "TripleCLIPLoader", "inputs": {
                "clip_name1": "t5xxl_fp8_e4m3fn.safetensors",
                "clip_name2": "clip_g.safetensors",
                "clip_name3": "clip_h.safetensors", "type": "sd3"}}}"""
        )
        val slots = detectModelSlots(wf, info)
        assertEquals(
            listOf("clip_name1", "clip_name2", "clip_name3"),
            slots.map { it.input }
        )
        assertEquals(listOf("t5xxl_fp8_e4m3fn.safetensors", "clip_l.safetensors"), slots.first().options)
    }

    @Test
    fun offersOnlyServerAdvertisedModelFileChoices() {
        val slots = detectModelSlots(workflow(), objectInfo)

        // KSampler's sampler_name and MysteryNode's free-text model_name are
        // never offered; the two loaders and their current values are.
        assertEquals(
            listOf("1|ckpt_name", "7|unet_name"),
            slots.map { it.key }
        )
        assertEquals("flux.safetensors", slots.first().current)
        assertEquals(
            listOf("flux.safetensors", "sd_xl base.safetensors"),
            slots.first().options
        )
    }

    @Test
    fun ordersCheckpointsBeforeOtherSlots() {
        val slots = detectModelSlots(workflow(), objectInfo)
        assertEquals("CheckpointLoaderSimple", slots.first().classType)
        assertEquals("Checkpoint", slots.first().label)
        assertEquals("UNET (GGUF)", slots[1].label)
    }

    @Test
    fun appliesOverridesAndIgnoresStaleKeys() {
        val wf = workflow()
        wf.applyModelOverrides(
            mapOf(
                "7|unet_name" to "qwen fp8.safetensors",
                "99|vae_name" to "ghost.safetensors",
                "3|seed" to "nope",
            )
        )

        assertEquals(
            "qwen fp8.safetensors",
            wf.getValue("7").inputs.jsonObject["unet_name"]!!.jsonPrimitive.content
        )
        assertNullSafe(wf, "99")
        // KSampler input untouched: unknown node id and unlisted inputs never apply
        assertEquals(5, wf.getValue("3").inputs.jsonObject["seed"]!!.jsonPrimitive.int)
    }

    @Test
    fun detectsSlotsInBundledFluxWorkflow() {
        // The shipped flux-dev graph loads UNET + a CLIP pair + VAE through
        // separate loaders; all must surface, ordered, for bundled workflows
        // exactly as for server ones.
        val wf = json.decodeFromString<Workflow>(
            java.io.File("src/main/assets/workflow_flux_dev.json").readText()
        )
        val slots = detectModelSlots(wf, objectInfo)
        assertEquals(
            listOf("unet_name", "clip_name1", "clip_name2", "vae_name"),
            slots.map { it.input }
        )
        assertEquals("UNET", slots.first().label)
        assertEquals("flux1-dev.sft", slots.first().current)
    }

    private fun assertNullSafe(wf: Workflow, id: String) {
        assertTrue("node $id must not be created", wf[id] == null)
    }
}

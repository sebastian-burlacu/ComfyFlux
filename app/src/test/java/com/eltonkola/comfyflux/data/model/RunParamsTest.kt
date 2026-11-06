package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RunParamsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsSamplerKnobsAndModelSlotsBackFromASubmittedRun() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "flux.safetensors"}},
              "5": {"class_type": "LoraLoader", "inputs": {"lora_name": "detail.safetensors", "strength_model": 0.8}},
              "9": {"class_type": "KSampler", "inputs": {
                "seed": 123456789012345, "steps": 28, "cfg": 3.5,
                "sampler_name": "euler", "scheduler": "normal", "denoise": 0.75
              }}
            }"""
        )
        val params = wf.runParams()
        assertEquals(123456789012345L, params.seed)
        assertEquals(28, params.steps)
        assertEquals(3.5f, params.cfg)
        assertEquals("euler", params.sampler)
        assertEquals("normal", params.scheduler)
        assertEquals(0.75f, params.denoise)
        assertEquals(listOf("flux.safetensors", "detail.safetensors"), params.models)
        assertEquals(
            "seed 123456789012345 - 28 steps - cfg 3.5 - euler - normal - denoise 0.75",
            params.summary()
        )
    }

    @Test
    fun workflowsWithoutSamplerControlsStaySilent() {
        val wf = json.decodeFromString<Workflow>(
            """{"2": {"class_type": "VAEEncode", "inputs": {"pixels": ["1", 0]}}}"""
        )
        val params = wf.runParams()
        assertEquals(null, params.seed)
        assertEquals("", params.summary())
    }
}

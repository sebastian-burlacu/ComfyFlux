package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowNeedsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun latentWorkflowsSizeFromTheSliders() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "9": {"class_type": "EmptySD3LatentImage", "inputs": {"width": 1024, "height": 1024}},
              "10": {"class_type": "CLIPTextEncode", "inputs": {"text": "hello"}},
              "11": {"class_type": "KSampler", "inputs": {"positive": ["10", 0], "negative": ["10", 0], "latent_image": ["9", 0]}},
              "12": {"class_type": "VAEDecode", "inputs": {"samples": ["11", 0]}}
            }"""
        )
        val needs = wf.needs()
        assertEquals(0, needs.imageCount)
        assertTrue(needs.hasLatent)
        assertFalse(needs.usesImageLatent)
        assertEquals("CLIPTextEncode", needs.encoder)
    }

    @Test
    fun vaEncodeWorkflowsFollowTheImageSize() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "1": {"class_type": "LoadImage", "inputs": {"image": "a.png"}},
              "2": {"class_type": "VAEEncode", "inputs": {"pixels": ["1", 0]}},
              "3": {"class_type": "KSampler", "inputs": {"latent_image": ["2", 0]}},
              "4": {"class_type": "CLIPTextEncode", "inputs": {"text": "x"}}
            }"""
        )
        val needs = wf.needs()
        assertEquals(1, needs.imageCount)
        assertFalse(needs.hasLatent)
        assertTrue(needs.usesImageLatent)
        assertEquals("CLIPTextEncode", needs.encoder)
    }
}

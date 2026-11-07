package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-workflow memory: remembered choices come back for the workflow they
 * belong to, and anything the current workflow/server no longer offers is
 * dropped instead of being sent to the server.
 */
class WorkflowChoicesTest {

    private val slots = listOf(
        ModelSlot(
            "7", "LoraLoader", "lora_name", "a.safetensors",
            listOf("a.safetensors", "b.safetensors"),
            listOf(StrengthSlot("strength_model", "model", 1f, -100f, 100f))
        )
    )

    private val groups = listOf(
        StrengthGroup(
            "12", "ControlNetApplyAdvanced", "ControlNet",
            listOf(StrengthSlot("strength", "strength", 0.8f, 0f, 10f))
        )
    )

    private val samplers = SamplerSlots(
        samplerOptions = listOf("euler", "dpmpp_2m"),
        schedulerOptions = listOf("karras", "simple"),
        currentSampler = "euler",
        currentScheduler = "karras",
        hasDenoise = true,
        currentDenoise = 1f
    )

    private val sizeSlot = SizeSlot("20", listOf("1:1", "16:9"), "1:1", 1f)

    private val remembered = WorkflowChoices(
        modelOverrides = mapOf("7|lora_name" to "b.safetensors", "99|lora_name" to "gone.safetensors"),
        strengthOverrides = mapOf("7|strength_model" to 0.6f, "12|strength" to 0.3f, "5|strength_model" to 0.9f),
        samplerChoice = "dpmpp_2m",
        schedulerChoice = "lcm",
        denoise = 0.7f,
        aspectChoice = "16:9",
        cfg = 3.5f,
        steps = 24
    )

    @Test
    fun rememberedChoicesComeBackAndStaleOnesAreDropped() {
        val state = ImageGenerationUiState().withRememberedChoices(remembered, slots, groups, samplers, sizeSlot)

        assertEquals(mapOf("7|lora_name" to "b.safetensors"), state.modelOverrides)
        assertEquals(setOf("7|strength_model", "12|strength"), state.strengthOverrides.keys)
        assertEquals("dpmpp_2m", state.samplerChoice)
        // "lcm" is not in this server's scheduler list.
        assertNull(state.schedulerChoice)
        assertEquals(0.7f, state.denoise!!, 0.0001f)
        assertEquals("16:9", state.aspectChoice)
        assertEquals(3.5f, state.cfg, 0.0001f)
        assertEquals(24, state.steps)
    }

    @Test
    fun denoiseAndAspectOnlySurviveOnWorkflowsThatOfferThem() {
        val noDenoise = samplers.copy(hasDenoise = false)
        val state = ImageGenerationUiState().withRememberedChoices(remembered, slots, groups, noDenoise, null)

        assertNull(state.denoise)
        assertNull(state.aspectChoice)
    }

    @Test
    fun aWorkflowWithoutMemoriesKeepsOnlyValidSessionValues() {
        val session = ImageGenerationUiState(
            modelOverrides = mapOf("7|lora_name" to "b.safetensors", "3|ckpt_name" to "old.safetensors"),
            samplerChoice = "euler"
        )

        val state = session.withRememberedChoices(null, slots, groups, samplers, sizeSlot)

        assertEquals(mapOf("7|lora_name" to "b.safetensors"), state.modelOverrides)
        assertEquals("euler", state.samplerChoice)
        assertEquals(0f, state.cfg, 0.0001f)
    }

    @Test
    fun aSnapshotRoundTripsThroughThePrefsJson() {
        val live = ImageGenerationUiState(
            modelOverrides = mapOf("7|lora_name" to "b.safetensors"),
            strengthOverrides = mapOf("12|strength" to 0.35f),
            schedulerChoice = "karras",
            denoise = 0.65f,
            aspectChoice = "1:1",
            cfg = 1f,
            steps = 0
        )

        val stored = Json.encodeToString(live.asWorkflowChoices())
        val back = Json.decodeFromString<WorkflowChoices>(stored)

        assertEquals(live.asWorkflowChoices(), back)
        assertTrue(stored.contains("12|strength"))
    }
}

package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowCfgStepsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // One plain KSampler, one KSamplerAdvanced and a SaveImage sink, in the
    // API format the app uploads.
    private val samplersFixture = """
        {
          "3": {"inputs": {"seed": 1, "steps": 20, "cfg": 3.5, "sampler_name": "euler", "scheduler": "normal", "denoise": 1.0, "model": ["4", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}, "class_type": "KSampler", "_meta": {"title": "Sample"}},
          "10": {"inputs": {"add_noise": "enable", "noise_seed": 5, "steps": 25, "cfg": 6.5, "sampler_name": "dpmpp_2m", "scheduler": "karras", "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}, "class_type": "KSamplerAdvanced", "_meta": {"title": "Sample Advanced"}},
          "9": {"inputs": {"images": ["3", 0], "filename_prefix": "ComfyUI"}, "class_type": "SaveImage", "_meta": {"title": "Save"}}
        }
        """.trimIndent()

    private fun load(text: String): Workflow = json.decodeFromString(text)

    @Test
    fun patchesCfgAndStepsOnBothSamplerVariants() {
        val wf = load(samplersFixture)

        wf.updateCfgAndSteps(7.5f, 30)

        assertEquals(7.5f, wf.getValue("3").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(30, wf.getValue("3").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
        assertEquals(7.5f, wf.getValue("10").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(30, wf.getValue("10").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
    }

    @Test
    fun theFieldsStartAtWhatTheGraphHolds() {
        val targets = load(samplersFixture).guidanceTargets()

        // "0 = keep" hid the number that was already there, so you could not
        // tweak it; the defaults come from the first writable sampler node.
        assertEquals(20, targets.stepsDefault)
        assertEquals(3.5f, targets.cfgDefault!!, 0.0001f)
        assertEquals(true, targets.takesSteps)
        assertEquals(true, targets.takesCfg)
    }

    @Test
    fun defaultsFollowPrimitiveLinksAndVanishWithoutWidgets() {
        val linked = load(
            """{
              "1": {"class_type": "PrimitiveFloat", "inputs": {"value": 1.5}},
              "2": {"class_type": "KSampler", "inputs": {"cfg": ["1", "value"], "steps": 8}}
            }"""
        )

        assertEquals(1.5f, linked.guidanceTargets().cfgDefault!!, 0.0001f)
        assertEquals(8, linked.guidanceTargets().stepsDefault)

        // No widget at all: nothing to advertise, the fields stay "workflow's own".
        val bare = load("""{"7": {"class_type": "CLIPTextEncode", "inputs": {"text": "x"}}}""")
        assertNull(bare.guidanceTargets().cfgDefault)
        assertNull(bare.guidanceTargets().stepsDefault)
    }

    @Test
    fun otherInputsSurviveThePatchUntouched() {
        val wf = load(samplersFixture)
        val samplerBefore = wf.getValue("3").inputs.jsonObject
        val advancedBefore = wf.getValue("10").inputs.jsonObject

        wf.updateCfgAndSteps(7.5f, 30)

        assertUntouchedExcept(samplerBefore, wf.getValue("3").inputs.jsonObject, "cfg", "steps")
        assertUntouchedExcept(advancedBefore, wf.getValue("10").inputs.jsonObject, "cfg", "steps")
    }

    @Test
    fun conditioningLinksAreNeverClobbered() {
        val wf = load(samplersFixture)

        wf.updateCfgAndSteps(7.5f, 30)

        // The KSampler's positive link ["6", 0] still points at its source node.
        assertEquals(
            """["6",0]""",
            wf.getValue("3").inputs.jsonObject["positive"].toString().replace(" ", "")
        )
    }

    @Test
    fun saveImageNodeIsLeftAlone() {
        val wf = load(samplersFixture)
        val saveBefore = wf.getValue("9").inputs.jsonObject

        wf.updateCfgAndSteps(7.5f, 30)

        assertEquals(saveBefore, wf.getValue("9").inputs.jsonObject)
        assertNull(wf.getValue("9").inputs.jsonObject["cfg"])
    }

    @Test
    fun zeroValuesKeepTheWorkflowsOwnSettings() {
        val wf = load(samplersFixture)

        wf.updateCfgAndSteps(0f, 0)

        assertEquals(3.5f, wf.getValue("3").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(20, wf.getValue("3").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
        assertEquals(6.5f, wf.getValue("10").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(25, wf.getValue("10").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
    }

    @Test
    fun eachOverrideIsSkippedIndependently() {
        val cfgOnly = load(samplersFixture)
        cfgOnly.updateCfgAndSteps(9f, 0)
        assertEquals(9f, cfgOnly.getValue("3").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(20, cfgOnly.getValue("3").inputs.jsonObject["steps"]!!.jsonPrimitive.int)

        val stepsOnly = load(samplersFixture)
        stepsOnly.updateCfgAndSteps(0f, 12)
        assertEquals(3.5f, stepsOnly.getValue("3").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(12, stepsOnly.getValue("3").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
    }

    @Test
    fun cfgGuiderGetsCfgButNeverSteps() {
        val wf = load(
            """
            {
              "20": {"inputs": {"cfg": 1.0, "guider": ["21", 0], "sampler": ["22", 0], "sigmas": ["23", 0]}, "class_type": "CFGGuider", "_meta": {"title": "Guider"}}
            }
            """.trimIndent()
        )

        wf.updateCfgAndSteps(8f, 40)

        assertEquals(8f, wf.getValue("20").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertNull(wf.getValue("20").inputs.jsonObject["steps"])
    }

    private fun assertUntouchedExcept(before: JsonObject, after: JsonObject, vararg changed: String) {
        assertEquals(before.keys.toSet(), after.keys.toSet())
        before.forEach { (key, value) ->
            if (key !in changed) assertEquals(value, after[key])
        }
    }
}

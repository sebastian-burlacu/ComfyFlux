package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guide-graph workflows (RandomNoise + CFGGuider/BasicGuider + BasicScheduler,
 * plus KSamplerSelect) split the knobs the classic KSampler keeps in one node.
 * The Steps slider and the scheduler/denoise controls have to find them there.
 */
class GuideGraphControlsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val info = Json.parseToJsonElement(
        """{
          "KSamplerSelect": {"input": {"required": {
            "sampler_name": ["COMBO", {"options": ["euler", "euler_ancestral", "dpmpp_2m"]}]
          }}},
          "BasicScheduler": {"input": {"required": {
            "scheduler": ["COMBO", {"options": ["simple", "karras", "sgm_uniform"]}],
            "steps": ["INT", {"default": 20, "min": 1, "max": 10000}],
            "denoise": ["FLOAT", {"default": 1.0, "min": 0.0, "max": 1.0, "step": 0.01}]
          }}},
          "CFGGuider": {"input": {"required": {
            "cfg": ["FLOAT", {"default": 8.0, "min": 0.0, "max": 100.0, "step": 0.1}]
          }}},
          "BasicGuider": {"input": {"required": {"conditioning": ["CONDITIONING", {}]}}},
          "DualCFGGuider": {"input": {"required": {
            "cfg_conds": ["FLOAT", {"default": 8.0, "min": 0.0, "max": 100.0}],
            "cfg_cond2_negative": ["FLOAT", {"default": 8.0, "min": -100.0, "max": 100.0}]
          }}},
          "RandomNoise": {"input": {"required": {
            "noise_seed": ["INT", {"default": 0, "min": 0, "max": 18446744073709551615}]
          }}},
          "ControlNetApplyAdvanced": {"input": {"required": {
            "strength": ["FLOAT", {"default": 1.0, "min": 0.0, "max": 10.0, "step": 0.01}],
            "start_percent": ["FLOAT", {"default": 0.0, "min": 0.0, "max": 1.0}],
            "end_percent": ["FLOAT", {"default": 1.0, "min": 0.0, "max": 1.0}]
          }}}
        }"""
    ).jsonObject

    private val guideGraph = json.decodeFromString<Workflow>(
        """{
          "1": {"class_type": "RandomNoise", "inputs": {"noise_seed": 42}},
          "2": {"class_type": "KSamplerSelect", "inputs": {"sampler_name": "euler"}},
          "3": {"class_type": "CFGGuider", "inputs": {"cfg": 2.5, "model": ["9", 0], "positive": ["6", 0], "negative": ["7", 0]}},
          "4": {"class_type": "BasicScheduler", "inputs": {"scheduler": "simple", "steps": 4, "denoise": 1.0, "model": ["9", 0]}},
          "5": {"class_type": "ControlNetApplyAdvanced", "inputs": {"strength": 0.8, "start_percent": 0.0, "end_percent": 1.0, "positive": ["6", 0], "negative": ["7", 0], "control_net": ["8", 0], "image": ["10", 0]}},
          "11": {"class_type": "KSampler", "inputs": {"seed": 1, "steps": 4, "cfg": 2.5, "sampler_name": "euler", "scheduler": "simple", "denoise": 1.0}}
        }"""
    )

    @Test
    fun stepsLandOnTheSchedulerNodeAndCfgOnTheGuider() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "3": {"class_type": "CFGGuider", "inputs": {"cfg": 2.5, "model": ["9", 0]}},
              "4": {"class_type": "BasicScheduler", "inputs": {"scheduler": "simple", "steps": 4, "denoise": 1.0}},
              "5": {"class_type": "EmptyLatentImage", "inputs": {"width": 1024, "height": 1024}}
            }"""
        )

        wf.updateCfgAndSteps(7.5f, 30)

        assertEquals(7.5f, wf.getValue("3").inputs.jsonObject["cfg"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(30, wf.getValue("4").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
        // Links are never turned into literals.
        assertEquals(listOf("9", 0 as Any), (wf.getValue("3").inputs.jsonObject["model"] as kotlinx.serialization.json.JsonArray).map {
            (it as kotlinx.serialization.json.JsonPrimitive).let { p -> if (p.isString) p.content else p.int }
        })
        assertNull(wf.getValue("5").inputs.jsonObject["steps"])
    }

    @Test
    fun dualCfgGuiderTakesTheCondCfgOnly() {
        val wf = json.decodeFromString<Workflow>(
            """{"7": {"class_type": "DualCFGGuider", "inputs": {"cfg_conds": 8.0, "cfg_cond2_negative": 8.0}}}"""
        )

        wf.updateCfgAndSteps(3.5f, 0)

        assertEquals(3.5f, wf.getValue("7").inputs.jsonObject["cfg_conds"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(8.0f, wf.getValue("7").inputs.jsonObject["cfg_cond2_negative"]!!.jsonPrimitive.float, 0.0001f)
    }

    @Test
    fun guideGraphOffersSchedulerAndDenoise() {
        val slots = detectSamplerSlots(guideGraph, info)

        // The sampler list comes from KSamplerSelect, the scheduler/denoise
        // from BasicScheduler: neither node alone has all three.
        assertEquals(listOf("euler", "euler_ancestral", "dpmpp_2m"), slots.samplerOptions)
        assertEquals(listOf("simple", "karras", "sgm_uniform"), slots.schedulerOptions)
        assertEquals("euler", slots.currentSampler)
        assertEquals("simple", slots.currentScheduler)
        assertTrue(slots.hasDenoise)
        assertEquals(1f, slots.currentDenoise, 0.0001f)
    }

    @Test
    fun choicesAreWrittenToTheNodesThatOwnThem() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "2": {"class_type": "KSamplerSelect", "inputs": {"sampler_name": "euler"}},
              "4": {"class_type": "BasicScheduler", "inputs": {"scheduler": "simple", "steps": 4, "denoise": 1.0}}
            }"""
        )

        wf.applySamplerChoices("dpmpp_2m", "karras", 0.6f)

        assertEquals("dpmpp_2m", wf.getValue("2").inputs.jsonObject["sampler_name"]!!.jsonPrimitive.content)
        assertEquals("karras", wf.getValue("4").inputs.jsonObject["scheduler"]!!.jsonPrimitive.content)
        assertEquals(0.6f, wf.getValue("4").inputs.jsonObject["denoise"]!!.jsonPrimitive.float, 0.0001f)
        // steps belong to updateCfgAndSteps, not to the sampler choice.
        assertEquals(4, wf.getValue("4").inputs.jsonObject["steps"]!!.jsonPrimitive.int)
    }

    @Test
    fun applyNodeStrengthIsOverwritableButLinkedValuesAreNot() {
        val wf = json.decodeFromString<Workflow>(
            """{
              "5": {"class_type": "ControlNetApplyAdvanced", "inputs": {"strength": 0.8, "start_percent": 0.0, "end_percent": 1.0, "positive": ["6", 0]}},
              "6": {"class_type": "ControlNetApplyAdvanced", "inputs": {"strength": ["12", 0], "start_percent": 0.0}}
            }"""
        )

        wf.applyStrengthOverrides(mapOf("5|strength" to 0.35f, "6|strength" to 0.1f))

        assertEquals(0.35f, wf.getValue("5").inputs.jsonObject["strength"]!!.jsonPrimitive.float, 0.0001f)
        // Node 6's strength comes from another node: a literal would break the link.
        assertTrue(wf.getValue("6").inputs.jsonObject["strength"] is kotlinx.serialization.json.JsonArray)
    }


    // Real Comfy-Org templates (image_flux2_text_to_image, the Qwen graphs)
    // park the step count behind PrimitiveInt nodes, sometimes behind a
    // ComfySwitchNode offering two of them. Clobbering the link would break
    // the graph, so the write has to follow it.
    @Test
    fun stepsWrittenThroughASwitchReachBothPrimitiveBranches() {
        val wf = json.decodeFromString<Workflow>(
            "{\n" +
                    "  \"48\": {\"class_type\": \"Flux2Scheduler\", \"inputs\": {\"steps\": [\"103\", 0], \"width\": [\"104\", 0], \"height\": [\"105\", 0]}},\n" +
                    "  \"103\": {\"class_type\": \"ComfySwitchNode\", \"inputs\": {\"on_true\": [\"99\", 0], \"on_false\": [\"100\", 0], \"switch\": [\"106\", 0]}},\n" +
                    "  \"99\": {\"class_type\": \"PrimitiveInt\", \"inputs\": {\"value\": 20}},\n" +
                    "  \"100\": {\"class_type\": \"PrimitiveInt\", \"inputs\": {\"value\": 4}},\n" +
                    "  \"106\": {\"class_type\": \"PrimitiveBoolean\", \"inputs\": {\"value\": false}}\n" +
                    "}"
        )

        assertEquals(listOf("48"), wf.guidanceTargets().stepsNodeIds)

        wf.updateCfgAndSteps(0f, 33)

        assertEquals(33, wf.getValue("99").inputs.jsonObject["value"]!!.jsonPrimitive.int)
        assertEquals(33, wf.getValue("100").inputs.jsonObject["value"]!!.jsonPrimitive.int)
        // The scheduler's own input keeps pointing at the switch; so do size.
        assertTrue(wf.getValue("48").inputs.jsonObject["steps"] is kotlinx.serialization.json.JsonArray)
        assertTrue(wf.getValue("48").inputs.jsonObject["width"] is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun strengthBehindAPrimitiveFloatIsWrittenThere() {
        val wf = json.decodeFromString<Workflow>(
            "{\n" +
                    "  \"5\": {\"class_type\": \"ControlNetApplyAdvanced\", \"inputs\": {\"strength\": [\"9\", 0], \"start_percent\": 0.0}},\n" +
                    "  \"9\": {\"class_type\": \"PrimitiveFloat\", \"inputs\": {\"value\": 0.8}}\n" +
                    "}"
        )

        wf.applyStrengthOverrides(mapOf("5|strength" to 0.3f))

        assertEquals(0.3f, wf.getValue("9").inputs.jsonObject["value"]!!.jsonPrimitive.float, 0.0001f)
        assertTrue(wf.getValue("5").inputs.jsonObject["strength"] is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun aWidgetFedByRealComputationIsLeftAloneAndReportedAsUntakeable() {
        val wf = json.decodeFromString<Workflow>(
            "{\n" +
                    "  \"4\": {\"class_type\": \"BasicScheduler\", \"inputs\": {\"steps\": [\"9\", 0], \"scheduler\": \"simple\"}},\n" +
                    "  \"9\": {\"class_type\": \"ComfyMathNode\", \"inputs\": {\"a\": 1, \"b\": 2}}\n" +
                    "}"
        )

        wf.updateCfgAndSteps(0f, 30)

        val targets = wf.guidanceTargets()
        assertEquals(emptyList<String>(), targets.stepsNodeIds)
        assertEquals(false, targets.takesSteps)
        assertEquals(false, targets.fixedSigmaSchedule)
        assertTrue(wf.getValue("4").inputs.jsonObject["steps"] is kotlinx.serialization.json.JsonArray)
        assertEquals(1, wf.getValue("9").inputs.jsonObject["a"]!!.jsonPrimitive.int)
    }

    @Test
    fun aSigmaListNodeIsReportedAsAFixedStepSchedule() {
        val wf = json.decodeFromString<Workflow>(
            "{\n" +
                    "  \"484\": {\"class_type\": \"ManualSigmas\", \"inputs\": {\"sigmas\": \"1.0, 0.5, 0\"}},\n" +
                    "  \"486\": {\"class_type\": \"SamplerCustomAdvanced\", \"inputs\": {\"sigmas\": [\"484\", 0]}}\n" +
                    "}"
        )

        val targets = wf.guidanceTargets()
        assertEquals(false, targets.takesSteps)
        assertEquals(true, targets.fixedSigmaSchedule)
    }

    @Test
    fun applyNodeStrengthsAreDetectedAsTheirOwnGroup() {

        val groups = detectStrengthGroups(guideGraph, info)

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("5", group.nodeId)
        assertEquals("ControlNet", group.label)
        assertEquals(listOf("strength"), group.strengths.map { it.input })
        assertEquals(0.8f, group.strengths.first().current, 0.0001f)
        assertEquals(0f, group.strengths.first().min, 0.0001f)
        assertEquals(10f, group.strengths.first().max, 0.0001f)
    }
}

package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prompt targeting for graphs whose text widgets are not plain
 * CLIPTextEncode literals: the two reported bugs - user prompt ignored
 * (krea-style link-fed encoders) and History rows crashing on
 * getPrompt() of link-fed text.
 */
class PromptTextTargetsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val kreaStyle = json.decodeFromString<Workflow>(
        """
        {
          "51": {"class_type": "CLIPTextEncode", "inputs": {"text": [63, 0], "clip": [56, 0]}},
          "63": {"class_type": "PrimitiveStringMultiline", "inputs": {"value": "old marathon prompt"}},
          "56": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl.safetensors", "type": "text_encoder"}},
          "45": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": [51, 0]}},
          "40": {"class_type": "KSampler", "inputs": {"positive": [51, 0], "negative": [45, 0],
                  "model": [71, 0], "seed": 1}},
          "71": {"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": "krea.gguf"}}
        }
        """.trimIndent()
    )

    private val customEncoderGraph = json.decodeFromString<Workflow>(
        """
        {
          "1": {"class_type": "KSampler", "inputs": {"positive": [2, 1], "model": [9, 0]}},
          "9": {"class_type": "UNETLoader", "inputs": {"unet_name": "x.safetensors"}},
          "2": {"class_type": "TextEncodeQwenImage21", "inputs": {"prompt": [3, 0], "clip_vision_output": [8, 0]}},
          "8": {"class_type": "CLIPVisionEncode", "inputs": {}},
          "3": {"class_type": "ComfySwitchNode", "inputs": {"on_false": "fallback literal", "on_true": [4, 0], "switch": false}},
          "4": {"class_type": "TextGenerate", "inputs": {"prompt": "old user prompt", "system": [5, 0]}},
          "5": {"class_type": "PrimitiveStringMultiline", "inputs": {"value": "# Rewriting Expert system prompt"}}
        }
        """.trimIndent()
    )

    @Test
    fun patchesTheWidgetNodeBehindALinkedEncoder() {
        val wf = kreaStyle
        wf.updatePrompt("a red bicycle on the moon")

        assertEquals(
            "a red bicycle on the moon",
            wf.getValue("63").inputs.jsonObject["value"]!!.jsonPrimitive.content
        )
        // The encoder's link must survive: replacing it would break the graph.
        assertEquals(
            listOf("63", "0"),
            (wf.getValue("51").inputs.jsonObject["text"] as kotlinx.serialization.json.JsonArray)
                .map { (it as JsonPrimitive).content }
        )
        assertEquals("a red bicycle on the moon", wf.getPrompt())
    }

    @Test
    fun getPromptNeverThrowsOnLinkedText() {
        // Regression: History rows crashed here with a JsonArray cast.
        assertEquals("old marathon prompt", kreaStyle.getPrompt())
    }

    @Test
    fun switchNodePatchesActiveBranchAndLinkedGenerator() {
        // Qwen-image 2.1 template: one prompt box feeds both the switch's
        // on_false branch (ACTIVE while switch=false) and TextGenerate.prompt.
        // The browser writes both; patching only the generator produced the
        // reported "workflow not getting prompt" (switch=false runs on_false).
        val wf = customEncoderGraph
        assertEquals("fallback literal", wf.getPrompt()) // active branch is what runs

        wf.updatePrompt("statue in a garden")

        assertEquals(
            "statue in a garden",
            wf.getValue("3").inputs.jsonObject["on_false"]!!.jsonPrimitive.content
        )
        assertEquals(
            "statue in a garden",
            wf.getValue("4").inputs.jsonObject["prompt"]!!.jsonPrimitive.content
        )
        // LLM system prompt stays untouched.
        assertEquals(
            "# Rewriting Expert system prompt",
            wf.getValue("5").inputs.jsonObject["value"]!!.jsonPrimitive.content
        )
        assertEquals("statue in a garden", wf.getPrompt())
    }

    @Test
    fun switchOnFollowsTheTrueBranchGenerator() {
        val wf = json.decodeFromString<Workflow>(
            """
            {
              "1": {"class_type": "KSampler", "inputs": {"positive": [2, 1], "model": [9, 0]}},
              "9": {"class_type": "UNETLoader", "inputs": {"unet_name": "x.safetensors"}},
              "2": {"class_type": "TextEncodeQwenImage21", "inputs": {"prompt": [3, 0]}},
              "3": {"class_type": "ComfySwitchNode", "inputs": {"on_false": "fallback literal", "on_true": [4, 0], "switch": true}},
              "4": {"class_type": "TextGenerate", "inputs": {"prompt": "old user prompt", "system": [5, 0]}},
              "5": {"class_type": "PrimitiveStringMultiline", "inputs": {"value": "# system prompt"}}
            }
            """.trimIndent()
        )
        wf.updatePrompt("lighthouse at dusk")

        assertEquals("lighthouse at dusk", wf.getValue("4").inputs.jsonObject["prompt"]!!.jsonPrimitive.content)
        // Inactive branch keeps its file text.
        assertEquals("fallback literal", wf.getValue("3").inputs.jsonObject["on_false"]!!.jsonPrimitive.content)
        // getPrompt reads through the active branch.
        assertEquals("lighthouse at dusk", wf.getPrompt())
    }

    @Test
    fun negativeGoesToItsOwnSlotNotThePositiveOne() {
        val wf = json.decodeFromString<Workflow>(
            """
            {
              "458": {"class_type": "KSampler", "inputs": {"positive": [452, 0], "negative": [452, 1], "model": [451, 0]}},
              "452": {"class_type": "TextEncodeQwenImage21", "inputs": {"prompt": "baked positive", "negative_prompt": "old negative"}},
              "451": {"class_type": "UNETLoader", "inputs": {"unet_name": "x.safetensors"}},
              "9": {"class_type": "SaveImage", "inputs": {"images": [458, 0]}}
            }
            """.trimIndent()
        )
        assertEquals(listOf("452" to "negative_prompt"), wf.negativeTextTargets())
        wf.updatePrompt("a llama in a hat")
        wf.updateNegativePrompt("blurry text")
        assertEquals("a llama in a hat", wf.getValue("452").inputs.jsonObject["prompt"]!!.jsonPrimitive.content)
        assertEquals("blurry text", wf.getValue("452").inputs.jsonObject["negative_prompt"]!!.jsonPrimitive.content)
    }

    @Test
    fun zeroOutNegativeNeverOverwritesThePositive() {
        // krea-style: negative = ConditioningZeroOut(positive encoder).
        val wf = kreaStyle
        assertEquals(emptyList<Pair<String, String>>(), wf.negativeTextTargets())
        wf.updateNegativePrompt("should not be written")
        // Positive widget untouched.
        assertEquals("old marathon prompt", wf.getValue("63").inputs.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun classicSecondEncoderGetsTheNegative() {
        val wf = json.decodeFromString<Workflow>(
            """
            {
              "6": {"class_type": "CLIPTextEncode", "inputs": {"text": "old", "clip": [1, 0]}},
              "7": {"class_type": "CLIPTextEncode", "inputs": {"text": "bad dogs", "clip": [1, 0]}},
              "10": {"class_type": "KSampler", "inputs": {"positive": [6, 0], "negative": [7, 0]}},
              "1": {"class_type": "CLIPLoader", "inputs": {"clip_name": "clip.safetensors"}}
            }
            """.trimIndent()
        )
        wf.updateNegativePrompt("watermarks")
        assertEquals("old", wf.getValue("6").inputs.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("watermarks", wf.getValue("7").inputs.jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun classicLiteralEncoderStillPatches() {
        val wf = json.decodeFromString<Workflow>(
            """
            {
              "6": {"class_type": "CLIPTextEncode", "inputs": {"text": "old", "clip": [1, 0]}},
              "7": {"class_type": "CLIPTextEncode", "inputs": {"text": "bad dogs", "clip": [1, 0]}},
              "10": {"class_type": "KSampler", "inputs": {"positive": [6, 0], "negative": [7, 0]}},
              "1": {"class_type": "CLIPLoader", "inputs": {"clip_name": "clip.safetensors"}}
            }
            """.trimIndent()
        )
        wf.updatePrompt("sunset over fjord")
        assertEquals("sunset over fjord", wf.getValue("6").inputs.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("bad dogs", wf.getValue("7").inputs.jsonObject["text"]!!.jsonPrimitive.content)
    }
}

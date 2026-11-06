package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fixtures are real files saved from the ComfyUI web UI against ComfyUI
 * 0.43, plus an object_info subset of exactly the classes they use.
 */
class WorkflowConverterTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val objectInfo: JsonObject by lazy {
        json.parseToJsonElement(File("src/test/assets/object_info_subset.json").readText()).jsonObject
    }

    private fun graph(name: String): JsonObject =
        json.parseToJsonElement(File("src/test/assets/wf_$name").readText()).jsonObject

    private fun inputs(w: Workflow, id: String) = w.getValue(id).inputs.jsonObject

    @Test
    fun dynamicComboMainAndSubWidgetsKeepValueAlignment() {
        // Combo-like custom types (COMFY_DYNAMICCOMBO_V3) are widgets and the
        // frontend exposes each sub-widget as a dotted input slot; widgets_values
        // carries main + sub values positionally. Dropping the main key broke
        // server validation ("Prompt outputs failed validation") and shifted
        // every later value.
        val info = json.parseToJsonElement(
            """{
              "SeedNode": {"input": {"required": {"value": ["INT", {}]}}},
              "SaveImg": {"input": {"required": {
                "images": ["IMAGE", {}],
                "prefix": ["STRING", {}],
                "format": ["COMFY_DYNAMICCOMBO_V3", {"options": ["png", "webp"]}]
              }}}
            }"""
        ).jsonObject
        val graph = json.parseToJsonElement(
            """{
              "nodes": [
                {"id": 1, "type": "SeedNode", "widgets_values": [42]},
                {"id": 2, "type": "SaveImg",
                 "inputs": [
                   {"name": "images", "link": 9},
                   {"name": "format.bit_depth", "link": null},
                   {"name": "format.input_color_space", "link": null}
                 ],
                 "widgets_values": ["out", "png", "8-bit", "sRGB"]}
              ],
              "links": [[9, 1, 0, 2, 0]]
            }"""
        ).jsonObject

        val w = WorkflowConverter(info).convert(graph)
        val sav = inputs(w, "2")
        assertEquals(listOf("1", 0), sav["images"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int })
        assertEquals("out", sav["prefix"]!!.jsonPrimitive.content)
        assertEquals("png", sav["format"]!!.jsonPrimitive.content)
        assertEquals("8-bit", sav["format.bit_depth"]!!.jsonPrimitive.content)
        assertEquals("sRGB", sav["format.input_color_space"]!!.jsonPrimitive.content)
    }

    @Test
    fun linkedSubWidgetSlotEmitsLinkAndStillConsumesItsValue() {
        val info = json.parseToJsonElement(
            """{
              "SeedNode": {"input": {"required": {"value": ["INT", {}]}}},
              "Gen": {"input": {"required": {
                "mode": ["COMFY_DYNAMICCOMBO_V3", {"options": ["on", "off"]}],
                "tail": ["STRING", {}]
              }}}
            }"""
        ).jsonObject
        val graph = json.parseToJsonElement(
            """{
              "nodes": [
                {"id": 1, "type": "SeedNode", "widgets_values": [42]},
                {"id": 2, "type": "Gen",
                 "inputs": [
                   {"name": "mode.sub", "link": 9},
                   {"name": "mode.sub2", "link": null}
                 ],
                 "widgets_values": ["on", 7, "extra-string", "tail-value"]}
              ],
              "links": [[9, 1, 0, 2, 0]]
            }"""
        ).jsonObject

        val w = WorkflowConverter(info).convert(graph)
        val gen = inputs(w, "2")
        assertEquals("on", gen["mode"]!!.jsonPrimitive.content)
        assertEquals(listOf("1", 0), gen["mode.sub"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int })
        assertEquals("extra-string", gen["mode.sub2"]!!.jsonPrimitive.content)
        // tail stays aligned after the sub-widget consumed its value slot
        assertEquals("tail-value", gen["tail"]!!.jsonPrimitive.content)
    }

    @Test
    fun plainGraphConvertsToApiFormat() {
        val w = WorkflowConverter(objectInfo).convert(graph("krea-2-turbo-v2.json"))
        assertEquals(
            setOf("51", "52", "53", "58", "66", "57", "54", "71", "59", "49", "29", "63", "56"),
            w.keys
        )
        // MarkdownNote is not executable and must not reach the prompt.
        assertTrue(w.values.all { it.classType != "MarkdownNote" })

        val sampler = inputs(w, "53")
        assertEquals("KSampler", w.getValue("53").classType)
        for (key in listOf("seed", "steps", "cfg", "sampler_name", "scheduler", "denoise")) {
            assertTrue("KSampler missing $key", sampler.containsKey(key))
        }
        assertEquals("randomize", sampler["control_after_generate"]!!.jsonPrimitive.content)

        val lora = inputs(w, "59")
        assertEquals("krea2_darkbrush.safetensors", lora["lora_name"]!!.jsonPrimitive.content)
        assertEquals(1.0, lora["strength_model"]!!.jsonPrimitive.float.toDouble(), 0.0)
        assertEquals(listOf("71", 0), lora["model"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int })

        assertEquals("qwen_image_vae_krea.safetensors", inputs(w, "57")["vae_name"]!!.jsonPrimitive.content)
        assertEquals("Krea-2-Turbo-Q8_0.gguf", inputs(w, "71")["unet_name"]!!.jsonPrimitive.content)
        assertEquals("qwen3vl_4b_fp8_scaled.safetensors", inputs(w, "56")["clip_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun subgraphFlattensWithNamespacedIds() {
        val w = WorkflowConverter(objectInfo).convert(graph("image_qwen.json"))
        assertTrue(w.containsKey("9"))
        assertTrue(w.containsKey("57_3"))
        // The outer SaveImage consumes the subgraph IMAGE output, which is
        // produced by the inner VAEDecode.
        assertEquals(
            listOf("57_8", 0),
            inputs(w, "9")["images"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int }
        )
        assertEquals("high fives for all! good family fun! families giving each other high fives and enjoying themselves very much!", inputs(w, "57_27")["text"]!!.jsonPrimitive.content)
        assertEquals(1024, inputs(w, "57_13")["width"]!!.jsonPrimitive.int)
        assertEquals(8, inputs(w, "57_3")["steps"]!!.jsonPrimitive.int)
    }

    @Test
    fun patchedSubgraphGraphTargetsInnerNodes() {
        val w = WorkflowConverter(objectInfo).convert(graph("image_qwen.json"))
        w.updatePrompt(prompt = "a red fox")
        w.updateSeed(seed = 999L)
        w.updateSizeAndBatch(width = 512, height = 768, batchSize = 2)
        assertEquals("a red fox", inputs(w, "57_27")["text"]!!.jsonPrimitive.content)
        assertEquals(999L, inputs(w, "57_3")["seed"]!!.jsonPrimitive.content.toLong())
        assertEquals(512, inputs(w, "57_13")["width"]!!.jsonPrimitive.int)
        assertEquals(2, inputs(w, "57_13")["batch_size"]!!.jsonPrimitive.int)
    }

    @Test
    fun linkedSizeInputsAreNeverClobbered() {
        val w = WorkflowConverter(objectInfo).convert(graph("krea-2-turbo-v2.json"))
        w.updateSizeAndBatch(width = 512, height = 512, batchSize = 3)
        // Node 52's width/height are links into ResolutionSelector; only
        // batch_size is a literal and may be replaced.
        assertEquals(
            listOf("49", 0),
            inputs(w, "52")["width"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int }
        )
        assertEquals(3, inputs(w, "52")["batch_size"]!!.jsonPrimitive.int)
    }

    @Test
    fun largeNestedGraphConverts() {
        val w = WorkflowConverter(objectInfo).convert(graph("image_qwen_image_2_1_t2i.json"))
        // outer graph is just the subgraph instance + Save/Note nodes;
        // everything executable lives under the 459_ namespace.
        assertTrue(w.containsKey("459_452"))
        assertEquals("TextEncodeQwenImage21", w.getValue("459_452").classType)
        val sampler = inputs(w, "459_458")
        assertEquals(listOf("459_452", 0), sampler["positive"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int })
        assertEquals(listOf("459_452", 1), sampler["negative"]!!.jsonArray.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int })
        // ResolutionSelector in the outer graph feeds the inner latent node.
        val latent = inputs(w, "459_456")
        val width = latent["width"]!!.jsonArray
        assertEquals(listOf("13", 0), width.mapIndexed { i, e -> if (i == 0) e.jsonPrimitive.content else e.jsonPrimitive.int })
    }

    @Test
    fun v3OptionSubinputsExpandToDottedApiNames() {
        // Real-world break: ComfyUI 0.43 SaveImageAdvanced/TextGenerate
        // declare per-option subinputs in the dynamic combo spec, and the
        // server validates the DOTTED names ("format.bit_depth"). The
        // saved graph carries no dotted input entries for them - the only
        // hints are the spec plus widgets_values order.
        val info = json.parseToJsonElement(
            """{
              "Save": {"input": {"required": {
                "images": ["IMAGE", {}],
                "filename_prefix": ["STRING", {}],
                "format": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                  {"key": "png", "inputs": {"required": {
                    "bit_depth": ["COMBO", {"default": "8-bit", "options": ["8-bit", "16-bit"]}],
                    "input_color_space": ["COMBO", {"default": "sRGB", "options": ["sRGB"]}]
                  }}}
                ]}]
              }}}
            }"""
        ).jsonObject
        val graph = json.parseToJsonElement(
            """{
              "nodes": [
                {"id": 1, "type": "Save", "inputs": [], "widgets_values": ["x", "png"]},
                {"id": 5, "type": "Save",
                 "inputs": [{"name": "images", "link": 9}],
                 "widgets_values": ["Qwen", "png", "8-bit", "sRGB"]}
              ],
              "links": [[9, 1, 0, 5, 0]]
            }"""
        ).jsonObject

        val sav = inputs(WorkflowConverter(info).convert(graph), "5")
        assertEquals("png", sav["format"]!!.jsonPrimitive.content)
        assertEquals("8-bit", sav["format.bit_depth"]!!.jsonPrimitive.content)
        assertEquals("sRGB", sav["format.input_color_space"]!!.jsonPrimitive.content)
    }

    @Test
    fun socketlessRequiredInputGetsPlaceholderAndBadComboValuesSnap() {
        // ImageCompare's compare_view is a required, socketless aggregate:
        // the frontend auto-provides it; without one the whole prompt is
        // rejected. A stale combo value (mtp: 0) must snap to the default
        // instead of failing validation.
        val info = json.parseToJsonElement(
            """{
              "ImageCompare": {"input": {
                "required": {"compare_view": ["IMAGECOMPARE", {"socketless": true}]},
                "optional": {
                  "image_a": ["IMAGE", {}],
                  "mtp": ["COMBO", {"default": "auto", "options": ["auto", "off"]}]
                }
              }},
              "Src": {"input": {"required": {"x": ["INT", {}]}}}
            }"""
        ).jsonObject
        val graph = json.parseToJsonElement(
            """{
              "nodes": [
                {"id": 1, "type": "Src", "widgets_values": [1]},
                {"id": 9, "type": "ImageCompare",
                 "inputs": [{"name": "image_a", "link": 9}],
                 "widgets_values": [0]}
              ],
              "links": [[9, 1, 0, 9, 0]]
            }"""
        ).jsonObject

        val cmp = inputs(WorkflowConverter(info).convert(graph), "9")
        assertEquals(true, cmp["compare_view"]!!.jsonPrimitive.boolean)
        assertEquals("auto", cmp["mtp"]!!.jsonPrimitive.content)
    }
}

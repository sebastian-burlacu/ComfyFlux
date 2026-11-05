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
}

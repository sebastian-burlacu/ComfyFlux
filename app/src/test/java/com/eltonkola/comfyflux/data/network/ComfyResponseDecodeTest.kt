package com.eltonkola.comfyflux.data.network

import com.eltonkola.comfyflux.data.model.SystemStats
import com.eltonkola.comfyflux.data.model.Workflow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Payloads copied from the shape a 2025-era ComfyUI actually returns. The
 * strict decoder these replace crashed on the added fields, which made a
 * perfectly reachable server look like an "Invalid URL".
 */
class ComfyResponseDecodeTest {

    private val modernSystemStats = """
        {"system":{"os":"Windows","python_version":"3.12.10 (tags/v3.12.10) [MSC v.1943 64 bit]",
        "embedded_python":false,"RAM":{"total":68620611584,"free":33054191616,"used":35566419968}},
        "devices":[{"name":"cuda:0","type":"cuda","index":0,"vram_total":25757220864,
        "vram_free":14942437376,"torch_vram_total":16777216,"torch_vram_free":5177344}],
        "comfyui_version":"0.3.45","required_frontend_version":"1.16.6",
        "installed_templates_hash":"139106963e90",
        "comfy_session":{"id":"22588647","start_time":1762345678901,"last_activity":1762345678901},
        "remotes":[{"name":"Comfy Registry","site":"https://api.comfy.org","icon":null}]}
    """.trimIndent()

    @Test
    fun modernSystemStatsDecodes() {
        val stats = ComfyJson.decodeFromString<SystemStats>(modernSystemStats)
        assertEquals("Windows", stats.system.os)
        assertEquals(25757220864L, stats.devices.first().vram_total)
    }

    @Test
    fun queuePromptResponseIgnoresUnknownFields() {
        val r = ComfyJson.decodeFromString<QueuePromptResponse>(
            """{"prompt_id":"d5c6","number":0,"node_errors":{},"extra_field":42}"""
        )
        assertEquals("d5c6", r.prompt_id)
    }

    private val modernHistory = """
        {"d5c6":{"prompt":[42,"d5c6",
          {"3":{"inputs":{"text":"a cat","anything_new":true},"class_type":"CLIPTextEncode"},
           "9":{"inputs":{"text":"bad"},"class_type":"CLIPTextEncode"},
           "6":{"inputs":{"conditioning":["3",0]},"class_type":"CheckpointLoaderSimple",
                "_meta":{"title":"Load Checkpoint"}}}],
         "outputs":{"9":{"images":[{"filename":"00001-a.png","subfolder":"","type":"output"}]},
                    "7":{"audio":[{"filename":"a.mp3","subfolder":"","type":"output"}]}},
         "status":{"status_str":"success","completed":true,
                   "messages":[["execution_start",{"prompt_id":"d5c6","timestamp":1762345678901}]]},
         "seed":1234567890,"workflow":{"nodes":[]},"extra_data":{"client_id":"abc"}}}
    """.trimIndent()

    @Test
    fun modernHistoryDecodesWithAudioOutputAndUnknownKeys() {
        val history = ComfyJson.decodeFromString(
            MapSerializer(String.serializer(), HistoryResponse.serializer()),
            modernHistory
        )
        val entry = history.getValue("d5c6")
        assertEquals(1, entry.outputs.values.flatMap { it.images }.size)
        assertEquals(true, entry.status.completed)
    }

    @Test
    fun workflowNodesDecodeWithoutMetaAndWithUnknownInputKeys() {
        val workflow = ComfyJson.decodeFromString<Workflow>("""
            {"3":{"inputs":{"text":"a cat","anything_new":true},"class_type":"CLIPTextEncode"}}
        """.trimIndent())
        assertEquals("CLIPTextEncode", workflow["3"]!!.classType)
        assertEquals("", workflow["3"]!!.meta.title)
    }
}

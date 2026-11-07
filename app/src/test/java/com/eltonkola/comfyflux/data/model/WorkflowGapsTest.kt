package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowGapsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val info = Json.parseToJsonElement(
        """{
          "UNETLoader": {"input": {"required": {
            "unet_name": ["qwen_image_fp8_e4m3fn.safetensors", "flux1.safetensors"],
            "weight_dtype": ["default"]
          }}},
          "CLIPLoader": {"input": {"required": {
            "clip_name": ["qwen3vl_4b_fp8_scaled.safetensors"],
            "type": ["qwen_image"]
          }}},
          "VAELoader": {"input": {"required": {
            "vae_name": ["qwen_image_vae.safetensors", "ae.safetensors"]
          }}},
          "SaveImage": {"input": {"required": {"images": ["IMAGE"]}}}
        }"""
    ).jsonObject

    private fun gapsOf(workflowJson: String) =
        json.decodeFromString<Workflow>(workflowJson).gaps(info)

    @Test
    fun filesTheServerDoesNotListAreReportedWithTheirPlace() {
        val gaps = gapsOf(
            """{
              "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "qwen_image_fp8_e4m3fn.safetensors"}},
              "2": {"class_type": "UNETLoader", "inputs": {"unet_name": "qwen_image_2.1_int8_convrot.safetensors"}},
              "3": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl_8b_int8_convrot.safetensors"}},
              "4": {"class_type": "VAELoader", "inputs": {"vae_name": "qwen_image_2.1_vae_bf16.safetensors"}}
            }"""
        )

        assertEquals(
            listOf(
                "2" to "qwen_image_2.1_int8_convrot.safetensors",
                "3" to "qwen3vl_8b_int8_convrot.safetensors",
                "4" to "qwen_image_2.1_vae_bf16.safetensors"
            ),
            gaps.missingFiles.map { it.nodeId to it.wanted }
        )
        assertTrue(gaps.missingNodeTypes.isEmpty())
        assertEquals(
            "UNETLoader node 2: 'qwen_image_2.1_int8_convrot.safetensors' is not on this server (input 'unet_name').",
            gaps.missingFiles.first().note
        )
    }

    @Test
    fun anEmptyServerFolderIsReportedToo() {
        val emptyFolderInfo = Json.parseToJsonElement(
            """{
              "ControlNetLoader": {"input": {"required": {
                "control_net_name": [[], {"default": ""}]
              }}}
            }"""
        ).jsonObject

        val gaps = json.decodeFromString<Workflow>(
            """{"5": {"class_type": "ControlNetLoader", "inputs": {"control_net_name": "instantx_controlnet.safetensors"}}}"""
        ).gaps(emptyFolderInfo)

        assertEquals(
            listOf("control_net_name" to "instantx_controlnet.safetensors"),
            gaps.missingFiles.map { it.input to it.wanted }
        )
    }

    @Test
    fun unknownNodeClassesAreReportedSeparately() {
        val gaps = gapsOf(
            """{
              "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "flux1.safetensors"}},
              "7": {"class_type": "SomethingCustom", "inputs": {"anything": 1}},
              "8": {"class_type": "SomethingCustom", "inputs": {"other": 2}}
            }"""
        )

        assertEquals(listOf("SomethingCustom"), gaps.missingNodeTypes)
        assertTrue(gaps.missingFiles.isEmpty())
    }

    @Test
    fun aWorkflowTheServerCanRunHasNoGaps() {
        val gaps = gapsOf(
            """{
              "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "flux1.safetensors"}},
              "2": {"class_type": "VAELoader", "inputs": {"vae_name": "ae.safetensors"}},
              "3": {"class_type": "SaveImage", "inputs": {"images": ["1", 0]}}
            }"""
        )

        assertTrue(gaps.isEmpty)
    }

    @Test
    fun linkedInputsAndFreeTextInputsAreNotMistakenForMissingFiles() {
        val gaps = gapsOf(
            """{
              "1": {"class_type": "UNETLoader", "inputs": {"unet_name": ["9", 0]}},
              "2": {"class_type": "SomethingCustom", "inputs": {"model_name": "whatever the node wants"}}
            }"""
        )

        // node 1's file name comes from another node; node 2's server spec is
        // unknown, so the app cannot claim the file is missing.
        assertTrue(gaps.missingFiles.isEmpty())
        assertEquals(listOf("SomethingCustom"), gaps.missingNodeTypes)
    }
}

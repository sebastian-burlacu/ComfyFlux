package com.eltonkola.comfyflux.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCatalogTest {

    private val listing = """
    {
      "tree": [
        {"path": "templates/text_to_image_flux.json", "type": "blob"},
        {"path": "templates/text_to_image_flux-1.webp", "type": "blob"},
        {"path": "templates/sub/upscale_esrgan.json", "type": "blob"},
        {"path": "templates/notes.txt", "type": "blob"},
        {"path": "src/main.py", "type": "blob"},
        {"path": "templates", "type": "tree"}
      ]
    }
    """.trimIndent()

    @Test
    fun listingKeepsJsonGraphsAndPairsThumbnails() {
        val entries = TemplateCatalog.parseListing(listing)

        // Only templates/*.json (nested subfolders count); README etc. out.
        assertEquals(listOf("text_to_image_flux.json", "upscale_esrgan.json"), entries.map { it.fileName })
        assertEquals("text to image flux", entries.first().title)
        assertEquals(
            "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/text_to_image_flux-1.webp",
            entries.first().thumbUrl
        )
        assertEquals(
            "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/sub/upscale_esrgan.json",
            entries[1].downloadUrl
        )
        // A template without a sibling -1.webp gets no thumbnail.
        assertNull(entries[1].thumbUrl)
        // Kinds come out of the name classifier: plain vs "upscale" input.
        assertEquals(
            listOf(TemplateKind.IMAGE, TemplateKind.IMAGE_INPUT),
            entries.map { it.kind }
        )
    }

    @Test
    fun classifyTemplatesBucketsRealCatalogNames() {
        // Representative names straight from the live Comfy-Org listing.
        // Precedence: cloud API beats everything, then video/audio/3D.
        assertEquals(TemplateKind.CLOUD_API, classifyTemplate("api_wan2_7_i2v.json"))
        assertEquals(TemplateKind.CLOUD_API, classifyTemplate("api_anthropic_claude.json"))
        assertEquals(TemplateKind.VIDEO, classifyTemplate("image_to_video_wan.json"))
        assertEquals(TemplateKind.VIDEO, classifyTemplate("ltxv_text_to_video.json"))
        assertEquals(TemplateKind.VIDEO, classifyTemplate("template_ltx2_3_ic_lora_ingredients.json"))
        assertEquals(TemplateKind.VIDEO, classifyTemplate("template_image_speech_to_video.json"))
        assertEquals(TemplateKind.AUDIO, classifyTemplate("audio_stable_audio_3_medium.json"))
        assertEquals(TemplateKind.MODEL_3D, classifyTemplate("3d_hunyuan3d_image_to_model.json"))
        assertEquals(TemplateKind.MODEL_3D, classifyTemplate("3d_pixal3d_trellis2_image_to_model.json"))
        // Workflows that need an input photo (img2img, editing, upscale).
        assertEquals(TemplateKind.IMAGE_INPUT, classifyTemplate("flux_fill_inpaint_example.json"))
        assertEquals(TemplateKind.IMAGE_INPUT, classifyTemplate("image_boogu_image_0_1_edit.json"))
        assertEquals(TemplateKind.IMAGE_INPUT, classifyTemplate("utility_z_image_turbo_2k_upscaler.app.json"))
        // Plain text-to-image stays the default.
        assertEquals(TemplateKind.IMAGE, classifyTemplate("image_z_image_turbo.json"))
        assertEquals(TemplateKind.IMAGE, classifyTemplate("flux_dev_checkpoint_example.json"))
    }

    @Test
    fun missingNodeTypesReportsUnknownClassesOnly() {
        val graph = """
        {
          "nodes": [
            {"type": "LoadImage"},
            {"type": "MyMissingNode"},
            {"type": "def-uuid-1"}
          ]
        }
        """.trimIndent()

        // No definitions block: the instance type has nothing resolving it,
        // so it is reported like any unknown class.
        val missing = TemplateCatalog.missingNodeTypes(graph, setOf("LoadImage", "KSampler"))
        assertEquals(listOf("MyMissingNode", "def-uuid-1"), missing)
    }

    @Test
    fun uiOnlyNodesAreNeverReported() {
        val graph = """
        {"nodes": [{"type": "Note"}, {"type": "MarkdownNote"}, {"type": "Reroute"}]}
        """.trimIndent()
        assertTrue(TemplateCatalog.missingNodeTypes(graph, emptySet()).isEmpty())
    }

    @Test
    fun missingNodeTypesHandlesSubgraphDefinitionsAndBadInput() {
        // Real shape: definitions.subgraphs[] with id = instance node type.
        val graph = """
        {
          "nodes": [{"type": "def-uuid-1"}],
          "definitions": {
            "subgraphs": [
              {"id": "def-uuid-1", "nodes": [{"type": "SaveImage"}, {"type": "CustomXyz"}]}
            ]
          }
        }
        """.trimIndent()

        // The instance type is resolved by the subgraph definition; the
        // inner CustomXyz is what's actually missing on the server.
        assertEquals(
            listOf("CustomXyz"),
            TemplateCatalog.missingNodeTypes(graph, setOf("SaveImage"))
        )

        // Garbage graph: no crash, no warnings (the import itself fails later).
        assertTrue(TemplateCatalog.missingNodeTypes("not json", setOf("LoadImage")).isEmpty())
    }
}

package com.eltonkola.comfyflux.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a template actually produces, judged from its file name. */
enum class TemplateKind(val label: String) {
    IMAGE("Image"),
    IMAGE_INPUT("Image input"),
    VIDEO("Video"),
    AUDIO("Audio"),
    MODEL_3D("3D"),
    CLOUD_API("Comfy API")
}

/** One Comfy-Org workflow template, as offered by the public catalog. */
data class TemplateEntry(
    val fileName: String,
    val title: String,
    val downloadUrl: String,
    val thumbUrl: String? = null,
    val kind: TemplateKind = TemplateKind.IMAGE
)

private val VIDEO_TOKEN = Regex("(^|_)(v2v|i2v|t2v|r2v|flf2v|wan\\d*|vidu|ltx\\d*|sora|kling|hailuo)([_.]|$)")

/**
 * Bucket a template by file name. The Comfy-Org repo names are strict
 * enough that this is reliable: "api_" prefixes are Comfy Cloud-hosted
 * workflows (they cannot run locally), model families keep their tokens.
 * Precedence: cloud API > video > audio > 3D > image input > image.
 */
internal fun classifyTemplate(fileName: String): TemplateKind {
    val n = fileName.lowercase()
    if (n.startsWith("api_") || n.contains("_api_")) return TemplateKind.CLOUD_API
    if (n.contains("video") || VIDEO_TOKEN.containsMatchIn(n)) return TemplateKind.VIDEO
    if (n.contains("audio") || n.contains("music") || n.contains("tts") || n.contains("speech")) return TemplateKind.AUDIO
    if (n.contains("3d") || n.contains("meshy") || n.contains("glb") || n.contains("gltf") ||
        n.contains("trellis") || n.contains("triposr") || n.contains("splat") || n.contains("panorama")
    ) return TemplateKind.MODEL_3D
    val imageInput = listOf(
        "img2img", "inpaint", "outpaint", "image_edit", "image_to", "_edit", "edit_",
        "canny", "depth", "pose", "scribble", "redux", "upscale", "detailer", "hires",
        "controlnet", "control_", "tile", "brightness", "contrast", "layer", "deblur",
        "background", "soften", "sharp", "remove", "clean", "fix"
    )
    if (imageInput.any { n.contains(it) }) return TemplateKind.IMAGE_INPUT
    return TemplateKind.IMAGE
}

@Serializable
internal data class TreeEntry(
    val path: String = "",
    val type: String = ""
)

/**
 * The public Comfy-Org workflow template library
 * (github.com/Comfy-Org/workflow_templates, templates/ folder): web-UI
 * graph JSONs plus matching "-1.webp" preview thumbnails. No auth needed;
 * importing a template stores it on the user's own server as a saved
 * workflow.
 */
class TemplateCatalog {

    companion object {
        // Git tree of the whole repo, recursive: the contents API truncates
        // at 1000 entries and the templates folder is past that (1700+).
        const val LIST_URL =
            "https://api.github.com/repos/Comfy-Org/workflow_templates/git/trees/main?recursive=1"
        const val RAW_BASE =
            "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Pure listing parse (unit-tested): keep the web-UI graph JSONs
         * under templates/ (nested subfolders included) and pair each with
         * its sibling "-1.webp" preview when the repo contains one.
         */
        internal fun parseListing(body: String): List<TemplateEntry> {
            val items = json.parseToJsonElement(body).jsonObject["tree"]?.jsonArray
                ?.mapNotNull { el ->
                    runCatching { json.decodeFromJsonElement(TreeEntry.serializer(), el) }.getOrNull()
                }
                ?.filter { it.type == "blob" && it.path.startsWith("templates/") }
                .orEmpty()

            val thumbnails = items.filter { it.path.endsWith(".webp") }.map { it.path }.toSet()

            return items
                .filter { it.path.endsWith(".json") }
                .map { e ->
                    val dir = e.path.substringBeforeLast('/', "")
                    val name = e.path.substringAfterLast('/')
                    val stem = name.removeSuffix(".json")
                    TemplateEntry(
                        fileName = name,
                        title = stem.replace('_', ' '),
                        downloadUrl = RAW_BASE + e.path,
                        thumbUrl = if ("$dir/$stem-1.webp".removePrefix("/") in thumbnails) {
                            "$RAW_BASE$dir/$stem-1.webp".removeSuffix("/")
                        } else null,
                        kind = classifyTemplate(name)
                    )
                }
                .sortedBy { it.title }
        }

        internal fun missingNodeTypes(graphJson: String, known: Set<String>): List<String> {
            val graph = runCatching { json.parseToJsonElement(graphJson).jsonObject }
                .getOrNull() ?: return emptyList()

            val types = linkedSetOf<String>()
            fun collect(nodesKey: JsonElement?) {
                nodesKey?.jsonArray?.forEach { n ->
                    runCatching {
                        n.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { types.add(it) }
                }
            }
            collect(graph["nodes"])

            val subgraphIds = linkedSetOf<String>()
            runCatching {
                graph["definitions"]?.jsonObject?.get("subgraphs")?.jsonArray
            }.getOrNull()?.forEach { sg ->
                runCatching {
                    sg.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.let { subgraphIds.add(it) }
                    collect(sg.jsonObject["nodes"])
                }
            }

            // Front-end-implemented classes (notes, reroutes, primitive
            // inputs): they never appear in the server's object_info yet
            // the converter/graph handles them - not a missing-node warning.
            val uiOnly = setOf("Note", "MarkdownNote", "Reroute", "PrimitiveNode")

            return (types - known - subgraphIds - uiOnly).sorted()
        }
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    private var cache: List<TemplateEntry>? = null

    suspend fun list(refresh: Boolean = false): List<TemplateEntry> {
        cache?.takeIf { !refresh }?.let { return it }
        val response = client.get(LIST_URL) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            throw Exception("Template list failed: HTTP ${response.status.value}")
        }
        return parseListing(response.bodyAsText()).also { cache = it }
    }

    suspend fun fetchTemplate(downloadUrl: String): String {
        val response = client.get(downloadUrl)
        if (!response.status.isSuccess()) {
            throw Exception("Template download failed: HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }
}

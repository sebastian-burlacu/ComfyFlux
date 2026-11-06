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

/** One Comfy-Org workflow template, as offered by the public catalog. */
data class TemplateEntry(
    val fileName: String,
    val title: String,
    val downloadUrl: String,
    val thumbUrl: String?
)

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
                        } else null
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

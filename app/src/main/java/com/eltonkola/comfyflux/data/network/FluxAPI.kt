package com.eltonkola.comfyflux.data.network

import android.net.Uri
import android.util.Log
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.data.model.HistoryItem
import com.eltonkola.comfyflux.data.model.ProgressGenerationUIState
import com.eltonkola.comfyflux.data.model.PromptRequest
import com.eltonkola.comfyflux.data.model.Queue
import com.eltonkola.comfyflux.data.model.SystemStats
import com.eltonkola.comfyflux.data.model.WSMessage
import com.eltonkola.comfyflux.data.model.Workflow
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import java.util.UUID

@Serializable
data class PromptHistory(val outputs: Map<String, PromptData>)

@Serializable
data class PromptData(
    val prompt: List<JsonElement>,  // Use JsonElement for dynamic content
    val outputs: Map<String, OutputData>,
    val status: Status
)

@Serializable
data class OutputData(
    val images: List<Image>
)

@Serializable
data class Image(
    val filename: String,
    val subfolder: String,
    val type: String
)

@Serializable
data class Status(
    val status_str: String,
    val completed: Boolean,
    val messages: List<List<JsonElement>>
)


@Serializable
data class QueuePromptResponse(
    val prompt_id: String,
    val number: Int,
    val node_errors: Map<String, String>
)

const val DEFAULT_URL = "http://192.168.1.5:8188"

// Server responses evolve with every ComfyUI update (RAM, comfyui_version,
// comfy_session, remotes, extra node fields). Decode leniently so newer
// servers never crash the app.
internal val ComfyJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * Accepts what users actually type ("192.168.1.5:8188", trailing slashes,
 * surrounding whitespace) and turns it into a usable base URL.
 */
fun normalizeServerUrl(raw: String): String {
    var url = raw.trim().removeSuffix("/")
    if (url.isEmpty()) return ""
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
    return url
}

fun extractAddressAndPort(url: String): Pair<String, Int> {
    val uri = Uri.parse(url)
    val address = uri.host ?: ""
    val port = if (uri.port != -1) uri.port else 80 // Use 80 if no port is specified
    return Pair(address, port)
}

/**
 * Why a body failed to decode, phrased for the Logs screen / error banner:
 * an empty body almost always means the server is mid-restart, a corrupt one
 * is best shown with its head.
 */
fun decodeFailureReason(body: String, endpoint: String, status: Int): String =
    if (body.isBlank()) {
        "$endpoint returned an empty response (HTTP $status) - is ComfyUI restarting?"
    } else {
        "$endpoint returned invalid JSON after ${body.length} chars (HTTP $status): ${body.take(120)}"
    }

/** Non-2xx answer from the ComfyUI server, carrying the server's own error body. */
class ComfyServerException(
    val status: Int,
    val path: String,
    val body: String,
    cause: Throwable? = null,
) : Exception(
    "HTTP $status from $path: ${body.take(500).ifEmpty { "no response body" }}",
    cause,
)

/*
Api docs:
Rest:
https://github.com/comfyanonymous/ComfyUI/issues/2110

WS:
https://github.com/comfyanonymous/ComfyUI/issues/2258
*/

class FluxAPI(private val logStore: LogStore) {

    companion object {
        // Hard stop for a single generation; covers slow model loads.
        private const val GENERATION_TIMEOUT_MS = 15 * 60 * 1000L
    }

    val clientId = UUID.randomUUID().toString()

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
        }
        install(HttpCallValidator) {
            // Surface the server's own error text (/prompt node_errors, 404
            // bodies, ...) instead of ktor's bare status line.
            handleResponseExceptionWithRequest { cause, request ->
                if (cause is ResponseException) {
                    val response = cause.response
                    val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                    throw ComfyServerException(
                        status = response.status.value,
                        path = request.url.encodedPath,
                        body = body,
                        cause = cause,
                    )
                }
            }
        }
    }

    // WebSocket sessions are long lived; they must not inherit request timeouts.
    private val wsClient = HttpClient(CIO) {
        install(WebSockets)
    }

    private var serverAddress = DEFAULT_URL
    private var objectInfoCache: JsonObject? = null

    fun setServerAddress(address: String) {
        if (serverAddress != address) objectInfoCache = null
        serverAddress = address
    }

    /** Decodes an HTTP body, rethrowing malformed JSON as a message the user can act on. */
    private inline fun <reified T> decodeBody(body: String, endpoint: String, status: Int): T =
        try {
            ComfyJson.decodeFromString(body)
        } catch (e: SerializationException) {
            throw Exception(decodeFailureReason(body, endpoint, status), e)
        }

    suspend fun fetchObjectInfo(): JsonObject {
        objectInfoCache?.let { return it }
        val response = client.get("$serverAddress/object_info")
        val body = response.bodyAsText()
        val info = decodeBody<JsonElement>(body, "/object_info", response.status.value).jsonObject
        objectInfoCache = info
        return info
    }

    /** Workflow names saved in the ComfyUI web UI (server user data). */
    suspend fun fetchWorkflowNames(): List<String> {
        val response = client.get("$serverAddress/userdata") {
            parameter("dir", "workflows")
        }
        val el = decodeBody<JsonElement>(response.bodyAsText(), "/userdata", response.status.value)
        val names = when (el) {
            is JsonArray -> el.mapNotNull { it as? JsonPrimitive }
                .filter { it.isString }.map { it.content }
            is JsonObject -> (el["files"] as? JsonArray)?.mapNotNull { entry ->
                (entry as? JsonPrimitive)?.contentOrNull
                    ?: entry?.jsonObject?.get("path")?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
            else -> emptyList()
        }
        return names.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.sorted()
    }

    suspend fun fetchWorkflowContent(name: String): String {
        // aiohttp matches the raw path: %2F keeps the file route's single segment.
        val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val response = client.get("$serverAddress/userdata/workflows%2F$encoded")
        return response.bodyAsText()
    }

    private suspend fun queuePrompt(promptRequest: PromptRequest): String {
        val prompt = Json.encodeToString(promptRequest)

        Log.d("FluxAPI", "Sending prompt: $prompt")
        val response = client.post("$serverAddress/prompt") {
            contentType(ContentType.Application.Json)
            setBody(prompt)
        }
        val responseBody = response.bodyAsText()
        Log.d("FluxAPI", "Received response: $responseBody")
        val jsonResponse = decodeBody<QueuePromptResponse>(responseBody, "/prompt", response.status.value)
        return jsonResponse.prompt_id
    }

    private val _progressUiState = MutableStateFlow(ProgressGenerationUIState())
    val progressUiState: StateFlow<ProgressGenerationUIState> = _progressUiState.asStateFlow()

    private fun handleProgressMessage(message: WSMessage.ProgressMessage): ProgressGenerationUIState {
        return _progressUiState.value.copy(
            progress = message.value,
            maxProgress = message.max,
        )
    }

    private fun handleStatusMessage(message: WSMessage.StatusMessage): ProgressGenerationUIState {
        return _progressUiState.value.copy(
            queueRemaining = message.status.execInfo?.queueRemaining ?: 0,
            statusMessage = "Status updated"
        )
    }

    private fun handleExecutedMessage(message: WSMessage.ExecutedMessage): ProgressGenerationUIState {

        val images = message.output.images.map {
            getImage(
                filename = it.filename,
                subfolder = it.subfolder,
                type = it.type
            )
        }



        return _progressUiState.value.copy(
            generatedImages = images,
            statusMessage = "Image generated by ${message.node}",
        )
    }

    private fun handlePromptMessage(message: WSMessage.PromptMessage): ProgressGenerationUIState {
        return _progressUiState.value.copy(
            promptId = message.promptId,
            error = message.nodeErrors.map { "${it.key}: ${it.value}" }.joinToString(", ")
        )
    }

    private fun handleExecutionSuccessMessage(message: WSMessage.ExecutionSuccessMessage): ProgressGenerationUIState {
        return _progressUiState.value.copy(
            statusMessage = "Execution completed at ${message.timestamp}",
            executing = false,
            remainingNodes = emptyList()
        )
    }

    private fun handleExecutionCachedMessage(message: WSMessage.ExecutionCachedMessage): ProgressGenerationUIState {
        val nodes = _progressUiState.value.remainingNodes.toMutableList().apply {
            removeAll(message.nodes)
        }
        return _progressUiState.value.copy(
            statusMessage = "Execution cached at ${message.timestamp}",
            remainingNodes = nodes
        )
    }

    private fun handleExecutingMessage(message: WSMessage.ExecutingMessage): ProgressGenerationUIState {

        val notes = _progressUiState.value.remainingNodes.toMutableList().apply {
            remove(message.node)
        }

        return _progressUiState.value.copy(
            statusMessage = "Executing on node ${message.node}",
            remainingNodes = notes
        )
    }

    suspend fun generateImage(prompt: PromptRequest) {
        val promptId = queuePrompt(prompt)
        Log.d("FluxAPI", "Requested prompt: $promptId")
        _progressUiState.value = ProgressGenerationUIState(
            executing = true,
            promptId = promptId,
            allNodes = prompt.prompt.keys.toList(),
            remainingNodes = prompt.prompt.keys.toList(),
        )

        val (address, port) = extractAddressAndPort(serverAddress)
        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                wsClient.webSocket(
                    method = HttpMethod.Get,
                    host = address,
                    port = port,
                    path = "/ws?clientId=$clientId"
                ) {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val messageText = frame.readText()
                        Log.d("FluxAPI", "Received websocket raw: $messageText")

                        val wsMessage = WSMessage.fromJson(messageText)
                        Log.d("FluxAPI", "Received ws message: $wsMessage")

                        // Ignore events belonging to prompts owned by other clients.
                        if (wsMessage.promptId != null && wsMessage.promptId != promptId) continue

                        if (handleWsMessage(wsMessage)) break
                    }
                }
            }
        } catch (e: Exception) {
            val message = when (e) {
                is TimeoutCancellationException ->
                    "Generation timed out after ${GENERATION_TIMEOUT_MS / 60_000} minutes"
                else -> e.message ?: "Connection to the server was lost"
            }
            logStore.error("client", "generation websocket: $message")
            _progressUiState.update {
                it.copy(
                    executing = false,
                    error = message,
                )
            }
            throw e
        }
    }

    /** Report an app-side failure that aborted a generation (queue/conversion errors). */
    fun setGenerationError(message: String) {
        _progressUiState.update { it.copy(executing = false, error = message) }
    }

    // Applies one websocket event to the progress state; returns true when the
    // event terminates the execution of our prompt.
    private fun handleWsMessage(message: WSMessage): Boolean {
        when (message) {
            is WSMessage.PromptMessage -> {
                _progressUiState.value = handlePromptMessage(message)
                if (message.nodeErrors.isNotEmpty()) {
                    logStore.error(
                        "server",
                        "prompt rejected: " + message.nodeErrors.entries.joinToString("; ") { "${it.key}: ${it.value}" }
                    )
                }
            }
            is WSMessage.StatusMessage -> _progressUiState.value = handleStatusMessage(message)
            is WSMessage.ExecutionStartMessage -> _progressUiState.update {
                it.copy(statusMessage = "Execution started")
            }
            is WSMessage.ExecutionCachedMessage -> _progressUiState.value =
                handleExecutionCachedMessage(message)
            is WSMessage.ExecutingMessage -> _progressUiState.value = handleExecutingMessage(message)
            is WSMessage.ProgressMessage -> _progressUiState.value = handleProgressMessage(message)
            is WSMessage.ExecutedMessage -> _progressUiState.value = handleExecutedMessage(message)
            is WSMessage.ExecutionSuccessMessage -> _progressUiState.value =
                handleExecutionSuccessMessage(message)
            is WSMessage.ExecutionErrorMessage -> {
                val error = buildString {
                    append(message.exceptionType ?: "ExecutionError")
                    append(": ")
                    append(message.exceptionMessage ?: "unknown error")
                    message.nodeType?.let { type -> append(" (node: $type)") }
                }
                logStore.error("server", "execution failed: $error")
                _progressUiState.update {
                    it.copy(
                        executing = false,
                        statusMessage = "Execution failed",
                        error = error,
                    )
                }
            }
            is WSMessage.ExecutionInterruptedMessage -> {
                logStore.warn("server", "execution interrupted")
                _progressUiState.update {
                    it.copy(executing = false, statusMessage = "Execution interrupted")
                }
            }
            is WSMessage.Unknown -> {
                logStore.info("server", "unhandled server event: ${message.type}")
                Log.w("FluxAPI", "Ignoring unknown ws message: ${message.type}")
            }
        }
        return message is WSMessage.ExecutionSuccessMessage ||
                message is WSMessage.ExecutionErrorMessage ||
                message is WSMessage.ExecutionInterruptedMessage
    }

    suspend fun checkSystemStats(): SystemStats {
        Log.d("FluxAPI", "call checkSystemStats")
        val response = client.get("$serverAddress/system_stats") {
            contentType(ContentType.Application.Json)
        }
        val responseBody = response.bodyAsText()
        Log.d("FluxAPI", "Received response: $responseBody")
        return decodeBody<SystemStats>(responseBody, "/system_stats", response.status.value)
    }

    suspend fun fetchHistory(): List<HistoryItem> {
        return withContext(Dispatchers.IO) {
            val response = client.get("$serverAddress/history")
            val responseBody = response.bodyAsText()

            Log.d("FluxAPI", "Received fetchHistory responseBody: $responseBody")
            val history = decodeBody<Map<String, HistoryResponse>>(responseBody, "/history", response.status.value)
            Log.d("FluxAPI", "call fetchHistory history: $history")

            history.values.map { it.normalize() }
        }
    }

    suspend fun fetchQueue(): Queue {
        return withContext(Dispatchers.IO) {
            val response = client.get("$serverAddress/queue")
            val responseBody = response.bodyAsText()

            Log.d("FluxAPI", "Received fetchQueue responseBody: $responseBody")
            val queue = decodeBody<QueueResponse>(responseBody, "/queue", response.status.value)
            Log.d("FluxAPI", "call fetchQueue queue: $queue")

            queue.normalize()
        }
    }

    private fun QueueResponse.normalize(): Queue {
        Log.d("FluxAPI", "call QueueResponse normalize: $this")

        return Queue(
            running = this.queue_running.map { it.toQueueItem() },
            pending = this.queue_pending.map { it.toQueueItem() }
        )
    }

    private fun JsonElement.toQueueItem(): Queue.Item {

        val workflow = ComfyJson.decodeFromString<Workflow>(this.jsonArray[2].toString())

        val prompt = workflow.getPrompt()
        val id = this.jsonArray[1].toString().replace("\"", "")

        Log.d("FluxAPI", "call toQueueItem: $this")


        return Queue.Item(
            prompt = prompt,
            id = id
        )
    }

    suspend fun deleteHistory(id: String) {
        val deleteBody = buildJsonObject {
            putJsonArray("delete") {
                add(id)
            }
        }.toString()

        return withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/history") {
                contentType(ContentType.Application.Json)
                setBody(deleteBody)
            }
            val responseBody = response.bodyAsText()

            Log.d("FluxAPI", "Received deleteHistory: $deleteBody")
            Log.d("FluxAPI", "Received delete history: $responseBody")
        }
    }


    suspend fun cancelWorkflow(id: String) {
        val deleteBody = buildJsonObject {
            putJsonArray("delete") {
                add(id)
            }
        }.toString()

        return withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/queue") {
                contentType(ContentType.Application.Json)
                setBody(deleteBody)
            }
            val responseBody = response.bodyAsText()

            Log.d("FluxAPI", "Received deleteBody: $deleteBody")

            Log.d("FluxAPI", "Received delete workflow: $responseBody")
        }
    }


    private fun HistoryResponse.normalize(): HistoryItem {

        val prompt = ComfyJson.decodeFromString<Workflow>(this.prompt[2].toString())

        return HistoryItem(
            images = this.outputs.values.flatMap {
                it.images.filter { it.type == "output" }
                    .map { getImage(it.filename, it.subfolder, it.type) }
            },
            success = this.status.status_str == "success",
            completed = this.status.completed,
            prompt = prompt,
            id = this.prompt[1].toString().cleanHistoryPrompt()
        )
    }

    suspend fun clearQueue() {
        val clearBody = buildJsonObject {
            put("clear", JsonPrimitive(true))
        }.toString()

        return withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/queue") {
                contentType(ContentType.Application.Json)
                setBody(clearBody)
            }
            val responseBody = response.bodyAsText()

            Log.d("FluxAPI", "Received clear queue: $responseBody")
        }
    }

    suspend fun interrupt() {
        withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/interrupt")
            val responseBody = response.bodyAsText()
            Log.d("FluxAPI", "Received interrupt: $responseBody")
        }
    }

    private fun getImage(filename: String, subfolder: String, type: String): String {
        return "$serverAddress/view?filename=$filename&subfolder=$subfolder&type=$type"
    }

}

fun String.cleanHistoryPrompt(): String {
    return this.trim('"').trim('\\').removePrefix("\"")
}

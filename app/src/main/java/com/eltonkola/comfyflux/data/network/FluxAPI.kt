package com.eltonkola.comfyflux.data.network

import android.net.Uri
import android.util.Log
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.data.exceptionChain
import com.eltonkola.comfyflux.data.model.PromptSettlement
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
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.Headers
import io.ktor.http.isSuccess
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
    val prompt_id: String? = null,
    val number: Int? = null,
    // ComfyUI nests real errors here ({node: {errors: [...], valid: false}})
    // and adds a top-level "error" object on rejection; keep both unparsed.
    val node_errors: JsonElement? = null,
    val error: JsonElement? = null,
)

/**
 * Human-readable reason from a /prompt rejection body: the top-level message
 * plus per-node validation errors ComfyUI nests under node_errors.
 */
fun serverPromptErrorText(body: String): String {
    val root = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
    }.getOrNull() ?: return body.take(400)
    val parts = ArrayList<String>()
    (root["error"] as? JsonObject)?.let { err ->
        val msg = err["message"]?.jsonPrimitive?.contentOrNull
        val details = err["details"]?.jsonPrimitive?.contentOrNull
        if (!msg.isNullOrEmpty()) parts += msg + if (!details.isNullOrEmpty()) " ($details)" else ""
    }
    (root["node_errors"] as? JsonObject)?.forEach { (node, value) ->
        val errors = (value as? JsonObject)?.get("errors") as? JsonArray ?: return@forEach
        errors.forEach { e ->
            val obj = e as? JsonObject ?: return@forEach
            val msg = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val details = obj["details"]?.jsonPrimitive?.contentOrNull.orEmpty()
            parts += "node $node: $msg${if (details.isNotEmpty()) " ($details)" else ""}"
        }
    }
    return parts.joinToString("; ").ifEmpty { body.take(400) }
}

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
        "$endpoint returned invalid JSON after ${body.length} chars (HTTP $status): ${body.take(300)}"
    }

/**
 * Userdata file route for a workflow name. The listing strips the ".json"
 * suffix; aiohttp only serves the exact filename, so put it back.
 * %2F keeps the whole "workflows/<file>" as the route's single {dir+file}
 * segment, and ktor's UrlBuilder leaves the escaped slash alone.
 */
fun workflowContentPath(name: String): String {
    val safe = name.substringAfterLast('/')
    val file = if (safe.endsWith(".json")) safe else "$safe.json"
    return "userdata/workflows%2F" + java.net.URLEncoder.encode(file, "UTF-8").replace("+", "%20")
}

/** Like [workflowContentPath] but for the POST that saves a workflow. */
fun workflowWritePath(name: String): String = workflowContentPath(name)

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

    /** Times every server call; failures always land in the log, traces only when verbose. */
    private suspend fun <T> traced(endpoint: String, block: suspend () -> T): T {
        val started = System.currentTimeMillis()
        try {
            val result = block()
            logStore.debug("client", "$endpoint ok in ${System.currentTimeMillis() - started} ms")
            return result
        } catch (e: Exception) {
            logStore.error("client", "$endpoint failed in ${System.currentTimeMillis() - started} ms: ${e.javaClass.simpleName}: ${e.message}")
            logStore.debug("client", exceptionChain(e))
            throw e
        }
    }

    suspend fun fetchObjectInfo(): JsonObject = traced("/object_info") {
        objectInfoCache?.let {
            logStore.debug("client", "/object_info from cache (${it.size} nodes)")
            return@traced it
        }
        val response = client.get("$serverAddress/object_info")
        val body = response.bodyAsText()
        logStore.debug("client", "/object_info HTTP ${response.status.value}, ${body.length} chars")
        val info = decodeBody<JsonElement>(body, "/object_info", response.status.value).jsonObject
        objectInfoCache = info
        info
    }

    /** Workflow names saved in the ComfyUI web UI (server user data). */
    suspend fun fetchWorkflowNames(): List<String> = traced("/userdata?dir=workflows") {
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
        names.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.sorted()
    }

    suspend fun fetchWorkflowContent(name: String): String = traced("/userdata/workflows/$name") {
        val response = client.get("$serverAddress/${workflowContentPath(name)}")
        val body = response.bodyAsText()
        logStore.debug("client", "/userdata/workflows/$name HTTP ${response.status.value}, ${body.length} chars")
        if (body.isBlank()) {
            throw Exception("Workflow '$name' came back empty (HTTP ${response.status.value}) - the file is missing on the server or ComfyUI is restarting")
        }
        body
    }

    private suspend fun queuePrompt(promptRequest: PromptRequest): String = traced("POST /prompt") {
        val prompt = Json.encodeToString(promptRequest)

        logStore.debug("client", "POST /prompt body ${prompt.length} chars, ${promptRequest.prompt.size} nodes")
        logStore.trace("client", "POST /prompt full request: $prompt")
        val response = client.post("$serverAddress/prompt") {
            contentType(ContentType.Application.Json)
            setBody(prompt)
        }
        val responseBody = response.bodyAsText()
        val jsonResponse = decodeBody<QueuePromptResponse>(responseBody, "/prompt", response.status.value)
        val promptId = jsonResponse.prompt_id
        if (promptId.isNullOrEmpty()) {
            throw Exception("prompt rejected: ${serverPromptErrorText(responseBody)}")
        }
        logStore.debug("client", "queued as $promptId")
        promptId
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

        // Only saved outputs. Preview-style nodes (PreviewImage /
        // PreviewAny / ImageCompare's compare view) emit temp images in the
        // same event: showing them put the workflow's INPUT reference (and
        // the compare thumbnails) in the result gallery, which reads as
        // "it did nothing". History normalisation filters the same way.
        val images = message.output.images.filter { it.type == "output" }.map {
            getImage(
                filename = it.filename,
                subfolder = it.subfolder,
                type = it.type
            )
        }

        // Intermediate nodes finish with no images: keep the gallery intact.
        if (images.isEmpty()) {
            return _progressUiState.value.copy(
                statusMessage = "Completed node ${message.node}",
            )
        }

        return _progressUiState.value.copy(
            // Multiple queued jobs stream into one grid.
            generatedImages = _progressUiState.value.generatedImages + images,
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

    // Jobs this app queued that have not settled. A second Create tap joins
    // the running websocket session instead of opening a parallel one.
    private val settlement = PromptSettlement()
    private var sessionActive = false

    suspend fun generateImage(prompt: PromptRequest) {
        val promptId = queuePrompt(prompt)

        var ownsSession: Boolean
        synchronized(settlement) {
            ownsSession = !sessionActive
            if (ownsSession) sessionActive = true
            settlement.add(promptId)
        }
        if (!ownsSession) {
            // A session is already streaming: the server runs our job right
            // after the current one; progress keeps updating in place.
            _progressUiState.update {
                it.copy(
                    promptId = promptId,
                    queueRemaining = it.queueRemaining + 1,
                    statusMessage = "Queued behind the running job"
                )
            }
            logStore.info("client", "queued prompt ${promptId.take(8)} behind the running generation")
            return
        }

        _progressUiState.value = ProgressGenerationUIState(
            executing = true,
            promptId = promptId,
            allNodes = prompt.prompt.keys.toList(),
            remainingNodes = prompt.prompt.keys.toList(),
        )

        try {
            runGenerationSession()
        } finally {
            synchronized(settlement) { sessionActive = false }
        }
    }

    private suspend fun runGenerationSession() {
        val (address, port) = extractAddressAndPort(serverAddress)
        try {
            logStore.debug("client", "connecting websocket /ws?clientId=$clientId")
            wsClient.webSocket(
                method = HttpMethod.Get,
                host = address,
                port = port,
                path = "/ws?clientId=$clientId"
            ) {
                logStore.debug("client", "websocket connected")
                // The timeout now guards each incoming message, not the whole
                // session: every job resets it (active jobs always produce
                // progress frames), so a long queue is fine but a stuck job
                // still aborts.
                while (true) {
                    val frameResult = try {
                        withTimeout(GENERATION_TIMEOUT_MS) { incoming.receiveCatching() }
                    } catch (e: TimeoutCancellationException) {
                        logStore.error("client", "generation websocket: no server message for ${GENERATION_TIMEOUT_MS / 60_000} minutes")
                        throw e
                    }
                    if (frameResult.isClosed) {
                        if (!settlement.isEmpty()) {
                            logStore.error("client", "websocket closed with jobs still queued")
                            throw Exception("Connection to the server was lost while jobs were still queued")
                        }
                        break
                    }
                    val frame = frameResult.getOrNull() ?: continue
                    if (frame !is Frame.Text) continue
                    val messageText = frame.readText()

                    val wsMessage = WSMessage.fromJson(messageText)
                    logStore.debug(
                        "client",
                        "ws event: ${wsMessage.javaClass.simpleName}" +
                                (wsMessage.promptId?.let { " prompt=${it.take(8)}" } ?: "")
                    )

                    // Ignore events belonging to prompts owned by other clients.
                    val origin = wsMessage.promptId
                    if (origin != null && !settlement.contains(origin)) continue

                    if (handleWsMessage(wsMessage)) break
                }
            }
        } catch (e: Exception) {
            val message = when (e) {
                is TimeoutCancellationException ->
                    "Generation stalled: the server sent nothing for ${GENERATION_TIMEOUT_MS / 60_000} minutes"
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

    // Applies one websocket event to the progress state; returns true when
    // the event settled the LAST job this app queued - the session can close.
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
                it.copy(
                    promptId = message.promptId,
                    statusMessage = "Execution started",
                    // Fresh job: don't leave an earlier failure hanging on
                    // the screen while the queue keeps running.
                    error = ""
                )
            }
            is WSMessage.ExecutionCachedMessage -> _progressUiState.value =
                handleExecutionCachedMessage(message)
            is WSMessage.ExecutingMessage -> _progressUiState.value = handleExecutingMessage(message)
            is WSMessage.ProgressMessage -> _progressUiState.value = handleProgressMessage(message)
            is WSMessage.ExecutedMessage -> _progressUiState.value = handleExecutedMessage(message)
            is WSMessage.ExecutionSuccessMessage -> {
                val moreJobs = !settlement.settle(message.promptId)
                _progressUiState.value = handleExecutionSuccessMessage(message).let {
                    if (moreJobs) it.copy(executing = true) else it
                }
                if (moreJobs) {
                    logStore.info("client", "job settled; ${settlement.count()} queued job(s) continue")
                }
            }
            is WSMessage.ExecutionErrorMessage -> {
                val moreJobs = !settlement.settle(message.promptId)
                val error = buildString {
                    append(message.exceptionType ?: "ExecutionError")
                    append(": ")
                    append(message.exceptionMessage ?: "unknown error")
                    message.nodeType?.let { type -> append(" (node: $type)") }
                }
                logStore.error("server", "execution failed: $error")
                _progressUiState.update {
                    it.copy(
                        executing = moreJobs,
                        statusMessage = if (moreJobs) "Execution failed (queued jobs continue)" else "Execution failed",
                        error = error,
                    )
                }
            }
            is WSMessage.ExecutionInterruptedMessage -> {
                val moreJobs = !settlement.settle(message.promptId)
                logStore.warn("server", "execution interrupted")
                _progressUiState.update {
                    it.copy(
                        executing = moreJobs,
                        statusMessage = if (moreJobs) "Execution interrupted (queued jobs continue)" else "Execution interrupted"
                    )
                }
            }
            is WSMessage.Unknown -> {
                logStore.info("server", "unhandled server event: ${message.type}")
                Log.w("FluxAPI", "Ignoring unknown ws message: ${message.type}")
            }
        }
        val settledAll = message is WSMessage.ExecutionSuccessMessage ||
                message is WSMessage.ExecutionErrorMessage ||
                message is WSMessage.ExecutionInterruptedMessage
        return settledAll && settlement.isEmpty()
    }

    suspend fun checkSystemStats(): SystemStats = traced("/system_stats") {
        val response = client.get("$serverAddress/system_stats") {
            contentType(ContentType.Application.Json)
        }
        val responseBody = response.bodyAsText()
        decodeBody<SystemStats>(responseBody, "/system_stats", response.status.value)
    }

    suspend fun fetchHistory(): List<HistoryItem> = traced("/history") {
        withContext(Dispatchers.IO) {
            val response = client.get("$serverAddress/history")
            val responseBody = response.bodyAsText()

            val history = decodeBody<Map<String, HistoryResponse>>(responseBody, "/history", response.status.value)

            history.values.mapNotNull { it.normalizeOrNull() }
        }
    }

    suspend fun fetchQueue(): Queue = traced("/queue") {
        withContext(Dispatchers.IO) {
            val response = client.get("$serverAddress/queue")
            val responseBody = response.bodyAsText()

            val queue = decodeBody<QueueResponse>(responseBody, "/queue", response.status.value)

            queue.normalize()
        }
    }

    private fun QueueResponse.normalize(): Queue {

        return Queue(
            running = this.queue_running.mapNotNull { it.toQueueItem() },
            pending = this.queue_pending.mapNotNull { it.toQueueItem() }
        )
    }

    private fun JsonElement.toQueueItem(): Queue.Item? {
        // Same positional-array guard as history normalisation: one odd
        // entry must not take down the whole queue view.
        val arr = this as? JsonArray ?: return null
        val id = (arr.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: return null
        val promptJson = arr.getOrNull(2)?.toString() ?: return null
        val workflow = runCatching { ComfyJson.decodeFromString<Workflow>(promptJson) }.getOrNull()
            ?: return null
        val prompt = workflow.getPrompt()



        return Queue.Item(
            prompt = prompt,
            id = id
        )
    }

    suspend fun deleteHistory(id: String) = traced("POST /history delete") {
        val deleteBody = buildJsonObject {
            putJsonArray("delete") {
                add(id)
            }
        }.toString()

        withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/history") {
                contentType(ContentType.Application.Json)
                setBody(deleteBody)
            }
            val responseBody = response.bodyAsText()

            Unit
        }
    }


    suspend fun cancelWorkflow(id: String) = traced("POST /queue delete") {
        val deleteBody = buildJsonObject {
            putJsonArray("delete") {
                add(id)
            }
        }.toString()

        withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/queue") {
                contentType(ContentType.Application.Json)
                setBody(deleteBody)
            }
            val responseBody = response.bodyAsText()


            Unit
        }
    }


    private fun HistoryResponse.normalizeOrNull(): HistoryItem? {
        // Positional LiteGraph arrays: [prompt_id?, prompt, extra]. A shape
        // change on the server must drop THIS entry, not kill the whole
        // history list with one IndexOutOfBounds.
        val promptJson = this.prompt.getOrNull(2)?.toString() ?: return null
        val prompt = runCatching { ComfyJson.decodeFromString<Workflow>(promptJson) }.getOrNull()
            ?: return null

        return HistoryItem(
            images = this.outputs.values.flatMap {
                it.images.filter { it.type == "output" }
                    .map { getImage(it.filename, it.subfolder, it.type) }
            },
            success = this.status.status_str == "success",
            completed = this.status.completed,
            prompt = prompt,
            id = this.prompt.getOrNull(1).toString().cleanHistoryPrompt()
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

        }
    }

    suspend fun interrupt() {
        withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/interrupt")
            val responseBody = response.bodyAsText()
        }
    }

    @Serializable
    private data class UploadImageResponse(
        val name: String = "",
        val subfolder: String = "",
        val type: String = ""
    )

    /**
     * Upload a photo into the server's input folder (POST /upload/image,
     * multipart) and return the name the graph's LoadImage node needs.
     * Throws with the server's reason when it refuses.
     */
    suspend fun uploadImage(fileName: String, bytes: ByteArray, mimeType: String): String =
        withContext(Dispatchers.IO) {
            logStore.debug("client", "POST /upload/image $fileName (${bytes.size} bytes, $mimeType)")
            val parts = formData {
                append(
                    "image",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    }
                )
                append("overwrite", "true")
                append("type", "input")
            }
            val response = client.submitFormWithBinaryData(
                url = "$serverAddress/upload/image",
                formData = parts
            )
            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw Exception("Image upload rejected: HTTP ${response.status.value} ${responseBody.take(200)}")
            }
            val uploaded = decodeBody<UploadImageResponse>(responseBody, "/upload/image", response.status.value)
            if (uploaded.name.isBlank()) throw Exception("Image upload returned no filename")
            logStore.info("client", "uploaded $fileName as '${uploaded.name}' (subfolder '${uploaded.subfolder}')")
            if (uploaded.subfolder.isNotBlank()) "${uploaded.subfolder}/${uploaded.name}" else uploaded.name
        }

    /**
     * Write a workflow file into the server's saved-workflow folder
     * (POST /userdata/workflows/<name>, raw body - ComfyUI's own save
     * path). The file shows up in the web UI and our Server Workflows list.
     * The slash after "workflows" must stay URL-encoded: the server's
     * route is /userdata/{file} and aiohttp's {file} never matches a bare
     * '/'. (A plain slash reaches the /userdata route instead ->
     * 405 Method Not Allowed.)
     */
    suspend fun saveUserWorkflow(fileName: String, content: String): String =
        withContext(Dispatchers.IO) {
            val response = client.post("$serverAddress/${workflowWritePath(fileName)}") {
                contentType(ContentType.Application.Json)
                setBody(content)
            }
            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw Exception("Saving the workflow failed: HTTP ${response.status.value} ${responseBody.take(200)}")
            }
            logStore.info("client", "saved server workflow '$fileName'")
            responseBody
        }

    private fun getImage(filename: String, subfolder: String, type: String): String {
        return "$serverAddress/view?filename=$filename&subfolder=$subfolder&type=$type"
    }

}

fun String.cleanHistoryPrompt(): String {
    return this.trim('"').trim('\\').removePrefix("\"")
}

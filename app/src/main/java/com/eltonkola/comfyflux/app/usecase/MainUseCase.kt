package com.eltonkola.comfyflux.app.usecase

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import com.eltonkola.comfyflux.data.AppSettings
import com.eltonkola.comfyflux.data.AppState
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.data.Notifier
import com.eltonkola.comfyflux.data.model.HistoryItem
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.PromptRequest
import com.eltonkola.comfyflux.data.model.Workflow
import com.eltonkola.comfyflux.data.model.WorkflowConverter
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.data.model.updatePrompt
import com.eltonkola.comfyflux.data.model.updateSeed
import com.eltonkola.comfyflux.data.model.updateSizeAndBatch
import com.eltonkola.comfyflux.data.network.ComfyJson
import com.eltonkola.comfyflux.data.network.ComfyServerException
import com.eltonkola.comfyflux.data.network.FluxAPI
import com.eltonkola.comfyflux.data.network.GroqAPI
import com.eltonkola.comfyflux.data.network.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

class MainUseCase(
    private val fluxAPI: FluxAPI,
    private val groqAPI: GroqAPI,
    private val appSettings: AppSettings,
    private val logStore: LogStore,
    private val applicationContext: Application,
    private val notifier: Notifier,
    private val appState: AppState
) {

    private val _uiState = MutableStateFlow(
        ImageGenerationUiState(server = appSettings.serverUrl())
    )
    val uiState: StateFlow<ImageGenerationUiState> = _uiState.asStateFlow()

    init {
        fluxAPI.setServerAddress(appSettings.serverUrl())
    }

    suspend fun updateServerUrl(url: String) {
        _uiState.update { it.copy(server = url) }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun updateWorkflow(workflow: WorkflowFile) {
        _uiState.update { it.copy(workflow = workflow) }
    }

    fun setImageWidth(width: Int) {
        _uiState.update { it.copy(width = width) }
    }

    fun setImageHeight(height: Int) {
        _uiState.update { it.copy(height = height) }
    }

    fun setBatchSize(batchSize: Int) {
        _uiState.update { it.copy(batchSize = batchSize) }
    }

    fun setSeed(seed: Long, isRandom: Boolean) {
        _uiState.update { it.copy(seed = seed, isRandom = isRandom) }
    }

    /** A1111-style reroll: keep everything, bump the seed by one, go deterministic. */
    fun incrementSeed() {
        _uiState.update { it.copy(seed = it.seed + 1, isRandom = false) }
    }

    suspend fun enrichPrompt() {
        if (uiState.value.prompt.isBlank()) {
            _uiState.update { it.copy(promptEnrichError = "Write a short prompt first, then tap the magic wand to expand it.") }
            return
        }
        if (!groqAPI.hasUsableKey()) {
            _uiState.update {
                it.copy(promptEnrichError = "Prompt enrichment uses the Groq AI service. Add a free API key from console.groq.com in Settings to enable it.")
            }
            return
        }
        _uiState.update { it.copy(promptEnrichError = null, enrichingPrompt = true) }
        try {
            val richPrompt = groqAPI.enrichPrompt(uiState.value.prompt)
            _uiState.update { it.copy(prompt = richPrompt, enrichingPrompt = false) }
        } catch (e: Exception) {
            logStore.error("client", "prompt enrichment failed: ${e.message}")
            _uiState.update {
                it.copy(enrichingPrompt = false, promptEnrichError = "Prompt enrichment failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun randomSeed() {
        _uiState.update { it.copy(seed = generateRandom16DigitNumber()) }
    }

    suspend fun checkStatus() {
        val url = normalizeServerUrl(uiState.value.server)
        _uiState.update { it.copy(loadingStats = true, server = url) }
        fluxAPI.setServerAddress(url)
        runCatching { fluxAPI.checkSystemStats() }
            .onSuccess { stats ->
                // Only remember URLs that actually answer.
                appSettings.setServerUrl(url)
                _uiState.update { it.copy(loadingStats = false, stats = stats, serverError = null) }
                logStore.info("server", "connected to $url (${stats.system.os}, python ${stats.system.python_version})")
                refreshServerWorkflows()
            }
            .onFailure { e ->
                val friendly = friendlyConnectionError(e)
                logStore.error("client", "connection to $url failed: $friendly [${e.javaClass.simpleName}: ${e.message}]")
                _uiState.update {
                    it.copy(loadingStats = false, stats = null, serverError = friendly)
                }
            }
    }

    /** Workflows saved in the ComfyUI web UI; a listing failure must not break the connection status. */
    private suspend fun refreshServerWorkflows() {
        runCatching { fluxAPI.fetchWorkflowNames() }
            .onFailure { e -> logStore.warn("server", "workflow listing failed: ${e.message}") }
            .onSuccess { names ->
                _uiState.update { state ->
                    state.copy(
                        serverWorkflows = names.map {
                            WorkflowFile(it, "Saved from the ComfyUI web UI", "server://$it", isAsset = false, isServer = true)
                        }
                    )
                }
            }
    }

    private fun friendlyConnectionError(e: Throwable): String = when (e) {
        is UnknownHostException ->
            "Hostname not found. Use the LAN address of the machine running ComfyUI (e.g. http://192.168.1.5:8188) and make sure the phone is on the same Wi-Fi."
        is ConnectException ->
            "Connection refused. Is ComfyUI started with --listen, and is the port correct (default 8188)?"
        is SocketTimeoutException, is TimeoutCancellationException ->
            "The server did not answer in time."
        is SerializationException ->
            "Server responded, but with unexpected data. Please report (new ComfyUI version?)."
        is IllegalArgumentException ->
            "Invalid URL. Use the format http://<ip>:8188."
        else -> "${e.javaClass.simpleName}: ${e.message ?: "connection failed"}"
    }


    private suspend fun loadWorkflow(): PromptRequest {
        val seed = if (uiState.value.isRandom) {
            val newSeed = generateRandom16DigitNumber()
            _uiState.update { it.copy(seed = newSeed) }
            newSeed
        } else {
            uiState.value.seed
        }

        val workflowConfig = _uiState.value.workflow
        val workflow: Workflow = if (workflowConfig.isServer) {
            val raw = fluxAPI.fetchWorkflowContent(workflowConfig.name)
            val element = ComfyJson.parseToJsonElement(raw)
            if (element is JsonObject && element.containsKey("nodes")) {
                WorkflowConverter(fluxAPI.fetchObjectInfo()).convert(element)
            } else {
                // Already API format (e.g. saved via "Save (Export)").
                ComfyJson.decodeFromString(element.toString())
            }
        } else {
            val workflowInput = applicationContext.getWorkflowAsText(workflowConfig)
                ?: throw Exception("Error reading workflow!")
            Json.decodeFromString<Workflow>(workflowInput)
        }
        workflow.updateSizeAndBatch(
            width = uiState.value.width,
            height = uiState.value.height,
            batchSize = uiState.value.batchSize
        )
        workflow.updatePrompt(prompt = uiState.value.prompt.cleanPrompt())
        workflow.updateSeed(seed = seed)
        return PromptRequest(fluxAPI.clientId, workflow)
    }

    suspend fun generateImages(start: () -> Unit, stop: () -> Unit) {
        if (uiState.value.prompt.isBlank()) {
            return
        }
        start()
        try {
            fluxAPI.setServerAddress(normalizeServerUrl(uiState.value.server))
            val prompt = loadWorkflow()
            Log.d("FluxApp", "prompt: $prompt")
            withContext(Dispatchers.IO) {
                fluxAPI.generateImage(prompt)
            }
            checkStatus()
            notifyGenerationOutcome()
        } catch (e: Exception) {
            reportGenerationFailure(e, "generate (${uiState.value.workflow.name})")
        }
        stop()
    }

    /** Re-queue a past generation exactly as it ran (server history keeps the full prompt). */
    suspend fun rerunImages(item: HistoryItem, start: () -> Unit, stop: () -> Unit) {
        start()
        try {
            fluxAPI.setServerAddress(normalizeServerUrl(uiState.value.server))
            logStore.info("client", "re-running history prompt ${item.id}")
            withContext(Dispatchers.IO) {
                fluxAPI.generateImage(PromptRequest(fluxAPI.clientId, item.prompt))
            }
            checkStatus()
            notifyGenerationOutcome()
        } catch (e: Exception) {
            reportGenerationFailure(e, "rerun (history ${item.id})")
        }
        stop()
    }

    /** Removes every pending job; the running generation is left alone (use interrupt for that). */
    suspend fun clearQueue() {
        try {
            val pending = runCatching { fluxAPI.fetchQueue() }.getOrNull()?.pending?.size ?: 0
            fluxAPI.clearQueue()
            logStore.info("client", "cleared $pending pending queue jobs")
        } catch (e: Exception) {
            logStore.error("client", "clearing queue failed: ${e.message}")
        }
    }

    /**
     * Notify about a finished run. generateImage() returns normally even when
     * the server reported an execution_error over the websocket, so the
     * progress state's error field - not the exception flow - decides success.
     */
    private fun notifyGenerationOutcome() {
        val error = fluxAPI.progressUiState.value.error
        if (error.isNotEmpty()) {
            notifier.generationFinished(appState.foreground, success = false, detail = error)
        } else {
            notifier.generationFinished(appState.foreground, success = true, detail = null)
        }
    }

    private fun reportGenerationFailure(e: Exception, label: String) {
        val message = when (e) {
            is TimeoutCancellationException ->
                "Generation timed out after ${15} minutes"
            else -> e.message ?: "generation failed"
        }
        val source = if (e is ComfyServerException) "server" else "client"
        logStore.error(source, "$label: $message")
        fluxAPI.setGenerationError(message)
        notifier.generationFinished(appState.foreground, success = false, detail = message)
        e.printStackTrace()
    }


    private fun String.cleanPrompt(): String {
        // Whitespace only: kotlinx-serialization escapes quotes/backslashes
        // itself, hand-escaping here would double-escape the text.
        return this
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
    }
}


fun Context.getWorkflowAsText(workflow: WorkflowFile): String? {
    return try {
        if (workflow.isAsset) {
            // Load from assets
            val fileName = workflow.workflowUri.removePrefix("file:///android_asset/")
            assets.open(fileName).bufferedReader().use { it.readText() }
        } else {
            Log.d("ComfyFlux", "reading file: ${workflow.workflowUri}")
            val uri = Uri.parse(workflow.workflowUri)
            contentResolver.openInputStream(uri)?.bufferedReader().use {
                it?.readText()
            }

        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun generateRandom16DigitNumber(): Long {
    val random = Random.nextLong(100000000000000, 999999999999999)
    return random
}
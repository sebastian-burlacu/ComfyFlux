package com.eltonkola.comfyflux.app.usecase

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import com.eltonkola.comfyflux.data.AppSettings
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.PromptRequest
import com.eltonkola.comfyflux.data.model.Workflow
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.data.model.updatePrompt
import com.eltonkola.comfyflux.data.model.updateSeed
import com.eltonkola.comfyflux.data.model.updateSizeAndBatch
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

class MainUseCase(
    private val fluxAPI: FluxAPI,
    private val groqAPI: GroqAPI,
    private val appSettings: AppSettings,
    private val applicationContext: Application
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

    suspend fun enrichPrompt() {
        try {
            val richPrompt = groqAPI.enrichPrompt(uiState.value.prompt)
            _uiState.update { it.copy(prompt = richPrompt) }
        } catch (e: Exception) {
            e.printStackTrace()
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
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(loadingStats = false, stats = null, serverError = friendlyConnectionError(e))
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
        is IllegalArgumentException ->
            "Invalid URL. Use the format http://<ip>:8188."
        else -> "${e.javaClass.simpleName}: ${e.message ?: "connection failed"}"
    }


    private fun loadWorkflow(): PromptRequest {
        val seed = if (uiState.value.isRandom) {
            val newSeed = generateRandom16DigitNumber()
            _uiState.update { it.copy(seed = newSeed) }
            newSeed
        } else {
            uiState.value.seed
        }

        val workflowConfig = _uiState.value.workflow
        val workflowInput = applicationContext.getWorkflowAsText(workflowConfig) ?: throw Exception(
            "Error reading workflow!"
        )
        val workflow = Json.decodeFromString<Workflow>(workflowInput)
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stop()
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
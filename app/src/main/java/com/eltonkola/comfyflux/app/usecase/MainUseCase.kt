package com.eltonkola.comfyflux.app.usecase

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.eltonkola.comfyflux.data.AppSettings
import com.eltonkola.comfyflux.data.AppState
import com.eltonkola.comfyflux.data.GenerationDraft
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.data.Notifier
import com.eltonkola.comfyflux.data.exceptionChain
import com.eltonkola.comfyflux.data.model.HistoryItem
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.TemplateBrowserState
import com.eltonkola.comfyflux.data.model.PromptRequest
import com.eltonkola.comfyflux.data.model.Workflow
import com.eltonkola.comfyflux.data.model.WorkflowConverter
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.data.model.ImagePick
import com.eltonkola.comfyflux.data.model.applyInputImages
import com.eltonkola.comfyflux.data.model.imageInputs
import com.eltonkola.comfyflux.data.model.needs
import com.eltonkola.comfyflux.data.model.applyModelOverrides
import com.eltonkola.comfyflux.data.model.hasImageInput
import com.eltonkola.comfyflux.data.model.detectModelSlots
import com.eltonkola.comfyflux.data.model.negativeTextTargets
import com.eltonkola.comfyflux.data.model.updateNegativePrompt
import com.eltonkola.comfyflux.data.model.detectWorkflowToggles
import com.eltonkola.comfyflux.data.model.promptTextTargets
import com.eltonkola.comfyflux.data.model.updateCfgAndSteps
import com.eltonkola.comfyflux.data.model.updatePrompt
import com.eltonkola.comfyflux.data.model.updateSeed
import com.eltonkola.comfyflux.data.model.updateSizeAndBatch
import com.eltonkola.comfyflux.data.model.workflows
import com.eltonkola.comfyflux.data.network.ComfyJson
import com.eltonkola.comfyflux.data.network.TemplateCatalog
import com.eltonkola.comfyflux.data.network.TemplateEntry
import com.eltonkola.comfyflux.data.network.ComfyServerException
import com.eltonkola.comfyflux.data.network.decodeFailureReason
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/** Fit an image's aspect inside [cap] px, multiples of 32, 256 minimum. */
internal fun fittedSize(imgW: Int, imgH: Int, cap: Int = 1024): Pair<Int, Int> {
    // Nearest multiple of 32 (truncating would shrink portrait sizes by
    // one 32-block); never below 256.
    val round32: (Int) -> Int = { v -> (((v + 16) / 32) * 32).coerceAtLeast(256) }
    // Portrait: cap the height, scale the width (and vice versa).
    return if (imgH >= imgW) {
        round32(cap * imgW / imgH) to cap
    } else {
        cap to round32(cap * imgH / imgW)
    }
}

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
        restorePersistedState()
    }

    suspend fun updateServerUrl(url: String) {
        _uiState.update { it.copy(server = url) }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
        saveDraft()
    }

    fun updateWorkflow(workflow: WorkflowFile) {
        _uiState.update {
            it.copy(
                workflow = workflow,
                modelSlots = emptyList(),
                modelOverrides = emptyMap(),
                workflowToggles = emptyList(),
                toggleOverrides = emptyMap()
            )
        }
        saveDraft()
    }

    /** Flip a boolean input the workflow exposes (key = "nodeId|inputIndex"). */
    fun setWorkflowToggle(key: String, value: Boolean) {
        _uiState.update { it.copy(toggleOverrides = it.toggleOverrides + (key to value)) }
    }

    fun updateNegativePrompt(prompt: String) {
        _uiState.update { it.copy(negativePrompt = prompt) }
        saveDraft()
    }

    /** Pick a model file for one detected slot (ModelSlot.key -> filename). */
    fun setModelChoice(key: String, value: String) {
        _uiState.update { it.copy(modelOverrides = it.modelOverrides + (key to value)) }
    }

    fun setImageWidth(width: Int) {
        _uiState.update { it.copy(width = width) }
        saveDraft()
    }

    fun setImageHeight(height: Int) {
        _uiState.update { it.copy(height = height) }
        saveDraft()
    }

    fun setBatchSize(batchSize: Int) {
        _uiState.update { it.copy(batchSize = batchSize) }
        saveDraft()
    }

    fun setSeed(seed: Long, isRandom: Boolean) {
        _uiState.update { it.copy(seed = seed, isRandom = isRandom) }
        saveDraft()
    }

    /** 0 keeps the workflow's own CFG scale / step count. */
    fun setCfg(cfg: Float) {
        _uiState.update { it.copy(cfg = cfg) }
        saveDraft()
    }

    fun setSteps(steps: Int) {
        _uiState.update { it.copy(steps = steps) }
        saveDraft()
    }

    /** A1111-style reroll: keep everything, bump the seed by one, go deterministic. */
    fun incrementSeed() {
        _uiState.update { it.copy(seed = it.seed + 1, isRandom = false) }
        saveDraft()
    }

    suspend fun enrichPrompt() {
        if (uiState.value.prompt.isBlank()) {
            _uiState.update { it.copy(promptEnrichError = "Write a short prompt first, then tap the magic wand to expand it.") }
            return
        }
        if (!groqAPI.hasUsableKey()) {
            _uiState.update {
                it.copy(promptEnrichError = "Prompt enrichment needs an API key for this endpoint. Add one in Settings (Groq key at console.groq.com is free) or point the endpoint at a local Ollama server.")
            }
            return
        }
        _uiState.update { it.copy(promptEnrichError = null, enrichingPrompt = true) }
        try {
            val richPrompt = groqAPI.enrichPrompt(uiState.value.prompt)
            _uiState.update { it.copy(prompt = richPrompt, enrichingPrompt = false) }
            saveDraft()
        } catch (e: Exception) {
            logStore.error("client", "prompt enrichment failed: ${e.message}")
            logStore.debug("client", exceptionChain(e))
            _uiState.update {
                it.copy(enrichingPrompt = false, promptEnrichError = "Prompt enrichment failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun randomSeed() {
        _uiState.update { it.copy(seed = generateRandom16DigitNumber()) }
        saveDraft()
    }

    /**
     * Match the size sliders to the picked photo's aspect ratio (fit inside
     * 1024, multiples of 32). Edit workflows generate at the latent size:
     * 1024x1024 for a portrait photo crops the reference - the model can
     * only fill the latent - which reads as "it just cropped my image".
     */
    suspend fun adaptSizeToInputImage(uriString: String) {
        val uri = android.net.Uri.parse(uriString)
        val dims = withContext(Dispatchers.IO) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                applicationContext.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
            opts.outWidth to opts.outHeight
        }
        val (imgW, imgH) = dims
        if (imgW <= 0 || imgH <= 0) return
        val (w, h) = fittedSize(imgW, imgH)
        _uiState.update { it.copy(width = w, height = h) }
        saveDraft()
        logStore.info("client", "input image ${imgW}x${imgH}: size set to ${w}x${h}")
    }

    /**
     * Assign (or clear, with a blank uri) one photo for one LoadImage slot.
     * Remembered only for this app session; content permissions don't
     * survive restarts.
     */
    fun setInputImage(slotId: String, uriString: String) {
        _uiState.update { state ->
            val picks = state.imagePicks.toMutableMap()
            if (uriString.isBlank()) picks.remove(slotId) else picks[slotId] =
                ImagePick(uriString, displayName(android.net.Uri.parse(uriString)))
            state.copy(imagePicks = picks)
        }
        uploadedInputs.remove(uriString)
        logStore.info("client", if (uriString.isBlank()) "input image cleared ($slotId)"
        else "input image selected for $slotId")
    }

    /** Same photo for every LoadImage (workflows whose branches share one input). */
    fun setAllInputImages(uriString: String) {
        if (uriString.isBlank()) {
            _uiState.update { it.copy(imagePicks = emptyMap()) }
            return
        }
        val name = displayName(android.net.Uri.parse(uriString))
        _uiState.update { state ->
            state.copy(imagePicks = state.imageInputs.associate { it.id to ImagePick(uriString, name) })
        }
        logStore.info("client", "same photo set for all ${_uiState.value.imagePicks.size} image inputs")
    }

    private fun displayName(uri: android.net.Uri): String {
        return try {
            applicationContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "phone_image.jpg"
        } catch (e: Exception) {
            uri.lastPathSegment?.substringAfterLast('/') ?: "phone_image.jpg"
        }
    }

    // uri -> server filename, so tapping Create repeatedly with the same
    // photo uploads it once, even when several slots use it.
    private val uploadedInputs = mutableMapOf<String, String>()

    /** Upload every picked photo and wire each one into its LoadImage node. */
    private suspend fun uploadInputImages(prompt: Workflow) {
        val picks = _uiState.value.imagePicks
        if (picks.isEmpty() || !prompt.hasImageInput()) return
        val assignments = picks.entries
            .filter { prompt.containsKey(it.key) }
            .map { (slotId, pick) ->
                slotId to uploadedInputs.getOrPut(pick.uri) {
                    val uri = android.net.Uri.parse(pick.uri)
                    val mimeType = applicationContext.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = withContext(Dispatchers.IO) {
                        applicationContext.contentResolver.openInputStream(uri)?.readBytes()
                    } ?: throw Exception("Could not read ${pick.name} - pick it again and tap Create")
                    withContext(Dispatchers.IO) {
                        fluxAPI.uploadImage(pick.name, bytes, mimeType)
                    }
                }
            }
            .toMap()
        prompt.applyInputImages(assignments)
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
                refreshModelSlots()
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
                // After a restart the draft can name a SERVER workflow, which
                // only exists once the listing is in. Re-select it exactly
                // once so the app "remembers" the workflow you work on.
                if (!restoredServerWorkflow) {
                    restoredServerWorkflow = true
                    val desired = pickRestoredServerWorkflow(appSettings.generationDraft(), names)
                    if (desired != null && _uiState.value.workflow.workflowUri != "server://$desired") {
                        updateWorkflow(
                            WorkflowFile(desired, "Saved from the ComfyUI web UI", "server://$desired", isAsset = false, isServer = true)
                        )
                        logStore.debug("client", "restored last-used workflow '$desired'")
                    }
                }
            }
    }

    private var restoredServerWorkflow = false

    // ---- Comfy-Org template browser -------------------------------------

    private val templateCatalog = TemplateCatalog()

    private val _templateState = MutableStateFlow(TemplateBrowserState())
    val templateState: StateFlow<TemplateBrowserState> = _templateState.asStateFlow()

    /** Show/hide one template category in the browser. */
    fun toggleTemplateKind(kind: com.eltonkola.comfyflux.data.network.TemplateKind) {
        _templateState.update {
            val kinds = it.kinds.toMutableSet()
            if (!kinds.remove(kind)) kinds.add(kind)
            it.copy(kinds = kinds)
        }
    }

    suspend fun refreshTemplates(force: Boolean = false) {
        if (_templateState.value.loading) return
        _templateState.update { it.copy(loading = true, error = null) }
        runCatching { templateCatalog.list(refresh = force) }
            .onSuccess { entries -> _templateState.update { it.copy(entries = entries, loading = false) } }
            .onFailure { e ->
                logStore.warn("client", "template catalog fetch failed: ${e.message}")
                _templateState.update {
                    it.copy(loading = false, error = "Couldn't load the template list (${e.message ?: "network"}). Check the phone's internet connection and retry.")
                }
            }
    }

    /** Download a template and save it into the server's workflow folder. */
    suspend fun importTemplate(entry: TemplateEntry) {
        if (_templateState.value.importingName != null) return
        _templateState.update { it.copy(importingName = entry.fileName, error = null, message = null) }
        try {
            val raw = templateCatalog.fetchTemplate(entry.downloadUrl)
            val graph = runCatching { ComfyJson.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: throw Exception("The downloaded file is not a workflow graph")
            if (graph["nodes"] == null) throw Exception("The downloaded graph has no nodes")

            // Warn (don't block) when the server lacks node classes; skip
            // the check when we're not connected anyway.
            val missing = if (_uiState.value.stats != null) {
                TemplateCatalog.missingNodeTypes(raw, fluxAPI.fetchObjectInfo().keys)
            } else emptyList()

            fluxAPI.saveUserWorkflow(entry.fileName, raw.toString())
            refreshServerWorkflows()
            _templateState.update {
                it.copy(
                    message = if (missing.isEmpty())
                        "'${entry.title}' is saved on the server - pick it under Server Workflows."
                    else
                        "'${entry.title}' is saved, but these node types are missing on your server: ${missing.take(8).joinToString(", ")}${if (missing.size > 8) "…" else ""}. The workflow won't run until you install them (ComfyUI Manager)."
                )
            }
        } catch (e: Exception) {
            logStore.error("client", "template import failed (${entry.fileName}): ${e.message}")
            logStore.debug("client", exceptionChain(e))
            _templateState.update {
                it.copy(error = "Importing '${entry.title}' failed: ${e.message ?: "network error"}. " +
                        "Saving needs a working connection to your ComfyUI server.")
            }
        } finally {
            _templateState.update { it.copy(importingName = null) }
        }
    }

    // Recomputed only when the server+workflow pair changes or slots were cleared.
    private var slotsKey: String? = null

    /**
     * Recompute the model choices offered for the selected workflow (the
     * "pick your checkpoint/lora" part of the web UI, minus the graph).
     * Needs the connection for /object_info; failures are silent because the
     * section is an enhancement, never a blocker for generating.
     */
    suspend fun refreshModelSlots() {
        val state = _uiState.value
        if (state.stats == null) return
        val key = "${state.server}|${state.workflow.name}"
        if (key == slotsKey && state.modelSlots.isNotEmpty()) return
        runCatching {
            val info = fluxAPI.fetchObjectInfo()
            val raw = fetchRawGraph()
            val workflow = raw?.let { WorkflowConverter(info).convert(it) } ?: loadRawWorkflow()
            val slots = detectModelSlots(workflow, info)
            val toggles = raw?.let { detectWorkflowToggles(it) } ?: emptyList()
            slotsKey = key
            _uiState.update { state2 ->
                state2.copy(
                    modelSlots = slots,
                    // Drop overrides from a previous workflow or files the
                    // server no longer lists.
                    modelOverrides = state2.modelOverrides.filter { (k, v) ->
                        slots.any { it.key == k && it.options.contains(v) }
                    },
                    workflowToggles = toggles,
                    toggleOverrides = state2.toggleOverrides.filterKeys { k ->
                        toggles.any { it.key == k }
                    },
                    supportsNegativePrompt = workflow.negativeTextTargets().isNotEmpty(),
                    supportsImageInput = workflow.hasImageInput(),
                    workflowNeeds = workflow.needs(),
                    imageInputs = workflow.imageInputs(),
                    // Picked photos only make sense for the slots the
                    // current workflow actually has.
                    imagePicks = state2.imagePicks.filterKeys { id ->
                        workflow.hasImageInput() && workflow.containsKey(id)
                    }
                )
            }
        }.onFailure { e ->
            logStore.debug("client", "model slot detection skipped: ${e.message}")
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


    /** Raw graph JSON of the selected workflow; null when it is not graph format. */
    private suspend fun fetchRawGraph(): JsonObject? {
        val workflowConfig = _uiState.value.workflow
        val raw = if (workflowConfig.isServer) {
            fluxAPI.fetchWorkflowContent(workflowConfig.name)
        } else {
            // Downloaded workflows (web UI exports) are graph format just
            // like the server's own files; they need the same conversion.
            applicationContext.getWorkflowAsText(workflowConfig) ?: return null
        }
        val element = try {
            ComfyJson.parseToJsonElement(raw)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw Exception(decodeFailureReason(raw, "workflow ${workflowConfig.name}", 200), e)
        }
        return (element as? JsonObject)?.takeIf { it.containsKey("nodes") }
    }

    /** Parse the currently selected workflow into API form, without patching it. */
    private suspend fun loadRawWorkflow(toggleOverrides: Map<String, JsonElement> = emptyMap()): Workflow {
        fetchRawGraph()?.let { raw ->
            return WorkflowConverter(fluxAPI.fetchObjectInfo()).convert(raw, toggleOverrides)
        }
        val workflowConfig = _uiState.value.workflow
        return if (workflowConfig.isServer) {
            // Already API format (e.g. saved via "Save (Export)").
            val raw = fluxAPI.fetchWorkflowContent(workflowConfig.name)
            try {
                ComfyJson.decodeFromString<Workflow>(raw)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw Exception(decodeFailureReason(raw, "workflow ${workflowConfig.name}", 200), e)
            }
        } else {
            val workflowInput = applicationContext.getWorkflowAsText(workflowConfig)
                ?: throw Exception("Error reading workflow!")
            Json.decodeFromString<Workflow>(workflowInput)
        }
    }

    private suspend fun loadWorkflow(): PromptRequest {
        val seed = if (uiState.value.isRandom) {
            val newSeed = generateRandom16DigitNumber()
            _uiState.update { it.copy(seed = newSeed) }
            newSeed
        } else {
            uiState.value.seed
        }

        val toggleJson: Map<String, JsonElement> = uiState.value.toggleOverrides
            .filterKeys { k -> uiState.value.workflowToggles.any { it.key == k } }
            .mapValues { JsonPrimitive(it.value) }
        val workflow = loadRawWorkflow(toggleJson)
        workflow.updateSizeAndBatch(
            width = uiState.value.width,
            height = uiState.value.height,
            batchSize = uiState.value.batchSize
        )
        workflow.updateCfgAndSteps(
            cfg = uiState.value.cfg,
            steps = uiState.value.steps
        )
        workflow.applyModelOverrides(_uiState.value.modelOverrides)
        workflow.updatePrompt(prompt = uiState.value.prompt.cleanPrompt())
        workflow.updateNegativePrompt(prompt = uiState.value.negativePrompt.cleanPrompt())
        workflow.updateSeed(seed = seed)
        logStore.trace(
            "client",
            "generate config: workflow=${_uiState.value.workflow.name}, " +
                "size=${_uiState.value.width}x${_uiState.value.height} batch=${_uiState.value.batchSize}, " +
                "seed=$seed cfg=${_uiState.value.cfg} steps=${_uiState.value.steps}, " +
                "prompt-patched-into=${workflow.promptTextTargets()}, " +
                "negative-patched-into=${workflow.negativeTextTargets()}, " +
                "switches=${_uiState.value.toggleOverrides}, " +
                "models=${_uiState.value.modelOverrides}"
        )
        return PromptRequest(fluxAPI.clientId, workflow)
    }

    suspend fun generateImages(start: () -> Unit, stop: () -> Unit) {
        if (uiState.value.prompt.isBlank()) {
            return
        }
        // Tapping Create while a generation runs queues this job behind it:
        // the FluxAPI session streams every queued job, so only the call that
        // is NOT joining a running session owns the timer, the status
        // refresh and the outcome toast.
        val joiningRunningSession = fluxAPI.progressUiState.value.executing
        if (!joiningRunningSession) {
            start()
        }
        try {
            fluxAPI.setServerAddress(normalizeServerUrl(uiState.value.server))
            val prompt = loadWorkflow()
            uploadInputImages(prompt.prompt)
            withContext(Dispatchers.IO) {
                fluxAPI.generateImage(prompt)
            }
            if (fluxAPI.progressUiState.value.error.isEmpty()) {
                addRecentPrompt(uiState.value.prompt)
            }
            val stillRunning = fluxAPI.progressUiState.value.executing
            if (joiningRunningSession && stillRunning) return
            checkStatus()
            notifyGenerationOutcome()
            if (!joiningRunningSession) stop()
        } catch (e: Exception) {
            reportGenerationFailure(e, "generate (${uiState.value.workflow.name})")
            if (!joiningRunningSession) stop()
        }
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
        logStore.debug(source, exceptionChain(e))
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

    private fun addRecentPrompt(prompt: String) {
        val recent = pushRecent(uiState.value.recentPrompts, prompt)
        _uiState.update { it.copy(recentPrompts = recent) }
        appSettings.setRecentPrompts(recent)
    }

    /** Create-screen selections and recent prompts survive an app restart. */
    private fun restorePersistedState() {
        _uiState.update { state ->
            val draft = appSettings.generationDraft()
            // Only restore a workflow that still ships as a bundled asset.
            val workflow = draft?.let { saved -> workflows.firstOrNull { it.name == saved.workflowName } }
            state.copy(
                prompt = draft?.prompt ?: state.prompt,
                seed = draft?.seed ?: state.seed,
                isRandom = draft?.isRandom ?: state.isRandom,
                width = draft?.width ?: state.width,
                height = draft?.height ?: state.height,
                batchSize = draft?.batchSize ?: state.batchSize,
                cfg = draft?.cfg ?: state.cfg,
                steps = draft?.steps ?: state.steps,
                negativePrompt = draft?.negativePrompt ?: state.negativePrompt,
                workflow = workflow ?: state.workflow,
                recentPrompts = appSettings.recentPrompts()
            )
        }
    }

    private fun saveDraft() {
        val state = uiState.value
        appSettings.setGenerationDraft(
            GenerationDraft(
                prompt = state.prompt,
                seed = state.seed,
                isRandom = state.isRandom,
                width = state.width,
                height = state.height,
                batchSize = state.batchSize,
                cfg = state.cfg,
                steps = state.steps,
                negativePrompt = state.negativePrompt,
                workflowName = state.workflow.name,
                workflowUri = state.workflow.workflowUri,
                workflowIsServer = state.workflow.isServer
            )
        )
    }
}


fun Context.getWorkflowAsText(workflow: WorkflowFile): String? {
    return try {
        if (workflow.isAsset) {
            // Load from assets
            val fileName = workflow.workflowUri.removePrefix("file:///android_asset/")
            assets.open(fileName).bufferedReader().use { it.readText() }
        } else {
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

/** Prompt history for the create screen: trimmed, deduplicated (case-sensitive), newest first, capped at 10. */
fun pushRecent(existing: List<String>, prompt: String): List<String> {
    val trimmed = prompt.trim()
    if (trimmed.isEmpty()) return existing
    return (listOf(trimmed) + existing.filter { it.trim() != trimmed }).take(10)
}
/**
 * Workflow to re-select after a restart once the server listing arrived:
 * the draft's server workflow, but only if it still exists server-side.
 * Asset-drafts are restored earlier (before any listing) and return null.
 */
internal fun pickRestoredServerWorkflow(draft: GenerationDraft?, names: List<String>): String? =
    draft?.takeIf { it.workflowIsServer }?.workflowName?.takeIf { it in names }

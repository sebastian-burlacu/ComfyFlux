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
import com.eltonkola.comfyflux.data.model.ImagePick
import com.eltonkola.comfyflux.data.model.PaintedMask
import com.eltonkola.comfyflux.data.model.hasMaskConsumer
import com.eltonkola.comfyflux.data.model.rasterizeMask
import com.eltonkola.comfyflux.data.model.compositeMaskPixels
import com.eltonkola.comfyflux.data.model.Workflow
import com.eltonkola.comfyflux.data.model.WorkflowConverter
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.data.model.applyInputImages
import com.eltonkola.comfyflux.data.model.imageInputs
import com.eltonkola.comfyflux.data.model.RunParams
import com.eltonkola.comfyflux.data.model.summary
import com.eltonkola.comfyflux.data.model.needs
import com.eltonkola.comfyflux.data.model.applyModelOverrides
import com.eltonkola.comfyflux.data.model.hasImageInput
import com.eltonkola.comfyflux.data.model.detectSamplerSlots
import com.eltonkola.comfyflux.data.model.detectResolutionSelector
import com.eltonkola.comfyflux.data.model.nearestAspect
import com.eltonkola.comfyflux.data.model.applyResolutionSize
import com.eltonkola.comfyflux.data.model.applySamplerChoices
import com.eltonkola.comfyflux.data.model.dragWidth
import com.eltonkola.comfyflux.data.model.dragHeight
import com.eltonkola.comfyflux.data.model.aspectRatioOf
import com.eltonkola.comfyflux.data.model.fitToAspect
import com.eltonkola.comfyflux.data.model.SIZE_STEPS
import com.eltonkola.comfyflux.data.model.applyStrengthOverrides
import com.eltonkola.comfyflux.data.model.applyGraphKnobs
import com.eltonkola.comfyflux.data.model.detectGraphKnobs
import com.eltonkola.comfyflux.data.model.finishedForeignRuns
import com.eltonkola.comfyflux.data.model.detectModelSlots
import com.eltonkola.comfyflux.data.model.detectStrengthGroups
import com.eltonkola.comfyflux.data.model.guidanceTargets
import com.eltonkola.comfyflux.data.model.gaps
import com.eltonkola.comfyflux.data.model.prunedTo
import com.eltonkola.comfyflux.data.model.withRememberedChoices
import com.eltonkola.comfyflux.data.model.asWorkflowChoices
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

/**
 * Index of the closest entry in a sorted size list. Sizes can be arbitrary
 * (photo aspect fitting), so an exact index lookup can miss - never here.
 */
internal fun nearestSizeIndex(sizes: List<Int>, value: Int): Int {
    var best = 0
    var bestDiff = Int.MAX_VALUE
    sizes.forEachIndexed { index, size ->
        val diff = kotlin.math.abs(size - value)
        if (diff < bestDiff) {
            bestDiff = diff
            best = index
        }
    }
    return best
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

    // Background jobs must not die silently: an unhandled failure here is the
    // kind of thing that later looks like an unexplained freeze.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e ->
                logStore.error("client", "unhandled background failure: ${e.stackTraceToString().take(1500)}")
            }
    )

    companion object {
        // How often the foreground queue watch asks the server what it is
        // running. /queue is tiny; 3 s is responsive without chattering.
        private const val QUEUE_WATCH_MS = 3_000L
    }

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
        persistChoices()
    }

    fun setImageWidth(width: Int) {
        _uiState.update { state ->
            val ratio = pinnedRatio(state)
            if (ratio == null) {
                // Free sliders: the values are the output size, so an explicit
                // aspect pick is superseded by what you just dragged.
                state.copy(width = width, aspectChoice = null)
            } else {
                // A shape is pinned (and the node computes the real pixels from
                // aspect + megapixels): dragging width must not silently warp
                // it, so the other axis follows the same ratio.
                val (w, h) = dragWidth(width, ratio, SIZE_STEPS)
                state.copy(width = w, height = h)
            }
        }
        saveDraft()
    }

    fun setImageHeight(height: Int) {
        _uiState.update { state ->
            val ratio = pinnedRatio(state)
            if (ratio == null) {
                state.copy(height = height, aspectChoice = null)
            } else {
                val (w, h) = dragHeight(height, ratio, SIZE_STEPS)
                state.copy(width = w, height = h)
            }
        }
        saveDraft()
    }

    /** The ratio the sliders must hold, or null when they are free. */
    private fun pinnedRatio(state: ImageGenerationUiState): Float? {
        val label = state.aspectChoice ?: return null
        return aspectRatioOf(label)
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
        persistChoices()
    }

    fun setSteps(steps: Int) {
        _uiState.update { it.copy(steps = steps) }
        saveDraft()
        persistChoices()
    }

    /**
     * Strength for one of the widgets beside a model slot
     * ("nodeId|strength_model"); null = the workflow's own value.
     */
    fun setStrength(key: String, value: Float?) {
        _uiState.update { state ->
            val map = state.strengthOverrides.toMutableMap()
            if (value == null) map.remove(key) else map[key] = value
            state.copy(strengthOverrides = map)
        }
        persistChoices()
    }

    /** null = leave the sampler name the workflow saved. */
    fun setSamplerChoice(name: String?) {
        _uiState.update { it.copy(samplerChoice = name) }
        persistChoices()
    }

    fun setSchedulerChoice(name: String?) {
        _uiState.update { it.copy(schedulerChoice = name) }
        persistChoices()
    }

    /** null = keep the workflow's denoise; 1.0 is a full (normal) run. */
    fun setDenoise(value: Float?) {
        _uiState.update { it.copy(denoise = value?.coerceIn(0.05f, 1f)) }
        persistChoices()
    }

    /**
     * Pick the aspect the workflow's ResolutionSelector should use. The
     * sliders are refit to the same shape so the size preview stays honest.
     */
    fun setAspectChoice(label: String?) {
        _uiState.update { state ->
            val slot = state.sizeSlot
            if (label == null || slot == null) {
                state.copy(aspectChoice = null)
            } else {
                val (w, h) = fitToAspect(state.width, state.height, label, SIZE_STEPS)
                state.copy(aspectChoice = label, width = w, height = h)
            }
        }
        saveDraft()
        persistChoices()
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
        val painted = _uiState.value.paintedMask?.takeUnless { it.isEmpty }
        val assignments = picks.entries
            .filter { prompt.containsKey(it.key) }
            .map { (slotId, pick) ->
                slotId to if (painted != null && painted.slotId == slotId) {
                    // The mask rides in the photo's alpha channel, so this slot
                    // uploads a new file every time the mask changed.
                    uploadMaskedPhoto(pick, painted)
                } else {
                    uploadedInputs.getOrPut(pick.uri) {
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
            }
            .toMap()
        prompt.applyInputImages(assignments)
    }

    /** Store the painted area for one image slot; applied when Create uploads. */
    fun setPaintedMask(mask: PaintedMask) {
        _uiState.update { it.copy(paintedMask = mask.takeUnless { m -> m.isEmpty }) }
    }

    fun clearPaintedMask() {
        _uiState.update { it.copy(paintedMask = null) }
    }

    /**
     * Composite the painted area into the photo's alpha channel and upload the
     * result: ComfyUI's LoadImage derives its MASK output from alpha, which is
     * how inpaint workflows get the mask without any custom node.
     */
    private suspend fun uploadMaskedPhoto(pick: ImagePick, painted: PaintedMask): String =
        withContext(Dispatchers.IO) {
            val uri = android.net.Uri.parse(pick.uri)
            val decoded = applicationContext.contentResolver.openInputStream(uri)
                ?.use { android.graphics.BitmapFactory.decodeStream(it) }
                ?: throw Exception("Could not read ${pick.name} - pick it again and tap Create")
            // Cap the long edge: a 12 MP phone photo is a 48 MB int array.
            val scale = (2048f / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
            val width = (decoded.width * scale).toInt().coerceAtLeast(1)
            val height = (decoded.height * scale).toInt().coerceAtLeast(1)
            val bitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(decoded, width, height, true)
            } else decoded
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val paintedMap = rasterizeMask(painted.strokes, width, height)
            bitmap.setPixels(compositeMaskPixels(pixels, paintedMap), 0, width, 0, 0, width, height)
            val bytes = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
            val name = "comfyflux-mask-${System.currentTimeMillis()}.png"
            fluxAPI.uploadImage(name, bytes.toByteArray(), "image/png")
        }

    /** Set (or clear, with null) one per-node knob; applied at Create. */
    fun setGraphKnob(key: String, value: String?) {
        _uiState.update { state ->
            val map = HashMap(state.graphKnobOverrides)
            if (value == null || value.isBlank()) map.remove(key) else map[key] = value
            state.copy(graphKnobOverrides = map)
        }
        persistChoices()
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
                startQueueWatch()
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
            val workflow = if (raw != null) {
                val converter = WorkflowConverter(info)
                val w = converter.convert(raw)
                reportSubstitutions(converter)
                w
            } else {
                loadRawWorkflow()
            }
            val slots = detectModelSlots(workflow, info)
            val groups = detectStrengthGroups(workflow, info, slots.map { it.nodeId }.toSet())
            val samplers = detectSamplerSlots(workflow, info)
            val sizeSlot = detectResolutionSelector(workflow, info)
            val guidance = workflow.guidanceTargets()
            val toggles = raw?.let { detectWorkflowToggles(it) } ?: emptyList()
            slotsKey = key
            // What this workflow remembers from earlier runs (per server);
            // null for a workflow never configured from here.
            val remembered = appSettings.workflowChoices()[key]
            _uiState.update { state2 ->
                val controls = if (remembered == null) {
                    // Drop overrides from a previous workflow or files the
                    // server no longer lists.
                    state2.prunedTo(slots, groups, samplers, sizeSlot)
                } else {
                    state2.withRememberedChoices(remembered, slots, groups, samplers, sizeSlot)
                }
                controls.copy(
                    // "0 = keep the workflow value" is useless when you cannot
                    // see that value, so an unset field starts at what the
                    // graph actually holds; typing over it overrides as before.
                    cfg = controls.cfg.takeIf { it > 0f } ?: guidance.cfgDefault ?: 0f,
                    steps = controls.steps.takeIf { it > 0 } ?: guidance.stepsDefault ?: 0,
                    modelSlots = slots,
                    strengthGroups = groups,
                    workflowToggles = toggles,
                    toggleOverrides = state2.toggleOverrides.filterKeys { k ->
                        toggles.any { it.key == k }
                    },
                    // Sampler/size controls of the new workflow; a choice only
                    // survives while the new workflow still offers it.
                    samplerSlots = samplers.takeUnless { it.isEmpty },
                    sizeSlot = sizeSlot,
                    supportsNegativePrompt = workflow.negativeTextTargets().isNotEmpty(),
                    supportsImageInput = workflow.hasImageInput(),
                    workflowNeeds = workflow.needs(),
                    guidance = guidance,
                    hasMaskInput = workflow.hasMaskConsumer(),
                    graphKnobs = workflow.detectGraphKnobs(info),
                    gaps = workflow.gaps(info),
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

    // ---------------------------------------------------------------------
    // Server queue watch
    //
    // The websocket session only streams prompts THIS app queued, so a run
    // started in the web UI or on another phone used to leave the app with no
    // Stop button, a stale queue count and no notification. A foreground poll
    // of /queue (a tiny response) keeps those three honest.
    // ---------------------------------------------------------------------

    private var queueWatchJob: Job? = null

    private fun startQueueWatch() {
        if (queueWatchJob?.isActive == true) return
        queueWatchJob = appScope.launch {
            var runningIds = emptySet<String>()
            while (isActive) {
                delay(QUEUE_WATCH_MS)
                // Foreground-only: no background polling, no wake-locks.
                if (!appState.foreground) continue
                if (_uiState.value.stats == null) {
                    // Disconnected: nothing is known to be running, so the
                    // Stop button must not keep showing a stale server state.
                    fluxAPI.noteServerActivity(false, 0)
                    runningIds = emptySet()
                    continue
                }
                val queue = runCatching { fluxAPI.fetchQueue() }.getOrNull() ?: continue
                val running = queue.running.map { it.id }.toSet()
                fluxAPI.noteServerActivity(running.isNotEmpty(), queue.pending.size)

                // running -> idle: a prompt the server was working on and that
                // this app never queued finished without anyone telling the user.
                finishedForeignRuns(runningIds, running) { fluxAPI.wasLocallyQueued(it) }
                    .firstOrNull()
                    ?.let { notifyForeignRun(it) }
                runningIds = running
            }
        }
    }

    /** Notify about a run this app watched but did not start. */
    private suspend fun notifyForeignRun(promptId: String) {
        if (fluxAPI.progressUiState.value.executing) return
        val success = runCatching { fluxAPI.runSucceeded(promptId) }.getOrNull()
        logStore.info(
            "client",
            "run $promptId (started elsewhere) finished: ${when (success) { true -> "success"; false -> "failed"; else -> "unknown" }}"
        )
        notifier.foreignRunFinished(
            foreground = appState.foreground,
            success = success ?: true,
            unknown = success == null
        )
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

    /**
     * The converter replaces template settings the server cannot provide
     * (typically model files a template names but this server lacks) with
     * legal values from the server. That keeps the import working, but the
     * pairing may not match - name it, so a bad run is explainable.
     */
    private fun reportSubstitutions(converter: WorkflowConverter) {
        val notes = converter.substitutions.map { s ->
            "${s.classType} node ${s.nodeId}: wanted '${s.wanted}', server has no such file for " +
                    "${s.input} - used '${s.used}'"
        }
        _uiState.update { it.copy(unavailableFiles = notes) }
        converter.substitutions.forEach { s ->
            logStore.info(
                "client",
                "${s.classType} node ${s.nodeId}: '${s.wanted}' is not on this server - " +
                        "using '${s.used}' for ${s.input}. If the run fails with shape or " +
                        "dtype errors, install the file the template asks for."
            )
        }
    }

    /** Parse the currently selected workflow into API form, without patching it. */
    private suspend fun loadRawWorkflow(toggleOverrides: Map<String, JsonElement> = emptyMap()): Workflow {
        fetchRawGraph()?.let { raw ->
            val converter = WorkflowConverter(fluxAPI.fetchObjectInfo())
            val workflow = converter.convert(raw, toggleOverrides)
            reportSubstitutions(converter)
            return workflow
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
        // Aspect-driven workflows size through the selector node; width and
        // height still decide the megapixel count.
        _uiState.value.sizeSlot?.let { slot ->
            val aspect = _uiState.value.aspectChoice
                ?: nearestAspect(slot.aspectOptions, uiState.value.width, uiState.value.height)
            if (aspect != null) {
                val megapixels = uiState.value.width * uiState.value.height / 1_000_000f
                workflow.applyResolutionSize(slot.nodeId, aspect, megapixels)
            }
        }
        workflow.applyStrengthOverrides(_uiState.value.strengthOverrides)
        workflow.applyGraphKnobs(_uiState.value.graphKnobOverrides)
        workflow.applySamplerChoices(
            sampler = _uiState.value.samplerChoice,
            scheduler = _uiState.value.schedulerChoice,
            denoise = _uiState.value.denoise
        )
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
                "models=${_uiState.value.modelOverrides}, " +
                "strengths=${_uiState.value.strengthOverrides}, " +
                "sampler=${_uiState.value.samplerChoice} scheduler=${_uiState.value.schedulerChoice} " +
                "denoise=${_uiState.value.denoise} aspect=${_uiState.value.aspectChoice}"
        )
        return PromptRequest(fluxAPI.clientId, workflow)
    }

    /** New jobs jump ahead of what is already pending (submit-time priority). */
    fun setQueueNext(enabled: Boolean) {
        _uiState.update { it.copy(queueNextJobs = enabled) }
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
            // Foreground service: the notification shows live progress and
            // keeps the session alive when the app leaves the foreground.
            com.eltonkola.comfyflux.data.GenerationService.begin(applicationContext)
            start()
        }
        try {
            fluxAPI.setServerAddress(normalizeServerUrl(uiState.value.server))
            val prompt = loadWorkflow()
            uploadInputImages(prompt.prompt)
            withContext(Dispatchers.IO) {
                fluxAPI.generateImage(prompt, front = uiState.value.queueNextJobs)
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
            if (!joiningRunningSession) {
                // Nothing reached the server, so the progress notification is
                // already a lie: take it down instead of letting it linger.
                com.eltonkola.comfyflux.data.GenerationService.end(applicationContext)
                stop()
            }
        }
    }

    /** Re-queue a past generation exactly as it ran (server history keeps the full prompt). */
    /** Prefill the Create sliders from a past run (seed becomes FIXED). */
    fun applyRunSettings(params: RunParams) {
        _uiState.update { state ->
            state.copy(
                seed = params.seed ?: state.seed,
                steps = params.steps ?: state.steps,
                cfg = params.cfg ?: state.cfg
            )
        }
        saveDraft()
        logStore.info("client", "create settings prefilled from a history run: ${params.summary()}")
    }

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
            // Say what landed, not just "done": a video run and an image run
            // look the same otherwise.
            val outcome = fluxAPI.progressUiState.value.generatedMedia.summary()
            notifier.generationFinished(
                appState.foreground,
                success = true,
                detail = outcome.takeIf { it != "no outputs" }
            )
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

    /**
     * Remember the Create choices for the selected workflow (keyed per
     * server), so switching workflows or restarting the app no longer drops
     * model picks, strengths, sampler/scheduler/denoise, aspect, CFG/steps.
     */
    private fun persistChoices() {
        val state = uiState.value
        appSettings.setWorkflowChoices(
            "${'$'}{state.server}|${'$'}{state.workflow.name}",
            state.asWorkflowChoices()
        )
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

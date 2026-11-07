package com.eltonkola.comfyflux.data.model

import com.eltonkola.comfyflux.data.network.DEFAULT_URL
import com.eltonkola.comfyflux.app.usecase.generateRandom16DigitNumber


data class HistoryUiState(
    val history: List<HistoryItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val silentLoading: Boolean = false,
    // The phone's own keep/discard marks; they never leave the device.
    val triage: Map<String, Triage> = emptyMap(),
    val triageFilter: TriageFilter = TriageFilter.All
)

data class QueueUiState(
    val queue: Queue? = null,
    val loading: Boolean = true,
    val error: Boolean = false
)

data class HistoryItem(
    val images: List<String>,
    val success: Boolean,
    val completed: Boolean,
    val prompt: Workflow,
    val id: String
)

data class Queue(
    val running: List<Item>,
    val pending: List<Item>,
) {
    data class Item(
        val id: String,
        val prompt: String,
        /** Position in the server queue; what /queue delete actually takes. */
        val number: Int? = null
    )
}

data class ProgressGenerationUIState(
    val promptId: String = "",
    val progress: Int = 0,
    val maxProgress: Int = 0,
    val queueRemaining: Int = 0,
    val generatedImages: List<String> = emptyList(),
    val statusMessage: String = "",
    val error: String = "",
    // Full server failure (node id/type, message, what ran first); empty
    // when the failure is app-side.
    val errorDetail: String = "",
    val executing: Boolean = false,
    // The SERVER has a job running (it may be another client's, or a job this
    // app queued before a restart). Stop is offered whenever this is true,
    // so the button does not vanish while the queue still has work.
    val serverRunning: Boolean = false,
    val allNodes: List<String> = emptyList(),
    val remainingNodes: List<String> = emptyList(),
    // Live latent preview (android.graphics.Bitmap) decoded from the WS
    // binary preview frames; Any? so this state stays JVM-testable.
    val previewImage: Any? = null
) {
    fun totalProgress(): Float {
        if (allNodes.isEmpty()) return 0f
        return (allNodes.size - remainingNodes.size) / allNodes.size.toFloat()
    }

    fun partialProgress(): Float {
        if (maxProgress == 0) return 0f
        return progress / maxProgress.toFloat()
    }
}

data class ImageGenerationUiState(
    val server: String = DEFAULT_URL,

    val loadingStats: Boolean = false,
    val stats: SystemStats? = null,
    val serverError: String? = null,

    val workflow: WorkflowFile = workflows.first(),
    val serverWorkflows: List<WorkflowFile> = emptyList(),

    val prompt: String = "",
    val enrichingPrompt: Boolean = false,
    val promptEnrichError: String? = null,
    val width: Int = 512,
    val height: Int = 512,
    val batchSize: Int = 1,
    val seed: Long = generateRandom16DigitNumber(),
    val isRandom: Boolean = true,
    // 0 keeps whatever the workflow itself specifies.
    val cfg: Float = 0f,
    val steps: Int = 0,
    // Sampler knobs the selected workflow exposes; null choice = keep the
    // workflow's own value.
    val samplerSlots: SamplerSlots? = null,
    val samplerChoice: String? = null,
    val schedulerChoice: String? = null,
    val denoise: Float? = null,
    // Set when the workflow sizes itself through a ResolutionSelector node
    // (aspect ratio + megapixels) instead of latent width/height.
    val sizeSlot: SizeSlot? = null,
    val aspectChoice: String? = null,
    // Model choices detected on the selected workflow + files picked for them.
    val modelSlots: List<ModelSlot> = emptyList(),
    val modelOverrides: Map<String, String> = emptyMap(),
    // Strength knobs of nodes without a file choice (ControlNet/adapter
    // applies); their overrides share the "nodeId|input" key space.
    val strengthGroups: List<StrengthGroup> = emptyList(),
    // Strength overrides for the widgets beside model slots, keyed
    // "nodeId|strength_model"; absent = the workflow's own value.
    val strengthOverrides: Map<String, Float> = emptyMap(),
    // Boolean inputs the workflow itself exposes (e.g. prompt enhance on/off)
    // and the user's flips; key = "nodeId|inputIndex".
    val workflowToggles: List<WorkflowToggle> = emptyList(),
    val toggleOverrides: Map<String, Boolean> = emptyMap(),
    // Optional user negative; empty keeps whatever the workflow specifies.
    val negativePrompt: String = "",
    val supportsNegativePrompt: Boolean = false,
    // img2img: every LoadImage node the workflow has becomes a slot the user
    // can fill with a phone photo. Picks are session-only (content
    // permissions don't survive restarts).
    val supportsImageInput: Boolean = false,
    val workflowNeeds: WorkflowNeeds? = null,
    // Which of CFG/Steps this graph can actually take (null = not analysed yet).
    val guidance: GuidanceTargets? = null,
    // Painting offered only when the graph consumes a MASK somewhere.
    val hasMaskInput: Boolean = false,
    // Painted area for one image slot (null = no mask; painting starts on demand).
    val paintedMask: PaintedMask? = null,
    // Per-node knobs the graph exposes beyond the dedicated controls.
    val graphKnobs: List<GraphKnob> = emptyList(),
    // Picked values, keyed "nodeId|input" (string-encoded, see applyGraphKnobs).
    val graphKnobOverrides: Map<String, String> = emptyMap(),
    // New jobs jump ahead of the pending queue (POST /prompt {"front": true}).
    val queueNextJobs: Boolean = false,
    // What this workflow asks for that the server cannot provide; drives the
    // "your server is missing" card before the run, not only after it fails.
    val gaps: WorkflowGaps = WorkflowGaps(),
    val imageInputs: List<ImageInput> = emptyList(),
    val imagePicks: Map<String, ImagePick> = emptyMap(),
    // Template settings this server could not provide (model files mostly);
    // shown with any failure so a broken pairing is explainable.
    val unavailableFiles: List<String> = emptyList(),
    val recentPrompts: List<String> = emptyList()
)

data class TemplateBrowserState(
    val entries: List<com.eltonkola.comfyflux.data.network.TemplateEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    // File currently being downloaded/saved, drives the spinner.
    val importingName: String? = null,
    // Result of the last import (success with warnings, or failure text).
    val message: String? = null,
    // Categories the browser shows. Video/3D/audio/Comfy-API templates can
    // never run on a plain local server (the API ones call hosted nodes),
    // so they start hidden; the chips let you opt in.
    val kinds: Set<com.eltonkola.comfyflux.data.network.TemplateKind> =
        setOf(
            com.eltonkola.comfyflux.data.network.TemplateKind.IMAGE,
            com.eltonkola.comfyflux.data.network.TemplateKind.IMAGE_INPUT
        )
)

data class ImageViewerUiState(
    val images: List<String> = emptyList(),
    val selected: Int = 0
)


/** One phone photo picked for a LoadImage slot. */
data class ImagePick(val uri: String, val name: String)

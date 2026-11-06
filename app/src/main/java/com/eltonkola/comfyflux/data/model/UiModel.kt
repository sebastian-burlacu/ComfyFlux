package com.eltonkola.comfyflux.data.model

import com.eltonkola.comfyflux.data.network.DEFAULT_URL
import com.eltonkola.comfyflux.app.usecase.generateRandom16DigitNumber


data class HistoryUiState(
    val history: List<HistoryItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val silentLoading: Boolean = false
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
        val prompt: String
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
    val executing: Boolean = false,
    val allNodes: List<String> = emptyList(),
    val remainingNodes: List<String> = emptyList()
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
    // Model choices detected on the selected workflow + files picked for them.
    val modelSlots: List<ModelSlot> = emptyList(),
    val modelOverrides: Map<String, String> = emptyMap(),
    // Boolean inputs the workflow itself exposes (e.g. prompt enhance on/off)
    // and the user's flips; key = "nodeId|inputIndex".
    val workflowToggles: List<WorkflowToggle> = emptyList(),
    val toggleOverrides: Map<String, Boolean> = emptyMap(),
    // Optional user negative; empty keeps whatever the workflow specifies.
    val negativePrompt: String = "",
    val supportsNegativePrompt: Boolean = false,
    // img2img: workflows with a LoadImage node accept a phone photo; the uri
    // is session-only (content permissions don't survive restarts).
    val supportsImageInput: Boolean = false,
    val inputImageUri: String = "",
    val inputImageName: String = "",
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


package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.Serializable

/**
 * The Create-screen choices remembered for ONE workflow (per "server|name"
 * key): which model files, which strengths, which sampler/scheduler/denoise,
 * which aspect, and CFG/steps. Before this they were session-only, so every
 * workflow switch or app restart silently dropped back to the workflow's own
 * defaults.
 */
@Serializable
data class WorkflowChoices(
    val modelOverrides: Map<String, String> = emptyMap(),
    val strengthOverrides: Map<String, Float> = emptyMap(),
    val samplerChoice: String? = null,
    val schedulerChoice: String? = null,
    val denoise: Float? = null,
    val aspectChoice: String? = null,
    // 0 = "use the workflow's own value", which is itself a choice worth
    // remembering, so these are plain (nullable) copies of the sliders.
    val cfg: Float = 0f,
    val steps: Int = 0,
    val graphKnobOverrides: Map<String, String> = emptyMap()
)

/**
 * Apply remembered choices to the freshly detected controls. Anything the
 * workflow no longer offers (file removed from the server, sampler gone,
 * node edited away) is dropped here rather than being sent to the server.
 */
fun ImageGenerationUiState.withRememberedChoices(
    stored: WorkflowChoices?,
    slots: List<ModelSlot>,
    groups: List<StrengthGroup>,
    samplers: SamplerSlots,
    sizeSlot: SizeSlot?
): ImageGenerationUiState {
    // Never configured from here: keep this session's picks, just pruned to
    // what the workflow/server actually offers.
    if (stored == null) return prunedTo(slots, groups, samplers, sizeSlot)
    val remembered = copy(
        modelOverrides = stored.modelOverrides,
        strengthOverrides = stored.strengthOverrides,
        samplerChoice = stored.samplerChoice,
        schedulerChoice = stored.schedulerChoice,
        denoise = stored.denoise,
        aspectChoice = stored.aspectChoice,
        cfg = stored.cfg,
        steps = stored.steps,
        graphKnobOverrides = stored.graphKnobOverrides
    )
    return remembered.prunedTo(slots, groups, samplers, sizeSlot)
}

/**
 * Drop remembered choices the current workflow/server cannot honour. Same
 * rules the session used to rely on, now shared by the remembered path.
 */
fun ImageGenerationUiState.prunedTo(
    slots: List<ModelSlot>,
    groups: List<StrengthGroup>,
    samplers: SamplerSlots,
    sizeSlot: SizeSlot?
): ImageGenerationUiState = copy(
    modelOverrides = modelOverrides.filter { (key, value) ->
        slots.any { it.key == key && it.options.contains(value) }
    },
    strengthOverrides = strengthOverrides.filter { (key, _) ->
        slots.any { slot -> slot.strengths.any { slot.strengthKey(it) == key } } ||
                groups.any { group -> group.strengths.any { "${group.nodeId}|${it.input}" == key } }
    },
    samplerChoice = samplerChoice?.takeIf { samplers.samplerOptions.contains(it) },
    schedulerChoice = schedulerChoice?.takeIf { samplers.schedulerOptions.contains(it) },
    denoise = if (samplers.hasDenoise) denoise else null,
    aspectChoice = sizeSlot?.let { slot -> aspectChoice?.takeIf { slot.aspectOptions.contains(it) } },
    // Knobs a graph no longer declares (node edited away) are dropped, not sent.
    graphKnobOverrides = graphKnobOverrides.filterKeys { key ->
        graphKnobs.any { it.key == key }
    }
)

/** Snapshot the live choices for persistence. */
fun ImageGenerationUiState.asWorkflowChoices(): WorkflowChoices = WorkflowChoices(
    modelOverrides = modelOverrides,
    strengthOverrides = strengthOverrides,
    samplerChoice = samplerChoice,
    schedulerChoice = schedulerChoice,
    denoise = denoise,
    aspectChoice = aspectChoice,
    cfg = cfg,
    steps = steps,
    graphKnobOverrides = graphKnobOverrides
)

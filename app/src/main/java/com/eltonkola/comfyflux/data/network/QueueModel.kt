package com.eltonkola.comfyflux.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class QueueResponse(
    val queue_running: List<JsonElement>,
    val queue_pending: List<JsonElement>,
)

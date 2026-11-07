package com.eltonkola.comfyflux.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HistoryResponse(
    val prompt: List<JsonElement>,
    val outputs: Map<String, Output>,
    val status: Status
) {

    @Serializable
    data class Output(
        val images: List<Image> = emptyList(),
        // SaveVideo / SaveAudio finish their graphs with these instead.
        val video: List<Image> = emptyList(),
        val audio: List<Image> = emptyList()
    )

    @Serializable
    data class Image(
        val filename: String,
        val subfolder: String,
        val type: String,
        // Present for clips saved under "images" (e.g. "video/mp4").
        val format: String? = null
    )

    @Serializable
    data class Status(
        val status_str: String,
        val completed: Boolean,
        val messages: List<List<JsonElement>>
    )
}



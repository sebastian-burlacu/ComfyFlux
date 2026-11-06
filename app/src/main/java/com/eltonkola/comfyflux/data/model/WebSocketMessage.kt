package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
sealed class WSMessage {
    abstract val promptId: String?

    @Serializable
    data class PromptMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val number: Int,
        // Real servers nest {node: {errors: [...], valid: false}}; keep raw.
        @SerialName("node_errors")
        val nodeErrors: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
    ) : WSMessage()


    @Serializable
    data class StatusMessage(
        val status: Status,
        val sid: String? = null,
        @SerialName("prompt_id")
        override val promptId: String? = null
    ) : WSMessage()

    @Serializable
    data class ExecutionStartMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val timestamp: Long
    ) : WSMessage()

    @Serializable
    data class ExecutionCachedMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val nodes: List<String>,
        val timestamp: Long
    ) : WSMessage()

    @Serializable
    data class ExecutingMessage(
        @SerialName("prompt_id")
        override val promptId: String? = null,
        // null once the prompt has run out of nodes - the completion marker.
        val node: String = ""
    ) : WSMessage()

    @Serializable
    data class ProgressMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val value: Double,
        val max: Double,
        val node: String = ""
    ) : WSMessage()

    /**
     * ComfyUI 0.43+ replaced the single-node "progress" event with a map of
     * every active node's state, sent as "progress_state".
     */
    @Serializable
    data class ProgressStateMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val nodes: Map<String, NodeProgress> = emptyMap()
    ) : WSMessage() {
        @Serializable
        // 0.43 sends fractional values and lowercase states
        // ("pending" / "running" / "finished").
        data class NodeProgress(
            val value: Double = 0.0,
            val max: Double = 0.0,
            val state: String = ""
        )

        /**
         * The node the progress bar should follow: a running one, preferring
         * the furthest along. Finished nodes (value == max) are skipped so a
         * quick node can't peg the bar at 100% while the sampler starts.
         */
        fun active(): NodeProgress? {
            val candidates = nodes.values.filter { it.max > 0 }
            return candidates.filter { it.state == "running" }
                .maxByOrNull { it.value / it.max }
                ?: candidates.filter { it.state != "finished" }.maxByOrNull { it.value / it.max }
        }
    }

    @Serializable
    data class ExecutedMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val node: String = "",
        val output: Output = Output()
    ) : WSMessage()

    /** Server's reply to our feature_flags handshake (server-side feature map). */
    data object FeatureFlagsMessage : WSMessage() {
        override val promptId: String? = null
    }

    @Serializable
    data class ExecutionSuccessMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val timestamp: Long
    ) : WSMessage()

    @Serializable
    data class ExecutionErrorMessage(
        @SerialName("prompt_id")
        override val promptId: String? = null,
        @SerialName("node_id")
        val nodeId: String? = null,
        @SerialName("node_type")
        val nodeType: String? = null,
        val executed: List<String> = emptyList(),
        @SerialName("exception_message")
        val exceptionMessage: String? = null,
        @SerialName("exception_type")
        val exceptionType: String? = null
    ) : WSMessage()

    @Serializable
    data class ExecutionInterruptedMessage(
        @SerialName("prompt_id")
        override val promptId: String? = null,
        val timestamp: Long
    ) : WSMessage()

    // Any server event we do not model yet (bypass, executor_*, ...): log it
    // instead of crashing the connection.
    data class Unknown(
        val type: String?,
        override val promptId: String? = null
    ) : WSMessage()

    @Serializable
    data class Status(
        @SerialName("exec_info")
        val execInfo: ExecInfo? = null
    )

    @Serializable
    data class ExecInfo(
        @SerialName("queue_remaining")
        val queueRemaining: Int
    )

    @Serializable
    data class Output(
        // 'executed' fires for EVERY finished node; intermediate nodes
        // (TextGenerate, PreviewAny, ...) report other keys or none at all.
        val images: List<Image> = emptyList()
    )

    @Serializable
    data class Image(
        val filename: String,
        val subfolder: String,
        val type: String
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            // Real servers send "node": null on the executing/executed
            // completion markers; coerce to the defaults instead of throwing.
            coerceInputValues = true
        }

        fun fromJson(jsonString: String): WSMessage {
            val baseMessage = json.decodeFromString<BaseMessage>(jsonString)
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            val data = jsonObject["data"]?.toString() ?: jsonString

            return when (baseMessage.type) {
                null -> json.decodeFromString<PromptMessage>(jsonString)
                "status" -> json.decodeFromString<StatusMessage>(data)
                "execution_start" -> json.decodeFromString<ExecutionStartMessage>(data)
                "execution_cached" -> json.decodeFromString<ExecutionCachedMessage>(data)
                "executing" -> json.decodeFromString<ExecutingMessage>(data)
                "progress" -> json.decodeFromString<ProgressMessage>(data)
                "progress_state" -> json.decodeFromString<ProgressStateMessage>(data)
                "executed" -> json.decodeFromString<ExecutedMessage>(data)
                "feature_flags" -> FeatureFlagsMessage
                "execution_success" -> json.decodeFromString<ExecutionSuccessMessage>(data)
                "execution_error" -> json.decodeFromString<ExecutionErrorMessage>(data)
                "execution_interrupted" -> json.decodeFromString<ExecutionInterruptedMessage>(data)
                else -> Unknown(baseMessage.type)
            }
        }
    }

    @Serializable
    private data class BaseMessage(
        val type: String? = null,
        @SerialName("prompt_id")
        val promptId: String? = null
    )
}

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
        val node: String
    ) : WSMessage()

    @Serializable
    data class ProgressMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val value: Int,
        val max: Int,
        val node: String
    ) : WSMessage()

    @Serializable
    data class ExecutedMessage(
        @SerialName("prompt_id")
        override val promptId: String,
        val node: String,
        val output: Output = Output()
    ) : WSMessage()

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
        private val json = Json { ignoreUnknownKeys = true }

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
                "executed" -> json.decodeFromString<ExecutedMessage>(data)
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

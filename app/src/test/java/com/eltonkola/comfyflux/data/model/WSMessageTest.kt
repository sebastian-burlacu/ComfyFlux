package com.eltonkola.comfyflux.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WSMessageTest {

    @Test
    fun testPromptMessageParsing() {
        val promptRequest = """
        {"prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "number": 163, "node_errors": {}}
        """

        val message = WSMessage.fromJson(promptRequest) as WSMessage.PromptMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals(163, message.number)
        assertEquals(emptyMap<String, String>(), message.nodeErrors)
    }

    @Test
    fun promptMessageSurvivesNestedNodeErrors() {
        val nested = """
        {"prompt_id": "9b2", "number": 3, "node_errors": {"471": {"errors": [{"type": "bad_input", "message": "nope"}], "valid": false}}}
        """
        val message = WSMessage.fromJson(nested) as WSMessage.PromptMessage
        assertEquals("9b2", message.promptId)
        assertEquals(setOf("471"), message.nodeErrors.keys)
    }

    @Test
    fun executedMessageWithoutImagesDecodes() {
        // 'executed' also fires for intermediate nodes (TextGenerate,
        // PreviewAny, ...); demanding 'images' crashed generation.
        val textOutput = """
        {"type": "executed", "data": {"node": "471", "prompt_id": "7fe2", "output": {"text": ["rewritten prompt"]}}}
        """
        val message = WSMessage.fromJson(textOutput) as WSMessage.ExecutedMessage
        assertEquals("471", message.node)
        assertEquals(emptyList<String>(), message.output.images.map { it.filename })

        val noOutput = """
        {"type": "executed", "data": {"node": "472", "prompt_id": "7fe2"}}
        """
        val second = WSMessage.fromJson(noOutput) as WSMessage.ExecutedMessage
        assertEquals(emptyList<String>(), second.output.images.map { it.filename })
    }

    @Test
    fun testStatusMessageParsing() {
        val status = """
        {"type": "status", "data": {"status": {"exec_info": {"queue_remaining": 1}}, "sid": "74fce948-fd4d-4104-93eb-388c54fd01e0"}}
        """

        val message = WSMessage.fromJson(status) as WSMessage.StatusMessage
        assertEquals(1, message.status.execInfo?.queueRemaining)
        assertEquals("74fce948-fd4d-4104-93eb-388c54fd01e0", message.sid)
    }

    @Test
    fun testExecutionStartMessageParsing() {
        val executionStart = """
        {"type": "execution_start", "data": {"prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "timestamp": 1723167670000}}
        """

        val message = WSMessage.fromJson(executionStart) as WSMessage.ExecutionStartMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals(1723167670000L, message.timestamp)
    }

    @Test
    fun testExecutionCachedMessageParsing() {
        val executionCached = """
        {"type": "execution_cached", "data": {"nodes": ["5", "16", "10", "11"], "prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "timestamp": 1723167675096}}
        """

        val message = WSMessage.fromJson(executionCached) as WSMessage.ExecutionCachedMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals(listOf("5", "16", "10", "11"), message.nodes)
        assertEquals(1723167675096L, message.timestamp)
    }

    @Test
    fun testExecutingMessageParsing() {
        val executing = """
        {"type": "executing", "data": {"node": "6", "prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c"}}
        """

        val message = WSMessage.fromJson(executing) as WSMessage.ExecutingMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals("6", message.node)
    }

    @Test
    fun testExecutingMessageWithoutPromptId() {
        val executing = """
        {"type": "executing", "data": {"node": "6", "prompt_id": null}}
        """

        val message = WSMessage.fromJson(executing) as WSMessage.ExecutingMessage
        assertEquals("6", message.node)
    }

    @Test
    fun testProgressMessageParsing() {
        val progress = """
        {"type": "progress", "data": {"value": 4, "max": 4, "prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "node": "13"}}
        """

        val message = WSMessage.fromJson(progress) as WSMessage.ProgressMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals(4.0, message.value, 0.0001)
        assertEquals(4.0, message.max, 0.0001)
        assertEquals("13", message.node)
    }

    // Server payloads seen in the wild (0.43): null node markers and
    // fractional progress_state values must not break the session.
    @Test
    fun executingCompletionMarkerHasNullNode() {
        val m = WSMessage.fromJson(
            """{"type": "executing", "data": {"node": null, "prompt_id": "ef39f6e2-c6bf-48ee-9f58-e3991ac76799"}}"""
        ) as WSMessage.ExecutingMessage
        assertEquals("ef39f6e2-c6bf-48ee-9f58-e3991ac76799", m.promptId)
        assertEquals("", m.node)
    }

    @Test
    fun executedMarkerToleratesNullNode() {
        val m = WSMessage.fromJson(
            """{"type": "executed", "data": {"node": null, "prompt_id": "ef39f6e2-c6bf-48ee-9f58-e3991ac76799"}}"""
        ) as WSMessage.ExecutedMessage
        assertEquals("ef39f6e2-c6bf-48ee-9f58-e3991ac76799", m.promptId)
        assertEquals(emptyList<WSMessage.Image>(), m.output.images)
    }

    @Test
    fun progressStateHandlesFloatsAndPicksTheRunningNode() {
        val m = WSMessage.fromJson(
            """{"type": "progress_state", "data": {"prompt_id": "cb", "nodes": {
              "57_13": {"value": 1.0, "max": 1.0, "state": "finished"},
              "9": {"value": 3.0, "max": 28.0, "state": "running"}
            }}}"""
        ) as WSMessage.ProgressStateMessage
        val active = m.active()!!
        assertEquals(3.0, active.value, 0.0001)
        assertEquals(28.0, active.max, 0.0001)
        assertEquals("running", active.state)
    }

    @Test
    fun featureFlagsAckIsRecognized() {
        val m = WSMessage.fromJson(
            """{"type": "feature_flags", "data": {"supports_preview_metadata": true}}"""
        )
        assertEquals(WSMessage.FeatureFlagsMessage, m)
    }

    @Test
    fun testExecutedMessageParsing() {
        val executed = """
        {"type": "executed", "data": {"node": "9", "output": {"images": [{"filename": "ComfyUI_00946_.png", "subfolder": "", "type": "output"}]}, "prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c"}}
        """

        val message = WSMessage.fromJson(executed) as WSMessage.ExecutedMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals("9", message.node)
        assertEquals(1, message.output.images.size)
        assertEquals("ComfyUI_00946_.png", message.output.images[0].filename)
        assertEquals("", message.output.images[0].subfolder)
        assertEquals("output", message.output.images[0].type)
    }

    @Test
    fun executedMessageCarriesVideoAndAudioOutputs() {
        val executed = """
        {"type": "executed", "data": {"node": "12", "output": {
            "video": [{"filename": "video/ComfyUI_00001_.mp4", "subfolder": "", "type": "output"}],
            "audio": [{"filename": "audio/ComfyUI_00002_.flac", "subfolder": "", "type": "output"}]
        }, "prompt_id": "9b1c"}}
        """

        val message = WSMessage.fromJson(executed) as WSMessage.ExecutedMessage
        assertEquals("12", message.node)
        assertEquals("video/ComfyUI_00001_.mp4", message.output.video.single().filename)
        assertEquals("audio/ComfyUI_00002_.flac", message.output.audio.single().filename)
        assertEquals(emptyList<WSMessage.Image>(), message.output.images)
    }

    @Test
    fun testExecutionSuccessMessageParsing() {
        val executionSuccess = """
        {"type": "execution_success", "data": {"prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "timestamp": 1723167762147}}
        """

        val message = WSMessage.fromJson(executionSuccess) as WSMessage.ExecutionSuccessMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals(1723167762147L, message.timestamp)
    }

    @Test
    fun testExecutionErrorMessageParsing() {
        val executionError = """
        {"type": "execution_error", "data": {"prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "node_id": "12", "node_type": "UNETLoader", "executed": ["5", "6"], "exception_message": "File not found: flux1-dev.safetensors", "exception_type": "FileNotFoundError", "traceback": ["  File ..."]}}
        """

        val message = WSMessage.fromJson(executionError) as WSMessage.ExecutionErrorMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals("12", message.nodeId)
        assertEquals("UNETLoader", message.nodeType)
        assertEquals(listOf("5", "6"), message.executed)
        assertEquals("File not found: flux1-dev.safetensors", message.exceptionMessage)
        assertEquals("FileNotFoundError", message.exceptionType)
    }

    @Test
    fun testExecutionInterruptedMessageParsing() {
        val interrupted = """
        {"type": "execution_interrupted", "data": {"prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "timestamp": 1723167765000}}
        """

        val message = WSMessage.fromJson(interrupted) as WSMessage.ExecutionInterruptedMessage
        assertEquals("f79020a5-b9ca-465d-9fb0-59c9ffef379c", message.promptId)
        assertEquals(1723167765000L, message.timestamp)
    }

    @Test
    fun testUnknownMessageDoesNotThrow() {
        val bypass = """
        {"type": "bypass", "data": {"node": "7", "nodes": ["7"], "prompt_id": "f79020a5-b9ca-465d-9fb0-59c9ffef379c", "epoch": 0}}
        """

        val message = WSMessage.fromJson(bypass) as WSMessage.Unknown
        assertEquals("bypass", message.type)
        assertTrue(WSMessage.fromJson("""{"type": "executor_what_is_this"}""") is WSMessage.Unknown)
    }
}

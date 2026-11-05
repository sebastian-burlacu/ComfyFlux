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
        assertEquals(4, message.value)
        assertEquals(4, message.max)
        assertEquals("13", message.node)
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

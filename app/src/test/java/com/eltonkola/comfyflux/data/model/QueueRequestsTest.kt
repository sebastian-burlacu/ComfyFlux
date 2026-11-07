package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Queue bodies for ComfyUI 0.43: delete matches the PROMPT id (not the queue
 * position), and ordering is only choosable at submit time via `front`.
 */
class QueueRequestsTest {

    @Test
    fun deleteBodyCarriesThePromptId() {
        val body = Json.parseToJsonElement(queueDeleteBody("abc-123")).jsonObject

        assertEquals(listOf("abc-123"), body["delete"]!!.jsonArray.map { it.toString().trim('"') })
    }

    @Test
    fun frontIsAppendedOnlyWhenRequested() {
        assertEquals("{\"prompt\":{}}", promptPostBody("{\"prompt\":{}}", front = false))
        assertEquals("{\"prompt\":{},\"front\":true}", promptPostBody("{\"prompt\":{}}", front = true))
        assertEquals("not-an-object", promptPostBody("not-an-object", front = true))
    }
}

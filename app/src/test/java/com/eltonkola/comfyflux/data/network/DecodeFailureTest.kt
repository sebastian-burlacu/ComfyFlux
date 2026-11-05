package com.eltonkola.comfyflux.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a user sees in the Logs screen / error banner when a ComfyUI endpoint
 * answers with something that isn't valid JSON. An empty body right after a
 * restart is the most common case and must not read like a generic parse bug.
 */
class DecodeFailureTest {

    @Test
    fun emptyBodyPointsAtServerRestart() {
        val msg = decodeFailureReason("", "/system_stats", 200)
        assertTrue(msg, msg.contains("/system_stats"))
        assertTrue(msg, msg.contains("empty response"))
        assertTrue(msg, msg.contains("restarting"))
        assertTrue(msg, msg.contains("HTTP 200"))
    }

    @Test
    fun whitespaceBodyIsTreatedAsEmpty() {
        val msg = decodeFailureReason("  \n\t ", "/history", 502)
        assertTrue(msg, msg.contains("empty response"))
        assertTrue(msg, msg.contains("HTTP 502"))
    }

    @Test
    fun garbageBodyShowsSizeAndHead() {
        val body = "<html><body>502 Bad Gateway</body></html>" + "x".repeat(500)
        val msg = decodeFailureReason(body, "/queue", 502)
        assertTrue(msg, msg.contains("/queue"))
        assertTrue(msg, msg.contains("invalid JSON"))
        assertTrue(msg, msg.contains("after ${body.length} chars"))
        assertTrue(msg, msg.contains("HTTP 502"))
        assertTrue(msg, msg.contains("<html><body>502 Bad Gateway"))
    }

    @Test
    fun longGarbageBodyIsExcerptedNotDumped() {
        val msg = decodeFailureReason("y".repeat(5000), "/object_info", 200)
        assertTrue(msg, msg.contains("after 5000 chars"))
        assertFalse(msg, msg.contains("y".repeat(121)))
    }
}

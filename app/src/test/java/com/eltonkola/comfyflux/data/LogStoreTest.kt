package com.eltonkola.comfyflux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogStoreTest {

    @Test
    fun appendsInOrder() {
        val store = LogStore(capacity = 10)
        store.info("server", "one")
        store.error("client", "two")
        val entries = store.entries.value
        assertEquals(2, entries.size)
        assertEquals("one", entries.first().message)
        assertEquals("two", entries.last().message)
        assertEquals(LogLevel.ERROR, entries.last().level)
        assertEquals("client", entries.last().source)
    }

    @Test
    fun keepsNewestWhenOverCapacity() {
        val store = LogStore(capacity = 3)
        (1..5).forEach { store.info("server", "event $it") }
        val entries = store.entries.value
        assertEquals(3, entries.size)
        assertEquals(listOf("event 3", "event 4", "event 5"), entries.map { it.message })
    }

    @Test
    fun truncatesHugeMessages() {
        val store = LogStore()
        store.error("server", "x".repeat(LogEntry.MAX_MESSAGE_LENGTH + 500))
        assertEquals(LogEntry.MAX_MESSAGE_LENGTH, store.entries.value.single().message.length)
    }

    @Test
    fun clearEmptiesTheLog() {
        val store = LogStore()
        store.error("client", "boom")
        store.clear()
        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun debugEntriesOnlyStoredWhenVerbose() {
        val store = LogStore()
        store.debug("client", "silent")
        assertTrue(store.entries.value.isEmpty())
        store.verbose = true
        store.debug("client", "traced")
        assertEquals(LogLevel.DEBUG, store.entries.value.single().level)
    }

    @Test
    fun exportTextHasHeaderAndFormattedLines() {
        val store = LogStore()
        store.verbose = true
        store.info("server", "connected")
        store.error("client", "boom")
        val text = store.exportText()
        assertTrue(text.startsWith("ComfyFlux log export"))
        assertTrue(text.contains("(2 entries, verbose=true)"))
        assertTrue(text.contains("INFO [server] connected"))
        assertTrue(text.contains("ERROR [client] boom"))
        assertTrue(text.lines().size == 3) // header + two entries
    }

    @Test
    fun exceptionChainShowsCauseAndFrames() {
        val root = IllegalStateException("root cause")
        val outer = RuntimeException("outer", root)
        val chain = exceptionChain(outer)
        assertTrue(chain.startsWith("java.lang.RuntimeException: outer"))
        assertTrue(chain.contains("Caused by: java.lang.IllegalStateException: root cause"))
        assertTrue(chain.contains("at "))
    }
}

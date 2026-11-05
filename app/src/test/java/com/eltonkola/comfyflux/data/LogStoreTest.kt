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
}

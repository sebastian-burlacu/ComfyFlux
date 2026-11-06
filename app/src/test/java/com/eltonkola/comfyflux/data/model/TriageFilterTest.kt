package com.eltonkola.comfyflux.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TriageFilterTest {

    private val history = listOf(
        HistoryItem(emptyList(), true, true, Workflow(), "a"),
        HistoryItem(emptyList(), true, true, Workflow(), "b"),
        HistoryItem(emptyList(), true, true, Workflow(), "c"),
        HistoryItem(emptyList(), false, false, Workflow(), "d")
    )

    private val marks = mapOf("a" to Triage.Kept, "b" to Triage.Discarded)

    @Test
    fun filtersSplitTheListByMark() {
        assertEquals(listOf("a", "b", "c", "d"), filterHistory(history, marks, TriageFilter.All).map { it.id })
        assertEquals(listOf("a"), filterHistory(history, marks, TriageFilter.Kept).map { it.id })
        assertEquals(listOf("b"), filterHistory(history, marks, TriageFilter.Discarded).map { it.id })
        assertEquals(listOf("c", "d"), filterHistory(history, marks, TriageFilter.Unmarked).map { it.id })
    }

    @Test
    fun chipCountsCoverEveryRunExactlyOnce() {
        val counts = triageCounts(history, marks)
        assertEquals(4, counts[TriageFilter.All])
        assertEquals(1, counts[TriageFilter.Kept])
        assertEquals(1, counts[TriageFilter.Discarded])
        assertEquals(2, counts[TriageFilter.Unmarked])
        assertEquals(
            history.size,
            counts[TriageFilter.Kept]!! + counts[TriageFilter.Discarded]!! + counts[TriageFilter.Unmarked]!!
        )
    }

    @Test
    fun marksOfRunsThatLeftTheServerShowAsUnmarked() {
        // A mark keyed to a deleted run must not make the Unmarked filter lie.
        val stale = mapOf("zz" to Triage.Kept)
        assertEquals(emptyList<String>(), filterHistory(history, stale, TriageFilter.Kept).map { it.id })
        assertEquals(4, filterHistory(history, stale, TriageFilter.Unmarked).size)
    }
}

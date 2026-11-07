package com.eltonkola.comfyflux.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rule behind "notify for a run that started elsewhere". */
class QueueWatchTest {

    @Test
    fun aForeignJobThatLeftTheEmptyRunningListIsReported() {
        val finished = finishedForeignRuns(setOf("aaa"), emptySet()) { false }

        assertEquals(listOf("aaa"), finished)
    }

    @Test
    fun ourOwnFinishedJobIsNotReportedAgain() {
        val finished = finishedForeignRuns(setOf("aaa"), emptySet()) { it == "aaa" }

        assertEquals(emptyList<String>(), finished)
    }

    @Test
    fun nothingCountsAsFinishedWhileTheServerStillRuns() {
        // The next queued job started; the previous one is not "done" yet as
        // far as the user is concerned, and our own session keeps notifying.
        val finished = finishedForeignRuns(setOf("aaa"), setOf("bbb")) { false }

        assertEquals(emptyList<String>(), finished)
    }

    @Test
    fun anEmptyServerStaysSilent() {
        assertEquals(emptyList<String>(), finishedForeignRuns(emptySet(), emptySet()) { false })
    }
}

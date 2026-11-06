package com.eltonkola.comfyflux.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSettlementTest {

    @Test
    fun queuedJobsSettleOneByOne() {
        val settlement = PromptSettlement()
        settlement.add("a")
        settlement.add("b")
        settlement.add("c")
        assertTrue(settlement.contains("b"))
        assertEquals(3, settlement.count())

        // First job finishes, two stay queued: the session must keep listening.
        assertFalse(settlement.settle("a"))
        // An unknown id must not settle somebody else's job.
        assertFalse(settlement.settle("zzz"))
        // Interrupt/error events sometimes omit the id: settle the oldest.
        assertFalse(settlement.settle(null))
        assertTrue(settlement.contains("c"))
        assertFalse(settlement.contains("b"))

        // Last job settled: session can close.
        assertTrue(settlement.settle("c"))
        assertTrue(settlement.isEmpty())
    }

    @Test
    fun settlingAnEmptySetIsAlreadyDone() {
        val settlement = PromptSettlement()
        assertTrue(settlement.settle(null))
        assertTrue(settlement.settle("x"))
        assertEquals(0, settlement.count())
    }
}

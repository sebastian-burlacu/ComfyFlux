package com.eltonkola.comfyflux.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BundledWorkflowsTest {

    private val bundled = WorkflowFile("4 steps Flux", "fast", "file:///android_asset/w.json", true)
    private val server =
        WorkflowFile("Mine", "saved", "server://mine", isAsset = false, isServer = true)
    private val local = WorkflowFile("Local", "local workflow", "content://1", isAsset = false)

    @Test
    fun offKeepsEveryWorkflow() {
        val all = listOf(bundled, server, local)
        assertEquals(all, all.withoutBundled(false))
    }

    @Test
    fun onDropsOnlyBundled() {
        assertEquals(
            listOf(server, local),
            listOf(bundled, server, local).withoutBundled(true)
        )
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(emptyList<WorkflowFile>(), emptyList<WorkflowFile>().withoutBundled(true))
        assertEquals(emptyList<WorkflowFile>(), emptyList<WorkflowFile>().withoutBundled(false))
    }

    @Test
    fun onlyBundledBecomesEmpty() {
        assertEquals(
            emptyList<WorkflowFile>(),
            listOf(bundled, bundled.copy(name = "Other")).withoutBundled(true)
        )
    }
}

package com.eltonkola.comfyflux.app.usecase

import com.eltonkola.comfyflux.data.GenerationDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerWorkflowRestoreTest {

    private fun serverDraft(name: String) = GenerationDraft(
        prompt = "p", seed = 1, isRandom = true, width = 512, height = 512,
        batchSize = 1, cfg = 0f, steps = 0,
        workflowName = name, workflowUri = "server://$name", workflowIsServer = true
    )

    @Test
    fun restoresServerDraftWhenStillPresent() {
        assertEquals(
            "krea-2-turbo-v2",
            pickRestoredServerWorkflow(serverDraft("krea-2-turbo-v2"), listOf("image_qwen", "krea-2-turbo-v2"))
        )
    }

    @Test
    fun skipsServerDraftDeletedFromServer() {
        assertEquals(null, pickRestoredServerWorkflow(serverDraft("gone"), listOf("image_qwen")))
    }

    @Test
    fun assetDraftsAreHandledBeforeListing() {
        val assetDraft = GenerationDraft(
            prompt = "p", seed = 1, isRandom = true, width = 512, height = 512,
            batchSize = 1, cfg = 0f, steps = 0,
            workflowName = "flux_dev", workflowUri = "asset://flux_dev", workflowIsServer = false
        )
        assertEquals(null, pickRestoredServerWorkflow(assetDraft, listOf("flux_dev")))
        assertEquals(null, pickRestoredServerWorkflow(null, listOf("flux_dev")))
    }
}

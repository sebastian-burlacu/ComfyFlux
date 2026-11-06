package com.eltonkola.comfyflux.data.network

import com.eltonkola.comfyflux.data.model.WSMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureDetailTest {

    @Test
    fun shapeFailuresNameTheNodeAndExplainTheMismatch() {
        val detail = failureDetail(
            WSMessage.ExecutionErrorMessage(
                promptId = "p",
                nodeId = "459_458",
                nodeType = "KSampler",
                executed = listOf("459_451", "459_453"),
                exceptionType = "RuntimeError",
                exceptionMessage = "Given normalized_shape=[3584], expected input with shape [*, 3584], but got input of size[1, 3345, 2560]"
            )
        )
        assertTrue(detail.contains("RuntimeError: Given normalized_shape"))
        assertTrue(detail.contains("Failed node: 459_458 (KSampler)"))
        assertTrue(detail.contains("Completed first: 459_451, 459_453"))
        assertTrue(detail.contains("Shape mismatch"))
    }

    @Test
    fun oomAndListFailuresGetTheirOwnHint() {
        val oom = failureDetail(
            WSMessage.ExecutionErrorMessage(exceptionType = "torch.cuda.OutOfMemoryError", exceptionMessage = "CUDA out of memory. Tried to allocate 2 GiB")
        )
        assertTrue(oom.contains("GPU ran out"))
        val list = failureDetail(
            WSMessage.ExecutionErrorMessage(nodeType = "UNETLoader", exceptionType = "ValueError", exceptionMessage = "Value not in list: unet_name must be one of [...]")
        )
        assertTrue(list.contains("not offered by this server"))
    }
}

package com.eltonkola.comfyflux.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

/**
 * POST /queue {"delete": [...]} - ComfyUI 0.43 matches `a[1] == id`, i.e. the
 * PROMPT ID, not the queue position. Deleting by position silently no-ops,
 * which is exactly what v1.32 shipped; every job's prompt id is in the queue
 * response, so this always works.
 */
internal fun queueDeleteBody(promptId: String): String =
    buildJsonObject {
        putJsonArray("delete") { add(promptId) }
    }.toString()

/**
 * POST /prompt body, optionally queued at the front: ComfyUI assigns a
 * negative number when `front` is set, so the job runs before everything
 * pending. That is the only ordering control 0.43 offers - already queued
 * jobs cannot be reordered, so it has to be chosen at submit time.
 */
internal fun promptPostBody(promptJson: String, front: Boolean): String {
    if (!front) return promptJson
    // Append the key to the object body; the client_id/prompt members stay
    // byte-identical to what the request already had.
    val trimmed = promptJson.trim()
    if (!trimmed.endsWith("}")) return promptJson
    return trimmed.dropLast(1).trimEnd() + ",\"front\":true}"
}

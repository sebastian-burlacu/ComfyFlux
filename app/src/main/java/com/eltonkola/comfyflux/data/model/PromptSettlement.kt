package com.eltonkola.comfyflux.data.model

/**
 * Bookkeeping for the generations this app queued and the server has not
 * finished yet. Repeated Create taps add prompt ids to the same session; the
 * websocket connection stays open until every id settles (success, error or
 * interrupt), so queued jobs keep streaming progress one after another.
 */
class PromptSettlement {

    private val active = LinkedHashSet<String>()

    fun add(promptId: String) = synchronized(active) { active.add(promptId) }

    fun contains(promptId: String) = synchronized(active) { promptId in active }

    fun isEmpty() = synchronized(active) { active.isEmpty() }

    fun count() = synchronized(active) { active.size }

    /**
     * Marks one job as settled. A null id (interrupted/error events sometimes
     * omit it) settles the oldest queued job. Returns true when nothing is
     * left, i.e. the websocket session can close.
     */
    fun settle(promptId: String?): Boolean = synchronized(active) {
        if (promptId == null) {
            if (active.isNotEmpty()) active.remove(active.first())
        } else {
            active.remove(promptId)
        }
        active.isEmpty()
    }
}

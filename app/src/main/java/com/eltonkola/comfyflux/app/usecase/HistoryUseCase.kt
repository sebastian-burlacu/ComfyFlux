package com.eltonkola.comfyflux.app.usecase

import com.eltonkola.comfyflux.data.model.HistoryUiState
import com.eltonkola.comfyflux.data.model.Triage
import com.eltonkola.comfyflux.data.model.TriageFilter
import com.eltonkola.comfyflux.data.model.TriageStore
import com.eltonkola.comfyflux.data.network.FluxAPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HistoryUseCase(
    private val fluxAPI: FluxAPI,
    private val triageStore: TriageStore
) {
    private val _historyUiState = MutableStateFlow(HistoryUiState())
    val historyUiState: StateFlow<HistoryUiState> = _historyUiState.asStateFlow()

    suspend fun loadHistory(silentUpdate: Boolean = false, onDone: () -> Unit) {
        if (silentUpdate) {
            _historyUiState.update { it.copy(silentLoading = true) }
        } else {
            _historyUiState.update { it.copy(loading = true) }
        }
        try {
            val history = fluxAPI.fetchHistory()
            _historyUiState.update {
                it.copy(
                    loading = false,
                    history = history.reversed(),
                    error = false,
                    silentLoading = false,
                    // Marks only matter while their run still exists.
                    triage = triageStore.all().filterKeys { id ->
                        history.any { item -> item.id == id }
                    }
                )
            }
        } catch (e: Exception) {
            _historyUiState.update { it.copy(loading = false, error = true, silentLoading = false) }
        }
        onDone()
    }

    /** Keep / discard one run on the phone (null clears the mark). */
    fun setTriage(id: String, mark: Triage?) {
        triageStore.mark(id, mark)
        _historyUiState.update { state ->
            val marks = state.triage.toMutableMap()
            if (mark == null) marks.remove(id) else marks[id] = mark
            state.copy(triage = marks)
        }
    }

    fun setTriageFilter(filter: TriageFilter) {
        _historyUiState.update { it.copy(triageFilter = filter) }
    }

    /** Runs marked discarded: candidates for a bulk server delete. */
    fun discardedIds(): List<String> =
        _historyUiState.value.triage.filterValues { it == Triage.Discarded }.keys.toList()

    /** Forgets the local marks of runs that were just deleted. */
    fun forgetTriage(ids: List<String>) {
        ids.forEach { triageStore.mark(it, null) }
        _historyUiState.update { state ->
            state.copy(triage = state.triage.filterKeys { it !in ids })
        }
    }
}
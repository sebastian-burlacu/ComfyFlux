package com.eltonkola.comfyflux.app.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.eltonkola.comfyflux.data.PromptRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class PromptSearchUseCase(
    private val promptRepo: PromptRepo
) {
    private val queryFlow = MutableStateFlow("")

    // One pager per query: flatMapLatest cancels the previous search instead
    // of leaving a never-ending collector per keystroke.
    val results: Flow<PagingData<String>> = queryFlow
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(pageSize = 100),
                pagingSourceFactory = { promptRepo.getPagingSource(query) }
            ).flow
        }

    fun search(query: String) {
        queryFlow.value = query
    }
}

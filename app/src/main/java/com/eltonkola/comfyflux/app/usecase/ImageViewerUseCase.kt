package com.eltonkola.comfyflux.app.usecase

import com.eltonkola.comfyflux.data.model.ImageViewerUiState
import com.eltonkola.comfyflux.data.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ImageViewerUseCase {

    private val _imageUiState = MutableStateFlow(ImageViewerUiState())
    val imageUiState: StateFlow<ImageViewerUiState> = _imageUiState.asStateFlow()

    fun viewMedia(media: List<MediaItem> = emptyList(), selected: Int = 0) {
        _imageUiState.value = ImageViewerUiState(media, selected)
    }

}
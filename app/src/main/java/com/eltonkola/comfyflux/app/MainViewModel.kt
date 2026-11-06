package com.eltonkola.comfyflux.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.eltonkola.comfyflux.data.AppSettings
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.data.PromptRepo
import com.eltonkola.comfyflux.data.SettingsState
import com.eltonkola.comfyflux.data.model.HistoryItem
import com.eltonkola.comfyflux.data.model.HistoryUiState
import com.eltonkola.comfyflux.data.model.ImageGenerationUiState
import com.eltonkola.comfyflux.data.model.ImageViewerUiState
import com.eltonkola.comfyflux.data.model.ProgressGenerationUIState
import com.eltonkola.comfyflux.data.model.Queue
import com.eltonkola.comfyflux.data.model.QueueUiState
import com.eltonkola.comfyflux.data.model.WorkflowFile
import com.eltonkola.comfyflux.data.network.FluxAPI
import com.eltonkola.comfyflux.app.usecase.HistoryUseCase
import com.eltonkola.comfyflux.app.usecase.ImageViewerUseCase
import com.eltonkola.comfyflux.app.usecase.MainUseCase
import com.eltonkola.comfyflux.app.usecase.PromptSearchUseCase
import com.eltonkola.comfyflux.app.usecase.QueueUseCase
import com.eltonkola.comfyflux.app.usecase.TimerUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class MainViewModel(
    private val applicationContext: Application,
    private val fluxAPI: FluxAPI,
    private val promptRepo: PromptRepo,
    private val promptSearchUseCase: PromptSearchUseCase,
    private val historyUseCase: HistoryUseCase,
    private val imageViewerUseCase: ImageViewerUseCase,
    private val queueUseCase: QueueUseCase,
    private val mainUseCase: MainUseCase,
    private val appSettings: AppSettings,
    private val logStore: LogStore,
    private val timerUseCase: TimerUseCase
) : AndroidViewModel(applicationContext) {

    val promptSearchPaging: StateFlow<PagingData<String>> = promptSearchUseCase.results
        .cachedIn(viewModelScope)
        .stateIn(viewModelScope, SharingStarted.Lazily, PagingData.empty())
    val historyUiState: StateFlow<HistoryUiState> = historyUseCase.historyUiState
    val progressUiState: StateFlow<ProgressGenerationUIState> = fluxAPI.progressUiState
    val imageUiState: StateFlow<ImageViewerUiState> = imageViewerUseCase.imageUiState
    val queueUiState: StateFlow<QueueUiState> = queueUseCase.queueUiState
    val uiState: StateFlow<ImageGenerationUiState> = mainUseCase.uiState
    val settingsState: StateFlow<SettingsState> = appSettings.settingsState
    val timer: StateFlow<String> = timerUseCase.timer

    init {
        checkStatus()
    }

    fun searchLines(query: String) {
        promptSearchUseCase.search(query)
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            mainUseCase.updateServerUrl(url)
        }
    }

    fun viewImage(images: List<String> = emptyList(), selected: Int = 0) {
        imageViewerUseCase.viewImage(images, selected)
    }

    fun updatePrompt(prompt: String) {
        viewModelScope.launch {
            mainUseCase.updatePrompt(prompt)
        }
    }

    fun updateWorkflow(workflow: WorkflowFile) {
        viewModelScope.launch {
            mainUseCase.updateWorkflow(workflow)
            mainUseCase.refreshModelSlots()
        }
    }

    fun setModelChoice(key: String, value: String) {
        mainUseCase.setModelChoice(key, value)
    }

    fun setWorkflowToggle(key: String, value: Boolean) {
        mainUseCase.setWorkflowToggle(key, value)
    }

    fun updateNegativePrompt(prompt: String) {
        mainUseCase.updateNegativePrompt(prompt)
    }

    fun setImageWidth(width: Int) {
        viewModelScope.launch {
            mainUseCase.setImageWidth(width)
        }
    }

    fun setImageHeight(height: Int) {
        viewModelScope.launch {
            mainUseCase.setImageHeight(height)
        }
    }

    fun setBatchSize(batchSize: Int) {
        viewModelScope.launch {
            mainUseCase.setBatchSize(batchSize)
        }
    }

    fun setSeed(seed: Long, isRandom: Boolean) {
        viewModelScope.launch {
            mainUseCase.setSeed(seed, isRandom)
        }
    }

    fun incrementSeed() {
        mainUseCase.incrementSeed()
    }

    fun setCfg(cfg: Float) {
        mainUseCase.setCfg(cfg)
    }

    fun setSteps(steps: Int) {
        mainUseCase.setSteps(steps)
    }

    fun interruptImages() {
        viewModelScope.launch {
            fluxAPI.interrupt()
            checkStatus()
        }
    }

    fun generateImages() {
        viewModelScope.launch {
            mainUseCase.generateImages(
                start = {
                    viewModelScope.launch {
                        timerUseCase.resetTimer()
                        timerUseCase.startTimer()
                    }
                },
                stop = {
                    timerUseCase.stopTimer()
                }
            )
        }
    }

    fun enrichPrompt() {
        viewModelScope.launch {
            mainUseCase.enrichPrompt()
        }
    }

    fun checkStatus() {
        viewModelScope.launch {
            mainUseCase.checkStatus()
        }
    }

    fun setInputImage(slotId: String, uriString: String) {
        mainUseCase.setInputImage(slotId, uriString)
        if (uriString.isNotBlank()) {
            viewModelScope.launch { mainUseCase.adaptSizeToInputImage(uriString) }
        }
    }

    /** One photo for every LoadImage slot (branches that share one input). */
    fun setAllInputImages(uriString: String) {
        mainUseCase.setAllInputImages(uriString)
        if (uriString.isNotBlank()) {
            viewModelScope.launch { mainUseCase.adaptSizeToInputImage(uriString) }
        }
    }

    val templateState = mainUseCase.templateState

    fun toggleTemplateKind(kind: com.eltonkola.comfyflux.data.network.TemplateKind) =
        mainUseCase.toggleTemplateKind(kind)

    fun refreshTemplates(force: Boolean = false) {
        viewModelScope.launch { mainUseCase.refreshTemplates(force) }
    }

    fun importTemplate(entry: com.eltonkola.comfyflux.data.network.TemplateEntry) {
        viewModelScope.launch { mainUseCase.importTemplate(entry) }
    }

    fun randomSeed() {
        mainUseCase.randomSeed()
    }

    fun loadHistory(silentUpdate: Boolean = false) {
        viewModelScope.launch {
            historyUseCase.loadHistory(silentUpdate) {
                checkStatus()
            }
        }
    }

    fun loadQueue(showLoading: Boolean = true) {
        viewModelScope.launch {
            queueUseCase.loadQueue(showLoading) {
                checkStatus()
            }
        }
    }

    fun cancelQueueWorkflow(item: Queue.Item) {
        viewModelScope.launch {
            fluxAPI.cancelWorkflow(item.id)
            loadQueue(showLoading = false)
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            mainUseCase.clearQueue()
            loadQueue(showLoading = false)
        }
    }

    fun rerunFromHistory(item: HistoryItem) {
        viewModelScope.launch {
            mainUseCase.rerunImages(
                item,
                start = {
                    viewModelScope.launch {
                        timerUseCase.resetTimer()
                        timerUseCase.startTimer()
                    }
                },
                stop = {
                    timerUseCase.stopTimer()
                }
            )
            loadHistory(silentUpdate = true)
        }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            fluxAPI.deleteHistory(id)
            loadHistory(silentUpdate = true)
        }
    }

    fun randomPrompt() {
        viewModelScope.launch {
            val prompt = promptRepo.randomPrompt()
            updatePrompt(prompt)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerUseCase.resetTimer()
    }

    fun updateSystemTheme(system: Boolean) {
        appSettings.setSystemTheme(system)
    }

    fun updateDarkTheme(dark: Boolean) {
        appSettings.setDarkTheme(dark)
    }

    fun updateDynamicColor(dynamic: Boolean) {
        appSettings.setDynamicColor(dynamic)
    }

    fun updateVerboseLogging(verbose: Boolean) {
        appSettings.setVerboseLogging(verbose)
        logStore.verbose = verbose
    }

    fun updateLogBodies(enabled: Boolean) {
        appSettings.setLogBodies(enabled)
        logStore.bodies = enabled
    }

    fun setGrqApiKey(key: String) {
        appSettings.setGroqApiKey(key)
    }

    fun setEnhanceEndpoint(endpoint: String) {
        appSettings.setEnhanceEndpoint(endpoint)
    }

    fun setEnhanceModel(model: String) {
        appSettings.setEnhanceModel(model)
    }

}

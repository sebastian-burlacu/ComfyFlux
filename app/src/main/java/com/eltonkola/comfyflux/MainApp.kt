package com.eltonkola.comfyflux

import android.app.Application
import android.content.Context
import coil.Coil
import com.eltonkola.comfyflux.app.MainViewModel
import com.eltonkola.comfyflux.app.galleryImageLoader
import com.eltonkola.comfyflux.app.presets.PresetStore
import com.eltonkola.comfyflux.app.presets.PresetUseCase
import com.eltonkola.comfyflux.app.usecase.HistoryUseCase
import com.eltonkola.comfyflux.app.usecase.ImageViewerUseCase
import com.eltonkola.comfyflux.app.usecase.MainUseCase
import com.eltonkola.comfyflux.app.usecase.PromptSearchUseCase
import com.eltonkola.comfyflux.app.usecase.QueueUseCase
import com.eltonkola.comfyflux.app.usecase.TimerUseCase
import com.eltonkola.comfyflux.data.AppSettings
import com.eltonkola.comfyflux.data.AppState
import com.eltonkola.comfyflux.data.CrashJournal
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.data.model.TriageStore
import com.eltonkola.comfyflux.data.Notifier
import com.eltonkola.comfyflux.data.PromptRepo
import com.eltonkola.comfyflux.data.network.FluxAPI
import com.eltonkola.comfyflux.data.network.GroqAPI
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class MainApp : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            // Log Koin into Android logger
            androidLogger()
            // Reference Android context
            androidContext(this@MainApp)
            // Load modules
            modules(appModule)
        }

        // One Coil loader for every screen: shared memory + disk cache, so a
        // history image seen once never hits the network again.
        Coil.setImageLoader(galleryImageLoader(this))

        // Track foreground/background so finished generations can notify when the
        // app is not in front.
        val appState: AppState = get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appState)

        // Re-arm verbose logging from persisted settings.
        val appSettings: AppSettings = get()
        val logStore: LogStore = get()
        logStore.verbose = appSettings.settingsState.value.verboseLogging
        logStore.bodies = appSettings.settingsState.value.logBodies

        // A crash kills the process, and the in-memory log with it: record the
        // trace on disk first, then let the system handle the death. The last
        // traces are replayed into the log on the next start, so Settings ->
        // Logs (and its export file) can show what actually broke.
        val crashJournal: CrashJournal = get()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashJournal.record(thread.name, error) }
            previousHandler?.uncaughtException(thread, error)
        }
        crashJournal.recent().forEach { logStore.error("crash", it) }
    }
}


private val appModule = module {

    single { LogStore() }
    singleOf(::CrashJournal)
    singleOf(::AppState)
    singleOf(::Notifier)
    singleOf(::GroqAPI)
    singleOf(::PromptRepo)
    singleOf(::FluxAPI)
    singleOf(::PromptSearchUseCase)
    singleOf(::TriageStore)
    singleOf(::HistoryUseCase)
    singleOf(::ImageViewerUseCase)
    singleOf(::QueueUseCase)

    singleOf(::MainUseCase)
    viewModelOf(::MainViewModel)
    singleOf(::TimerUseCase)

    single {
        androidContext().getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE)
    }
    singleOf(::AppSettings)

    // User prompt presets live in their own prefs file, away from AppSettings.
    single { PresetStore(androidContext()) }
    singleOf(::PresetUseCase)


//          AppSettings(get())
//      }  factoryOf(::FactoryPresenter)
//
//    viewModelOf(::SimpleViewModel)
//
//    scope<MyActivity>(){
//        scopedOf(::Session)
//    }
//
//    workerOf(::SimpleWorker)
}

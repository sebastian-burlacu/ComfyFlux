package com.eltonkola.comfyflux.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Whether the app is in the foreground; registered against ProcessLifecycleOwner in MainApp. */
class AppState : DefaultLifecycleObserver {

    @Volatile
    var foreground = false
        private set

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
    }
}

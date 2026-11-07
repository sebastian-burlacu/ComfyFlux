package com.eltonkola.comfyflux.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eltonkola.comfyflux.MainActivity
import com.eltonkola.comfyflux.R
import com.eltonkola.comfyflux.data.model.ProgressGenerationUIState
import com.eltonkola.comfyflux.data.network.FluxAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Keeps a generation alive with the app backgrounded and shows live step
 * progress (plus queue position) in an ongoing notification. Started when a
 * generation begins; stops itself a moment after the server reports the
 * session idle, so queued follow-up jobs keep the notification alive.
 */
class GenerationService : Service(), KoinComponent {

    companion object {
        private const val CHANNEL_ID = "generation_progress"
        private const val NOTIF_ID = 42
        private const val ACTION_START = "start"
        /** Idle time before the notification is dropped, so queued jobs share it. */
        private const val IDLE_STOP_MS = 3_000L
        /** A submit that never reached the server must not keep a notification. */
        private const val NEVER_STARTED_MS = 60_000L

        fun begin(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GenerationService::class.java).setAction(ACTION_START)
            )
        }

        /** The app knows the job is over (or never started): drop the notification now. */
        fun end(context: Context) {
            context.stopService(Intent(context, GenerationService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sawRunning = false
    private var idleSinceMs = 0L
    private val startedAt = System.currentTimeMillis()

    // Latest progress snapshot; the ticker below reads it instead of relying on
    // emissions alone - a prompt the server rejects never emits again, and the
    // ongoing notification used to sit there until the app was force-stopped.
    @Volatile
    private var latest: ProgressGenerationUIState? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val flux: FluxAPI = get()
        scope.launch {
            flux.progressUiState.collect { latest = it }
        }
        // Own clock, so the notification can never outlive the run it belongs
        // to, whatever the websocket did or did not send.
        scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, build("Starting generation…", 0))
        return START_NOT_STICKY
    }

    private fun tick() {
        val state = latest
        val now = System.currentTimeMillis()
        if (state == null) {
            // No progress state at all (server already gone).
            if (now - startedAt > NEVER_STARTED_MS) stopSelf()
            return
        }
        // Queued-but-not-running counts as activity: the job is on the server.
        val busy = state.executing || state.serverRunning || state.queueRemaining > 0
        when {
            busy -> {
                sawRunning = sawRunning || state.executing
                idleSinceMs = 0
                val percent = when {
                    state.maxProgress > 0 -> state.progress * 100 / state.maxProgress
                    else -> (state.totalProgress() * 100).toInt()
                }
                val detail = buildString {
                    append(
                        state.statusMessage.ifBlank {
                            if (state.executing) "Generating" else "Waiting in the queue"
                        }
                    )
                    if (state.queueRemaining > 0) append(" - ${state.queueRemaining} queued")
                }
                NotificationManagerCompat.from(this)
                    .notify(NOTIF_ID, build(detail, percent))
            }

            sawRunning -> {
                // Give queued jobs a moment; a genuinely idle session stops us.
                if (idleSinceMs == 0L) idleSinceMs = now
                else if (now - idleSinceMs > IDLE_STOP_MS) stopSelf()
            }

            now - startedAt > NEVER_STARTED_MS -> stopSelf()
        }
    }

    private fun build(text: String, percent: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_comfyflux)
            .setContentTitle(
                if (percent in 1..99) "Generating - $percent%" else "Generating"
            )
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (percent == 100) 0 else percent, percent == 0)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Generation progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live progress while a generation runs"
            }
        )
    }
}

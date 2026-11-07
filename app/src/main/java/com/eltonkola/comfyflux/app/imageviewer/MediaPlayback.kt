package com.eltonkola.comfyflux.app.imageviewer

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Pause
import com.eltonkola.comfyflux.ui.theme.ikona.PlayMovie

/**
 * Clip playback straight from the server's /view URL. VideoView + MediaController
 * is the boring choice: no media3 dependency, and it already handles mp4/webm
 * streaming on every API level the app supports. Formats Android cannot decode
 * (e.g. ProRes .mov) stay reachable through the viewer's "open in another app".
 */
@Composable
fun VideoPlayback(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { host ->
            // FrameLayout wrapper: VideoView measures at the video's own size,
            // so the clip is sized to the screen once its aspect is known.
            android.widget.FrameLayout(host).apply {
                val view = android.widget.VideoView(host)
                addView(view)
                val controller = android.widget.MediaController(host)
                // Anchor on the video itself: the control bar sits over the
                // picture and fades out on its own.
                controller.setAnchorView(view)
                view.setMediaController(controller)
                view.setVideoURI(android.net.Uri.parse(url))
                view.setOnPreparedListener { player ->
                    val hostWidth = width.takeIf { it > 0 } ?: 1
                    val hostHeight = height.takeIf { it > 0 } ?: 1
                    val aspect = player.videoWidth.toFloat() / player.videoHeight.toFloat()
                    val fitWidth = hostWidth.toFloat() / hostHeight > aspect
                    view.layoutParams = android.widget.FrameLayout.LayoutParams(
                        if (fitWidth) (hostHeight * aspect).toInt() else hostWidth,
                        if (fitWidth) hostHeight else (hostWidth / aspect).toInt()
                    ).also { it.gravity = android.view.Gravity.CENTER }
                    view.requestLayout()
                    view.start()
                }
                view.setOnErrorListener { _, _, _ ->
                    // A codec the phone lacks: let the user pick another player.
                    false
                }
            }
        },
        onRelease = { frame ->
            (frame.getChildAt(0) as? android.widget.VideoView)?.stopPlayback()
        }
    )
}

/**
 * Audio playback with a scrub bar. MediaPlayer against the same /view URL;
 * nothing is cached on the phone.
 */
@Composable
fun AudioPlayback(url: String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var failure by remember { mutableStateOf<String?>(null) }

    // Keep the scrub bar and the seconds readout moving while it plays.
    LaunchedEffect(playing) {
        while (playing) {
            player?.currentPosition?.let { positionMs = it.toLong() }
            kotlinx.coroutines.delay(500)
        }
    }

    DisposableEffect(url) {
        val created = MediaPlayer()
        player = created
        created.setOnPreparedListener {
            prepared = true
            durationMs = it.duration.toLong()
            it.start()
            playing = true
        }
        created.setOnCompletionListener {
            playing = false
            positionMs = durationMs
        }
        created.setOnErrorListener { _, _, _ ->
            failure = "This phone cannot play that audio format."
            playing = false
            true
        }
        created.setDataSource(url)
        created.prepareAsync()
        onDispose {
            runCatching { created.release() }
            player = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!prepared && failure == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        failure?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    player?.let { mp ->
                        if (playing) {
                            mp.pause()
                        } else {
                            mp.start()
                        }
                        playing = !playing
                    }
                },
                enabled = prepared
            ) {
                Icon(
                    imageVector = if (playing) Ikona.Pause else Ikona.PlayMovie,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "${positionMs / 1000}s / ${durationMs / 1000}s",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
            onValueChange = { fraction ->
                player?.let { mp ->
                    val target = (fraction * mp.duration).toInt()
                    mp.seekTo(target)
                    positionMs = target.toLong()
                }
            },
            enabled = prepared
        )
    }
}

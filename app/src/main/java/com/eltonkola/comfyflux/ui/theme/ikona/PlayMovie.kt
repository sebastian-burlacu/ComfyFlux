package com.eltonkola.comfyflux.ui.theme.ikona

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.eltonkola.comfyflux.ui.theme.Ikona

private var _movieIcon: ImageVector? = null
private var _audioIcon: ImageVector? = null
private var _pauseIcon: ImageVector? = null

/** Saved clip badge: frame + play triangle (video outputs the app cannot thumbnail). */
val Ikona.PlayMovie: ImageVector
    get() {
        if (_movieIcon != null) {
            return _movieIcon!!
        }
        _movieIcon = ImageVector.Builder(
            name = "play_movie",
            defaultWidth = 40.0.dp,
            defaultHeight = 40.0.dp,
            viewportWidth = 40.0f,
            viewportHeight = 40.0f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(5f, 8f)
                lineToRelative(30f, 0f)
                lineToRelative(0f, 24f)
                lineToRelative(-30f, 0f)
                close()
            }
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero) {
                moveTo(16f, 13f)
                lineToRelative(12f, 7f)
                lineToRelative(-12f, 7f)
                close()
            }
        }.build()
        return _movieIcon!!
    }

/** Saved audio badge: note head + stem. */
val Ikona.PlayAudio: ImageVector
    get() {
        if (_audioIcon != null) {
            return _audioIcon!!
        }
        _audioIcon = ImageVector.Builder(
            name = "play_audio",
            defaultWidth = 40.0.dp,
            defaultHeight = 40.0.dp,
            viewportWidth = 40.0f,
            viewportHeight = 40.0f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(5f, 8f)
                lineToRelative(30f, 0f)
                lineToRelative(0f, 24f)
                lineToRelative(-30f, 0f)
                close()
            }
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero) {
                // stem
                moveTo(23f, 12f)
                lineToRelative(3f, 0f)
                lineToRelative(0f, 12f)
                lineToRelative(-3f, 0f)
                close()
                // note head
                moveTo(15f, 22f)
                curveToRelative(0f, -2.8f, 2.2f, -4.5f, 5f, -4.5f)
                reflectiveCurveToRelative(5f, 1.7f, 5f, 4.5f)
                reflectiveCurveToRelative(-2.2f, 4.5f, -5f, 4.5f)
                reflectiveCurveToRelative(-5f, -1.7f, -5f, -4.5f)
                close()
            }
        }.build()
        return _audioIcon!!
    }

/** Two bars: the audio player's pause glyph (a stop square would lie). */
val Ikona.Pause: ImageVector
    get() {
        if (_pauseIcon != null) {
            return _pauseIcon!!
        }
        _pauseIcon = ImageVector.Builder(
            name = "pause",
            defaultWidth = 40.0.dp,
            defaultHeight = 40.0.dp,
            viewportWidth = 40.0f,
            viewportHeight = 40.0f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11f, 8f)
                lineToRelative(6f, 0f)
                lineToRelative(0f, 24f)
                lineToRelative(-6f, 0f)
                close()
                moveTo(23f, 8f)
                lineToRelative(6f, 0f)
                lineToRelative(0f, 24f)
                lineToRelative(-6f, 0f)
                close()
            }
        }.build()
        return _pauseIcon!!
    }

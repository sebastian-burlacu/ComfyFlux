package com.eltonkola.comfyflux.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Action icon button that shows help text on long press (persistent
 * Material 3 tooltip). Visually equivalent to IconButton; used so every
 * icon control explains itself without a manual.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HelpIconButton(
    imageVector: ImageVector,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip {
                Text(text = help, fontSize = 13.sp)
            }
        },
        state = tooltipState,
    ) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = { if (enabled) onClick() },
                    onLongClick = { scope.launch { tooltipState.show() } },
                    onLongClickLabel = help,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = help,
                modifier = Modifier.size(iconSize),
                tint = when {
                    !enabled -> LocalContentColor.current.copy(alpha = 0.38f)
                    tint != Color.Unspecified -> tint
                    else -> LocalContentColor.current
                },
            )
        }
    }
}

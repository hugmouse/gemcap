package mysh.dev.gemcap.ui.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.indicators.TimeText
import androidx.media3.common.util.Util
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import mysh.dev.gemcap.R

@OptIn(UnstableApi::class)
@Composable
fun FullscreenVideoDialog(
    player: Player,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            // Video surface — ContentFrame handles aspect ratio automatically
            ContentFrame(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize()
            )

            // Close button (top-right)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.embedded_media_exit_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Bottom controls overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Progress slider
                FullscreenProgressSlider(
                    player = player,
                    modifier = Modifier.fillMaxWidth()
                )

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SeekBackButton(
                        player,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                    PlayPauseButton(
                        player,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                    SeekForwardButton(
                        player,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        TimeText(player) {
                            val position = Util.getStringForTime(this.currentPositionMs)
                            val duration = Util.getStringForTime(this.durationMs)
                            Text(
                                text = "$position / $duration",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Progress slider for fullscreen mode with white-tinted track colors.
 */
@Composable
private fun FullscreenProgressSlider(
    player: Player,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (isActive) {
            if (!isSeeking) {
                val duration = player.duration.coerceAtLeast(1L)
                val position = player.currentPosition
                sliderPosition = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(200L)
        }
    }

    androidx.compose.material3.Slider(
        value = sliderPosition,
        onValueChange = { value ->
            isSeeking = true
            sliderPosition = value
        },
        onValueChangeFinished = {
            val duration = player.duration
            if (duration > 0) {
                player.seekTo((sliderPosition * duration).toLong())
            }
            isSeeking = false
        },
        modifier = modifier,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
        )
    )
}

package app.it.fast4x.rimusic.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.entities.Song
import player.PlayerController
import kotlin.math.roundToLong

@Composable
internal fun CubicMiniPlayer(
    controller: PlayerController,
    song: Song?,
    isResolving: Boolean,
    canGoNext: Boolean,
    onNext: () -> Unit,
    onRestore: () -> Unit
) {
    val state by controller.state.collectAsState()
    val liveProgress = if (state.duration > 0L) (state.timestamp.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    var scrubbing by remember(song?.id) { mutableStateOf(false) }
    var scrubProgress by remember(song?.id) { mutableFloatStateOf(0f) }
    val displayedProgress = if (scrubbing) scrubProgress else liveProgress
    val glassShape = RoundedCornerShape(28.dp)

    Row(
        Modifier.fillMaxSize().padding(3.dp).shadow(18.dp, glassShape).clip(glassShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF02B163E), Color(0xF51B1B23), Color(0xF02B2638))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), glassShape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CubicArtwork(song?.thumbnailUrl, Modifier.size(72.dp), 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(song?.title ?: "Cubic Music", color = CubicColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(if (isResolving) "Preparing stream" else song?.artistsText ?: "Nothing playing", color = CubicColors.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(
                value = displayedProgress,
                onValueChange = { value -> scrubbing = true; scrubProgress = value },
                onValueChangeFinished = {
                    if (state.duration > 0L) controller.seekTo((scrubProgress * state.duration).roundToLong())
                    scrubbing = false
                },
                enabled = song != null && state.duration > 0L,
                modifier = Modifier.fillMaxWidth().height(26.dp),
                colors = SliderDefaults.colors(
                    thumbColor = CubicColors.Accent,
                    activeTrackColor = CubicColors.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                    disabledThumbColor = CubicColors.TextMuted,
                    disabledActiveTrackColor = CubicColors.TextMuted.copy(alpha = 0.25f)
                )
            )
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(CubicColors.Accent)
                .clickable(enabled = song != null && !isResolving) { if (state.isPlaying) controller.pause() else controller.play() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = CubicColors.Background, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = if (canGoNext) CubicColors.TextSecondary else CubicColors.TextMuted, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = controller::toggleSound, enabled = song != null, modifier = Modifier.size(32.dp)) {
            Icon(if (state.isMuted || state.volume == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Mute", tint = CubicColors.TextSecondary, modifier = Modifier.size(19.dp))
        }
        Slider(
            value = if (state.isMuted) 0f else state.volume,
            onValueChange = controller::setVolume,
            enabled = song != null,
            modifier = Modifier.width(64.dp).height(26.dp),
            colors = SliderDefaults.colors(
                thumbColor = CubicColors.Accent,
                activeTrackColor = CubicColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            )
        )
        IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.OpenInFull, "Restore Cubic Music", tint = CubicColors.Accent, modifier = Modifier.size(19.dp))
        }
    }
}

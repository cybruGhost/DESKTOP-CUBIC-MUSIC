package app.it.fast4x.rimusic.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val glassShape = RoundedCornerShape(16.dp)
    val rose = Color(0xFFFF648D)

    Row(
        Modifier.fillMaxSize().clip(glassShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF29232F), Color(0xFF211F28), Color(0xFF292733))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), glassShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        CubicArtwork(song?.thumbnailUrl, Modifier.size(40.dp), 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(song?.title ?: "Cubic Music", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Text(if (isResolving) "Connecting…" else song?.artistsText ?: "Nothing playing", color = Color(0xFFB7B1BD), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.fillMaxWidth().height(10.dp).pointerInput(song?.id, state.duration) {
                    detectTapGestures { offset ->
                        if (song != null && state.duration > 0L && size.width > 0) {
                            controller.seekTo((offset.x / size.width * state.duration).roundToLong())
                        }
                    }
                },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.20f)))
                Box(Modifier.fillMaxWidth(liveProgress).height(2.dp).clip(CircleShape).background(rose))
            }
        }
        Box(
            Modifier.size(31.dp).clip(CircleShape).background(Color.White)
                .clickable(enabled = song != null && !isResolving) { if (state.isPlaying) controller.pause() else controller.play() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color(0xFF29232F), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(25.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = if (canGoNext) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRestore, modifier = Modifier.size(25.dp)) {
            Icon(Icons.Rounded.OpenInFull, "Restore Cubic Music", tint = rose, modifier = Modifier.size(15.dp))
        }
    }
}

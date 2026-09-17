package app.it.fast4x.rimusic.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class LyricsMode { Synced, Plain }
private data class TimedLyric(val timestampMs: Long, val text: String)

@Composable
internal fun CubicSynchronizedLyricsPane(
    syncedLyrics: String?,
    plainLyrics: String?,
    timestampMs: Long,
    loading: Boolean,
    modifier: Modifier,
    showLiveLine: Boolean = false
) {
    var mode by remember(syncedLyrics, plainLyrics) {
        mutableStateOf(if (!syncedLyrics.isNullOrBlank()) LyricsMode.Synced else LyricsMode.Plain)
    }
    val timed = remember(syncedLyrics) { syncedLyrics.parseTimedLyrics() }
    val currentIndex = timed.indexOfLast { it.timestampMs <= timestampMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    LaunchedEffect(currentIndex, mode) {
        if (mode == LyricsMode.Synced && timed.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    val activeLine = timed.getOrNull(currentIndex)

    Column(modifier.fillMaxWidth().fillMaxHeight()) {
        if (!syncedLyrics.isNullOrBlank() && !plainLyrics.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(CubicColors.Selection).padding(3.dp)) {
                LyricsMode.entries.forEach { option ->
                    Text(
                        text = if (option == LyricsMode.Synced) "Synchronized" else "Plain",
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (mode == option) CubicColors.PanelRaised else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { mode = option }.padding(vertical = 8.dp),
                        color = if (mode == option) CubicColors.Accent else CubicColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CubicColors.Accent, strokeWidth = 2.dp)
            }
            mode == LyricsMode.Synced && timed.isNotEmpty() -> {
                // The compact side player gets a dedicated current line; the expanded player stays a clean lyric list.
                if (showLiveLine) activeLine?.let { line ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(CubicColors.Accent.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(2.dp)).background(CubicColors.Accent))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            line.text,
                            modifier = Modifier.weight(1f),
                            color = CubicColors.Text,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(
                Modifier.weight(1f).fillMaxWidth().cubicKeyboardScroll(listState, scrollScope),
                state = listState,
                contentPadding = PaddingValues(vertical = 150.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(timed) { index, line ->
                    if (!(showLiveLine && index == currentIndex)) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (index == currentIndex) CubicColors.Accent.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = if (index == currentIndex) 8.dp else 2.dp)
                        ) {
                            Text(
                                line.text,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (index == currentIndex) CubicColors.Accent else CubicColors.TextSecondary,
                                fontSize = if (index == currentIndex) 15.sp else 13.sp,
                                lineHeight = 21.sp,
                                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
            }
            !plainLyrics.isNullOrBlank() -> LazyColumn(Modifier.weight(1f).fillMaxWidth().cubicKeyboardScroll(listState, scrollScope), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val lines = plainLyrics.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
                itemsIndexed(lines) { _, line -> Text(line, modifier = Modifier.fillMaxWidth(), color = CubicColors.TextSecondary, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center) }
                item { Spacer(Modifier.height(18.dp)) }
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Lyrics are not available for this track.", color = CubicColors.TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun String?.parseTimedLyrics(): List<TimedLyric> {
    if (isNullOrBlank()) return emptyList()
    val expression = Regex("^\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]\\s*(.*)$")
    return lineSequence().mapNotNull { line ->
        val match = expression.find(line.trim()) ?: return@mapNotNull null
        val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            1 -> fraction.toLongOrNull()?.times(100)
            2 -> fraction.toLongOrNull()?.times(10)
            3 -> fraction.toLongOrNull()
            else -> 0L
        } ?: 0L
        match.groupValues[4].trim().takeIf(String::isNotBlank)?.let { TimedLyric((minutes * 60 + seconds) * 1000 + millis, it) }
    }.sortedBy(TimedLyric::timestampMs).toList()
}

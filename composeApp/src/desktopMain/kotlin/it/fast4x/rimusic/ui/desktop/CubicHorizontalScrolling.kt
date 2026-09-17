package app.it.fast4x.rimusic.ui.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A horizontal shelf that works with mouse dragging, touchpad two-finger scrolling,
 * and keyboard arrows. Keeping the behavior here means every desktop shelf feels
 * the same without adding controls or visual noise to each page.
 */
@Composable
internal fun CubicHorizontalRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyRow(
        modifier = modifier.cubicHorizontalKeyboardScroll(state, scope),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}

private fun Modifier.cubicHorizontalKeyboardScroll(state: LazyListState, scope: CoroutineScope): Modifier =
    pointerInput(state) {
        detectDragGestures { _, dragAmount ->
            state.dispatchRawDelta(-dragAmount.x)
        }
    }.pointerInput(state) {
        // Desktop touchpads report a horizontal wheel delta rather than a drag.
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull() ?: continue
                    val delta = change.scrollDelta
                    if (delta.x != 0f) {
                        state.dispatchRawDelta(-delta.x * 48f)
                        change.consume()
                    }
                }
            }
        }
    }.focusable().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val action: (suspend () -> Unit)? = when (event.key) {
            Key.DirectionRight -> ({ state.animateScrollBy(220f) })
            Key.DirectionLeft -> ({ state.animateScrollBy(-220f) })
            Key.PageDown -> ({ state.animateScrollBy(620f) })
            Key.PageUp -> ({ state.animateScrollBy(-620f) })
            Key.MoveHome -> ({ state.animateScrollToItem(0) })
            Key.MoveEnd -> ({ state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) })
            else -> null
        }
        action?.let { scope.launch { it() } }
        action != null
    }

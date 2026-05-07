package app.marlboroadvance.mpvex.ui.components.liquid.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (PointerInputChange) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        val pointer = down.id
        while (true) {
            val event = awaitPointerEvent()
            val anyChange = event.changes.fastFirstOrNull { it.id == pointer }
            if (anyChange == null || !anyChange.pressed) {
                onDragEnd()
                break
            }
            if (anyChange.isConsumed) {
                onDragCancel()
                break
            }
            val dragAmount = anyChange.positionChange()
            if (dragAmount != Offset.Zero) {
                onDrag(anyChange, dragAmount)
            }
        }
    }
}

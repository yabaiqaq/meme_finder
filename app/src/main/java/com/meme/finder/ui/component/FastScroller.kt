package com.meme.finder.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 右侧快速滚动条：
 * - 整个组件 fillMaxSize 叠加在 grid 之上，但只有右侧 28dp 宽的 track 区域接收拖动手势
 *   （中间浮层区域不接收 pointerInput，手势透传给下层 grid，所以 grid 自身的滚动也不受影响）
 * - 拖动时 grid 滚到对应位置（按 entry index 比例换算）
 * - 拖动时居中浮层显示 [currentLabel]（例如当前年月）
 * - 非拖动状态下手柄位置跟随 [state] 实时滚动
 *
 * 用法（父容器是 Box）：
 *   Box {
 *     LazyVerticalGrid(...)
 *     FastScroller(state, totalItems, currentLabel, Modifier.fillMaxSize())
 *   }
 */
@Composable
fun FastScroller(
    state: LazyGridState,
    totalItems: Int,
    currentLabel: () -> String?,
    modifier: Modifier = Modifier,
) {
    if (totalItems <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 拖动时的实时比例（0..1），null 表示未在拖动
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    // track 高度（px），用于把拖动 delta 换算成比例
    var trackHeightPx by remember { mutableIntStateOf(0) }
    // 当前滚动位置算出的"显示比例"，非拖动时用作手柄位置
    val scrollRatio by remember {
        derivedStateOf {
            val total = totalItems
            if (total <= 1) 0f
            else {
                val first = state.firstVisibleItemIndex.toFloat()
                // 用 firstVisibleItemIndex / (total-1) 近似比例
                (first / (total - 1)).coerceIn(0f, 1f)
            }
        }
    }
    val handleRatio = dragRatio ?: scrollRatio
    val active = dragRatio != null
    val label = if (active) currentLabel() else null

    val handleHeightDp = 36.dp
    val handleHeightPx = with(density) { handleHeightDp.toPx() }

    // 整个 FastScroller 占满父容器，但只在右侧 track 区域接收拖动
    Box(modifier) {
        // ===== 右侧 track + handle 区域 =====
        // 用 width(28).fillMaxHeight() + align(CenterEnd) 限制为右侧窄竖条
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(28.dp)
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(totalItems) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val usable = (trackHeightPx - handleHeightPx)
                                .coerceAtLeast(1f)
                            val initRatio = ((offset.y - handleHeightPx / 2f) / usable)
                                .coerceIn(0f, 1f)
                            dragRatio = initRatio
                            val target = (initRatio * (totalItems - 1)).roundToInt()
                                .coerceIn(0, totalItems - 1)
                            scope.launch { state.scrollToItem(target) }
                        },
                        onDragEnd = { dragRatio = null },
                        onDragCancel = { dragRatio = null },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            val prev = dragRatio ?: 0f
                            val usable = (trackHeightPx - handleHeightPx)
                                .coerceAtLeast(1f)
                            val newRatio = (prev + delta / usable).coerceIn(0f, 1f)
                            dragRatio = newRatio
                            val target = (newRatio * (totalItems - 1)).roundToInt()
                                .coerceIn(0, totalItems - 1)
                            scope.launch { state.scrollToItem(target) }
                        },
                    )
                },
        ) {
            // 竖直 track 浅色背景
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = handleHeightDp / 2 + 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outline.copy(
                            alpha = if (active) 0.8f else 0.4f
                        ),
                        shape = RoundedCornerShape(50),
                    ),
            )

            // 拖动手柄（顶端对齐 + 按 ratio 偏移）
            val usablePx = (trackHeightPx - handleHeightPx).coerceAtLeast(0f)
            val topOffsetPx = (handleRatio * usablePx).coerceIn(0f, usablePx)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .size(width = 14.dp, height = handleHeightDp)
                    .alpha(if (active) 1f else 0.6f)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(50),
                    )
                    .offset { IntOffset(0, topOffsetPx.roundToInt()) },
            )
        }

        // ===== 中间浮层（仅在拖动时显示，不接收 pointerInput，手势透传给下层 grid） =====
        AnimatedVisibility(
            visible = active && !label.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        label.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

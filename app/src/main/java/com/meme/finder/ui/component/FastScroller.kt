package com.meme.finder.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
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
 *
 * 关键实现细节：
 * - scrollRatio 用 derivedStateOf 直接订阅 state，**不能 remember 包裹**
 *   （remember 会让 derivedStateOf 在首次计算后被固定，state 变化不再触发重算，手柄就不动了）
 * - 拖动 target 用 layoutInfo.totalItemsCount（grid 实际项数），不用外部传入的 totalItems
 *   （外部 totalItems 可能与 grid 实际项数不一致，scrollToItem 越界会闪退）
 * - 拖动用单个协程 Job 串行，避免多次 scrollToItem 抢占导致卡顿/竞态
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

    // 当前滚动位置算出的"显示比例"。
    // 关键：不能用 remember { derivedStateOf {...} }，那会让 derivedStateOf 在 first compose 后
    // 被 remember 缓存为同一实例，但闭包捕获的 state 引用没问题 —— 真正的坑是：如果用
    // remember(totalItems) 包，totalItems 变了会重建 derivedStateOf，反而 OK；但更简单的写法是
    // 直接用 `by derivedStateOf {...}` 不 remember，每次 recompose 都拿到同一个订阅。
    // 这里用 layoutInfo 精确算比例：基于第一个可见 item 的 index 和它在该 item 内的 offset。
    val scrollRatio by derivedStateOf {
        val layoutInfo = state.layoutInfo
        val total = layoutInfo.totalItemsCount
        if (total <= 1) 0f
        else {
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) 0f
            else {
                val firstInfo = visible.first()
                // 用第一个可见 item 的"全局位置"近似滚动比例。
                // 每个 item 在 viewport 顶部之上 = firstInfo.index - 0（用 index 直接近似）
                // 严格说应该算上 offset，但 grid 的 offset 单位是 px，需要除以 item 高度才合理；
                // 这里用纯 index 近似，足够手柄跟随。
                (firstInfo.index.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    val handleRatio = dragRatio ?: scrollRatio
    val active = dragRatio != null
    val label = if (active) currentLabel() else null

    val handleHeightDp = 36.dp
    val handleHeightPx = with(density) { handleHeightDp.toPx() }

    // 串行化 scrollToItem 调用，避免多次抢占导致卡顿/竞态
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val safeScrollTo: (Int) -> Unit = { target ->
        scrollJob?.cancel()
        scrollJob = scope.launch {
            runCatching {
                // 用 layoutInfo.totalItemsCount 防越界
                val total = state.layoutInfo.totalItemsCount
                val safeTarget = target.coerceIn(0, (total - 1).coerceAtLeast(0))
                state.scrollToItem(safeTarget)
            }
        }
    }

    Box(modifier) {
        // ===== 右侧 track + handle 区域 =====
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(28.dp)
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val usable = (trackHeightPx - handleHeightPx)
                                .coerceAtLeast(1f)
                            // offset.y 是相对 track 顶部的坐标，手柄中心点位置 = offset.y
                            // 反推比例 = offset.y / usable（手柄中心能到的范围）
                            val initRatio = (offset.y / usable).coerceIn(0f, 1f)
                            dragRatio = initRatio
                            val total = state.layoutInfo.totalItemsCount
                            if (total > 1) {
                                val target = (initRatio * (total - 1)).roundToInt()
                                    .coerceIn(0, total - 1)
                                safeScrollTo(target)
                            }
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
                            val total = state.layoutInfo.totalItemsCount
                            if (total > 1) {
                                val target = (newRatio * (total - 1)).roundToInt()
                                    .coerceIn(0, total - 1)
                                safeScrollTo(target)
                            }
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

package com.meme.finder.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 右侧快速滚动条（适配 [LazyVerticalGrid]）。
 *
 * 用法（父容器是 Box，FastScroller 叠加在 grid 之上）：
 *   Box {
 *     LazyVerticalGrid(...)
 *     FastScroller(state, totalItems, currentLabel, Modifier.fillMaxSize())
 *   }
 *
 * 关键实现要点（参考社区 DragDrop / ComposeReorderable 模式）：
 *
 * 1. **handle 位移**用 [Modifier.graphicsLayer] 的 `translationY` + lambda deferred read，
 *    内部**直接读 State**（dragRatio / scrollRatio / trackHeightPx），不用中间普通变量。
 *    原因：graphicsLayer 的 block 是 deferred read，layer 只在 block 内的 State read 变化时
 *    invalidate。如果 block 内读的是普通 val（如 `handleRatio`），即使该 val 在 recomposition
 *    时被重新计算并传入新 block，layer **不会因为 block 实例变化而 invalidate**——
 *    translationY 永远不更新。必须让 block 直接读 State，layer 才能订阅并刷新。
 *
 * 2. **drag → scroll 派发**：`detectVerticalDragGestures` 的 `onVerticalDrag` 回调
 *    只更新本地 `mutableFloatStateOf` 的 dragRatio，**不直接调用 suspend 的 scrollToItem**。
 *    真正的 `state.scrollToItem(targetIndex)` 派发到 `rememberCoroutineScope().launch { ... }`，
 *    Job 引用存到普通对象 holder（不用 mutableStateOf，避免触发 recomposition），
 *    新的 drag 来时 cancel 上一次未完成的 scroll，`onDragEnd`/`onDragCancel` 显式 cancel Job。
 *
 * 3. **不捕获 CancellationException**：用 try-catch 显式 rethrow CancellationException。
 *    runCatching 会把 CancellationException 当普通异常吞掉，导致 Job 进入 Completed 而非
 *    Cancelled 状态，破坏结构化并发——scope 取消时子 Job 泄漏，快速拖动场景下表现为卡死后闪退。
 *
 * 4. **isDragging 用 derivedStateOf 过滤**：`derivedStateOf { dragRatio >= 0f }`
 *    只在拖动开始/结束（true↔false 边界）触发 recomposition；拖动过程中 dragRatio 在 0..1
 *    高频变化时 isDragging 不变，不触发 recomposition，避免重组风暴。
 *
 * 5. **triggerScroll 用 remember 稳定**：避免每次重组创建新 lambda 实例。
 *
 * 6. **防 IndexOutOfBounds**：派发前 `targetIndex.coerceIn(0, (totalItemsCount - 1).coerceAtLeast(0))`；
 *    layoutInfo 在 dispatch 与 suspend resume 之间可能已变，try-catch 兜底。
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

    // 拖动时的实时比例（0..1），-1f 表示未在拖动
    var dragRatio by remember { mutableFloatStateOf(-1f) }
    // track 高度（px）
    var trackHeightPx by remember { mutableIntStateOf(0) }

    val handleHeightDp = 36.dp
    val handleHeightPx = with(density) { handleHeightDp.toPx() }

    // 非拖动时手柄位置：订阅 LazyGridState 的 firstVisibleItemIndex / totalItemsCount
    val scrollRatio by remember {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else (state.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
        }
    }

    // isDragging 只在边界变化触发 recomposition，拖动中 dragRatio 高频变化时不重组
    val isDragging by remember { derivedStateOf { dragRatio >= 0f } }
    val label = if (isDragging) currentLabel() else null

    // Job 用普通对象持有：不驱动 UI，不需要 mutableStateOf（mutableStateOf 会在 cancel/launch 时
    // 触发 recomposition，快速拖动场景下导致重组风暴 → ANR）
    val scrollJobHolder = remember { object { var job: Job? = null } }

    val triggerScroll: (Float) -> Unit = remember(state, scope, scrollJobHolder) {
        { ratio: Float ->
            scrollJobHolder.job?.cancel()
            scrollJobHolder.job = scope.launch {
                try {
                    val total = state.layoutInfo.totalItemsCount
                    if (total > 1) {
                        val target = (ratio * (total - 1)).roundToInt().coerceIn(0, total - 1)
                        state.scrollToItem(target)
                    }
                } catch (c: CancellationException) {
                    throw c  // 协程取消必须传播，不能吞
                } catch (e: Exception) {
                    // IndexOutOfBounds 等兜底，layoutInfo 在 suspend resume 之间可能已变
                }
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
                            val usable = (trackHeightPx - handleHeightPx).coerceAtLeast(1f)
                            val initRatio = (offset.y / usable).coerceIn(0f, 1f)
                            dragRatio = initRatio
                            triggerScroll(initRatio)
                        },
                        onDragEnd = {
                            dragRatio = -1f
                            scrollJobHolder.job?.cancel()
                            scrollJobHolder.job = null
                        },
                        onDragCancel = {
                            dragRatio = -1f
                            scrollJobHolder.job?.cancel()
                            scrollJobHolder.job = null
                        },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            val prev = if (dragRatio >= 0f) dragRatio else 0f
                            val usable = (trackHeightPx - handleHeightPx).coerceAtLeast(1f)
                            val newRatio = (prev + delta / usable).coerceIn(0f, 1f)
                            dragRatio = newRatio
                            triggerScroll(newRatio)
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
                            alpha = if (isDragging) 0.8f else 0.4f
                        ),
                        shape = RoundedCornerShape(50),
                    ),
            )

            // 拖动手柄：graphicsLayer block 内直接读 State（dragRatio/scrollRatio/trackHeightPx），
            // layer 订阅这些 State，任一变化触发 invalidate → translationY 实时刷新
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .size(width = 14.dp, height = handleHeightDp)
                    .alpha(if (isDragging) 1f else 0.6f)
                    .background(
                        color = if (isDragging) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(50),
                    )
                    .graphicsLayer {
                        // 关键：直接读 State，不要提取到中间普通变量
                        val ratio = if (dragRatio >= 0f) dragRatio else scrollRatio
                        val usable = (trackHeightPx - handleHeightPx).coerceAtLeast(0f)
                        translationY = (ratio * usable).coerceIn(0f, usable)
                    },
            )
        }

        // ===== 中间浮层（仅在拖动时显示，不接收 pointerInput） =====
        AnimatedVisibility(
            visible = isDragging && !label.isNullOrBlank(),
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

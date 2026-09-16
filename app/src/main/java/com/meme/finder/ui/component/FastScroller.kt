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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
 * 关键实现要点：
 *
 * 1. **totalItems<=1 不早退**：用 `if` 包住 UI 而非 return，避免数据加载过程中
 *    FastScroller 反复卸载重建导致 dragRatio/trackHeightPx 状态丢失。
 *
 * 2. **手柄位置用 `Modifier.offset { IntOffset(...) }`**：offset lambda 在 layout
 *    阶段同步执行，每次 recomposition 重算——手势回调改 State → recomposition →
 *    offset 重算，链路直接、时序正确。
 *
 * 3. **滚动用 `state.requestScrollToItem(targetIndex)`**（非挂起）：
 *    - 它只设置一个 pending request，grid 在下次 layout 时用估算像素位置直接跳到
 *      目标 index 附近，**只 measure 可见区，不循环测量中间 item**
 *    - 跳转成本 O(可见区) 而非 O(targetIndex)，1.8 万张图也能瞬时跳
 *    - 非挂起，不占协程，不竞争 mutate 锁，无 CancellationException 风险
 *
 *    之前用过的方案及为何放弃：
 *    - `scrollToItem(index)`（挂起）：为了精确对齐 index 会循环 scrollBy + layout，
 *      远距离跳转测量上千中间 item → ANR/闪退
 *    - `dispatchRawDelta(delta)`（非挂起）：不走 mutate 锁，直接操作 scroll position，
 *      推大 delta 时与 layoutInfo 不一致，越界访问 → 闪退
 *
 * 4. **isDragging 用 derivedStateOf 过滤**：只在拖动起止边界（true↔false）重组，
 *    拖动过程中 dragRatio 高频变化不触发重组。
 */
@Composable
fun FastScroller(
    state: LazyGridState,
    totalItems: Int,
    currentLabel: () -> String?,
    modifier: Modifier = Modifier,
) {
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

    // 请求 grid 跳到 dragRatio 对应的 index（非挂起，下次 layout 时执行，不测量中间 item）
    val requestScroll: (Float) -> Unit = { ratio ->
        val total = state.layoutInfo.totalItemsCount
        if (total > 1) {
            val target = (ratio * (total - 1)).roundToInt().coerceIn(0, total - 1)
            state.requestScrollToItem(target, 0)
        }
    }

    // 关键：不用 `if (totalItems <= 1) return` 早退，而是用 if 包住 UI
    // 早退会让 FastScroller 在数据加载过程中反复卸载重建，dragRatio/trackHeightPx 状态丢失
    if (totalItems > 1) {
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
                                dragRatio = (offset.y / usable).coerceIn(0f, 1f)
                                requestScroll(dragRatio)
                            },
                            onDragEnd = { dragRatio = -1f },
                            onDragCancel = { dragRatio = -1f },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                val prev = if (dragRatio >= 0f) dragRatio else 0f
                                val usable = (trackHeightPx - handleHeightPx).coerceAtLeast(1f)
                                dragRatio = (prev + delta / usable).coerceIn(0f, 1f)
                                requestScroll(dragRatio)
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

                // 拖动手柄：用 offset 而非 graphicsLayer，offset lambda 在 layout 阶段同步执行，
                // 每次 recomposition 重算——手势回调改 State → recomposition → offset 重算，
                // 链路直接、时序正确。graphicsLayer 的 deferred read 在手势场景下读到的是 stale 值。
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            val ratio = if (dragRatio >= 0f) dragRatio else scrollRatio
                            val usable = (trackHeightPx - handleHeightPx).coerceAtLeast(0f)
                            IntOffset(0, (ratio * usable).roundToInt())
                        }
                        .padding(end = 4.dp)
                        .size(width = 14.dp, height = handleHeightDp)
                        .alpha(if (isDragging) 1f else 0.6f)
                        .background(
                            color = if (isDragging) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(50),
                        ),
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
}

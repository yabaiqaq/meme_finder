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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
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
 * 关键实现要点（参考 Google 官方 photos 示例 / material3 PullToRefreshBox 模式）：
 *
 * 1. **totalItems<=1 不早退**：用 `if` 包住 UI 而非 return，避免数据加载过程中
 *    FastScroller 反复卸载重建导致 dragRatio/trackHeightPx 状态丢失。
 *
 * 2. **手柄位置用 `Modifier.offset { IntOffset(...) }` 而非 `graphicsLayer.translationY`**：
 *    offset lambda 在 layout 阶段同步执行，每次 recomposition 重算——手势回调改 State →
 *    recomposition → offset 重算，链路直接、时序正确。graphicsLayer 的 deferred read 适用于
 *    "不希望 recomposition 只要 draw 刷新"的动画场景；但手势回调写的 State 在 layout 阶段
 *    读到的是 stale 值（snapshot 隔离），这正是"位置不动"的根因。
 *
 * 3. **滚动用 `LaunchedEffect + snapshotFlow + collectLatest` 驱动**：
 *    - 手势回调只写 `dragRatio`（State），不直接调用挂起函数
 *    - `LaunchedEffect(state)` 里 `snapshotFlow { dragRatio }` 监听 State 变化
 *    - `collectLatest` 在新值到达时自动 cancel 上一个 collect 块（包括 `scrollToItem` 的
 *      `mutate`），cancel 信号正确传播，锁正确释放——天然实现"只跑最后一次"
 *    - `LaunchedEffect` 的 scope 与 composition 绑定，组件销毁时自动取消，无泄漏
 *    - 不需要手动管理 Job 引用，避免竞态
 *
 * 4. **不吞 CancellationException**：try-catch 显式 rethrow，避免破坏结构化并发导致
 *    `MutatorMutex` 锁泄漏（v0.1.5 用 runCatching 吞掉 cancel 信号，是闪退的真正元凶之一）。
 *
 * 5. **isDragging 用 derivedStateOf 过滤**：只在拖动起止边界（true↔false）重组，
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

    // 拖动手柄 → 用 LaunchedEffect + snapshotFlow + collectLatest 驱动 grid 滚动。
    //
    // 为什么不用手动 Job.cancel + launch：
    // - 手动管理 Job 引用在快速拖动时容易出竞态（cancel 信号没传到 mutate 内部）
    // - collectLatest 是结构化并发：新 dragRatio 到达时自动 cancel 上一个 collect 块
    //   （包括 state.scroll 的 mutate），cancel 信号正确传播，锁正确释放
    // - LaunchedEffect 的 scope 与 composition 绑定，组件销毁时自动取消，无泄漏
    //
    // 为什么用 state.scroll(MutatePriority.UserInput) 而非 scrollToItem：
    // - scrollToItem 内部也是 mutate，但优先级默认 Default，会被 grid 惯性滚动/测量阻塞
    // - UserInput 优先级抢占 grid 自身的滚动锁，立即生效
    // - scrollBy(deltaPx) 是同步推 delta，立即应用到 scroll position
    //
    // 为什么不用 dispatchRawDelta：
    // - dispatchRawDelta 是 @ExperimentalFoundationApi，API 不稳定
    // - 按像素推 delta，大量 item 时一次性推几千像素会触发大量 layout 计算，可能掉帧
    // - scroll {} 走正常的滚动管线，grid 会按可见区域分批 layout，性能更好
    LaunchedEffect(state) {
        snapshotFlow { dragRatio }
            .filter { it >= 0f }
            .collectLatest { ratio ->
                val total = state.layoutInfo.totalItemsCount
                if (total <= 1) return@collectLatest

                val targetIndex = (ratio * (total - 1)).roundToInt().coerceIn(0, total - 1)
                if (targetIndex == state.firstVisibleItemIndex) return@collectLatest

                // scrollToItem 内部是 mutate，立即跳（不是平滑滚动），不需要 layout 中间 item
                // collectLatest 的 cancel 会正确传播到 mutate 内部，锁正确释放
                try {
                    state.scrollToItem(targetIndex)
                } catch (c: CancellationException) {
                    throw c  // 协程取消必须传播，不能吞
                } catch (e: Exception) {
                    // 兜底：layoutInfo 在 dispatch 与 resume 之间可能已变，Ignore
                }
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
                            },
                            onDragEnd = { dragRatio = -1f },
                            onDragCancel = { dragRatio = -1f },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                val prev = if (dragRatio >= 0f) dragRatio else 0f
                                val usable = (trackHeightPx - handleHeightPx).coerceAtLeast(1f)
                                dragRatio = (prev + delta / usable).coerceIn(0f, 1f)
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

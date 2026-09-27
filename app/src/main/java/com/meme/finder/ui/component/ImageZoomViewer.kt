package com.meme.finder.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 全屏查看器：双指缩放 + 拖动 + 双击在"适配屏幕"与"放大"间切换，
 * 单击空白处或按返回键关闭。
 *
 * 平移量按当前缩放比夹在图片边界内，放大后不会拖出黑边。
 */
@Composable
fun ImageZoomViewer(
    uri: String,
    contentDescription: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val scope = rememberCoroutineScope()
        val scaleAnim = remember { Animatable(MIN_SCALE, MAX_SCALE) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var viewport by remember { mutableStateOf(IntSize.Zero) }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val next = (scaleAnim.value * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
            // snapTo 会取消进行中的双击动画，缩放中途再捏合也不会冲突
            scope.launch { scaleAnim.snapTo(next) }
            pan = clampPan(next, pan + panChange, viewport)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewport = it }
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scaleAnim.value <= MIN_SCALE + 0.01f) onDismiss() },
                        onDoubleTap = {
                            scope.launch {
                                val zoomedIn = scaleAnim.value > MIN_SCALE + 0.01f
                                val target = if (zoomedIn) MIN_SCALE else DOUBLE_TAP_SCALE
                                scaleAnim.animateTo(target, spring())
                                pan = if (target == MIN_SCALE) Offset.Zero else clampPan(target, pan, viewport)
                            }
                        },
                    )
                },
        ) {
            UriImage(
                uri = uri,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = pan.x
                        translationY = pan.y
                    },
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
        }
    }
}

private fun clampPan(scale: Float, value: Offset, viewport: IntSize): Offset {
    val maxX = ((scale - MIN_SCALE) * viewport.width / 2f).coerceAtLeast(0f)
    val maxY = ((scale - MIN_SCALE) * viewport.height / 2f).coerceAtLeast(0f)
    return Offset(x = value.x.coerceIn(-maxX, maxX), y = value.y.coerceIn(-maxY, maxY))
}

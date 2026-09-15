package com.meme.finder.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 简单的"可点击 + 导航"扩展，统一避免重复样板。
 * 内部走 Clickable 配置，由外部回调处理跳转。
 */
fun Modifier.clickableNavigate(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/**
 * 可点击 + 按压缩放反馈的扩展。
 * 按下时缩放到 0.94，弹起回弹到 1.0，配合 spring 让手感"弹润"。
 * 用于图片 cell 等需要明确按下反馈的卡片/网格项。
 */
fun Modifier.clickableWithPress(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

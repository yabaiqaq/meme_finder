package com.meme.finder.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * 简单的"可点击 + 导航"扩展，统一避免重复样板。
 * 内部走 Clickable 配置，由外部回调处理跳转。
 */
fun Modifier.clickableNavigate(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

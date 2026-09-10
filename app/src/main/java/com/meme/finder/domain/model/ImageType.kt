package com.meme.finder.domain.model

/**
 * 图片类型启发式分类。
 * - [MEME] 表情包（小尺寸 + 比例近似 1:1）
 * - [SCREENSHOT] 截图（屏幕比例 + 通常较宽）
 * - [PHOTO] 普通图片/照片
 * - [STICKER] 贴纸（透明背景的小图，GIF/PNG）
 * - [UNKNOWN] 未分类
 */
enum class ImageType {
    MEME, SCREENSHOT, PHOTO, STICKER, UNKNOWN
}

package com.meme.finder.data.label

import android.net.Uri

/** 图片标签识别引擎抽象。 */
interface LabelEngine {
    val name: String
    val requiresNetwork: Boolean

    /** 返回标签列表（已按置信度降序）。失败返回 null。 */
    suspend fun recognize(uri: Uri): List<String>?
}

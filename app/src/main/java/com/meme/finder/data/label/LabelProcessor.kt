package com.meme.finder.data.label

import android.net.Uri
import com.meme.finder.domain.model.ImageType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片标签处理：端侧 ML Kit Image Labeling + 基于标签的二次分类。
 */
@Singleton
class LabelProcessor @Inject constructor(
    private val mlKit: MlKitLabelEngine,
) {

    data class LabelResult(
        val labels: List<String>,
        val refinedType: ImageType,
    )

    suspend fun recognize(uri: Uri, hint: ImageType = ImageType.UNKNOWN): LabelResult? {
        val labels = mlKit.recognize(uri) ?: return null
        return LabelResult(
            labels = labels,
            refinedType = refineType(hint, labels),
        )
    }

    /**
     * 用标签细化分类。
     * 例如：labels 中包含 "Comic"/"Meme"/"Cartoon" 则归为 MEME；
     * 包含 "Screenshot" 则归为 SCREENSHOT。
     */
    private fun refineType(hint: ImageType, labels: List<String>): ImageType {
        val lower = labels.joinToString(",").lowercase()
        return when {
            "comic" in lower || "meme" in lower || "cartoon" in lower -> ImageType.MEME
            "screenshot" in lower -> ImageType.SCREENSHOT
            "text" in lower && (hint == ImageType.MEME || hint == ImageType.UNKNOWN) ->
                ImageType.MEME
            hint != ImageType.UNKNOWN -> hint
            else -> ImageType.PHOTO
        }
    }
}

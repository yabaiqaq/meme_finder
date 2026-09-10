package com.meme.finder.data.label

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 端侧标签识别：ML Kit Image Labeling。
 * 默认置信度阈值 0.7，过滤无关标签。
 */
@Singleton
class MlKitLabelEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : LabelEngine {

    override val name: String = "mlkit-label"
    override val requiresNetwork: Boolean = false

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.7f)
                .build()
        )
    }

    override suspend fun recognize(uri: Uri): List<String>? = runCatching {
        val image = InputImage.fromFilePath(context, uri)
        suspendCancellableCoroutine { cont ->
            labeler.process(image)
                .addOnSuccessListener { labels -> cont.resume(labels.map(ImageLabel::getText)) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
    }.getOrNull()
}

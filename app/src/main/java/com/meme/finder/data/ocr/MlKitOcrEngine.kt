package com.meme.finder.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 端侧 OCR：ML Kit 中英文识别。完全离线运行。
 * 需要 Google Play Services（v0.6 起用云端兜底无 GMS 设备）。
 */
@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {

    override val name: String = "mlkit"
    override val requiresNetwork: Boolean = false

    // 中文识别器也覆盖英文，单个 client 即可。
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(uri: Uri): String? = runCatching {
        val image = InputImage.fromFilePath(context, uri)
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { cont.resume(null) }
        }
    }.getOrNull()
}

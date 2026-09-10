package com.meme.finder.data.scan

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meme.finder.data.ocr.OcrProcessor
import com.meme.finder.data.repo.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台 Worker：对 Room 中 ocr_processed_at=0 的图片跑 OCR。
 * ML Kit 单图 ~200-500ms，串行处理避免占大量内存。
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: ImageRepository,
    private val ocr: OcrProcessor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = repo.getOcrPending()
        if (pending.isEmpty()) return Result.success()

        for (item in pending) {
            if (isStopped) return Result.success()
            val uri = runCatching { Uri.parse(item.uri) }.getOrNull() ?: continue
            val text = ocr.recognize(uri)
            // 即使 OCR 失败也写空字符串，避免反复重试
            repo.setOcrResult(item.id, text ?: "")
        }
        return if (repo.getOcrPending().isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "ocr_worker"
    }
}

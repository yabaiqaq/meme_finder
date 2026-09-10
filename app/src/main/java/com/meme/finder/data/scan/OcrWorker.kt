package com.meme.finder.data.scan

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meme.finder.data.ocr.OcrProcessor
import com.meme.finder.data.progress.ProgressTracker
import com.meme.finder.data.progress.TaskProgress
import com.meme.finder.data.repo.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台 Worker：对 Room 中 ocr_processed_at=0 的图片跑 OCR。
 * ML Kit 单图 ~200-500ms，串行处理避免占大量内存。
 * 用 setProgress + ProgressTracker 双推（前者给 ScanStarter，后者给 UI Flow）。
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: ImageRepository,
    private val ocr: OcrProcessor,
    private val progressTracker: ProgressTracker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = repo.getOcrPending()
        val total = pending.size
        if (total == 0) {
            progressTracker.setOcrProgress(TaskProgress(0, 0))
            return Result.success()
        }

        var done = 0
        progressTracker.setOcrProgress(TaskProgress(total = total, done = 0))
        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))

        for (item in pending) {
            if (isStopped) {
                progressTracker.resetOcr()
                return Result.success()
            }
            val uri = runCatching { Uri.parse(item.uri) }.getOrNull() ?: continue
            val text = ocr.recognize(uri)
            // 即使 OCR 失败也写空字符串，避免反复重试
            repo.setOcrResult(item.id, text ?: "")
            done++
            progressTracker.setOcrProgress(TaskProgress(total = total, done = done))
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
        }
        progressTracker.setOcrProgress(TaskProgress(total = total, done = total))
        return if (repo.getOcrPending().isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "ocr_worker"
        const val KEY_DONE = "ocr_done"
        const val KEY_TOTAL = "ocr_total"
    }
}

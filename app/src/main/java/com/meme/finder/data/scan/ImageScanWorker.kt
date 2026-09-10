package com.meme.finder.data.scan

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meme.finder.data.repo.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台 Worker：从 MediaStore 全量扫描相册，写入 Room。
 * 完成后由 [ScanStarter] 链式触发 OcrWorker。
 */
@HiltWorker
class ImageScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: ImageRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        repo.rescan()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val UNIQUE_NAME = "image_scan_worker"
    }
}

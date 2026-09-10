package com.meme.finder.data.scan

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meme.finder.data.label.LabelProcessor
import com.meme.finder.data.repo.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台 Worker：对 Room 中 label_processed_at=0 的图片跑标签识别 + 二次分类。
 */
@HiltWorker
class LabelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: ImageRepository,
    private val labeler: LabelProcessor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = repo.getLabelPending()
        if (pending.isEmpty()) return Result.success()

        for (item in pending) {
            if (isStopped) return Result.success()
            val uri = runCatching { Uri.parse(item.uri) }.getOrNull() ?: continue
            val result = labeler.recognize(uri, item.type)
            if (result != null) {
                repo.setLabelResult(item.id, result.labels)
                // 若分类被细化，更新主表 type 字段
                if (result.refinedType != item.type) {
                    repo.updateType(item.id, result.refinedType)
                }
            } else {
                // 失败也写入空列表，避免反复重试
                repo.setLabelResult(item.id, emptyList())
            }
        }
        return if (repo.getLabelPending().isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "label_worker"
    }
}

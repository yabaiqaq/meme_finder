package com.meme.finder.data.scan

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meme.finder.data.label.LabelProcessor
import com.meme.finder.data.progress.ProgressTracker
import com.meme.finder.data.progress.TaskProgress
import com.meme.finder.data.repo.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台 Worker：对 Room 中 label_processed_at=0 的图片跑标签识别 + 二次分类。
 * 用 setProgress 推送 (done, total)，UI 通过 ScanStarter 订阅。
 */
@HiltWorker
class LabelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: ImageRepository,
    private val labeler: LabelProcessor,
    private val progressTracker: ProgressTracker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = repo.getLabelPending()
        val total = pending.size
        if (total == 0) {
            progressTracker.setLabelProgress(TaskProgress(0, 0))
            return Result.success()
        }

        var done = 0
        progressTracker.setLabelProgress(TaskProgress(total = total, done = 0))
        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))

        for (item in pending) {
            if (isStopped) {
                progressTracker.resetLabel()
                return Result.success()
            }
            val uri = runCatching { Uri.parse(item.uri) }.getOrNull() ?: continue
            val result = labeler.recognize(uri, item.type)
            if (result != null) {
                repo.setLabelResult(item.id, result.labels)
                if (result.refinedType != item.type) {
                    repo.updateType(item.id, result.refinedType)
                }
            } else {
                repo.setLabelResult(item.id, emptyList())
            }
            done++
            progressTracker.setLabelProgress(TaskProgress(total = total, done = done))
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
        }
        progressTracker.setLabelProgress(TaskProgress(total = total, done = total))
        return if (repo.getLabelPending().isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "label_worker"
        const val KEY_DONE = "label_done"
        const val KEY_TOTAL = "label_total"
    }
}

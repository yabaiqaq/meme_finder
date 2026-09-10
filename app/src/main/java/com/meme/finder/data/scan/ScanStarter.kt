package com.meme.finder.data.scan

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对外暴露的扫描/OCR 启动入口。UI 只调用这里，不直接碰 WorkManager。
 */
@Singleton
class ScanStarter @Inject constructor(
    private val workManager: WorkManager,
) {

    /**
     * 启动一次全量后台扫描，OCR + 标签识别接在后面跑。
     * 用 REPLACE：每次调用都真正重新执行，避免上次"已完成"状态阻塞本次。
     */
    fun startScanWithOcr() {
        val scanReq = OneTimeWorkRequestBuilder<ImageScanWorker>()
            .addTag(ImageScanWorker.UNIQUE_NAME)
            .build()
        val ocrReq = OneTimeWorkRequestBuilder<OcrWorker>()
            .addTag(OcrWorker.UNIQUE_NAME)
            .build()
        val labelReq = OneTimeWorkRequestBuilder<LabelWorker>()
            .addTag(LabelWorker.UNIQUE_NAME)
            .build()

        workManager.beginUniqueWork(
            ImageScanWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            scanReq,
        ).then(ocrReq).then(labelReq).enqueue()
    }

    /**
     * 仅触发 OCR + 标签识别（扫描已由调用方同步完成时用）。
     * 链式：OcrWorker -> LabelWorker。
     */
    fun startOcrAndLabels() {
        val ocrReq = OneTimeWorkRequestBuilder<OcrWorker>()
            .addTag(OcrWorker.UNIQUE_NAME)
            .build()
        val labelReq = OneTimeWorkRequestBuilder<LabelWorker>()
            .addTag(LabelWorker.UNIQUE_NAME)
            .build()

        workManager.beginUniqueWork(
            OcrWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            ocrReq,
        ).then(labelReq).enqueue()
    }

    /** 仅触发 OCR（图库已扫过、想补 OCR 时）。 */
    fun startOcrOnly() {
        val ocrReq = OneTimeWorkRequestBuilder<OcrWorker>()
            .addTag(OcrWorker.UNIQUE_NAME)
            .build()
        workManager.enqueueUniqueWork(
            OcrWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            ocrReq,
        )
    }

    /** 仅触发标签识别。 */
    fun startLabelOnly() {
        val labelReq = OneTimeWorkRequestBuilder<LabelWorker>()
            .addTag(LabelWorker.UNIQUE_NAME)
            .build()
        workManager.enqueueUniqueWork(
            LabelWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            labelReq,
        )
    }
}

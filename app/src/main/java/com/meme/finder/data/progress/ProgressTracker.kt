package com.meme.finder.data.progress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单次扫描/OCR 任务进度。
 * @param total 待处理总数；0 表示尚未开始或已重置
 * @param done 已处理数量
 */
data class TaskProgress(
    val total: Int = 0,
    val done: Int = 0,
) {
    val isRunning: Boolean get() = total > 0 && done < total
    val isComplete: Boolean get() = total > 0 && done >= total
    val ratio: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class OverallProgress(
    val scan: TaskProgress = TaskProgress(),
    val ocr: TaskProgress = TaskProgress(),
    val label: TaskProgress = TaskProgress(),
) {
    val anyRunning: Boolean get() = scan.isRunning || ocr.isRunning || label.isRunning
}

/**
 * 全局进度跟踪器。各 Worker / Repository 通过它推送进度，UI 订阅 [overall]。
 */
@Singleton
class ProgressTracker @Inject constructor() {

    private val _overall = MutableStateFlow(OverallProgress())
    val overall: StateFlow<OverallProgress> = _overall.asStateFlow()

    fun setScanProgress(p: TaskProgress) {
        _overall.update { it.copy(scan = p) }
    }

    fun setOcrProgress(p: TaskProgress) {
        _overall.update { it.copy(ocr = p) }
    }

    fun setLabelProgress(p: TaskProgress) {
        _overall.update { it.copy(label = p) }
    }

    fun resetScan() { _overall.update { it.copy(scan = TaskProgress()) } }
    fun resetOcr()  { _overall.update { it.copy(ocr = TaskProgress()) } }
    fun resetLabel(){ _overall.update { it.copy(label = TaskProgress()) } }
}

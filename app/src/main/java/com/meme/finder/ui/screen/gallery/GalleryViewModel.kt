package com.meme.finder.ui.screen.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meme.finder.data.progress.OverallProgress
import com.meme.finder.data.progress.ProgressTracker
import com.meme.finder.data.progress.TaskProgress
import com.meme.finder.data.repo.ImageRepository
import com.meme.finder.data.scan.ScanStarter
import com.meme.finder.domain.model.ImageItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = false,
    val images: List<ImageItem> = emptyList(),
    val groups: List<GalleryGroup> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val hasScanned: Boolean = false,
    val lastScanCount: Int = 0,
    val progress: OverallProgress = OverallProgress(),
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repo: ImageRepository,
    private val scanStarter: ScanStarter,
    private val progressTracker: ProgressTracker,
) : ViewModel() {

    private val _local = MutableStateFlow(LocalState())

    val ui: StateFlow<GalleryUiState> =
        combine(repo.observeAll(), _local, progressTracker.overall) { images, local, prog ->
            GalleryUiState(
                isLoading = local.isScanning || prog.anyRunning,
                images = images,
                groups = groupByMonth(images),
                total = images.size,
                error = local.error,
                hasScanned = images.isNotEmpty() || local.hasScanned,
                lastScanCount = local.lastScanCount,
                progress = prog,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState(),
        )

    fun rescan() {
        if (_local.value.isScanning) return
        _local.update { it.copy(isScanning = true, error = null) }
        progressTracker.resetScan()
        progressTracker.setScanProgress(TaskProgress(total = 0, done = 0))
        viewModelScope.launch {
            runCatching {
                repo.rescan { done, total ->
                    progressTracker.setScanProgress(TaskProgress(total = total, done = done))
                }
            }
                .onSuccess { count ->
                    _local.update {
                        it.copy(isScanning = false, hasScanned = true, lastScanCount = count)
                    }
                    // 扫描完成 -> 后台跑 OCR + 标签（增量，只处理 ocr_processed_at=0 的）
                    scanStarter.startOcrAndLabels()
                }
                .onFailure { e ->
                    _local.update { it.copy(isScanning = false, error = e.message ?: "扫描失败") }
                    progressTracker.resetScan()
                }
        }
    }

    private data class LocalState(
        val isScanning: Boolean = false,
        val hasScanned: Boolean = false,
        val lastScanCount: Int = 0,
        val error: String? = null,
    )
}

package com.meme.finder.ui.screen.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val total: Int = 0,
    val error: String? = null,
    val hasScanned: Boolean = false,
    val lastScanCount: Int = 0,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repo: ImageRepository,
    private val scanStarter: ScanStarter,
) : ViewModel() {

    private val _local = MutableStateFlow(LocalState())

    val ui: StateFlow<GalleryUiState> =
        combine(repo.observeAll(), _local) { images, local ->
            GalleryUiState(
                isLoading = local.isScanning,
                images = images,
                total = images.size,
                error = local.error,
                hasScanned = images.isNotEmpty() || local.hasScanned,
                lastScanCount = local.lastScanCount,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState(),
        )

    /**
     * 同步扫描相册：直接调 repo.rescan()（IO 调度），等它写完库，
     * Room Flow 自动推新数据到 UI；然后再异步触发 OCR + 标签。
     * 不再用 WorkManager 跑扫描，避免"按了不生效"。
     */
    fun rescan() {
        if (_local.value.isScanning) return
        _local.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.rescan() }
                .onSuccess { count ->
                    _local.update {
                        it.copy(
                            isScanning = false,
                            hasScanned = true,
                            lastScanCount = count,
                        )
                    }
                    // 扫描完成 -> 后台跑 OCR + 标签（增量，只处理 ocr_processed_at=0 的）
                    scanStarter.startOcrAndLabels()
                }
                .onFailure { e ->
                    _local.update { it.copy(isScanning = false, error = e.message ?: "扫描失败") }
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

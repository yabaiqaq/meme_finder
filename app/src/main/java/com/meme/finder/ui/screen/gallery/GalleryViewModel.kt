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
    val ocrPending: Int = 0,
    val error: String? = null,
    val hasScanned: Boolean = false,
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
                ocrPending = local.ocrPendingHint,
                error = local.error,
                hasScanned = images.isNotEmpty() || local.hasScanned,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState(),
        )

    fun rescan() {
        if (_local.value.isScanning) return
        _local.update { it.copy(isScanning = true, error = null) }
        scanStarter.startScanWithOcr()
        // WorkManager 在后台异步跑；UI 端给出乐观反馈，几秒后兜底关闭 loading
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)
            _local.update { it.copy(isScanning = false, hasScanned = true) }
        }
    }

    fun forceOcrOnly() {
        scanStarter.startOcrOnly()
    }

    private data class LocalState(
        val isScanning: Boolean = false,
        val hasScanned: Boolean = false,
        val ocrPendingHint: Int = 0,
        val error: String? = null,
    )
}

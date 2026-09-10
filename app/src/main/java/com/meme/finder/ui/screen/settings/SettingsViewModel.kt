package com.meme.finder.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meme.finder.data.ocr.cloud.CloudOcrConfig
import com.meme.finder.data.ocr.cloud.CloudOcrConfigStore
import com.meme.finder.data.ocr.cloud.CloudOcrProvider
import com.meme.finder.data.progress.OverallProgress
import com.meme.finder.data.progress.ProgressTracker
import com.meme.finder.data.progress.TaskProgress
import com.meme.finder.data.repo.ImageRepository
import com.meme.finder.data.scan.ScanStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val total: Int = 0,
    val favorites: Int = 0,
    val isScanning: Boolean = false,
    val cloudProvider: CloudOcrProvider = CloudOcrProvider.NONE,
    val cloudApiKey: String = "",
    val cloudSecretKey: String = "",
    val cloudConfigured: Boolean = false,
    val progress: OverallProgress = OverallProgress(),
)

private data class Stats(val total: Int, val favorites: Int)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: ImageRepository,
    private val scanStarter: ScanStarter,
    private val cloudStore: CloudOcrConfigStore,
    private val progressTracker: ProgressTracker,
) : ViewModel() {

    private val _local = MutableStateFlow(LocalState(cloud = cloudStore.load()))

    private val statsFlow = repo.observeAll().map { all ->
        Stats(all.size, all.count { it.isFavorite })
    }

    val ui: StateFlow<SettingsUiState> =
        combine(statsFlow, _local, progressTracker.overall) { stats, local, prog ->
            SettingsUiState(
                total = stats.total,
                favorites = stats.favorites,
                isScanning = local.isScanning || prog.anyRunning,
                cloudProvider = local.cloud.provider,
                cloudApiKey = local.cloud.apiKey,
                cloudSecretKey = local.cloud.secretKey,
                cloudConfigured = cloudStore.isConfigured(),
                progress = prog,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setProvider(provider: CloudOcrProvider) {
        _local.update { it.copy(cloud = it.cloud.copy(provider = provider)) }
        cloudStore.save(_local.value.cloud)
    }

    fun setApiKey(key: String) {
        _local.update { it.copy(cloud = it.cloud.copy(apiKey = key)) }
    }

    fun setSecretKey(key: String) {
        _local.update { it.copy(cloud = it.cloud.copy(secretKey = key)) }
    }

    fun saveCloud() {
        cloudStore.save(_local.value.cloud)
    }

    /** 同步扫描：直接调 repo.rescan()，等写完库 UI 即刷新，再触发 OCR+标签。 */
    fun rescan() {
        if (_local.value.isScanning) return
        _local.update { it.copy(isScanning = true) }
        progressTracker.resetScan()
        progressTracker.setScanProgress(TaskProgress(total = 0, done = 0))
        viewModelScope.launch {
            runCatching {
                repo.rescan { done, total ->
                    progressTracker.setScanProgress(TaskProgress(total = total, done = done))
                }
            }
                .onSuccess {
                    _local.update { it.copy(isScanning = false) }
                    scanStarter.startOcrAndLabels()
                }
                .onFailure {
                    _local.update { it.copy(isScanning = false) }
                    progressTracker.resetScan()
                }
        }
    }

    fun forceOcr() {
        scanStarter.startOcrOnly()
    }

    fun forceLabel() {
        scanStarter.startLabelOnly()
    }

    private data class LocalState(
        val isScanning: Boolean = false,
        val cloud: CloudOcrConfig = CloudOcrConfig(),
    )
}

package com.meme.finder.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meme.finder.data.ocr.cloud.CloudOcrConfigStore
import com.meme.finder.data.ocr.cloud.CloudOcrProvider
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
import javax.inject.Inject

data class SettingsUiState(
    val total: Int = 0,
    val favorites: Int = 0,
    val cloudProvider: CloudOcrProvider = CloudOcrProvider.NONE,
    val cloudApiKey: String = "",
    val cloudSecretKey: String = "",
    val cloudConfigured: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: ImageRepository,
    private val scanStarter: ScanStarter,
    private val cloudStore: CloudOcrConfigStore,
) : ViewModel() {

    private val _local = MutableStateFlow(cloudStore.load())

    val ui: StateFlow<SettingsUiState> = combine(
        repo.observeAll().map { all -> all.size to all.count { it.isFavorite } },
        _local,
    ) { (total, favorites), cloud ->
        SettingsUiState(
            total = total,
            favorites = favorites,
            cloudProvider = cloud.provider,
            cloudApiKey = cloud.apiKey,
            cloudSecretKey = cloud.secretKey,
            cloudConfigured = cloudStore.isConfigured(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setProvider(provider: CloudOcrProvider) {
        _local.update { it.copy(provider = provider) }
        cloudStore.save(_local.value)
    }

    fun setApiKey(key: String) {
        _local.update { it.copy(apiKey = key) }
    }

    fun setSecretKey(key: String) {
        _local.update { it.copy(secretKey = key) }
    }

    fun saveCloud() {
        cloudStore.save(_local.value)
    }

    fun rescan() {
        scanStarter.startScanWithOcr()
    }

    fun forceOcr() {
        scanStarter.startOcrOnly()
    }

    fun forceLabel() {
        scanStarter.startLabelOnly()
    }
}

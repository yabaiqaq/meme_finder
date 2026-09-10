package com.meme.finder.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meme.finder.data.repo.ImageRepository
import com.meme.finder.data.scan.ScanStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val total: Int = 0,
    val favorites: Int = 0,
    val isScanning: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: ImageRepository,
    private val scanStarter: ScanStarter,
) : ViewModel() {

    val ui: StateFlow<SettingsUiState> =
        combineStats().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    private fun combineStats() = repo.observeAll().map { all ->
        SettingsUiState(total = all.size, favorites = all.count { it.isFavorite })
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

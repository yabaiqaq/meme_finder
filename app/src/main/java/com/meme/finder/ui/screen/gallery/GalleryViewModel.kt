package com.meme.finder.ui.screen.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meme.finder.data.repo.ImageRepository
import com.meme.finder.domain.model.ImageItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = false,
    val images: List<ImageItem> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val hasScanned: Boolean = false,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repo: ImageRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(GalleryUiState())
    val ui: StateFlow<GalleryUiState> = _ui.asStateFlow()

    fun scan() {
        if (_ui.value.isLoading) return
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.scanAll() }
                .onSuccess { list ->
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            images = list,
                            total = list.size,
                            hasScanned = true,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(isLoading = false, error = e.message ?: "扫描失败") }
                }
        }
    }
}

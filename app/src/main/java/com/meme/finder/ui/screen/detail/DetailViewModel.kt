package com.meme.finder.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meme.finder.data.repo.ImageRepository
import com.meme.finder.domain.model.ImageItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val item: ImageItem? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val imageId: Long = savedStateHandle.get<String>("imageId")?.toLongOrNull() ?: -1L

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val item = repo.getById(imageId)
            _ui.value = DetailUiState(
                item = item,
                isLoading = false,
                notFound = item == null,
            )
        }
    }

    fun toggleFavorite() {
        val item = _ui.value.item ?: return
        viewModelScope.launch {
            repo.setFavorite(item.id, !item.isFavorite)
            _ui.value = _ui.value.copy(item = item.copy(isFavorite = !item.isFavorite))
        }
    }
}

package com.photoshare.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoshare.data.model.Photo
import com.photoshare.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PhotoRepository(application)

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // IDs of ephemeral photos the current user has tapped-to-reveal this session
    private val _viewedEphemeral = MutableStateFlow<Set<String>>(emptySet())
    val viewedEphemeral: StateFlow<Set<String>> = _viewedEphemeral.asStateFlow()

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { repo.getPhotos() }
                .onSuccess { _photos.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load photos" }
            _isLoading.value = false
        }
    }

    fun markViewed(photoId: String) {
        viewModelScope.launch {
            runCatching { repo.markViewed(photoId) }
            // Update local state regardless of network result so the UI responds immediately
            _viewedEphemeral.value = _viewedEphemeral.value + photoId
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            runCatching { repo.deletePhoto(photoId) }
                .onSuccess { _photos.value = _photos.value.filter { it.id != photoId } }
                .onFailure { _error.value = it.message ?: "Failed to delete photo" }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

package com.photoshare.ui.gallery

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoshare.data.model.Photo
import com.photoshare.data.repository.PhotoRepository
import com.photoshare.notifications.NotificationHelper
import com.photoshare.util.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val fetchMutex = Mutex()
    private var deviceId: String? = null
    private var lastKnownIds: Set<String>? = null // null = first fetch, don't notify yet

    init {
        viewModelScope.launch {
            deviceId = PreferencesManager(getApplication()).appPrefs.firstOrNull()?.deviceId
            while (true) {
                fetch(showSpinner = _photos.value.isEmpty())
                delay(10_000)
            }
        }
    }

    private suspend fun fetch(showSpinner: Boolean) {
        fetchMutex.withLock {
            if (showSpinner) _isLoading.value = true
            _error.value = null
            runCatching { repo.getPhotos() }
                .onSuccess { photos ->
                    val known = lastKnownIds
                    if (known != null) {
                        val newPhotos = photos.filter { it.id !in known && it.uploaderId != deviceId }
                        if (newPhotos.isNotEmpty()) {
                            NotificationHelper.notify(getApplication(), newPhotos)
                            // Keep background worker's known IDs in sync so it doesn't double-notify
                            getApplication<Application>()
                                .getSharedPreferences("photosync", Context.MODE_PRIVATE)
                                .edit().putStringSet("known_ids", photos.map { it.id }.toSet()).apply()
                        }
                    }
                    lastKnownIds = photos.map { it.id }.toSet()
                    _photos.value = photos
                }
                .onFailure { _error.value = it.message ?: "Failed to load photos" }
            _isLoading.value = false
        }
    }

    fun loadPhotos() {
        viewModelScope.launch { fetch(showSpinner = true) }
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

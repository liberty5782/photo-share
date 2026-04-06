package com.photoshare.ui.upload

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoshare.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UploadState {
    object Idle : UploadState
    object Uploading : UploadState
    object Success : UploadState
    data class Error(val message: String) : UploadState
}

class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PhotoRepository(application)

    private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
    val state: StateFlow<UploadState> = _state.asStateFlow()

    fun upload(uri: Uri, caption: String, isEphemeral: Boolean) {
        viewModelScope.launch {
            _state.value = UploadState.Uploading
            runCatching { repo.uploadPhoto(uri, caption.takeIf { it.isNotBlank() }, isEphemeral) }
                .onSuccess { _state.value = UploadState.Success }
                .onFailure { _state.value = UploadState.Error(it.message ?: "Upload failed") }
        }
    }

    fun resetState() {
        _state.value = UploadState.Idle
    }
}

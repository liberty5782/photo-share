package com.photoshare.data.repository

import android.content.Context
import android.net.Uri
import com.photoshare.data.api.ApiClient
import com.photoshare.data.model.Photo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

class PhotoRepository(private val context: Context) {

    private val api get() = ApiClient.api

    suspend fun getPhotos(): List<Photo> = api.getPhotos()

    suspend fun markViewed(photoId: String) {
        api.markViewed(photoId)
    }

    suspend fun deletePhoto(photoId: String) {
        api.deletePhoto(photoId)
    }

    suspend fun uploadPhoto(
        uri: Uri,
        caption: String?,
        isEphemeral: Boolean,
    ): Photo {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read file")

        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileHash = sha256(bytes)
        val ext = mimeType.split("/").last().let { if (it == "jpeg") "jpg" else it }
        val fileName = "$fileHash.$ext"

        val filePart = MultipartBody.Part.createFormData(
            "file",
            fileName,
            bytes.toRequestBody(mimeType.toMediaType()),
        )
        val captionPart = caption?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaType())
        val ephemeralPart = isEphemeral.toString().toRequestBody("text/plain".toMediaType())
        val hashPart = fileHash.toRequestBody("text/plain".toMediaType())

        val response = api.uploadPhoto(filePart, captionPart, ephemeralPart, hashPart)
        return response.body() ?: error("Upload failed: ${response.code()}")
    }

    suspend fun checkHealth(): Boolean = runCatching { api.health() }.isSuccess

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

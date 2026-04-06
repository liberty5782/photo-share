package com.photoshare.data.model

import com.google.gson.annotations.SerializedName

data class Photo(
    val id: String,
    val caption: String?,
    @SerializedName("uploader_id") val uploaderId: String,
    @SerializedName("uploader_name") val uploaderName: String?,
    @SerializedName("is_ephemeral") val isEphemeral: Boolean,
    @SerializedName("file_hash") val fileHash: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("created_at") val createdAt: String,
)

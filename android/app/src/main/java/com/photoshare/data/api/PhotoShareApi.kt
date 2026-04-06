package com.photoshare.data.api

import com.photoshare.data.model.Photo
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PhotoShareApi {

    @GET("photos")
    suspend fun getPhotos(): List<Photo>

    @Multipart
    @POST("photos")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody?,
        @Part("is_ephemeral") isEphemeral: RequestBody,
        @Part("file_hash") fileHash: RequestBody,
    ): Response<Photo>

    @POST("photos/{id}/view")
    suspend fun markViewed(@Path("id") id: String): Map<String, Boolean>

    @DELETE("photos/{id}")
    suspend fun deletePhoto(@Path("id") id: String): Map<String, Boolean>

    @GET("health")
    suspend fun health(): Map<String, Boolean>
}

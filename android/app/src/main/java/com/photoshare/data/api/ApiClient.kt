package com.photoshare.data.api

import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private var _api: PhotoShareApi? = null
    private var _imageLoader: ImageLoader? = null
    private var _okHttpClient: OkHttpClient? = null

    val api: PhotoShareApi
        get() = _api ?: error("ApiClient not initialized — call ApiClient.init() first")

    val imageLoader: ImageLoader
        get() = _imageLoader ?: error("ApiClient not initialized")

    fun isInitialized() = _api != null

    fun reset() {
        _api = null
        _imageLoader = null
        _okHttpClient = null
    }

    fun init(serverUrl: String, deviceId: String, deviceName: String, context: Context) {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(DeviceHeaderInterceptor(deviceId, deviceName))
            .addInterceptor(logger)
            .build()

        _okHttpClient = client

        _api = Retrofit.Builder()
            .baseUrl("${serverUrl.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PhotoShareApi::class.java)

        _imageLoader = ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(context).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .build()
    }
}

private class DeviceHeaderInterceptor(
    private val deviceId: String,
    private val deviceName: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(
        chain.request().newBuilder()
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Device-Name", deviceName)
            .build()
    )
}

package com.photoshare.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.photoshare.data.api.ApiClient
import com.photoshare.notifications.NotificationHelper
import com.photoshare.util.PreferencesManager
import kotlinx.coroutines.flow.firstOrNull

class PhotoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appPrefs = PreferencesManager(applicationContext).appPrefs.firstOrNull()
            ?: return Result.success() // not configured yet

        ApiClient.init(appPrefs.serverUrl, appPrefs.deviceId, appPrefs.deviceName, applicationContext)

        val photos = runCatching { ApiClient.api.getPhotos() }.getOrNull()
            ?: return Result.retry()

        val prefs = applicationContext.getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val knownIds = prefs.getStringSet("known_ids", null)

        val currentIds = photos.map { it.id }.toSet()

        if (knownIds == null) {
            // First run — save current state, don't notify
            prefs.edit().putStringSet("known_ids", currentIds).apply()
            return Result.success()
        }

        val newPhotos = photos.filter { it.id !in knownIds && it.uploaderId != appPrefs.deviceId }
        if (newPhotos.isNotEmpty()) {
            NotificationHelper.notify(applicationContext, newPhotos)
        }

        prefs.edit().putStringSet("known_ids", currentIds).apply()
        return Result.success()
    }
}

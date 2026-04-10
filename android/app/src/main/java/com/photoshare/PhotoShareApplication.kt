package com.photoshare

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.photoshare.notifications.NotificationHelper
import com.photoshare.worker.PhotoSyncWorker
import java.util.concurrent.TimeUnit

class PhotoShareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<PhotoSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "photo_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

package cn.gdou.edu.timetable.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.gdou.edu.timetable.GdouApplication
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? GdouApplication ?: return Result.success()
        val container = app.container
        val settings = container.settingsStore.settings.value

        if (!settings.autoCheckUpdatesEnabled || settings.updateRepoSpec.isBlank()) {
            return Result.success()
        }

        val checkRes = container.appUpdateClient.checkLatestRelease(settings.updateRepoSpec)
        val info = checkRes.getOrElse {
            return Result.retry()
        }

        val now = System.currentTimeMillis()
        val encoded = container.appUpdateClient.encodeInfo(info)
        val shouldPush = info.isNewerThanCurrent() &&
            !info.versionName.equals(settings.ignoredUpdateVersion, ignoreCase = true) &&
            !info.versionName.equals(settings.lastNotifiedUpdateVersion, ignoreCase = true) &&
            settings.updateNotifyPushEnabled

        var notifiedVer: String? = null
        if (shouldPush) {
            val posted = UpdateNotificationHelper.notifyUpdateAvailable(applicationContext, info)
            if (posted) {
                notifiedVer = info.versionName
            }
        }

        container.settingsStore.recordUpdateCheck(
            timestamp = now,
            cachedJson = encoded,
            notifiedVersion = notifiedVer
        )
        return Result.success()
    }
}

class UpdateCheckScheduler(private val context: Context) {
    fun applySchedule(enabled: Boolean, hasConfiguredSource: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled || !hasConfiguredSource) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            12L,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "qiubi_app_periodic_update_check"
    }
}

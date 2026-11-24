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
import cn.gdou.edu.timetable.data.repository.RefreshOutcome
import java.util.concurrent.TimeUnit

class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? GdouApplication ?: return Result.success()
        val container = app.container
        val settings = container.settingsStore.settings.value
        val session = container.credentialStore.sessionState.value

        if (!session.isLoggedIn || !settings.autoRefreshEnabled) {
            return Result.success()
        }

        return when (val outcome = container.syncRepository.refreshAllData(
            triggerType = "AUTO_BG",
            notifyIfChanged = true
        )) {
            is RefreshOutcome.Success -> Result.success()
            is RefreshOutcome.Failure -> {
                if (outcome.requiresRelogin) Result.success() else Result.retry()
            }
        }
    }
}

class SyncScheduler(private val context: Context) {
    fun applySchedule(enabled: Boolean, intervalMinutes: Long) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val safeInterval = intervalMinutes.coerceAtLeast(15L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(
            safeInterval,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "gdou_edu_periodic_auto_sync"
    }
}

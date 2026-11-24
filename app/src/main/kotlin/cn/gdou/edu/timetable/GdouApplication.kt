package cn.gdou.edu.timetable

import android.app.Application
import android.content.Context
import cn.gdou.edu.timetable.data.local.AppSettingsStore
import cn.gdou.edu.timetable.data.local.SecureCredentialStore
import cn.gdou.edu.timetable.data.local.db.GdouDatabase
import cn.gdou.edu.timetable.data.remote.ApkUpdateDownloader
import cn.gdou.edu.timetable.data.remote.AppUpdateClient
import cn.gdou.edu.timetable.data.remote.GdouCasAuthClient
import cn.gdou.edu.timetable.data.remote.GdouHttpClientFactory
import cn.gdou.edu.timetable.data.remote.GdouJwApiService
import cn.gdou.edu.timetable.data.remote.PersistentCookieJar
import cn.gdou.edu.timetable.data.repository.SyncRepository
import cn.gdou.edu.timetable.worker.SyncNotificationHelper
import cn.gdou.edu.timetable.worker.SyncScheduler
import cn.gdou.edu.timetable.worker.UpdateCheckScheduler
import cn.gdou.edu.timetable.worker.UpdateNotificationHelper

class AppContainer(context: Context) {
    val database: GdouDatabase = GdouDatabase.create(context)
    val credentialStore: SecureCredentialStore = SecureCredentialStore(context)
    val settingsStore: AppSettingsStore = AppSettingsStore(context)
    val cookieJar: PersistentCookieJar = PersistentCookieJar(credentialStore)
    val httpClient = GdouHttpClientFactory.create(cookieJar)
    val authClient: GdouCasAuthClient = GdouCasAuthClient(httpClient, cookieJar)
    val jwApiService: GdouJwApiService = GdouJwApiService(httpClient)
    val appUpdateClient: AppUpdateClient = AppUpdateClient()
    val apkUpdateDownloader: ApkUpdateDownloader = ApkUpdateDownloader(context.applicationContext)
    val syncRepository: SyncRepository = SyncRepository(
        appContext = context.applicationContext,
        database = database,
        credentialStore = credentialStore,
        settingsStore = settingsStore,
        cookieJar = cookieJar,
        authClient = authClient,
        jwApiService = jwApiService
    )
    val syncScheduler: SyncScheduler = SyncScheduler(context.applicationContext)
    val updateCheckScheduler: UpdateCheckScheduler = UpdateCheckScheduler(context.applicationContext)
}

class GdouApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncNotificationHelper.ensureChannel(this)
        UpdateNotificationHelper.ensureChannel(this)

        val settings = container.settingsStore.settings.value
        val session = container.credentialStore.sessionState.value
        if (session.isLoggedIn && settings.autoRefreshEnabled) {
            container.syncScheduler.applySchedule(true, settings.refreshIntervalMinutes)
        }
        container.updateCheckScheduler.applySchedule(
            enabled = settings.autoCheckUpdatesEnabled,
            hasConfiguredSource = settings.updateRepoSpec.isNotBlank()
        )
    }
}

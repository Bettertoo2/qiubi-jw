package cn.gdou.edu.timetable.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.gdou.edu.timetable.AppContainer
import cn.gdou.edu.timetable.BuildConfig
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.local.SavedSessionInfo
import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.CustomScheduleEventEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import cn.gdou.edu.timetable.data.local.db.SyncLogEntity
import cn.gdou.edu.timetable.data.remote.ApkInstaller
import cn.gdou.edu.timetable.data.remote.AppUpdateClient
import cn.gdou.edu.timetable.data.remote.AppUpdateInfo
import cn.gdou.edu.timetable.data.remote.CasLoginFormState
import cn.gdou.edu.timetable.data.remote.CasLoginResult
import cn.gdou.edu.timetable.data.remote.GdouCasAuthClient
import cn.gdou.edu.timetable.data.remote.GdouParsers
import cn.gdou.edu.timetable.data.remote.GradeScoreComponent
import cn.gdou.edu.timetable.data.remote.SessionExpiredException
import cn.gdou.edu.timetable.data.repository.RefreshOutcome
import cn.gdou.edu.timetable.ui.exams.CustomExamDraft
import cn.gdou.edu.timetable.ui.login.WebViewCapturedCookies
import cn.gdou.edu.timetable.ui.timetable.CustomEventDraft
import cn.gdou.edu.timetable.worker.UpdateNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val rememberPassword: Boolean = true,
    val isLoading: Boolean = false,
    val needCaptcha: Boolean = false,
    val captchaBytes: ByteArray? = null,
    val errorMessage: String? = null,
    val formState: CasLoginFormState? = null,
    val showWebLogin: Boolean = false,
    val webLoginUrl: String = GdouCasAuthClient.CAS_LOGIN_URL,
    val webLoginSeedCookies: List<Pair<String, String>> = emptyList()
)

data class GradeBreakdownUiState(
    val grade: GradeEntity? = null,
    val isLoading: Boolean = false,
    val components: List<GradeScoreComponent> = emptyList(),
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false
)

enum class ApkDownloadStage {
    IDLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    NEEDS_PERMISSION,
    ERROR
}

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val showDialog: Boolean = false,
    val statusText: String = "",
    val downloadStage: ApkDownloadStage = ApkDownloadStage.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadPercent: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val downloadedApkPath: String? = null,
    val downloadError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val syncRepo = container.syncRepository
    private val settingsStore = container.settingsStore
    private val syncScheduler = container.syncScheduler
    private val appUpdateClient = container.appUpdateClient
    private val apkUpdateDownloader = container.apkUpdateDownloader
    private val updateCheckScheduler = container.updateCheckScheduler

    val session: StateFlow<SavedSessionInfo> = syncRepo.sessionState
    val settings: StateFlow<AppSettings> = syncRepo.settingsState
    val isSyncing: StateFlow<Boolean> = syncRepo.isSyncing

    val profile: StateFlow<StudentProfileEntity?> = syncRepo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val courses: StateFlow<List<CourseEntity>> = settings
        .flatMapLatest { s -> syncRepo.observeCoursesForTerm(s.selectedXnm, s.selectedXqm) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exams: StateFlow<List<ExamEntity>> = settings
        .flatMapLatest { s -> syncRepo.observeExamsForTerm(s.selectedXnm, s.selectedXqm) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grades: StateFlow<List<GradeEntity>> = syncRepo.observeAllGrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customEvents: StateFlow<List<CustomScheduleEventEntity>> = syncRepo.observeCustomEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncLogs: StateFlow<List<SyncLogEntity>> = syncRepo.observeSyncLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loginUiState = MutableStateFlow(
        LoginUiState(
            username = syncRepo.getSavedUsername(),
            rememberPassword = session.value.rememberPassword
        )
    )
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _selectedWeek = MutableStateFlow(settings.value.currentWeekNumber())
    val selectedWeek: StateFlow<Int> = _selectedWeek.asStateFlow()

    private val _gradeBreakdownState = MutableStateFlow(GradeBreakdownUiState())
    val gradeBreakdownState: StateFlow<GradeBreakdownUiState> = _gradeBreakdownState.asStateFlow()

    private val _appUpdateState = MutableStateFlow(
        AppUpdateUiState(
            availableUpdate = appUpdateClient.decodeInfo(settings.value.cachedUpdateJson)
                ?.takeIf { it.isNewerThanCurrent() || it.isTestPreview }
        )
    )
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private var lastAutoForegroundAttemptMs = 0L
    private var lastUpdateAutoCheckMs = 0L
    private var gradeBreakdownJob: Job? = null
    private var captchaCheckJob: Job? = null
    private var apkDownloadJob: Job? = null

    fun onUsernameChanged(value: String) {
        if (value == _loginUiState.value.username) return
        captchaCheckJob?.cancel()
        _loginUiState.update {
            it.copy(
                username = value,
                errorMessage = null,
                needCaptcha = false,
                captcha = "",
                captchaBytes = null,
                formState = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _loginUiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onCaptchaChanged(value: String) {
        _loginUiState.update { it.copy(captcha = value, errorMessage = null) }
    }

    fun onRememberPasswordChanged(remember: Boolean) {
        _loginUiState.update { it.copy(rememberPassword = remember) }
    }

    fun openWebLogin(url: String = GdouCasAuthClient.CAS_LOGIN_URL) {
        captchaCheckJob?.cancel()
        val allCookies = syncRepo.exportCookiesForWebView()
        // For fresh login page, only seed MFA fingerprint cookie so stale JSESSIONIDs don't interfere;
        // for mid-flow MFA continuation (reAuthCheck), seed all active authserver cookies.
        val seeds = if (url == GdouCasAuthClient.CAS_LOGIN_URL) {
            allCookies.filter { (_, header) ->
                header.contains("MULTIFACTOR_BROWSER_FINGERPRINT", ignoreCase = true)
            }
        } else {
            allCookies
        }
        _loginUiState.update {
            it.copy(
                showWebLogin = true,
                webLoginUrl = url,
                webLoginSeedCookies = seeds,
                formState = null,
                needCaptcha = false,
                captcha = "",
                captchaBytes = null,
                errorMessage = null
            )
        }
    }

    fun closeWebLogin() {
        captchaCheckJob?.cancel()
        _loginUiState.update {
            it.copy(
                showWebLogin = false,
                webLoginUrl = GdouCasAuthClient.CAS_LOGIN_URL,
                webLoginSeedCookies = emptyList(),
                formState = null,
                needCaptcha = false,
                captcha = "",
                captchaBytes = null
            )
        }
    }

    fun checkCaptchaForCurrentUsername() {
        val current = _loginUiState.value
        if (current.isLoading || current.showWebLogin) return
        val username = current.username.trim()
        if (username.length < 5) return
        captchaCheckJob?.cancel()
        captchaCheckJob = viewModelScope.launch {
            val currentForm = _loginUiState.value.formState
                ?: syncRepo.prepareCasLogin().getOrNull()
            if (_loginUiState.value.isLoading || _loginUiState.value.showWebLogin) return@launch
            val need = syncRepo.checkNeedCaptcha(username)
            if (_loginUiState.value.isLoading || _loginUiState.value.showWebLogin) return@launch
            if (need) {
                val img = syncRepo.fetchCaptchaImage().getOrNull()
                if (_loginUiState.value.isLoading || _loginUiState.value.showWebLogin) return@launch
                _loginUiState.update {
                    it.copy(
                        formState = currentForm?.copy(needCaptcha = true),
                        needCaptcha = true,
                        captchaBytes = img
                    )
                }
            } else {
                _loginUiState.update {
                    it.copy(
                        formState = currentForm?.copy(needCaptcha = false),
                        needCaptcha = false,
                        captcha = "",
                        captchaBytes = null
                    )
                }
            }
        }
    }

    fun refreshCaptchaImage() {
        viewModelScope.launch {
            val form = _loginUiState.value.formState ?: syncRepo.prepareCasLogin().getOrNull()
            val img = syncRepo.fetchCaptchaImage().getOrNull()
            if (img != null) {
                _loginUiState.update {
                    it.copy(
                        formState = form?.copy(needCaptcha = true) ?: it.formState,
                        captchaBytes = img,
                        captcha = ""
                    )
                }
            }
        }
    }

    fun submitLogin() {
        val state = _loginUiState.value
        if (state.isLoading) return
        if (state.username.isBlank() || state.password.isBlank()) {
            _loginUiState.update { it.copy(errorMessage = "请输入学号和密码") }
            return
        }
        if (state.needCaptcha && state.captcha.isBlank()) {
            _loginUiState.update { it.copy(errorMessage = "请输入图形验证码") }
            return
        }

        captchaCheckJob?.cancel()
        viewModelScope.launch {
            _loginUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = syncRepo.loginWithCredentials(
                username = state.username,
                password = state.password,
                captcha = state.captcha,
                rememberPassword = state.rememberPassword,
                existingForm = state.formState
            )) {
                is CasLoginResult.Success -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            password = "",
                            captcha = "",
                            needCaptcha = false,
                            captchaBytes = null,
                            formState = null,
                            errorMessage = null
                        )
                    }
                    applyCurrentAutoSyncSchedule()
                    _selectedWeek.value = settings.value.currentWeekNumber()
                    _snackbarMessages.tryEmit("登录成功，已同步教务数据到本地")
                }

                is CasLoginResult.CaptchaRequired -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            needCaptcha = true,
                            captcha = "",
                            captchaBytes = res.captchaBytes,
                            formState = res.nextFormState,
                            errorMessage = res.message
                        )
                    }
                }

                is CasLoginResult.MfaRequired -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            showWebLogin = true,
                            webLoginUrl = res.mfaUrl,
                            webLoginSeedCookies = syncRepo.exportCookiesForWebView(),
                            formState = null,
                            needCaptcha = false,
                            captcha = "",
                            captchaBytes = null,
                            errorMessage = res.message
                        )
                    }
                    _snackbarMessages.tryEmit(res.message)
                }

                is CasLoginResult.Failure -> {
                    _loginUiState.update {
                        it.copy(
                            isLoading = false,
                            formState = res.nextFormState?.takeIf { f ->
                                f.execution.isNotBlank() && f.pwdEncryptSalt.isNotBlank()
                            },
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun onWebViewLoginSucceeded(cookies: WebViewCapturedCookies) {
        captchaCheckJob?.cancel()
        viewModelScope.launch {
            _loginUiState.update {
                it.copy(
                    showWebLogin = false,
                    webLoginUrl = GdouCasAuthClient.CAS_LOGIN_URL,
                    webLoginSeedCookies = emptyList(),
                    formState = null,
                    needCaptcha = false,
                    captcha = "",
                    captchaBytes = null,
                    isLoading = true,
                    errorMessage = null
                )
            }
            when (val outcome = syncRepo.completeWebViewLogin(
                jwCookieHeader = cookies.jwCookies,
                authRootCookieHeader = cookies.authRootCookies,
                authPathCookieHeader = cookies.authPathCookies,
                ssoTicketUrl = cookies.ssoTicketUrl
            )) {
                is RefreshOutcome.Success -> {
                    _loginUiState.update {
                        it.copy(
                            username = syncRepo.getSavedUsername().ifBlank { it.username },
                            password = "",
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                    applyCurrentAutoSyncSchedule()
                    _selectedWeek.value = settings.value.currentWeekNumber()
                    _snackbarMessages.tryEmit("网页认证成功：${outcome.diff.summary}")
                }
                is RefreshOutcome.Failure -> {
                    _loginUiState.update {
                        it.copy(isLoading = false, errorMessage = outcome.message)
                    }
                    _snackbarMessages.tryEmit("网页登录同步失败：${outcome.message}")
                }
            }
        }
    }

    fun enterDemoMode() {
        viewModelScope.launch {
            _loginUiState.update { it.copy(isLoading = true, errorMessage = null) }
            val outcome = syncRepo.startDemoMode()
            _loginUiState.update { it.copy(isLoading = false) }
            applyCurrentAutoSyncSchedule()
            _selectedWeek.value = settings.value.currentWeekNumber()
            if (outcome is RefreshOutcome.Success) {
                _snackbarMessages.tryEmit("已进入本地演示模式（下拉刷新可模拟数据变动替换）")
            }
        }
    }

    fun manualRefresh() {
        if (isSyncing.value) return
        viewModelScope.launch {
            when (val outcome = syncRepo.refreshAllData(triggerType = "MANUAL", notifyIfChanged = false)) {
                is RefreshOutcome.Success -> {
                    _snackbarMessages.tryEmit(outcome.diff.summary)
                }
                is RefreshOutcome.Failure -> {
                    _snackbarMessages.tryEmit(outcome.message)
                }
            }
        }
    }

    fun maybeAutoRefreshOnForeground() {
        val currentSession = session.value
        val currentSettings = settings.value
        if (!currentSession.isLoggedIn || !currentSettings.refreshOnAppStart) return

        val now = System.currentTimeMillis()
        // Throttle foreground auto-refresh checks to at most once every 3 minutes while navigating
        if (now - lastAutoForegroundAttemptMs < 3 * 60 * 1000L) return
        val intervalMs = (currentSettings.refreshIntervalMinutes.coerceAtLeast(15L)) * 60 * 1000L
        val stale = currentSettings.lastSyncTimestamp == 0L || (now - currentSettings.lastSyncTimestamp) >= intervalMs
        if (!stale) return

        lastAutoForegroundAttemptMs = now
        viewModelScope.launch {
            when (val outcome = syncRepo.refreshAllData(
                triggerType = "AUTO_START",
                notifyIfChanged = false
            )) {
                is RefreshOutcome.Success -> {
                    if (outcome.diff.hasChanges) {
                        _snackbarMessages.tryEmit("自动刷新完成：${outcome.diff.summary}")
                    }
                }
                is RefreshOutcome.Failure -> {
                    // Keep showing local cached data quietly unless user manually refreshes
                }
            }
        }
    }

    fun selectWeek(week: Int) {
        _selectedWeek.value = week.coerceIn(1, 25)
    }

    fun jumpToCurrentWeek() {
        _selectedWeek.value = settings.value.currentWeekNumber()
    }

    fun selectTerm(xnm: String, xqm: String) {
        settingsStore.updateSelectedTerm(xnm, xqm)
        viewModelScope.launch {
            when (val outcome = syncRepo.refreshAllData(
                triggerType = "MANUAL",
                overrideXnm = xnm,
                overrideXqm = xqm,
                autoDetectCurrentTerm = false
            )) {
                is RefreshOutcome.Success -> _snackbarMessages.tryEmit("已切换学期并同步：${AppSettings.formatTermLabel(xnm, xqm)}")
                is RefreshOutcome.Failure -> _snackbarMessages.tryEmit("已切换学期，同步提示：${outcome.message}")
            }
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        settingsStore.updateAutoRefresh(enabled)
        syncScheduler.applySchedule(enabled, settings.value.refreshIntervalMinutes)
        _snackbarMessages.tryEmit(if (enabled) "已开启后台自动刷新" else "已关闭后台自动刷新")
    }

    fun setRefreshIntervalMinutes(minutes: Long) {
        settingsStore.updateRefreshInterval(minutes)
        syncScheduler.applySchedule(settings.value.autoRefreshEnabled, minutes)
        _snackbarMessages.tryEmit("自动刷新周期已设为每 ${formatIntervalLabel(minutes)}")
    }

    fun setRefreshOnAppStart(enabled: Boolean) {
        settingsStore.updateRefreshOnAppStart(enabled)
    }

    fun setNotifyOnChange(enabled: Boolean) {
        settingsStore.updateNotifyOnChange(enabled)
    }

    fun setSemesterStartDate(isoDate: String) {
        settingsStore.updateSemesterStartDate(isoDate)
        _selectedWeek.value = settings.value.currentWeekNumber()
        _snackbarMessages.tryEmit("已更新开学第一周日期：$isoDate")
    }

    fun setShowNonCurrentWeekCourses(show: Boolean) {
        settingsStore.updateShowNonCurrentWeekCourses(show)
    }

    fun setSchedulePreset(presetId: String) {
        settingsStore.updateSchedulePresetId(presetId)
        val name = cn.gdou.edu.timetable.domain.GdouPeriodSchedule.presets.find { it.id == presetId }?.name ?: presetId
        _snackbarMessages.tryEmit("作息时间已切换为：$name")
    }

    fun setThemeSeedColor(argb: Long) {
        settingsStore.updateThemeSeedColor(argb)
    }

    fun setThemeDarkMode(mode: String) {
        settingsStore.updateThemeDarkMode(mode)
    }

    fun setBottomBarTransparency(value: Float) {
        settingsStore.updateBottomBarTransparency(value)
    }

    fun setBottomBarFrost(value: Float) {
        settingsStore.updateBottomBarFrost(value)
    }

    fun setBottomBarStyle(style: String) {
        settingsStore.updateBottomBarStyle(style)
    }

    fun resetTheme() {
        settingsStore.resetTheme()
        _snackbarMessages.tryEmit("已恢复默认主题与底部栏设置")
    }

    fun saveCustomEvent(draft: CustomEventDraft) {
        viewModelScope.launch {
            val entity = CustomScheduleEventEntity(
                id = draft.existingId,
                title = draft.title,
                dateIso = draft.dateIso,
                startTime = draft.startTime,
                endTime = draft.endTime,
                location = draft.location,
                note = draft.note,
                stickyColorIndex = draft.stickyColorIndex,
                repeatWeekly = draft.repeatWeekly
            )
            syncRepo.saveCustomEvent(entity)
            _snackbarMessages.tryEmit(
                if (draft.existingId > 0L) "已更新便签日程「${draft.title}」"
                else "已贴上便签日程「${draft.title}」"
            )
        }
    }

    fun deleteCustomEvent(eventId: Long) {
        viewModelScope.launch {
            syncRepo.deleteCustomEvent(eventId)
            _snackbarMessages.tryEmit("已删除便签日程")
        }
    }

    fun saveCustomExam(draft: CustomExamDraft) {
        viewModelScope.launch {
            val s = settings.value
            val rawTime = "${draft.dateIso}(${draft.startTime}-${draft.endTime})"
            val startTs = GdouParsers.parseExamStartTimestamp(rawTime)
            val entity = ExamEntity(
                id = draft.existingId,
                xnm = s.selectedXnm,
                xqm = s.selectedXqm,
                courseCode = "CUSTOM_${draft.existingId.takeIf { it > 0L } ?: System.currentTimeMillis()}",
                courseName = draft.courseName,
                examName = draft.examName,
                examTimeRaw = rawTime,
                startTimestamp = startTs,
                classroom = draft.classroom,
                campus = draft.campus,
                seatNumber = draft.seatNumber,
                credits = "",
                examMode = draft.examMode,
                classGroup = "",
                isCustom = true
            )
            syncRepo.saveCustomExam(entity)
            _snackbarMessages.tryEmit(
                if (draft.existingId > 0L) "已更新自定义考试《${draft.courseName}》"
                else "已添加自定义考试《${draft.courseName}》并同步至课表"
            )
        }
    }

    fun deleteCustomExam(examId: Long) {
        viewModelScope.launch {
            syncRepo.deleteCustomExam(examId)
            _snackbarMessages.tryEmit("已删除自定义考试")
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            syncRepo.clearLocalCacheOnly()
            _snackbarMessages.tryEmit("已清空本地缓存，下拉或点击刷新可重新拉取")
        }
    }

    fun openGradeBreakdown(grade: GradeEntity, forceRefresh: Boolean = false) {
        gradeBreakdownJob?.cancel()
        val cached = GdouParsers.decodeBreakdown(grade.breakdownJson)
        if (!forceRefresh && cached.isNotEmpty()) {
            _gradeBreakdownState.value = GradeBreakdownUiState(
                grade = grade,
                isLoading = false,
                components = cached,
                errorMessage = null,
                requiresRelogin = false
            )
            return
        }

        _gradeBreakdownState.value = GradeBreakdownUiState(
            grade = grade,
            isLoading = true,
            components = cached,
            errorMessage = null,
            requiresRelogin = false
        )
        gradeBreakdownJob = viewModelScope.launch {
            val result = syncRepo.fetchGradeBreakdown(grade, forceRefresh = forceRefresh)
            result.fold(
                onSuccess = { list ->
                    _gradeBreakdownState.update { current ->
                        if (current.grade?.diffKey() != grade.diffKey()) current
                        else current.copy(
                            isLoading = false,
                            components = list,
                            errorMessage = null,
                            requiresRelogin = false
                        )
                    }
                },
                onFailure = { err ->
                    _gradeBreakdownState.update { current ->
                        if (current.grade?.diffKey() != grade.diffKey()) current
                        else current.copy(
                            isLoading = false,
                            errorMessage = err.message?.takeIf { it.isNotBlank() } ?: "获取分数构成失败，请重试",
                            requiresRelogin = err is SessionExpiredException
                        )
                    }
                }
            )
        }
    }

    fun closeGradeBreakdown() {
        gradeBreakdownJob?.cancel()
        _gradeBreakdownState.value = GradeBreakdownUiState()
    }

    // ---- App Version Update Push & In-App APK Upgrade ----

    fun saveUpdateSource(repoSpec: String, mirrorMode: String, customMirrorPrefix: String) {
        settingsStore.updateAppUpdateSource(repoSpec, mirrorMode, customMirrorPrefix)
        val updated = settings.value
        updateCheckScheduler.applySchedule(
            enabled = updated.autoCheckUpdatesEnabled,
            hasConfiguredSource = updated.updateRepoSpec.isNotBlank()
        )
        if (updated.updateRepoSpec.isNotBlank()) {
            _snackbarMessages.tryEmit("已保存更新源，正在检测最新版本…")
            checkAppUpdate(manual = true)
        } else {
            _appUpdateState.update {
                it.copy(availableUpdate = null, showDialog = false, statusText = "未配置更新源")
            }
            _snackbarMessages.tryEmit("已清空更新源并暂停后台更新检测")
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        settingsStore.updateAutoCheckUpdates(enabled)
        updateCheckScheduler.applySchedule(
            enabled = enabled,
            hasConfiguredSource = settings.value.updateRepoSpec.isNotBlank()
        )
        _snackbarMessages.tryEmit(if (enabled) "已开启应用版本自动检测" else "已关闭应用版本自动检测")
    }

    fun setUpdateNotifyPush(enabled: Boolean) {
        settingsStore.updateNotifyUpdatePush(enabled)
        _snackbarMessages.tryEmit(if (enabled) "已开启新版本系统通知推送" else "已关闭新版本系统通知推送")
    }

    fun maybeAutoCheckAppUpdateOnForeground(context: Context) {
        val s = settings.value
        if (!s.autoCheckUpdatesEnabled || s.updateRepoSpec.isBlank()) return
        val now = System.currentTimeMillis()
        // Throttle foreground version checks to at most once every 6 hours
        if (now - lastUpdateAutoCheckMs < 6 * 60 * 60 * 1000L) return
        if (s.lastUpdateCheckTimestamp > 0L && now - s.lastUpdateCheckTimestamp < 6 * 60 * 60 * 1000L) {
            return
        }
        lastUpdateAutoCheckMs = now
        checkAppUpdate(manual = false, contextForPush = context.applicationContext)
    }

    fun checkAppUpdate(manual: Boolean = true, contextForPush: Context? = null) {
        val s = settings.value
        if (_appUpdateState.value.isChecking) return
        if (s.updateRepoSpec.isBlank()) {
            if (manual) {
                _snackbarMessages.tryEmit("请先点击「配置更新源」填写你的 GitHub 仓库地址")
            }
            return
        }

        viewModelScope.launch {
            _appUpdateState.update { it.copy(isChecking = true, statusText = "正在检测新版本…") }
            val result = appUpdateClient.checkLatestRelease(s.updateRepoSpec)
            val now = System.currentTimeMillis()
            result.fold(
                onSuccess = { info ->
                    val encoded = appUpdateClient.encodeInfo(info)
                    val isNewer = info.isNewerThanCurrent()
                    val isIgnored = !manual && info.versionName.equals(s.ignoredUpdateVersion, ignoreCase = true)

                    var notifiedVersion: String? = null
                    if (isNewer && !isIgnored && s.updateNotifyPushEnabled && contextForPush != null &&
                        !info.versionName.equals(s.lastNotifiedUpdateVersion, ignoreCase = true)
                    ) {
                        if (UpdateNotificationHelper.notifyUpdateAvailable(contextForPush, info)) {
                            notifiedVersion = info.versionName
                        }
                    }

                    settingsStore.recordUpdateCheck(
                        timestamp = now,
                        cachedJson = encoded,
                        notifiedVersion = notifiedVersion
                    )

                    if (isNewer && !isIgnored) {
                        _appUpdateState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = info,
                                showDialog = true,
                                statusText = "发现新版本 v${info.versionName}",
                                downloadStage = ApkDownloadStage.IDLE,
                                downloadError = null
                            )
                        }
                    } else if (isNewer) {
                        _appUpdateState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = info,
                                statusText = "存在已忽略的新版本 v${info.versionName}"
                            )
                        }
                    } else {
                        _appUpdateState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = null,
                                showDialog = false,
                                statusText = "当前已是最新版本 v${BuildConfig.VERSION_NAME}"
                            )
                        }
                        if (manual) {
                            _snackbarMessages.tryEmit("当前已是最新版本（v${BuildConfig.VERSION_NAME}）")
                        }
                    }
                },
                onFailure = { err ->
                    val msg = err.message?.takeIf { it.isNotBlank() } ?: "检查更新失败，请检查网络连接"
                    _appUpdateState.update {
                        it.copy(isChecking = false, statusText = "检测失败：$msg")
                    }
                    if (manual) {
                        _snackbarMessages.tryEmit(msg)
                    }
                }
            )
        }
    }

    fun openUpdateDialogFromNotification() {
        val current = _appUpdateState.value.availableUpdate
            ?: appUpdateClient.decodeInfo(settings.value.cachedUpdateJson)
        if (current != null) {
            _appUpdateState.update {
                it.copy(availableUpdate = current, showDialog = true)
            }
        } else if (settings.value.updateRepoSpec.isNotBlank()) {
            checkAppUpdate(manual = true)
        }
    }

    fun openAvailableUpdateDialog() {
        val info = _appUpdateState.value.availableUpdate ?: return
        _appUpdateState.update { it.copy(availableUpdate = info, showDialog = true) }
    }

    fun dismissUpdateDialog() {
        if (_appUpdateState.value.downloadStage == ApkDownloadStage.DOWNLOADING) return
        _appUpdateState.update { it.copy(showDialog = false) }
    }

    fun ignoreCurrentUpdate() {
        val ver = _appUpdateState.value.availableUpdate?.versionName.orEmpty()
        if (ver.isNotBlank()) {
            settingsStore.setIgnoredUpdateVersion(ver)
            _snackbarMessages.tryEmit("已忽略版本 v$ver，后续可在「我的」手动检查更新")
        }
        _appUpdateState.update { it.copy(showDialog = false) }
    }

    fun startApkDownload(context: Context) {
        val info = _appUpdateState.value.availableUpdate ?: return
        if (info.apkDownloadUrl.isBlank()) {
            _snackbarMessages.tryEmit("该 Release 未附带 APK 文件，请点击「浏览器打开」下载")
            return
        }
        val appCtx = context.applicationContext
        val s = settings.value
        val urls = AppUpdateClient.buildDownloadUrls(
            directUrl = info.apkDownloadUrl,
            mirrorMode = s.updateMirrorMode,
            customMirrorPrefix = s.updateCustomMirrorPrefix
        )

        apkDownloadJob?.cancel()
        _appUpdateState.update {
            it.copy(
                downloadStage = ApkDownloadStage.DOWNLOADING,
                downloadedBytes = 0L,
                totalBytes = info.apkSizeBytes,
                downloadPercent = 0f,
                speedBytesPerSec = 0L,
                downloadError = null
            )
        }

        apkDownloadJob = viewModelScope.launch {
            try {
                val res = apkUpdateDownloader.downloadApk(
                    candidateUrls = urls,
                    versionName = info.versionName
                ) { p ->
                    _appUpdateState.update {
                        it.copy(
                            downloadStage = ApkDownloadStage.DOWNLOADING,
                            downloadedBytes = p.downloadedBytes,
                            totalBytes = if (p.totalBytes > 0L) p.totalBytes else it.totalBytes,
                            downloadPercent = p.percent,
                            speedBytesPerSec = p.speedBytesPerSec
                        )
                    }
                }
                res.fold(
                    onSuccess = { apkFile ->
                        _appUpdateState.update {
                            it.copy(
                                downloadStage = ApkDownloadStage.READY_TO_INSTALL,
                                downloadPercent = 1f,
                                downloadedApkPath = apkFile.absolutePath,
                                downloadError = null
                            )
                        }
                        if (settings.value.updateNotifyPushEnabled) {
                            UpdateNotificationHelper.notifyDownloadReady(appCtx, info.versionName, apkFile)
                        }
                        installDownloadedApk(appCtx)
                    },
                    onFailure = { err ->
                        if (err is CancellationException) throw err
                        val msg = err.message?.takeIf { it.isNotBlank() } ?: "下载失败，请检查网络或切换镜像加速后重试"
                        _appUpdateState.update {
                            it.copy(
                                downloadStage = ApkDownloadStage.ERROR,
                                downloadError = msg
                            )
                        }
                    }
                )
            } catch (_: CancellationException) {
                _appUpdateState.update {
                    it.copy(
                        downloadStage = ApkDownloadStage.IDLE,
                        downloadPercent = 0f,
                        speedBytesPerSec = 0L
                    )
                }
            }
        }
    }

    fun cancelApkDownload() {
        apkDownloadJob?.cancel()
        apkDownloadJob = null
        _appUpdateState.update {
            it.copy(
                downloadStage = ApkDownloadStage.IDLE,
                downloadPercent = 0f,
                speedBytesPerSec = 0L
            )
        }
        _snackbarMessages.tryEmit("已取消更新包下载")
    }

    fun installDownloadedApk(context: Context) {
        val path = _appUpdateState.value.downloadedApkPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _appUpdateState.update {
                it.copy(
                    downloadStage = ApkDownloadStage.ERROR,
                    downloadError = "更新包已被清理，请重新下载"
                )
            }
            return
        }
        if (!ApkInstaller.canInstallPackages(context)) {
            _appUpdateState.update { it.copy(downloadStage = ApkDownloadStage.NEEDS_PERMISSION) }
            return
        }
        runCatching {
            context.startActivity(ApkInstaller.createInstallIntent(context, file))
            _appUpdateState.update { it.copy(downloadStage = ApkDownloadStage.READY_TO_INSTALL) }
        }.onFailure { err ->
            _appUpdateState.update {
                it.copy(
                    downloadStage = ApkDownloadStage.ERROR,
                    downloadError = "无法调起系统安装器：${err.message ?: "未知错误"}"
                )
            }
        }
    }

    fun sendTestUpdatePush(context: Context) {
        val appCtx = context.applicationContext
        val sample = AppUpdateInfo(
            versionName = "1.1.0",
            versionCode = BuildConfig.VERSION_CODE + 1,
            releaseTitle = "啾比教务 v1.1.0 更新推送预览",
            changelog = "• 新增 GitHub Releases 自动版本检测与系统推送通知\n• 支持应用内进度下载与一键安装新版本 APK\n• 支持直连 / 镜像加速通道自动回退\n• 修复退出重登与若干体验细节",
            publishedAt = java.time.LocalDate.now().toString(),
            apkSizeBytes = 15L * 1024L * 1024L,
            sourceLabel = settings.value.updateRepoSpec.ifBlank { "测试预览" },
            isTestPreview = true
        )
        settingsStore.recordUpdateCheck(
            timestamp = System.currentTimeMillis(),
            cachedJson = appUpdateClient.encodeInfo(sample)
        )
        val posted = UpdateNotificationHelper.notifyUpdateAvailable(appCtx, sample)
        _appUpdateState.update {
            it.copy(
                availableUpdate = sample,
                showDialog = !posted, // If system notification posted, let user tap notification (or open from card); if blocked, show dialog directly
                statusText = if (posted) "已发送测试更新推送通知" else "通知权限未开启，已直接打开预览弹窗",
                downloadStage = ApkDownloadStage.IDLE,
                downloadError = null
            )
        }
        _snackbarMessages.tryEmit(
            if (posted) "已推送测试更新通知，下拉通知栏点击即可直达升级弹窗"
            else "系统通知未授权，已直接弹出更新卡片预览"
        )
    }

    fun logout() {
        viewModelScope.launch {
            captchaCheckJob?.cancel()
            gradeBreakdownJob?.cancel()
            _gradeBreakdownState.value = GradeBreakdownUiState()
            lastAutoForegroundAttemptMs = 0L
            syncScheduler.cancelAll()
            syncRepo.logout()
            _loginUiState.value = LoginUiState(
                username = syncRepo.getSavedUsername(),
                rememberPassword = true
            )
            _snackbarMessages.tryEmit("已退出登录并清除本地数据")
        }
    }

    private fun applyCurrentAutoSyncSchedule() {
        val s = settings.value
        syncScheduler.applySchedule(s.autoRefreshEnabled, s.refreshIntervalMinutes)
    }

    companion object {
        fun formatIntervalLabel(minutes: Long): String = when {
            minutes < 60 -> "${minutes}分钟"
            minutes % 60L == 0L -> "${minutes / 60}小时"
            else -> "${minutes}分钟"
        }

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(container) as T
                }
            }
    }
}

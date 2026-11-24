package cn.gdou.edu.timetable.data.repository

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.room.withTransaction
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.local.AppSettingsStore
import cn.gdou.edu.timetable.data.local.SavedSessionInfo
import cn.gdou.edu.timetable.data.local.SecureCredentialStore
import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.CustomScheduleEventEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GdouDatabase
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import cn.gdou.edu.timetable.data.local.db.SyncLogEntity
import cn.gdou.edu.timetable.data.remote.CasLoginFormState
import cn.gdou.edu.timetable.data.remote.CasLoginResult
import cn.gdou.edu.timetable.data.remote.GdouCasAuthClient
import cn.gdou.edu.timetable.data.remote.GdouJwApiService
import cn.gdou.edu.timetable.data.remote.GdouParsers
import cn.gdou.edu.timetable.data.remote.GradeScoreComponent
import cn.gdou.edu.timetable.data.remote.PersistentCookieJar
import cn.gdou.edu.timetable.data.remote.RemoteStudentBundle
import cn.gdou.edu.timetable.data.remote.SessionExpiredException
import cn.gdou.edu.timetable.domain.DataDiffEngine
import cn.gdou.edu.timetable.domain.DemoDataGenerator
import cn.gdou.edu.timetable.domain.SyncDiffResult
import cn.gdou.edu.timetable.worker.SyncNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface RefreshOutcome {
    data class Success(val diff: SyncDiffResult) : RefreshOutcome
    data class Failure(val message: String, val requiresRelogin: Boolean = false) : RefreshOutcome
}

/**
 * Single Source of Truth repository coordinating:
 * 1. GDOU CAS authentication & WebView cookie login
 * 2. Fetching latest student data from jw.gdou.edu.cn
 * 3. Computing diffs against cached Room records
 * 4. Atomically replacing old local data with new data in a single Room transaction
 */
class SyncRepository(
    private val appContext: Context,
    private val database: GdouDatabase,
    private val credentialStore: SecureCredentialStore,
    private val settingsStore: AppSettingsStore,
    private val cookieJar: PersistentCookieJar,
    private val authClient: GdouCasAuthClient,
    private val jwApiService: GdouJwApiService
) {
    private val syncMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var demoRevisionCounter = 0

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val sessionState: StateFlow<SavedSessionInfo> = credentialStore.sessionState
    val settingsState: StateFlow<AppSettings> = settingsStore.settings

    fun observeProfile(): Flow<StudentProfileEntity?> = database.profileDao().observeProfile()
    fun observeCoursesForTerm(xnm: String, xqm: String): Flow<List<CourseEntity>> =
        database.courseDao().observeCoursesForTerm(xnm, xqm)
    fun observeExamsForTerm(xnm: String, xqm: String): Flow<List<ExamEntity>> =
        database.examDao().observeExamsForTerm(xnm, xqm)
    fun observeAllGrades(): Flow<List<GradeEntity>> = database.gradeDao().observeAllGrades()
    fun observeCustomEvents(): Flow<List<CustomScheduleEventEntity>> = database.customEventDao().observeAll()
    fun observeSyncLogs(): Flow<List<SyncLogEntity>> = database.syncLogDao().observeRecentLogs()

    fun getSavedUsername(): String = credentialStore.getUsername()
    fun hasSavedPassword(): Boolean = credentialStore.hasSavedPassword()

    suspend fun prepareCasLogin(): Result<CasLoginFormState> = authClient.prepareLoginPage()
    suspend fun checkNeedCaptcha(username: String): Boolean = authClient.checkNeedCaptcha(username)
    suspend fun fetchCaptchaImage(): Result<ByteArray> = authClient.fetchCaptchaImage()

    suspend fun loginWithCredentials(
        username: String,
        password: String,
        captcha: String,
        rememberPassword: Boolean,
        existingForm: CasLoginFormState?
    ): CasLoginResult {
        val result = authClient.login(
            username = username,
            password = password,
            captcha = captcha,
            existingForm = existingForm,
            clearOldCookies = existingForm == null
        )
        if (result is CasLoginResult.Success) {
            val outcome = refreshAllDataInternal(
                triggerType = "LOGIN",
                autoDetectCurrentTerm = true,
                allowPendingLogin = true,
                pendingUsername = username.trim()
            )
            return when (outcome) {
                is RefreshOutcome.Success -> {
                    credentialStore.saveCredentials(username, password, rememberPassword)
                    result
                }
                is RefreshOutcome.Failure -> {
                    if (outcome.requiresRelogin) {
                        cookieJar.clearSessionCookies(preserveMfaFingerprint = true)
                    }
                    CasLoginResult.Failure(outcome.message)
                }
            }
        }
        return result
    }

    fun exportCookiesForWebView(): List<Pair<String, String>> =
        cookieJar.exportForWebView("gdou.edu.cn")

    suspend fun completeWebViewLogin(
        jwCookieHeader: String?,
        authRootCookieHeader: String?,
        authPathCookieHeader: String? = null,
        ssoTicketUrl: String? = null
    ): RefreshOutcome {
        val wasAlreadyLoggedIn = credentialStore.sessionState.value.isLoggedIn
        // Clear any leftover OkHttp session cookies so fresh WebView cookies never collide with stale ones
        cookieJar.clearSessionCookies(preserveMfaFingerprint = true)
        cookieJar.importWebViewCookieHeader(GdouCasAuthClient.JW_BASE_URL, jwCookieHeader, "/")
        cookieJar.importWebViewCookieHeader("https://authserver.gdou.edu.cn", authRootCookieHeader, "/")
        cookieJar.importWebViewCookieHeader(
            hostUrl = "https://authserver.gdou.edu.cn",
            cookieHeader = authPathCookieHeader,
            pathOverride = "/authserver",
            skipExactPairs = parseCookiePairs(authRootCookieHeader)
        )

        if (!ssoTicketUrl.isNullOrBlank()) {
            val exchangeRes = authClient.exchangeSsoTicket(ssoTicketUrl)
            if (exchangeRes.isFailure && jwCookieHeader.isNullOrBlank()) {
                val err = exchangeRes.exceptionOrNull()
                val msg = "教务换票连接失败：${err?.message ?: "请检查网络后重试"}"
                if (!wasAlreadyLoggedIn) {
                    cookieJar.clearSessionCookies(preserveMfaFingerprint = true)
                }
                return RefreshOutcome.Failure(msg, requiresRelogin = true)
            }
        }

        val outcome = refreshAllDataInternal(
            triggerType = "LOGIN",
            autoDetectCurrentTerm = true,
            allowPendingLogin = true,
            pendingUsername = credentialStore.getUsername()
        )
        when (outcome) {
            is RefreshOutcome.Success -> {
                val detectedStudentId = database.profileDao().getProfileOnce()?.studentId.orEmpty()
                val usernameHint = detectedStudentId.ifBlank { credentialStore.getUsername() }
                credentialStore.markWebLoginSuccess(usernameHint)
            }
            is RefreshOutcome.Failure -> {
                if (!wasAlreadyLoggedIn && outcome.requiresRelogin) {
                    cookieJar.clearSessionCookies(preserveMfaFingerprint = true)
                }
            }
        }
        return outcome
    }

    private fun parseCookiePairs(cookieHeader: String?): Set<Pair<String, String>> {
        if (cookieHeader.isNullOrBlank()) return emptySet()
        return cookieHeader.split(";").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
        }.filter { it.first.isNotEmpty() }.toSet()
    }

    suspend fun startDemoMode(): RefreshOutcome {
        demoRevisionCounter = 0
        credentialStore.markDemoModeLoggedIn("202411701208")
        return refreshAllData(triggerType = "LOGIN", autoDetectCurrentTerm = true)
    }

    /**
     * Refreshes all student information from GDOU JW, computes diffs against cached Room records,
     * and atomically replaces old local data with the fresh data.
     */
    suspend fun refreshAllData(
        triggerType: String = "MANUAL",
        overrideXnm: String? = null,
        overrideXqm: String? = null,
        autoDetectCurrentTerm: Boolean = false,
        notifyIfChanged: Boolean = false
    ): RefreshOutcome = refreshAllDataInternal(
        triggerType = triggerType,
        overrideXnm = overrideXnm,
        overrideXqm = overrideXqm,
        autoDetectCurrentTerm = autoDetectCurrentTerm,
        notifyIfChanged = notifyIfChanged,
        allowPendingLogin = false,
        pendingUsername = ""
    )

    private suspend fun refreshAllDataInternal(
        triggerType: String,
        overrideXnm: String? = null,
        overrideXqm: String? = null,
        autoDetectCurrentTerm: Boolean = false,
        notifyIfChanged: Boolean = false,
        allowPendingLogin: Boolean = false,
        pendingUsername: String = ""
    ): RefreshOutcome = syncMutex.withLock {
        val session = credentialStore.sessionState.value
        if (!session.isLoggedIn && !allowPendingLogin) {
            return RefreshOutcome.Failure("尚未登录教务系统", requiresRelogin = true)
        }

        _isSyncing.value = true
        try {
            val settings = settingsStore.settings.value
            val targetXnm = overrideXnm ?: settings.selectedXnm
            val targetXqm = overrideXqm ?: settings.selectedXqm

            val bundle: RemoteStudentBundle = if (session.isDemoMode && !allowPendingLogin) {
                val rev = if (triggerType == "LOGIN") 0 else ++demoRevisionCounter
                DemoDataGenerator.buildBundle(rev)
            } else if (allowPendingLogin) {
                jwApiService.fetchAllStudentData(
                    requestedXnm = targetXnm,
                    requestedXqm = targetXqm,
                    fallbackStudentId = pendingUsername.ifBlank { credentialStore.getUsername() },
                    autoDetectCurrentTerm = autoDetectCurrentTerm
                )
            } else {
                fetchRemoteWithAutoRelogin(
                    xnm = targetXnm,
                    xqm = targetXqm,
                    autoDetectCurrentTerm = autoDetectCurrentTerm
                )
            }

            if (bundle.termMetadata.availableTerms.isNotEmpty()) {
                settingsStore.updateAvailableTerms(bundle.termMetadata.availableTerms)
            }
            if (autoDetectCurrentTerm || overrideXnm != null || overrideXqm != null) {
                settingsStore.updateSelectedTerm(bundle.effectiveXnm, bundle.effectiveXqm)
            }

            // Read existing local cache for diff comparison (only synced exams so custom exams never trigger false diffs)
            val oldProfile = database.profileDao().getProfileOnce()
            val oldCourses = database.courseDao().getCoursesForTermOnce(bundle.effectiveXnm, bundle.effectiveXqm)
            val oldExams = database.examDao().getSyncedExamsForTermOnce(bundle.effectiveXnm, bundle.effectiveXqm)
            val oldGrades = database.gradeDao().getAllGradesOnce()

            val diff = DataDiffEngine.computeDiff(
                oldProfile = oldProfile,
                newProfile = bundle.profile,
                oldCourses = oldCourses,
                newCourses = bundle.courses,
                oldExams = oldExams,
                newExams = bundle.exams,
                oldGrades = oldGrades,
                newGrades = bundle.grades
            )

            val oldGradeByKey = oldGrades.associateBy { it.diffKey() }
            val mergedGrades = bundle.grades.map { fresh ->
                if (fresh.breakdownJson.isNotBlank()) {
                    fresh
                } else {
                    val prev = oldGradeByKey[fresh.diffKey()]
                    if (prev != null && prev.diffSignature() == fresh.diffSignature() && prev.breakdownJson.isNotBlank()) {
                        fresh.copy(breakdownJson = prev.breakdownJson)
                    } else {
                        fresh
                    }
                }
            }

            val now = System.currentTimeMillis()
            // Atomic full replacement: new data completely replaces old local data in one transaction
            database.withTransaction {
                database.profileDao().replaceProfile(bundle.profile)
                database.courseDao().replaceCoursesForTerm(
                    bundle.effectiveXnm,
                    bundle.effectiveXqm,
                    bundle.courses
                )
                database.examDao().replaceExamsForTerm(
                    bundle.effectiveXnm,
                    bundle.effectiveXqm,
                    bundle.exams
                )
                database.gradeDao().replaceAllGrades(mergedGrades)
                database.syncLogDao().insert(
                    SyncLogEntity(
                        timestamp = now,
                        triggerType = triggerType,
                        success = true,
                        hasChanges = diff.hasChanges,
                        summary = diff.summary,
                        details = diff.details.joinToString("\n")
                    )
                )
                database.syncLogDao().trimOldLogs()
            }

            if (!allowPendingLogin && bundle.profile.studentId.isNotBlank() && credentialStore.getUsername().isBlank()) {
                credentialStore.markWebLoginSuccess(bundle.profile.studentId)
            }

            settingsStore.recordSyncMeta(
                timestamp = now,
                status = if (diff.hasChanges) "已更新并替换本地数据" else "同步成功（无变动）",
                changeSummary = diff.summary
            )

            if (notifyIfChanged && diff.hasChanges && settingsStore.settings.value.notifyOnChange && triggerType != "LOGIN") {
                SyncNotificationHelper.notifyChanges(appContext, diff)
            }

            RefreshOutcome.Success(diff)
        } catch (expired: SessionExpiredException) {
            val msg = expired.message ?: "登录会话已过期，请重新登录"
            recordFailureLog(triggerType, msg)
            RefreshOutcome.Failure(msg, requiresRelogin = true)
        } catch (e: Exception) {
            val msg = e.message?.takeIf { it.isNotBlank() } ?: "同步失败，请检查网络连接"
            recordFailureLog(triggerType, msg)
            RefreshOutcome.Failure(msg, requiresRelogin = false)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Fetches and caches the detailed score breakdown (平时/实验/期中/期末/总评) for a single course grade.
     * If the local grade row predates schema v2 (`jxbId` blank), automatically refreshes grades first
     * so the user gets the breakdown seamlessly on the first tap.
     */
    suspend fun fetchGradeBreakdown(
        grade: GradeEntity,
        forceRefresh: Boolean = false
    ): Result<List<GradeScoreComponent>> {
        if (!forceRefresh && grade.breakdownJson.isNotBlank()) {
            val cached = GdouParsers.decodeBreakdown(grade.breakdownJson)
            if (cached.isNotEmpty()) return Result.success(cached)
        }

        val session = credentialStore.sessionState.value
        if (!session.isLoggedIn) {
            return Result.failure(SessionExpiredException("尚未登录教务系统"))
        }
        if (session.isDemoMode) {
            return Result.success(GdouParsers.decodeBreakdown(grade.breakdownJson))
        }

        var effectiveGrade = grade
        if (effectiveGrade.jxbId.isBlank()) {
            when (val refreshRes = refreshAllData(triggerType = "MANUAL")) {
                is RefreshOutcome.Failure -> {
                    val ex = if (refreshRes.requiresRelogin) {
                        SessionExpiredException(refreshRes.message)
                    } else {
                        IllegalStateException(refreshRes.message)
                    }
                    return Result.failure(ex)
                }
                is RefreshOutcome.Success -> {
                    val latestGrades = database.gradeDao().getAllGradesOnce()
                    effectiveGrade = latestGrades.find { it.diffKey() == grade.diffKey() }
                        ?: latestGrades.find {
                            it.xnm == grade.xnm && it.xqm == grade.xqm && it.courseName == grade.courseName
                        }
                        ?: effectiveGrade
                    if (!forceRefresh && effectiveGrade.breakdownJson.isNotBlank()) {
                        val inline = GdouParsers.decodeBreakdown(effectiveGrade.breakdownJson)
                        if (inline.isNotEmpty()) return Result.success(inline)
                    }
                }
            }
        }

        return try {
            val components = executeWithAutoRelogin { studentId ->
                jwApiService.fetchGradeBreakdown(effectiveGrade, studentId)
            }
            if (components.isNotEmpty()) {
                val targetId = database.gradeDao().getAllGradesOnce()
                    .find { it.diffKey() == effectiveGrade.diffKey() }?.id
                    ?: effectiveGrade.id
                if (targetId > 0L) {
                    database.gradeDao().updateBreakdown(targetId, GdouParsers.encodeBreakdown(components))
                }
            }
            Result.success(components)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchRemoteWithAutoRelogin(
        xnm: String,
        xqm: String,
        autoDetectCurrentTerm: Boolean
    ): RemoteStudentBundle = executeWithAutoRelogin { studentId ->
        jwApiService.fetchAllStudentData(
            requestedXnm = xnm,
            requestedXqm = xqm,
            fallbackStudentId = studentId,
            autoDetectCurrentTerm = autoDetectCurrentTerm
        )
    }

    private suspend fun <T> executeWithAutoRelogin(
        block: suspend (studentId: String) -> T
    ): T {
        val studentId = credentialStore.getUsername()
        try {
            return block(studentId)
        } catch (expired: SessionExpiredException) {
            // Attempt seamless background CAS re-login using locally encrypted password if available
            val savedPwd = credentialStore.getSavedPassword()
            if (studentId.isBlank() || savedPwd.isBlank()) {
                throw SessionExpiredException("登录态已失效，请重新输入密码或网页登录")
            }
            when (val relogin = authClient.login(
                username = studentId,
                password = savedPwd,
                captcha = "",
                existingForm = null,
                clearOldCookies = true
            )) {
                is CasLoginResult.Success -> {
                    return block(studentId)
                }
                is CasLoginResult.CaptchaRequired -> {
                    throw SessionExpiredException("教务系统要求输入验证码，请打开应用手动验证登录")
                }
                is CasLoginResult.MfaRequired -> {
                    throw SessionExpiredException("教务系统要求多因子认证，请打开应用使用网页认证")
                }
                is CasLoginResult.Failure -> {
                    throw SessionExpiredException("自动重登失败：${relogin.message}")
                }
            }
        }
    }

    private suspend fun recordFailureLog(triggerType: String, message: String) {
        val now = System.currentTimeMillis()
        runCatching {
            database.syncLogDao().insert(
                SyncLogEntity(
                    timestamp = now,
                    triggerType = triggerType,
                    success = false,
                    hasChanges = false,
                    summary = "同步失败",
                    details = message
                )
            )
            database.syncLogDao().trimOldLogs()
            settingsStore.recordSyncMeta(
                timestamp = settingsStore.settings.value.lastSyncTimestamp,
                status = "上次同步失败：$message",
                changeSummary = settingsStore.settings.value.lastChangeSummary
            )
        }
    }

    suspend fun saveCustomExam(exam: ExamEntity): Long =
        database.examDao().insert(exam.copy(isCustom = true))

    suspend fun deleteCustomExam(examId: Long) {
        database.examDao().deleteById(examId)
    }

    suspend fun saveCustomEvent(event: CustomScheduleEventEntity): Long =
        database.customEventDao().insert(event)

    suspend fun deleteCustomEvent(eventId: Long) {
        database.customEventDao().deleteById(eventId)
    }

    suspend fun clearLocalCacheOnly(clearCustomData: Boolean = false) {
        syncMutex.withLock {
            database.withTransaction {
                database.profileDao().deleteAll()
                database.courseDao().deleteAll()
                if (clearCustomData) {
                    database.examDao().deleteAll()
                    database.customEventDao().deleteAll()
                } else {
                    database.examDao().deleteAllSynced()
                }
                database.gradeDao().deleteAll()
                database.syncLogDao().deleteAll()
            }
            settingsStore.resetSyncMeta()
        }
    }

    suspend fun logout() {
        val wasDemo = credentialStore.sessionState.value.isDemoMode
        val oldCookies = if (!wasDemo) cookieJar.snapshotAll() else emptyList()
        clearLocalCacheOnly(clearCustomData = true)
        cookieJar.clearAll()
        clearWebViewCookiesAndStorage()
        credentialStore.clearSession(keepUsername = !wasDemo)
        if (oldCookies.isNotEmpty()) {
            // Invalidate old CAS/JW server session asynchronously using the frozen cookie snapshot
            // so logout UI is instant and never blocks or mutates cookies of a subsequent login.
            backgroundScope.launch {
                withTimeoutOrNull(3_000L) {
                    authClient.serverLogout(oldCookies)
                }
            }
        }
    }

    private suspend fun clearWebViewCookiesAndStorage() {
        withTimeoutOrNull(3_000L) {
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    val cm = CookieManager.getInstance()
                    suspendCancellableCoroutine<Unit> { cont ->
                        cm.removeAllCookies {
                            runCatching { cm.flush() }
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }
                runCatching {
                    WebStorage.getInstance().deleteAllData()
                }
            }
        }
    }
}

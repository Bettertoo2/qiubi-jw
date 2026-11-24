package cn.gdou.edu.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.domain.GpaCalculator
import cn.gdou.edu.timetable.ui.components.AppUpdateDialog
import cn.gdou.edu.timetable.ui.components.CustomFrostedBottomBar
import cn.gdou.edu.timetable.ui.components.CustomNavTab
import cn.gdou.edu.timetable.ui.exams.ExamsScreen
import cn.gdou.edu.timetable.ui.grades.GradesScreen
import cn.gdou.edu.timetable.ui.login.LoginScreen
import cn.gdou.edu.timetable.ui.login.WebLoginScreen
import cn.gdou.edu.timetable.ui.profile.ProfileScreen
import cn.gdou.edu.timetable.ui.timetable.TimetableScreen

@Composable
fun GdouAppRoot(
    viewModel: MainViewModel,
    openUpdateSignal: Int = 0
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val loginState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val grades by viewModel.grades.collectAsStateWithLifecycle()
    val customEvents by viewModel.customEvents.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val gradeBreakdownState by viewModel.gradeBreakdownState.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(openUpdateSignal) {
        if (openUpdateSignal > 0) {
            viewModel.openUpdateDialogFromNotification()
        }
    }

    // Trigger foreground auto-refresh and throttled app update check when app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session.isLoggedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (session.isLoggedIn) {
                    viewModel.maybeAutoRefreshOnForeground()
                }
                viewModel.maybeAutoCheckAppUpdateOnForeground(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Global App Update dialog (accessible from update push notifications even on LoginScreen)
    if (appUpdateState.showDialog && appUpdateState.availableUpdate != null) {
        AppUpdateDialog(
            state = appUpdateState,
            onStartDownload = { viewModel.startApkDownload(context) },
            onCancelDownload = viewModel::cancelApkDownload,
            onInstallNow = { viewModel.installDownloadedApk(context) },
            onIgnoreVersion = viewModel::ignoreCurrentUpdate,
            onDismiss = viewModel::dismissUpdateDialog
        )
    }

    // WebView login overlay (usable both before login and for MFA / re-authentication)
    if (loginState.showWebLogin) {
        WebLoginScreen(
            initialUrl = loginState.webLoginUrl,
            seedCookies = loginState.webLoginSeedCookies,
            onBack = viewModel::closeWebLogin,
            onLoginCaptured = viewModel::onWebViewLoginSucceeded
        )
        return
    }

    if (!session.isLoggedIn) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                LoginScreen(
                    state = loginState,
                    onUsernameChange = viewModel::onUsernameChanged,
                    onUsernameFocusLost = viewModel::checkCaptchaForCurrentUsername,
                    onPasswordChange = viewModel::onPasswordChanged,
                    onCaptchaChange = viewModel::onCaptchaChanged,
                    onRefreshCaptcha = viewModel::refreshCaptchaImage,
                    onRememberPasswordChange = viewModel::onRememberPasswordChanged,
                    onSubmitLogin = viewModel::submitLogin,
                    onOpenWebLogin = { viewModel.openWebLogin() },
                    onEnterDemoMode = viewModel::enterDemoMode
                )
            }
        }
        return
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            CustomNavTab("日程", Icons.Filled.CalendarViewWeek, Icons.Outlined.CalendarViewWeek),
            CustomNavTab("考试", Icons.AutoMirrored.Filled.EventNote, Icons.AutoMirrored.Outlined.EventNote),
            CustomNavTab("成绩绩点", Icons.Filled.Assessment, Icons.Outlined.Assessment),
            CustomNavTab("我的", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    val calculatedGpa = remember(grades) { GpaCalculator.summarize(grades).weightedGpa }
    val backdropLayer = rememberGraphicsLayer()
    var contentPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val fallbackNavBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (settings.bottomBarStyle == AppSettings.BOTTOM_BAR_STYLE_DOCKED) 68.dp else 86.dp
    var measuredBarHeightDp by remember { mutableStateOf(fallbackNavBottom) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Screen Content: recorded once into backdropLayer so CustomFrostedBottomBar can apply real GPU blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { contentPositionInRoot = it.positionInRoot() }
                .drawWithContent {
                    backdropLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(backdropLayer)
                }
                .statusBarsPadding()
        ) {
            when (currentTab) {
                0 -> TimetableScreen(
                    courses = courses,
                    exams = exams,
                    customEvents = customEvents,
                    settings = settings,
                    selectedWeek = selectedWeek,
                    isSyncing = isSyncing,
                    isDemoMode = session.isDemoMode,
                    bottomBarPadding = measuredBarHeightDp,
                    onSelectWeek = viewModel::selectWeek,
                    onJumpCurrentWeek = viewModel::jumpToCurrentWeek,
                    onSelectTerm = viewModel::selectTerm,
                    onToggleShowNonCurrent = viewModel::setShowNonCurrentWeekCourses,
                    onRefresh = viewModel::manualRefresh,
                    onSaveCustomExam = viewModel::saveCustomExam,
                    onDeleteCustomExam = viewModel::deleteCustomExam,
                    onSaveCustomEvent = viewModel::saveCustomEvent,
                    onDeleteCustomEvent = viewModel::deleteCustomEvent
                )

                1 -> ExamsScreen(
                    exams = exams,
                    settings = settings,
                    isSyncing = isSyncing,
                    bottomBarPadding = measuredBarHeightDp,
                    onRefresh = viewModel::manualRefresh,
                    onSaveCustomExam = viewModel::saveCustomExam,
                    onDeleteCustomExam = viewModel::deleteCustomExam
                )

                2 -> GradesScreen(
                    grades = grades,
                    profile = profile,
                    isSyncing = isSyncing,
                    breakdownState = gradeBreakdownState,
                    bottomBarPadding = measuredBarHeightDp,
                    onRefresh = viewModel::manualRefresh,
                    onGradeClick = { viewModel.openGradeBreakdown(it, forceRefresh = false) },
                    onRefreshBreakdown = { viewModel.openGradeBreakdown(it, forceRefresh = true) },
                    onDismissBreakdown = viewModel::closeGradeBreakdown,
                    onOpenWebReauth = { viewModel.openWebLogin() }
                )

                3 -> ProfileScreen(
                    profile = profile,
                    session = session,
                    settings = settings,
                    syncLogs = syncLogs,
                    calculatedGpa = calculatedGpa,
                    isSyncing = isSyncing,
                    appUpdateState = appUpdateState,
                    bottomBarPadding = measuredBarHeightDp,
                    onManualRefresh = viewModel::manualRefresh,
                    onSetAutoRefresh = viewModel::setAutoRefreshEnabled,
                    onSetIntervalMinutes = viewModel::setRefreshIntervalMinutes,
                    onSetRefreshOnStart = viewModel::setRefreshOnAppStart,
                    onSetNotifyOnChange = viewModel::setNotifyOnChange,
                    onSetSemesterStartDate = viewModel::setSemesterStartDate,
                    onSetSchedulePreset = viewModel::setSchedulePreset,
                    onSetThemeSeedColor = viewModel::setThemeSeedColor,
                    onSetThemeDarkMode = viewModel::setThemeDarkMode,
                    onSetBottomBarTransparency = viewModel::setBottomBarTransparency,
                    onSetBottomBarFrost = viewModel::setBottomBarFrost,
                    onSetBottomBarStyle = viewModel::setBottomBarStyle,
                    onResetTheme = viewModel::resetTheme,
                    onCheckAppUpdate = { viewModel.checkAppUpdate(manual = true, contextForPush = context) },
                    onOpenAvailableUpdate = viewModel::openAvailableUpdateDialog,
                    onSaveUpdateSource = viewModel::saveUpdateSource,
                    onSetAutoCheckUpdates = viewModel::setAutoCheckUpdates,
                    onSetUpdateNotifyPush = viewModel::setUpdateNotifyPush,
                    onSendTestUpdatePush = { viewModel.sendTestUpdatePush(context) },
                    onOpenWebReauth = { viewModel.openWebLogin() },
                    onClearCache = viewModel::clearLocalCache,
                    onLogout = viewModel::logout
                )
            }
        }

        // Snackbar sits cleanly above the custom frosted bottom bar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = measuredBarHeightDp + 6.dp)
        )

        // Custom Frosted-Glass Bottom Bar with customizable transparency & blur frost sliders
        CustomFrostedBottomBar(
            tabs = tabs,
            selectedIndex = currentTab,
            onSelectTab = { currentTab = it },
            transparency = settings.bottomBarTransparency,
            frost = settings.bottomBarFrost,
            style = settings.bottomBarStyle,
            backdropLayer = backdropLayer,
            contentPositionInRoot = contentPositionInRoot,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { size ->
                    if (size.height > 0) {
                        measuredBarHeightDp = with(density) { size.height.toDp() }
                    }
                }
        )
    }
}

package cn.gdou.edu.timetable.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.gdou.edu.timetable.BuildConfig
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.local.SavedSessionInfo
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import cn.gdou.edu.timetable.data.local.db.SyncLogEntity
import cn.gdou.edu.timetable.data.remote.AppUpdateClient
import cn.gdou.edu.timetable.domain.GdouPeriodSchedule
import cn.gdou.edu.timetable.ui.AppUpdateUiState
import cn.gdou.edu.timetable.ui.MainViewModel
import cn.gdou.edu.timetable.ui.components.UpdateSourceConfigDialog
import cn.gdou.edu.timetable.ui.theme.AccentDanger
import cn.gdou.edu.timetable.ui.theme.AccentInfo
import cn.gdou.edu.timetable.ui.theme.AccentSuccess
import cn.gdou.edu.timetable.ui.theme.ThemeColorPresets
import cn.gdou.edu.timetable.ui.theme.colorToHex
import cn.gdou.edu.timetable.worker.UpdateNotificationHelper
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: StudentProfileEntity?,
    session: SavedSessionInfo,
    settings: AppSettings,
    syncLogs: List<SyncLogEntity>,
    calculatedGpa: Double,
    isSyncing: Boolean,
    appUpdateState: AppUpdateUiState = AppUpdateUiState(),
    bottomBarPadding: Dp = 0.dp,
    onManualRefresh: () -> Unit,
    onSetAutoRefresh: (Boolean) -> Unit,
    onSetIntervalMinutes: (Long) -> Unit,
    onSetRefreshOnStart: (Boolean) -> Unit,
    onSetNotifyOnChange: (Boolean) -> Unit,
    onSetSemesterStartDate: (String) -> Unit,
    onSetSchedulePreset: (String) -> Unit,
    onSetThemeSeedColor: (Long) -> Unit,
    onSetThemeDarkMode: (String) -> Unit,
    onSetBottomBarTransparency: (Float) -> Unit,
    onSetBottomBarFrost: (Float) -> Unit,
    onSetBottomBarStyle: (String) -> Unit,
    onResetTheme: () -> Unit,
    onCheckAppUpdate: () -> Unit = {},
    onOpenAvailableUpdate: () -> Unit = {},
    onSaveUpdateSource: (String, String, String) -> Unit = { _, _, _ -> },
    onSetAutoCheckUpdates: (Boolean) -> Unit = {},
    onSetUpdateNotifyPush: (Boolean) -> Unit = {},
    onSendTestUpdatePush: () -> Unit = {},
    onOpenWebReauth: () -> Unit,
    onClearCache: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showThemeCustomizer by remember { mutableStateOf(false) }
    var showUpdateSourceConfig by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onSetNotifyOnChange(granted)
    }

    val updatePushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onSetUpdateNotifyPush(granted)
    }

    val testPushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onSendTestUpdatePush()
    }

    val timeFmt = remember {
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
    }

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onManualRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Student Identity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (profile?.name?.firstOrNull() ?: '学').toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = profile?.name?.ifBlank { session.username } ?: session.username.ifBlank { "在校学生" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = if (session.isDemoMode) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (session.isDemoMode) "演示模式" else (profile?.studentStatus?.ifBlank { "在籍" } ?: "已认证"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "学号：${profile?.studentId?.ifBlank { session.username } ?: session.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileInfoRow("学院", profile?.college.orEmpty().ifBlank { "待同步" })
                            ProfileInfoRow("专业", profile?.major.orEmpty().ifBlank { "待同步" })
                            ProfileInfoRow(
                                "班级 / 年级",
                                listOf(profile?.className.orEmpty(), profile?.grade.orEmpty().takeIf { it.isNotBlank() }?.let { "${it}级" })
                                    .filterNotNull()
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "待同步" }
                            )
                            ProfileInfoRow("校区", profile?.campus.orEmpty().ifBlank { "待同步" })
                            ProfileInfoRow(
                                "综合绩点 (GPA)",
                                String.format(Locale.US, "%.2f", profile?.officialGpa ?: calculatedGpa)
                            )
                        }
                    }
                }
            }

            // 1.5 Custom Theme & Frosted Bottom Bar Card (sliders control live bottom bar right below!)
            item {
                val activePresetName = ThemeColorPresets.presets.find {
                    (it.argb and 0xFFFFFFL) == (settings.themeSeedColorArgb and 0xFFFFFFL)
                }?.name ?: "自定义配色"
                val darkModeText = when (settings.themeDarkMode) {
                    AppSettings.THEME_MODE_DARK -> "深色"
                    AppSettings.THEME_MODE_LIGHT -> "浅色"
                    else -> "跟随系统"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(settings.themeSeedColorArgb)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Palette,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "自定义主题与底部栏",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$activePresetName (${colorToHex(settings.themeSeedColorArgb)}) · $darkModeText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                onClick = { showThemeCustomizer = true },
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "调色盘",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "底部栏样式",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = settings.bottomBarStyle != AppSettings.BOTTOM_BAR_STYLE_DOCKED,
                                    onClick = { onSetBottomBarStyle(AppSettings.BOTTOM_BAR_STYLE_FLOATING) },
                                    label = { Text("悬浮胶囊") }
                                )
                                FilterChip(
                                    selected = settings.bottomBarStyle == AppSettings.BOTTOM_BAR_STYLE_DOCKED,
                                    onClick = { onSetBottomBarStyle(AppSettings.BOTTOM_BAR_STYLE_DOCKED) },
                                    label = { Text("贴底玻璃") }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "底部栏透明度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(settings.bottomBarTransparency * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.bottomBarTransparency,
                                onValueChange = onSetBottomBarTransparency,
                                valueRange = 0f..1f
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "底部栏磨砂度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(settings.bottomBarFrost * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.bottomBarFrost,
                                onValueChange = onSetBottomBarFrost,
                                valueRange = 0f..1f
                            )
                        }
                    }
                }
            }

            // 2. Manual & Auto Sync Settings Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "本地数据与实时同步设置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val syncTimeText = if (settings.lastSyncTimestamp > 0L) {
                                    timeFmt.format(Instant.ofEpochMilli(settings.lastSyncTimestamp))
                                } else "尚未同步"
                                Text(
                                    text = "上次同步：$syncTimeText",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = settings.lastSyncStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (settings.lastChangeSummary.isNotBlank()) {
                                    Text(
                                        text = settings.lastChangeSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onManualRefresh,
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSyncing) "正在同步并替换本地数据..." else "立即手动刷新（新数据替换旧数据）")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingSwitchRow(
                            title = "后台定期自动刷新",
                            subtitle = "检测到课表、考试或成绩变动时自动替换本地数据",
                            checked = settings.autoRefreshEnabled,
                            onCheckedChange = onSetAutoRefresh
                        )

                        if (settings.autoRefreshEnabled) {
                            Text(
                                text = "自动刷新频率",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(15L, 30L, 60L, 180L, 360L, 720L).forEach { mins ->
                                    FilterChip(
                                        selected = settings.refreshIntervalMinutes == mins,
                                        onClick = { onSetIntervalMinutes(mins) },
                                        label = { Text(MainViewModel.formatIntervalLabel(mins)) }
                                    )
                                }
                            }
                        }

                        SettingSwitchRow(
                            title = "启动应用时自动检查更新",
                            subtitle = "距离上次同步超过设定周期时自动在后台刷新",
                            checked = settings.refreshOnAppStart,
                            onCheckedChange = onSetRefreshOnStart
                        )

                        SettingSwitchRow(
                            title = "数据变动时推送通知提醒",
                            subtitle = "新出成绩、调课或新增考场安排时立即通知",
                            checked = settings.notifyOnChange,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSetNotifyOnChange(wantEnable)
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "学期第一周周一日期",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "当前设置：${settings.semesterStartDate}（第 ${settings.currentWeekNumber()} 周）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("校准")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        val activePreset = GdouPeriodSchedule.presets.find { it.id == settings.schedulePresetId }
                            ?: GdouPeriodSchedule.presets.first()
                        Text(
                            text = "日程作息时间方案",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "当前：${activePreset.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GdouPeriodSchedule.presets.forEach { preset ->
                                FilterChip(
                                    selected = settings.schedulePresetId == preset.id,
                                    onClick = { onSetSchedulePreset(preset.id) },
                                    label = { Text(preset.name) }
                                )
                            }
                        }
                    }
                }
            }

            // 2.5 App Version Update Push Card (GitHub Releases + In-App APK Upgrade)
            item {
                val resolvedSource = remember(settings.updateRepoSpec) {
                    AppUpdateClient.resolveSource(settings.updateRepoSpec)
                }
                val availableUpdate = appUpdateState.availableUpdate
                val mirrorLabel = when (settings.updateMirrorMode) {
                    AppSettings.UPDATE_MIRROR_GHPROXY -> "镜像加速"
                    AppSettings.UPDATE_MIRROR_CUSTOM -> "自定义代理"
                    else -> "GitHub 直连"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "应用版本与更新推送",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (availableUpdate != null && (availableUpdate.isNewerThanCurrent() || availableUpdate.isTestPreview)) {
                                Surface(
                                    onClick = onOpenAvailableUpdate,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "新版本 v${availableUpdate.versionName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "当前版本：v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (resolvedSource != null) {
                                        "更新源：${resolvedSource.displayLabel} · $mirrorLabel"
                                    } else {
                                        "更新源：尚未配置（点击下方「配置更新源」填入 GitHub 仓库）"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val checkTimeText = if (settings.lastUpdateCheckTimestamp > 0L) {
                                    "上次检测：${timeFmt.format(Instant.ofEpochMilli(settings.lastUpdateCheckTimestamp))}"
                                } else {
                                    "尚未检测过远程更新"
                                }
                                val statusLine = appUpdateState.statusText.ifBlank { checkTimeText }
                                Text(
                                    text = statusLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (settings.updateRepoSpec.isBlank()) {
                                        showUpdateSourceConfig = true
                                    } else {
                                        onCheckAppUpdate()
                                    }
                                },
                                enabled = !appUpdateState.isChecking,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (appUpdateState.isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("检测中…")
                                } else {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("检查更新")
                                }
                            }
                            OutlinedButton(
                                onClick = { showUpdateSourceConfig = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("配置更新源")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingSwitchRow(
                            title = "后台定时检测新版本",
                            subtitle = "启动时及每 12 小时联网检查 GitHub Releases 新版本",
                            checked = settings.autoCheckUpdatesEnabled,
                            onCheckedChange = onSetAutoCheckUpdates
                        )

                        SettingSwitchRow(
                            title = "发现新版本时推送通知",
                            subtitle = "通过系统通知栏提醒，点击通知直接进入更新弹窗下载安装",
                            checked = settings.updateNotifyPushEnabled,
                            onCheckedChange = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !UpdateNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    updatePushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSetUpdateNotifyPush(enable)
                                }
                            }
                        )

                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !UpdateNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    testPushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSendTestUpdatePush()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("发送测试更新推送（验证通知与弹窗）")
                        }
                    }
                }
            }

            // 3. Recent Sync & Change Detection Log
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "最近同步与变动记录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (syncLogs.isEmpty()) {
                            Text(
                                text = "暂无同步记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(syncLogs.take(8), key = { it.id }) { log ->
                SyncLogCard(log = log, timeFmt = timeFmt)
            }

            // 4. Account & Cache Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!session.isDemoMode) {
                        OutlinedButton(
                            onClick = onOpenWebReauth,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("网页重新认证（刷新教务会话）")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("清空本地缓存")
                        }
                        OutlinedButton(
                            onClick = { showLogoutConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("退出登录", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showThemeCustomizer) {
        ThemeCustomizerBottomSheet(
            currentSeedArgb = settings.themeSeedColorArgb,
            currentDarkMode = settings.themeDarkMode,
            bottomBarTransparency = settings.bottomBarTransparency,
            bottomBarFrost = settings.bottomBarFrost,
            bottomBarStyle = settings.bottomBarStyle,
            onSelectColor = onSetThemeSeedColor,
            onSelectDarkMode = onSetThemeDarkMode,
            onSetBottomBarTransparency = onSetBottomBarTransparency,
            onSetBottomBarFrost = onSetBottomBarFrost,
            onSetBottomBarStyle = onSetBottomBarStyle,
            onResetDefault = onResetTheme,
            onDismiss = { showThemeCustomizer = false }
        )
    }

    if (showUpdateSourceConfig) {
        UpdateSourceConfigDialog(
            initialRepoSpec = settings.updateRepoSpec,
            initialMirrorMode = settings.updateMirrorMode,
            initialCustomMirror = settings.updateCustomMirrorPrefix,
            onSave = { repo, mirror, custom ->
                showUpdateSourceConfig = false
                onSaveUpdateSource(repo, mirror, custom)
            },
            onDismiss = { showUpdateSourceConfig = false }
        )
    }

    if (showDatePicker) {
        val initialMillis = remember(settings.semesterStartDate) {
            runCatching {
                java.time.LocalDate.parse(settings.semesterStartDate)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val monday = picked.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        onSetSemesterStartDate(monday.toString())
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清空本地缓存") },
            text = { Text("将清除本地保存的课表、考试和成绩数据，账号登录状态会保留，随时可点击刷新重新同步。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheConfirm = false
                    onClearCache()
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("退出将同时清除本机加密保存的密码、登录会话及本地教务数据，并停止后台自动刷新。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SyncLogCard(log: SyncLogEntity, timeFmt: DateTimeFormatter) {
    val triggerLabel = when (log.triggerType) {
        "LOGIN" -> "登录同步"
        "MANUAL" -> "手动刷新"
        "AUTO_START" -> "启动自动刷新"
        "AUTO_BG" -> "后台定时刷新"
        else -> log.triggerType
    }
    val statusColor = when {
        !log.success -> AccentDanger
        log.hasChanges -> AccentInfo
        else -> AccentSuccess
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = triggerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (log.hasChanges && log.success) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = "有变动",
                            tint = AccentInfo,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = timeFmt.format(Instant.ofEpochMilli(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (log.details.isNotBlank()) {
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

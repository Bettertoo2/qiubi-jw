package cn.gdou.edu.timetable.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class AppSettings(
    val autoRefreshEnabled: Boolean = true,
    val refreshIntervalMinutes: Long = 60L,
    val refreshOnAppStart: Boolean = true,
    val notifyOnChange: Boolean = true,
    val semesterStartDate: String = defaultSemesterStartDate(),
    val selectedXnm: String = defaultXnm(),
    val selectedXqm: String = defaultXqm(),
    val availableTermsCsv: String = "",
    val showNonCurrentWeekCourses: Boolean = false,
    val schedulePresetId: String = cn.gdou.edu.timetable.domain.GdouPeriodSchedule.PRESET_GDOU_STANDARD,
    val themeSeedColorArgb: Long = DEFAULT_THEME_SEED_ARGB,
    val themeDarkMode: String = THEME_MODE_SYSTEM,
    val bottomBarTransparency: Float = DEFAULT_BOTTOM_BAR_TRANSPARENCY, // 0f = opaque, 1f = fully transparent
    val bottomBarFrost: Float = DEFAULT_BOTTOM_BAR_FROST,               // 0f = crisp, 1f = strong frosted blur
    val bottomBarStyle: String = BOTTOM_BAR_STYLE_FLOATING,             // FLOATING | DOCKED
    val updateRepoSpec: String = DEFAULT_UPDATE_REPO_SPEC,              // e.g. "owner/repo", GitHub URL, or custom version.json URL
    val autoCheckUpdatesEnabled: Boolean = true,
    val updateNotifyPushEnabled: Boolean = true,
    val updateMirrorMode: String = UPDATE_MIRROR_DIRECT,                // DIRECT | GHPROXY | CUSTOM
    val updateCustomMirrorPrefix: String = "",
    val ignoredUpdateVersion: String = "",
    val lastUpdateCheckTimestamp: Long = 0L,
    val lastNotifiedUpdateVersion: String = "",
    val cachedUpdateJson: String = "",
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "尚未同步",
    val lastChangeSummary: String = ""
) {
    fun currentWeekNumber(today: LocalDate = LocalDate.now()): Int {
        val start = runCatching { LocalDate.parse(semesterStartDate) }.getOrNull() ?: return 1
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, today)
        val week = (days / 7L).toInt() + 1
        return week.coerceIn(1, 25)
    }

    fun availableTermPairs(): List<Pair<String, String>> {
        val currentYear = defaultXnm().toIntOrNull() ?: LocalDate.now().year
        val minYear = currentYear - 6
        val maxYear = currentYear + 5

        val parsed = if (availableTermsCsv.isBlank()) {
            emptyList()
        } else {
            availableTermsCsv.split(",")
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    val y = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val t = parts.getOrNull(1)?.trim()
                    if (y != null && !t.isNullOrBlank() && y in minYear..maxYear) {
                        y.toString() to t
                    } else null
                }
        }

        // Always include upcoming (+2 years) through past (-4 years) so both future pre-selection & history are accessible
        val defaults = ((currentYear + 2) downTo (currentYear - 4)).flatMap { y ->
            listOf(y.toString() to "3", y.toString() to "12")
        }

        return (parsed + defaults)
            .distinct()
            .sortedWith(
                compareByDescending<Pair<String, String>> { it.first.toIntOrNull() ?: 0 }
                    .thenBy { it.second.toIntOrNull() ?: 99 }
            )
    }

    /** Groups available terms by academic year descending (e.g. "2028" -> ["3", "12"]). */
    fun availableYearGroups(): List<Pair<String, List<String>>> {
        return availableTermPairs()
            .groupBy { it.first }
            .entries
            .sortedByDescending { it.key.toIntOrNull() ?: 0 }
            .map { (year, pairs) ->
                year to pairs.map { it.second }.distinct().sortedBy { it.toIntOrNull() ?: 99 }
            }
    }

    companion object {
        const val DEFAULT_THEME_SEED_ARGB: Long = 0xFF005A7DL
        const val THEME_MODE_SYSTEM = "SYSTEM"
        const val THEME_MODE_LIGHT = "LIGHT"
        const val THEME_MODE_DARK = "DARK"
        const val DEFAULT_BOTTOM_BAR_TRANSPARENCY: Float = 0.45f
        const val DEFAULT_BOTTOM_BAR_FROST: Float = 0.65f
        const val BOTTOM_BAR_STYLE_FLOATING = "FLOATING"
        const val BOTTOM_BAR_STYLE_DOCKED = "DOCKED"
        const val UPDATE_MIRROR_DIRECT = "DIRECT"
        const val UPDATE_MIRROR_GHPROXY = "GHPROXY"
        const val UPDATE_MIRROR_CUSTOM = "CUSTOM"
        const val DEFAULT_UPDATE_REPO_SPEC = "Bettertoo2/qiubi-jw"
        const val DEFAULT_GHPROXY_PREFIX = "https://gh-proxy.com/"

        fun defaultXnm(today: LocalDate = LocalDate.now()): String {
            return if (today.monthValue >= 8) today.year.toString() else (today.year - 1).toString()
        }

        fun defaultXqm(today: LocalDate = LocalDate.now()): String {
            // Zhengfang term codes: "3" = Term 1 (Fall), "12" = Term 2 (Spring), "16" = Short term
            return if (today.monthValue >= 8 || today.monthValue == 1) "3" else "12"
        }

        fun defaultSemesterStartDate(today: LocalDate = LocalDate.now()): String {
            val anchor = if (today.monthValue >= 8 || today.monthValue == 1) {
                val fallYear = if (today.monthValue == 1) today.year - 1 else today.year
                LocalDate.of(fallYear, 9, 1)
            } else {
                LocalDate.of(today.year, 3, 1)
            }
            return anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        }

        fun formatTermLabel(xnm: String, xqm: String): String {
            val yearInt = xnm.toIntOrNull()
            val yearSpan = if (yearInt != null) "${yearInt}-${yearInt + 1}" else xnm
            val termText = when (xqm) {
                "3", "1" -> "第1学期"
                "12", "2" -> "第2学期"
                "16", "3s" -> "短学期"
                else -> "第${xqm}学期"
            }
            return "$yearSpan 学年 $termText"
        }
    }
}

class AppSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings {
        val currentDefaultXnm = AppSettings.defaultXnm()
        val currentDefaultXqm = AppSettings.defaultXqm()
        val savedXnm = prefs.getString(KEY_SELECTED_XNM, null) ?: currentDefaultXnm
        val savedXqm = prefs.getString(KEY_SELECTED_XQM, null) ?: currentDefaultXqm

        return AppSettings(
            autoRefreshEnabled = prefs.getBoolean(KEY_AUTO_REFRESH, true),
            refreshIntervalMinutes = prefs.getLong(KEY_INTERVAL_MINUTES, 60L),
            refreshOnAppStart = prefs.getBoolean(KEY_REFRESH_ON_START, true),
            notifyOnChange = prefs.getBoolean(KEY_NOTIFY_ON_CHANGE, true),
            semesterStartDate = prefs.getString(KEY_SEMESTER_START, null)
                ?: AppSettings.defaultSemesterStartDate(),
            selectedXnm = savedXnm,
            selectedXqm = savedXqm,
            availableTermsCsv = prefs.getString(KEY_AVAILABLE_TERMS, "") ?: "",
            showNonCurrentWeekCourses = prefs.getBoolean(KEY_SHOW_NON_CURRENT_WEEK, false),
            schedulePresetId = prefs.getString(
                KEY_SCHEDULE_PRESET,
                cn.gdou.edu.timetable.domain.GdouPeriodSchedule.PRESET_GDOU_STANDARD
            ) ?: cn.gdou.edu.timetable.domain.GdouPeriodSchedule.PRESET_GDOU_STANDARD,
            themeSeedColorArgb = prefs.getLong(KEY_THEME_SEED_ARGB, AppSettings.DEFAULT_THEME_SEED_ARGB),
            themeDarkMode = prefs.getString(KEY_THEME_DARK_MODE, AppSettings.THEME_MODE_SYSTEM)
                ?: AppSettings.THEME_MODE_SYSTEM,
            bottomBarTransparency = prefs.getFloat(
                KEY_BOTTOM_BAR_TRANSPARENCY,
                AppSettings.DEFAULT_BOTTOM_BAR_TRANSPARENCY
            ).coerceIn(0f, 1f),
            bottomBarFrost = prefs.getFloat(
                KEY_BOTTOM_BAR_FROST,
                AppSettings.DEFAULT_BOTTOM_BAR_FROST
            ).coerceIn(0f, 1f),
            bottomBarStyle = prefs.getString(
                KEY_BOTTOM_BAR_STYLE,
                AppSettings.BOTTOM_BAR_STYLE_FLOATING
            ) ?: AppSettings.BOTTOM_BAR_STYLE_FLOATING,
            updateRepoSpec = prefs.getString(KEY_UPDATE_REPO_SPEC, AppSettings.DEFAULT_UPDATE_REPO_SPEC)
                ?: AppSettings.DEFAULT_UPDATE_REPO_SPEC,
            autoCheckUpdatesEnabled = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true),
            updateNotifyPushEnabled = prefs.getBoolean(KEY_UPDATE_NOTIFY_PUSH, true),
            updateMirrorMode = prefs.getString(
                KEY_UPDATE_MIRROR_MODE,
                AppSettings.UPDATE_MIRROR_DIRECT
            ) ?: AppSettings.UPDATE_MIRROR_DIRECT,
            updateCustomMirrorPrefix = prefs.getString(KEY_UPDATE_CUSTOM_MIRROR, "") ?: "",
            ignoredUpdateVersion = prefs.getString(KEY_IGNORED_UPDATE_VERSION, "") ?: "",
            lastUpdateCheckTimestamp = prefs.getLong(KEY_LAST_UPDATE_CHECK_TS, 0L),
            lastNotifiedUpdateVersion = prefs.getString(KEY_LAST_NOTIFIED_UPDATE_VERSION, "") ?: "",
            cachedUpdateJson = prefs.getString(KEY_CACHED_UPDATE_JSON, "") ?: "",
            lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TS, 0L),
            lastSyncStatus = prefs.getString(KEY_LAST_SYNC_STATUS, "尚未同步") ?: "尚未同步",
            lastChangeSummary = prefs.getString(KEY_LAST_CHANGE_SUMMARY, "") ?: ""
        )
    }

    fun updateAutoRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
        _settings.value = read()
    }

    fun updateRefreshInterval(minutes: Long) {
        prefs.edit().putLong(KEY_INTERVAL_MINUTES, minutes.coerceAtLeast(15L)).apply()
        _settings.value = read()
    }

    fun updateRefreshOnAppStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REFRESH_ON_START, enabled).apply()
        _settings.value = read()
    }

    fun updateNotifyOnChange(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON_CHANGE, enabled).apply()
        _settings.value = read()
    }

    fun updateSemesterStartDate(isoDate: String) {
        prefs.edit().putString(KEY_SEMESTER_START, isoDate).apply()
        _settings.value = read()
    }

    fun updateSelectedTerm(xnm: String, xqm: String) {
        prefs.edit()
            .putString(KEY_SELECTED_XNM, xnm)
            .putString(KEY_SELECTED_XQM, xqm)
            .apply()
        _settings.value = read()
    }

    fun updateAvailableTerms(terms: List<Pair<String, String>>) {
        if (terms.isEmpty()) return
        val csv = terms.distinct().joinToString(",") { "${it.first}:${it.second}" }
        prefs.edit().putString(KEY_AVAILABLE_TERMS, csv).apply()
        _settings.value = read()
    }

    fun updateShowNonCurrentWeekCourses(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NON_CURRENT_WEEK, show).apply()
        _settings.value = read()
    }

    fun updateSchedulePresetId(presetId: String) {
        prefs.edit().putString(KEY_SCHEDULE_PRESET, presetId).apply()
        _settings.value = read()
    }

    fun updateThemeSeedColor(argb: Long) {
        prefs.edit().putLong(KEY_THEME_SEED_ARGB, argb).apply()
        _settings.value = read()
    }

    fun updateThemeDarkMode(mode: String) {
        prefs.edit().putString(KEY_THEME_DARK_MODE, mode).apply()
        _settings.value = read()
    }

    fun updateBottomBarTransparency(value: Float) {
        prefs.edit().putFloat(KEY_BOTTOM_BAR_TRANSPARENCY, value.coerceIn(0f, 1f)).apply()
        _settings.value = read()
    }

    fun updateBottomBarFrost(value: Float) {
        prefs.edit().putFloat(KEY_BOTTOM_BAR_FROST, value.coerceIn(0f, 1f)).apply()
        _settings.value = read()
    }

    fun updateBottomBarStyle(style: String) {
        prefs.edit().putString(KEY_BOTTOM_BAR_STYLE, style).apply()
        _settings.value = read()
    }

    fun resetTheme() {
        prefs.edit()
            .putLong(KEY_THEME_SEED_ARGB, AppSettings.DEFAULT_THEME_SEED_ARGB)
            .putString(KEY_THEME_DARK_MODE, AppSettings.THEME_MODE_SYSTEM)
            .putFloat(KEY_BOTTOM_BAR_TRANSPARENCY, AppSettings.DEFAULT_BOTTOM_BAR_TRANSPARENCY)
            .putFloat(KEY_BOTTOM_BAR_FROST, AppSettings.DEFAULT_BOTTOM_BAR_FROST)
            .putString(KEY_BOTTOM_BAR_STYLE, AppSettings.BOTTOM_BAR_STYLE_FLOATING)
            .apply()
        _settings.value = read()
    }

    fun updateAppUpdateSource(
        repoSpec: String,
        mirrorMode: String,
        customMirrorPrefix: String
    ) {
        prefs.edit()
            .putString(KEY_UPDATE_REPO_SPEC, repoSpec.trim())
            .putString(KEY_UPDATE_MIRROR_MODE, mirrorMode)
            .putString(KEY_UPDATE_CUSTOM_MIRROR, customMirrorPrefix.trim())
            .remove(KEY_IGNORED_UPDATE_VERSION)
            .remove(KEY_CACHED_UPDATE_JSON)
            .apply()
        _settings.value = read()
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
        _settings.value = read()
    }

    fun updateNotifyUpdatePush(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_NOTIFY_PUSH, enabled).apply()
        _settings.value = read()
    }

    fun setIgnoredUpdateVersion(version: String) {
        prefs.edit().putString(KEY_IGNORED_UPDATE_VERSION, version.trim()).apply()
        _settings.value = read()
    }

    fun recordUpdateCheck(
        timestamp: Long,
        cachedJson: String? = null,
        notifiedVersion: String? = null
    ) {
        prefs.edit().apply {
            putLong(KEY_LAST_UPDATE_CHECK_TS, timestamp)
            if (cachedJson != null) putString(KEY_CACHED_UPDATE_JSON, cachedJson)
            if (notifiedVersion != null) putString(KEY_LAST_NOTIFIED_UPDATE_VERSION, notifiedVersion)
        }.apply()
        _settings.value = read()
    }

    fun recordSyncMeta(timestamp: Long, status: String, changeSummary: String) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TS, timestamp)
            .putString(KEY_LAST_SYNC_STATUS, status)
            .putString(KEY_LAST_CHANGE_SUMMARY, changeSummary)
            .apply()
        _settings.value = read()
    }

    fun resetSyncMeta() {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TS, 0L)
            .putString(KEY_LAST_SYNC_STATUS, "尚未同步")
            .putString(KEY_LAST_CHANGE_SUMMARY, "")
            .apply()
        _settings.value = read()
    }

    companion object {
        private const val PREFS_NAME = "gdou_app_settings"
        private const val KEY_AUTO_REFRESH = "auto_refresh_enabled"
        private const val KEY_INTERVAL_MINUTES = "refresh_interval_minutes"
        private const val KEY_REFRESH_ON_START = "refresh_on_app_start"
        private const val KEY_NOTIFY_ON_CHANGE = "notify_on_change"
        private const val KEY_SEMESTER_START = "semester_start_date"
        private const val KEY_SELECTED_XNM = "selected_xnm"
        private const val KEY_SELECTED_XQM = "selected_xqm"
        private const val KEY_AVAILABLE_TERMS = "available_terms_csv"
        private const val KEY_SHOW_NON_CURRENT_WEEK = "show_non_current_week"
        private const val KEY_SCHEDULE_PRESET = "schedule_preset_id"
        private const val KEY_THEME_SEED_ARGB = "theme_seed_color_argb"
        private const val KEY_THEME_DARK_MODE = "theme_dark_mode"
        private const val KEY_BOTTOM_BAR_TRANSPARENCY = "bottom_bar_transparency"
        private const val KEY_BOTTOM_BAR_FROST = "bottom_bar_frost"
        private const val KEY_BOTTOM_BAR_STYLE = "bottom_bar_style"
        private const val KEY_UPDATE_REPO_SPEC = "update_repo_spec"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_UPDATE_NOTIFY_PUSH = "update_notify_push"
        private const val KEY_UPDATE_MIRROR_MODE = "update_mirror_mode"
        private const val KEY_UPDATE_CUSTOM_MIRROR = "update_custom_mirror_prefix"
        private const val KEY_IGNORED_UPDATE_VERSION = "ignored_update_version"
        private const val KEY_LAST_UPDATE_CHECK_TS = "last_update_check_ts"
        private const val KEY_LAST_NOTIFIED_UPDATE_VERSION = "last_notified_update_version"
        private const val KEY_CACHED_UPDATE_JSON = "cached_update_json"
        private const val KEY_LAST_SYNC_TS = "last_sync_ts"
        private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
        private const val KEY_LAST_CHANGE_SUMMARY = "last_change_summary"
    }
}

package cn.gdou.edu.timetable.ui.timetable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.CustomScheduleEventEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.domain.GdouPeriodSchedule
import cn.gdou.edu.timetable.domain.TimetableDisplayBlock
import cn.gdou.edu.timetable.domain.TimetableEventMapper
import cn.gdou.edu.timetable.domain.TimetableExamBlock
import cn.gdou.edu.timetable.domain.TimetableExamMapper
import cn.gdou.edu.timetable.domain.TimetableSlotResolver
import cn.gdou.edu.timetable.domain.TimetableStickyEventBlock
import cn.gdou.edu.timetable.ui.exams.CustomExamDraft
import cn.gdou.edu.timetable.ui.exams.CustomExamEditorBottomSheet
import cn.gdou.edu.timetable.ui.theme.CourseCardPalette
import cn.gdou.edu.timetable.ui.theme.ExamGradientPalette
import cn.gdou.edu.timetable.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimetableScreen(
    courses: List<CourseEntity>,
    exams: List<ExamEntity>,
    customEvents: List<CustomScheduleEventEntity> = emptyList(),
    settings: AppSettings,
    selectedWeek: Int,
    isSyncing: Boolean,
    isDemoMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSelectWeek: (Int) -> Unit,
    onJumpCurrentWeek: () -> Unit,
    onSelectTerm: (String, String) -> Unit,
    onToggleShowNonCurrent: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSaveCustomExam: (CustomExamDraft) -> Unit = {},
    onDeleteCustomExam: (Long) -> Unit = {},
    onSaveCustomEvent: (CustomEventDraft) -> Unit = {},
    onDeleteCustomEvent: (Long) -> Unit = {}
) {
    val darkTheme = LocalIsDarkTheme.current
    val currentWeek = settings.currentWeekNumber()
    var nowTime by remember { mutableStateOf(LocalTime.now()) }
    var todayDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedBlock by remember { mutableStateOf<TimetableDisplayBlock?>(null) }
    var selectedExamBlock by remember { mutableStateOf<TimetableExamBlock?>(null) }
    var selectedEventBlock by remember { mutableStateOf<TimetableStickyEventBlock?>(null) }
    var activeFrontEventIds by remember { mutableStateOf(setOf<Long>()) }
    var showAddEventSheet by remember { mutableStateOf(false) }
    var editingCustomEvent by remember { mutableStateOf<CustomScheduleEventEntity?>(null) }
    var editingCustomExam by remember { mutableStateOf<ExamEntity?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showTermMenu by remember { mutableStateOf(false) }

    // Real-time ticker so current/next class banner stays live
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowTime = LocalTime.now()
            todayDate = LocalDate.now()
        }
    }

    val liveStatus = remember(courses, currentWeek, todayDate, nowTime, settings.schedulePresetId) {
        GdouPeriodSchedule.computeLiveStatus(
            courses = courses,
            currentWeek = currentWeek,
            todayDayOfWeek = todayDate.dayOfWeek.value,
            presetId = settings.schedulePresetId,
            nowTime = nowTime
        )
    }

    val activePeriods = remember(courses, settings.schedulePresetId) {
        val all = GdouPeriodSchedule.periodsFor(settings.schedulePresetId)
        val neededCount = (courses.maxOfOrNull { it.endSection } ?: 10).coerceIn(10, all.size)
        all.take(neededCount)
    }

    val semesterStart = remember(settings.semesterStartDate) {
        runCatching { LocalDate.parse(settings.semesterStartDate) }.getOrElse { LocalDate.now() }
    }
    val dateFmt = remember { DateTimeFormatter.ofPattern("M/d") }

    val pagerState = rememberPagerState(
        initialPage = (selectedWeek - 1).coerceIn(0, MAX_WEEKS - 1),
        pageCount = { MAX_WEEKS }
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync swipe gesture -> ViewModel selectedWeek
    LaunchedEffect(pagerState.settledPage) {
        val swipedWeek = pagerState.settledPage + 1
        if (swipedWeek != selectedWeek) {
            onSelectWeek(swipedWeek)
        }
    }

    // Sync external week selection (buttons / week picker / jump to current week) -> smooth pager animation
    LaunchedEffect(selectedWeek) {
        val targetPage = (selectedWeek - 1).coerceIn(0, MAX_WEEKS - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    val displayedWeek = pagerState.currentPage + 1
    val displayedWeekExams = remember(exams, semesterStart, displayedWeek, activePeriods) {
        TimetableExamMapper.mapExamsForWeek(exams, semesterStart, displayedWeek, activePeriods)
    }
    val weeksWithExams = remember(exams, semesterStart) {
        exams.mapNotNull { exam ->
            val parsed = TimetableExamMapper.parseExamDateTime(exam) ?: return@mapNotNull null
            val days = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, parsed.date)
            val w = Math.floorDiv(days, 7L).toInt() + 1
            w.takeIf { it in 1..MAX_WEEKS }
        }.toSet()
    }

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Controls Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (displayedWeek > 1) {
                                        onSelectWeek(displayedWeek - 1)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(displayedWeek - 2)
                                        }
                                    }
                                },
                                enabled = displayedWeek > 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
                            }

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showWeekPicker = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "第 $displayedWeek 周",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (displayedWeek == currentWeek) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "本周",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isDemoMode) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "演示",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (displayedWeekExams.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        ExamGradientPalette.colorsFor(0, darkTheme)
                                                    )
                                                )
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${displayedWeekExams.size}考",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = AppSettings.formatTermLabel(settings.selectedXnm, settings.selectedXqm),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (displayedWeek < MAX_WEEKS) {
                                        onSelectWeek(displayedWeek + 1)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(displayedWeek)
                                        }
                                    }
                                },
                                enabled = displayedWeek < MAX_WEEKS,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (displayedWeek != currentWeek) {
                                TextButton(
                                    onClick = {
                                        onJumpCurrentWeek()
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage((currentWeek - 1).coerceIn(0, MAX_WEEKS - 1))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("回本周", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            IconButton(
                                onClick = { showTermMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = "切换学期")
                            }
                            IconButton(
                                onClick = { onToggleShowNonCurrent(!settings.showNonCurrentWeekCourses) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (settings.showNonCurrentWeekCourses) {
                                        Icons.Outlined.Visibility
                                    } else {
                                        Icons.Outlined.VisibilityOff
                                    },
                                    contentDescription = "显示或隐藏非本周课程",
                                    tint = if (settings.showNonCurrentWeekCourses) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = onRefresh,
                                enabled = !isSyncing,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "刷新日程")
                            }
                        }
                    }

                    // Live Today Status Banner + Add Custom Sticky-Note Schedule Event Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TodayLiveBanner(
                                currentCourse = liveStatus.currentCourse,
                                nextCourse = liveStatus.nextCourse,
                                todayCount = liveStatus.todayCourses.size,
                                schedulePresetId = settings.schedulePresetId,
                                onCourseClick = { course ->
                                    selectedBlock = TimetableDisplayBlock(
                                        primary = course,
                                        isActiveInSelectedWeek = true
                                    )
                                }
                            )
                        }
                        Surface(
                            onClick = {
                                editingCustomEvent = null
                                showAddEventSheet = true
                            },
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "自定义日程",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "日程",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Swipeable HorizontalPager: swipe left/right to smoothly switch weeks (header + grid slide together)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { page ->
                val pageWeek = page + 1
                val pageWeekStartMonday = remember(semesterStart, pageWeek) {
                    semesterStart.plusWeeks((pageWeek - 1).toLong())
                }
                val pageDisplayBlocks = remember(courses, pageWeek, settings.showNonCurrentWeekCourses) {
                    TimetableSlotResolver.resolve(
                        courses = courses,
                        selectedWeek = pageWeek,
                        showNonCurrentWeek = settings.showNonCurrentWeekCourses
                    )
                }
                val pageExamBlocks = remember(exams, semesterStart, pageWeek, activePeriods) {
                    TimetableExamMapper.mapExamsForWeek(
                        exams = exams,
                        semesterStart = semesterStart,
                        weekNumber = pageWeek,
                        periods = activePeriods
                    )
                }
                val pageEventBlocks = remember(customEvents, semesterStart, pageWeek, activePeriods) {
                    TimetableEventMapper.mapEventsForWeek(
                        events = customEvents,
                        semesterStart = semesterStart,
                        weekNumber = pageWeek,
                        periods = activePeriods
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Weekday Header Row (Mon - Sun with actual calendar dates)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(TIME_COL_WIDTH),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${pageWeekStartMonday.monthValue}月",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        for (dayIdx in 0 until 7) {
                            val date = pageWeekStartMonday.plusDays(dayIdx.toLong())
                            val isToday = date == todayDate
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = GdouPeriodSchedule.weekdayLabels[dayIdx],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = date.format(dateFmt),
                                    fontSize = 10.sp,
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    // Main 7 x N Timetable Grid
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        val sectionCount = activePeriods.size
                        val totalGridHeight = SECTION_HEIGHT * sectionCount

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalGridHeight)
                        ) {
                            // Left section & time column (shows section #, start time, and end time)
                            Column(
                                modifier = Modifier
                                    .width(TIME_COL_WIDTH)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                activePeriods.forEach { period ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(SECTION_HEIGHT)
                                            .border(
                                                width = 0.25.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                            .padding(horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = period.section.toString(),
                                            fontSize = 12.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = period.startText,
                                            fontSize = 9.sp,
                                            lineHeight = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = period.endText,
                                            fontSize = 8.5.sp,
                                            lineHeight = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // 7-day Course Columns Overlay
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val dayWidth = maxWidth / 7

                                // Background faint grid lines
                                Column(modifier = Modifier.fillMaxSize()) {
                                    repeat(sectionCount) { idx ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(SECTION_HEIGHT)
                                                .border(
                                                    width = 0.25.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                )
                                                .background(
                                                    if (idx == 4 || idx == 8) {
                                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f)
                                                    } else Color.Transparent
                                                )
                                        )
                                    }
                                }

                                // Resolve overlaps between courses and custom sticky-note events:
                                // When a custom sticky note conflicts with a course, the course takes priority
                                // while a distinct colored sticky-note strip peeks on the left; tapping toggles!
                                fun overlapsCourse(course: CourseEntity, ev: TimetableStickyEventBlock): Boolean =
                                    course.dayOfWeek == ev.dayOfWeek &&
                                        course.startSection <= ev.endSection &&
                                        course.endSection >= ev.startSection

                                val conflictingEventIds = remember(pageDisplayBlocks, pageEventBlocks) {
                                    pageEventBlocks
                                        .filter { ev -> pageDisplayBlocks.any { overlapsCourse(it.primary, ev) } }
                                        .map { it.event.id }
                                        .toSet()
                                }

                                // 1. Render conflict-free, opaque course display blocks (prioritized over conflicting sticky notes)
                                pageDisplayBlocks.forEach { block ->
                                    val course = block.primary
                                    val isActive = block.isActiveInSelectedWeek
                                    val dayOffset = (course.dayOfWeek - 1).coerceIn(0, 6)
                                    val startIdx = (course.startSection - 1).coerceIn(0, sectionCount - 1)
                                    val span = (course.endSection - course.startSection + 1).coerceIn(1, sectionCount - startIdx)
                                    val palette = CourseCardPalette.colorFor(course.colorIndex, darkTheme)

                                    val overlappingEvents = pageEventBlocks.filter { overlapsCourse(course, it) }
                                    val hasConflict = overlappingEvents.isNotEmpty()
                                    val promotedEvent = overlappingEvents.find { it.event.id in activeFrontEventIds }
                                    val isYieldedToSticky = promotedEvent != null

                                    val animatedInsetStart by animateDpAsState(
                                        targetValue = when {
                                            !hasConflict -> 0.dp
                                            isYieldedToSticky -> 10.dp
                                            else -> STICKY_PEEK_WIDTH
                                        },
                                        label = "courseInset_${course.id}_${course.dayOfWeek}_${course.startSection}"
                                    )
                                    val courseZ = when {
                                        isYieldedToSticky -> 1.8f
                                        hasConflict -> 2.6f
                                        else -> 2.0f
                                    }

                                    val bgColor = if (isActive) {
                                        palette.background
                                    } else {
                                        if (darkTheme) Color(0xFF232A30) else Color(0xFFECEFF3)
                                    }
                                    val textColor = if (isActive) {
                                        palette.content
                                    } else {
                                        if (darkTheme) Color(0xFFA0AEB9) else Color(0xFF5D6B76)
                                    }
                                    val accentColor = if (isActive) {
                                        palette.accent
                                    } else {
                                        if (darkTheme) Color(0xFF54636E) else Color(0xFF9AA7B2)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .zIndex(courseZ)
                                            .offset(
                                                x = dayWidth * dayOffset + animatedInsetStart,
                                                y = SECTION_HEIGHT * startIdx
                                            )
                                            .width((dayWidth - animatedInsetStart).coerceAtLeast(24.dp))
                                            .height(SECTION_HEIGHT * span)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bgColor)
                                            .border(
                                                width = if (isActive) 0.9.dp else 0.6.dp,
                                                color = accentColor.copy(alpha = if (isActive) 0.55f else 0.45f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                if (promotedEvent != null) {
                                                    // Tapping the exposed course edge swaps back to showing the course in front
                                                    activeFrontEventIds = activeFrontEventIds - promotedEvent.event.id
                                                } else {
                                                    selectedBlock = block
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .background(accentColor)
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 5.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                if (!isActive || block.extraCourses.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                isActive -> "本周"
                                                                block.extraCourses.isNotEmpty() -> "他周"
                                                                else -> "非本周"
                                                            },
                                                            fontSize = 8.sp,
                                                            lineHeight = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = accentColor,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                        if (block.extraCourses.isNotEmpty()) {
                                                            Surface(
                                                                color = accentColor,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "+${block.extraCourses.size}",
                                                                    fontSize = 7.5.sp,
                                                                    lineHeight = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White,
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = course.courseName,
                                                    fontSize = 10.5.sp,
                                                    lineHeight = 12.5.sp,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = textColor,
                                                    maxLines = (span * 2).coerceIn(2, 6),
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                if (!isActive && course.weeksRaw.isNotBlank() && span >= 2) {
                                                    Text(
                                                        text = course.weeksRaw,
                                                        fontSize = 8.5.sp,
                                                        lineHeight = 10.sp,
                                                        color = textColor.copy(alpha = 0.78f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (course.classroom.isNotBlank()) {
                                                    Text(
                                                        text = "@${course.classroom}",
                                                        fontSize = 9.sp,
                                                        lineHeight = 10.5.sp,
                                                        color = textColor.copy(alpha = 0.85f),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2A. Non-conflicting sticky-note events: render full width
                                val nonConflictingEvents = pageEventBlocks.filter { it.event.id !in conflictingEventIds }
                                nonConflictingEvents
                                    .groupBy { it.dayOfWeek to it.startSection }
                                    .values
                                    .forEach { slotEvents ->
                                        val count = slotEvents.size
                                        slotEvents.forEachIndexed { subIdx, eventBlock ->
                                            val dayOffset = (eventBlock.dayOfWeek - 1).coerceIn(0, 6)
                                            val startIdx = (eventBlock.startSection - 1).coerceIn(0, sectionCount - 1)
                                            val span = (eventBlock.endSection - eventBlock.startSection + 1)
                                                .coerceIn(1, sectionCount - startIdx)
                                            val cardWidth = dayWidth / count
                                            val xOffset = dayWidth * dayOffset + cardWidth * subIdx

                                            StickyNoteEventCard(
                                                block = eventBlock,
                                                span = span,
                                                darkTheme = darkTheme,
                                                onClick = { selectedEventBlock = eventBlock },
                                                modifier = Modifier
                                                    .zIndex(2.2f)
                                                    .offset(x = xOffset, y = SECTION_HEIGHT * startIdx)
                                                    .width(cardWidth)
                                                    .height(SECTION_HEIGHT * span)
                                            )
                                        }
                                    }

                                // 2B. Conflicting sticky-note events:
                                // Default: visible sticky-note strip peeks on the LEFT edge of the course card;
                                // Clicking the peeking strip promotes the sticky note to the front (leaving course peeking right to swap back).
                                val conflictingEvents = pageEventBlocks.filter { it.event.id in conflictingEventIds }
                                conflictingEvents
                                    .groupBy { it.dayOfWeek to it.startSection }
                                    .values
                                    .forEach { slotEvents ->
                                        val promoted = slotEvents.find { it.event.id in activeFrontEventIds }
                                        if (promoted != null) {
                                            val dayOffset = (promoted.dayOfWeek - 1).coerceIn(0, 6)
                                            val startIdx = (promoted.startSection - 1).coerceIn(0, sectionCount - 1)
                                            val span = (promoted.endSection - promoted.startSection + 1)
                                                .coerceIn(1, sectionCount - startIdx)
                                            StickyNoteEventCard(
                                                block = promoted,
                                                span = span,
                                                darkTheme = darkTheme,
                                                onClick = { selectedEventBlock = promoted },
                                                modifier = Modifier
                                                    .zIndex(3.6f)
                                                    .offset(x = dayWidth * dayOffset, y = SECTION_HEIGHT * startIdx)
                                                    .width((dayWidth - 10.dp).coerceAtLeast(28.dp))
                                                    .height(SECTION_HEIGHT * span)
                                            )
                                        } else {
                                            val primarySticky = slotEvents.first()
                                            val dayOffset = (primarySticky.dayOfWeek - 1).coerceIn(0, 6)
                                            val startIdx = (primarySticky.startSection - 1).coerceIn(0, sectionCount - 1)
                                            val span = (primarySticky.endSection - primarySticky.startSection + 1)
                                                .coerceIn(1, sectionCount - startIdx)
                                            StickyNoteLeftPeekStrip(
                                                block = primarySticky,
                                                extraCount = (slotEvents.size - 1).coerceAtLeast(0),
                                                darkTheme = darkTheme,
                                                onClick = {
                                                    val conflictingIds = slotEvents.map { it.event.id }.toSet()
                                                    activeFrontEventIds = (activeFrontEventIds - conflictingIds) + primarySticky.event.id
                                                },
                                                modifier = Modifier
                                                    .zIndex(3.1f)
                                                    .offset(x = dayWidth * dayOffset, y = SECTION_HEIGHT * startIdx)
                                                    .width(STICKY_PEEK_WIDTH + 2.dp)
                                                    .height(SECTION_HEIGHT * span)
                                            )
                                        }
                                    }

                                // 3. Render colorful gradient exam schedule blocks on top of regular courses
                                pageExamBlocks.forEach { examBlock ->
                                    val exam = examBlock.exam
                                    val dayOffset = (examBlock.dayOfWeek - 1).coerceIn(0, 6)
                                    val startIdx = (examBlock.startSection - 1).coerceIn(0, sectionCount - 1)
                                    val span = (examBlock.endSection - examBlock.startSection + 1).coerceIn(1, sectionCount - startIdx)
                                    val gradColors = ExamGradientPalette.colorsFor(examBlock.gradientIndex, darkTheme)
                                    val examBrush = Brush.linearGradient(
                                        colors = gradColors,
                                        start = Offset.Zero,
                                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .zIndex(5.0f)
                                            .offset(
                                                x = dayWidth * dayOffset,
                                                y = SECTION_HEIGHT * startIdx
                                            )
                                            .width(dayWidth)
                                            .height(SECTION_HEIGHT * span)
                                            .padding(2.dp)
                                            .shadow(elevation = 3.dp, shape = RoundedCornerShape(9.dp))
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(examBrush)
                                            .border(
                                                width = 1.2.dp,
                                                color = Color.White.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(9.dp)
                                            )
                                            .clickable { selectedExamBlock = examBlock }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Surface(
                                                    color = Color.White.copy(alpha = 0.28f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (exam.isCustom) "考试·自" else "考试",
                                                        fontSize = 8.sp,
                                                        lineHeight = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Text(
                                                    text = exam.courseName,
                                                    fontSize = 10.5.sp,
                                                    lineHeight = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = (span * 2).coerceIn(2, 5),
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(
                                                    text = examBlock.timeRangeText,
                                                    fontSize = 7.2.sp,
                                                    lineHeight = 9.sp,
                                                    letterSpacing = (-0.25).sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.94f),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Clip
                                                )
                                                val roomText = buildString {
                                                    if (exam.classroom.isNotBlank()) append("@${exam.classroom}")
                                                    if (exam.seatNumber.isNotBlank()) {
                                                        if (isNotEmpty()) append(" ")
                                                        append("#${exam.seatNumber}")
                                                    }
                                                }
                                                if (roomText.isNotBlank()) {
                                                    Text(
                                                        text = roomText,
                                                        fontSize = 8.5.sp,
                                                        lineHeight = 10.sp,
                                                        color = Color.White.copy(alpha = 0.92f),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (courses.isEmpty() && pageExamBlocks.isEmpty() && pageEventBlocks.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "当前学期暂无日程数据\n下拉即可从教务系统刷新同步，或点击右上角「+ 日程」添加便签",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (bottomBarPadding > 0.dp) {
                            Spacer(modifier = Modifier.height(bottomBarPadding))
                        }
                    }
                }
            }
        }
    }

    // Sticky-note Custom Event Detail Sheet
    selectedEventBlock?.let { eventBlock ->
        val isPromoted = eventBlock.event.id in activeFrontEventIds
        ModalBottomSheet(onDismissRequest = { selectedEventBlock = null }) {
            CustomEventDetailSheet(
                block = eventBlock,
                darkTheme = darkTheme,
                hasOverlappingCourse = isPromoted,
                onSwitchBackToCourse = if (isPromoted) {
                    {
                        activeFrontEventIds = activeFrontEventIds - eventBlock.event.id
                        selectedEventBlock = null
                    }
                } else null,
                onEdit = {
                    val target = eventBlock.event
                    selectedEventBlock = null
                    editingCustomEvent = target
                    showAddEventSheet = true
                },
                onDelete = {
                    val id = eventBlock.event.id
                    activeFrontEventIds = activeFrontEventIds - id
                    selectedEventBlock = null
                    onDeleteCustomEvent(id)
                }
            )
        }
    }

    if (showAddEventSheet) {
        val defaultDateForWeek = if (displayedWeek == currentWeek) {
            todayDate
        } else {
            semesterStart.plusWeeks((displayedWeek - 1).toLong())
        }
        CustomEventEditorBottomSheet(
            periods = activePeriods,
            editingEvent = editingCustomEvent,
            defaultDate = defaultDateForWeek,
            darkTheme = darkTheme,
            onSave = { draft ->
                onSaveCustomEvent(draft)
                showAddEventSheet = false
                editingCustomEvent = null
            },
            onDismiss = {
                showAddEventSheet = false
                editingCustomEvent = null
            }
        )
    }

    // Exam Detail Bottom Sheet (triggered when tapping a colorful gradient exam card on the timetable)
    selectedExamBlock?.let { examBlock ->
        ModalBottomSheet(onDismissRequest = { selectedExamBlock = null }) {
            ExamBlockDetailSheet(
                block = examBlock,
                darkTheme = darkTheme,
                onEditCustom = {
                    val target = examBlock.exam
                    selectedExamBlock = null
                    editingCustomExam = target
                },
                onDeleteCustom = {
                    val id = examBlock.exam.id
                    selectedExamBlock = null
                    onDeleteCustomExam(id)
                }
            )
        }
    }

    editingCustomExam?.let { target ->
        CustomExamEditorBottomSheet(
            editingExam = target,
            onSave = { draft ->
                onSaveCustomExam(draft)
                editingCustomExam = null
            },
            onDismiss = { editingCustomExam = null }
        )
    }

    // Course Detail Bottom Sheet (supports viewing all courses sharing a slot cleanly)
    selectedBlock?.let { block ->
        ModalBottomSheet(onDismissRequest = { selectedBlock = null }) {
            CourseBlockDetailSheet(
                block = block,
                selectedWeek = displayedWeek,
                schedulePresetId = settings.schedulePresetId
            )
        }
    }

    // Scrollable Academic Year / Semester Bottom Sheet (supports past, current, and future years cleanly)
    if (showTermMenu) {
        ModalBottomSheet(onDismissRequest = { showTermMenu = false }) {
            TermPickerBottomSheet(
                yearGroups = settings.availableYearGroups(),
                selectedXnm = settings.selectedXnm,
                selectedXqm = settings.selectedXqm,
                onSelectTerm = { xnm, xqm ->
                    showTermMenu = false
                    onSelectTerm(xnm, xqm)
                }
            )
        }
    }

    // Week Picker Dialog
    if (showWeekPicker) {
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = { Text("选择周次（当前第 $currentWeek 周）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (w in 1..20) {
                            FilterChip(
                                selected = w == displayedWeek,
                                onClick = {
                                    onSelectWeek(w)
                                    showWeekPicker = false
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(w - 1)
                                    }
                                },
                                label = {
                                    val suffix = buildString {
                                        if (w == currentWeek) append("·本")
                                        if (w in weeksWithExams) append("·考")
                                    }
                                    Text("第${w}周$suffix")
                                }
                            )
                        }
                    }
                    Text(
                        text = "若当前周次与校历有偏差，可在「我的 → 学期第一周周一日期」中校准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeekPicker = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun TodayLiveBanner(
    currentCourse: CourseEntity?,
    nextCourse: CourseEntity?,
    todayCount: Int,
    schedulePresetId: String,
    onCourseClick: (CourseEntity) -> Unit
) {
    val activeCourse = currentCourse ?: nextCourse
    val containerColor = when {
        currentCourse != null -> MaterialTheme.colorScheme.primaryContainer
        nextCourse != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        currentCourse != null -> MaterialTheme.colorScheme.onPrimaryContainer
        nextCourse != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (activeCourse != null) Modifier.clickable { onCourseClick(activeCourse) }
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val tag = when {
                    currentCourse != null -> "正在上课"
                    nextCourse != null -> "下一节课"
                    todayCount > 0 -> "今日课程已结束"
                    else -> "今日无课"
                }
                Text(
                    text = if (activeCourse != null) {
                        "$tag · ${activeCourse.courseName}"
                    } else {
                        "$tag · 好好休息或安排自习吧"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeCourse != null) {
                    val timeRange = GdouPeriodSchedule.timeRangeForCourse(
                        activeCourse,
                        schedulePresetId
                    )
                    Text(
                        text = "$timeRange  ·  ${activeCourse.classroom.ifBlank { "地点待定" }}  ·  ${activeCourse.teacher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = contentColor.copy(alpha = 0.12f),
                shape = CircleShape
            ) {
                Text(
                    text = "今日 $todayCount 门",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseBlockDetailSheet(
    block: TimetableDisplayBlock,
    selectedWeek: Int,
    schedulePresetId: String
) {
    val allCourses = block.allCourses
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (allCourses.size > 1) {
            Text(
                text = "该时段共有 ${allCourses.size} 门不同周次/排课课程",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        allCourses.forEachIndexed { idx, course ->
            if (idx > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            SingleCourseDetailSection(
                course = course,
                isActiveThisWeek = course.isActiveInWeek(selectedWeek),
                schedulePresetId = schedulePresetId
            )
        }
    }
}

@Composable
private fun SingleCourseDetailSection(
    course: CourseEntity,
    isActiveThisWeek: Boolean,
    schedulePresetId: String
) {
    val dayName = GdouPeriodSchedule.weekdayLabels.getOrElse(course.dayOfWeek - 1) { "周一" }
    val timeRange = GdouPeriodSchedule.timeRangeForCourse(course, schedulePresetId)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = {},
                label = { Text(if (isActiveThisWeek) "本周上课" else "非本周") }
            )
        }

        DetailRow(Icons.Outlined.AccessTime, "上课时间", "$dayName 第${course.startSection}-${course.endSection}节 ($timeRange)")
        DetailRow(Icons.Outlined.CalendarMonth, "上课周次", course.weeksRaw.ifBlank { "全学期" })
        DetailRow(
            Icons.Outlined.LocationOn,
            "教室地点",
            listOf(course.campus, course.classroom).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "待定" }
        )
        DetailRow(Icons.Outlined.Person, "任课教师", course.teacher.ifBlank { "未知" })
        DetailRow(
            Icons.Outlined.Class,
            "课程信息",
            listOfNotNull(
                course.credits.takeIf { it.isNotBlank() }?.let { "${it}学分" },
                course.courseNature.takeIf { it.isNotBlank() },
                course.assessmentType.takeIf { it.isNotBlank() },
                course.classGroup.takeIf { it.isNotBlank() }
            ).joinToString("  |  ").ifBlank { course.courseCode }
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermPickerBottomSheet(
    yearGroups: List<Pair<String, List<String>>>,
    selectedXnm: String,
    selectedXqm: String,
    onSelectTerm: (String, String) -> Unit
) {
    val defaultXnm = remember { AppSettings.defaultXnm() }
    val defaultXqm = remember { AppSettings.defaultXqm() }
    val currentYearInt = defaultXnm.toIntOrNull() ?: LocalDate.now().year

    val initialIndex = remember(yearGroups, selectedXnm) {
        yearGroups.indexOfFirst { it.first == selectedXnm }
            .takeIf { it >= 0 }
            ?: yearGroups.indexOfFirst { it.first == defaultXnm }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialIndex - 1).coerceAtLeast(0)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "选择学年与学期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "上下滑动可查看未来学年与历史学年",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedXnm != defaultXnm || selectedXqm != defaultXqm) {
                AssistChip(
                    onClick = { onSelectTerm(defaultXnm, defaultXqm) },
                    label = { Text("回到当前学期") }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(yearGroups, key = { it.first }) { (year, terms) ->
                val yearInt = year.toIntOrNull()
                val yearTitle = if (yearInt != null) "${yearInt}-${yearInt + 1} 学年" else "$year 学年"
                val tagText = when {
                    yearInt == null -> null
                    yearInt > currentYearInt -> "未来"
                    yearInt == currentYearInt -> "当前学年"
                    else -> null
                }
                val isYearSelected = year == selectedXnm

                Surface(
                    color = if (isYearSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = yearTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isYearSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (tagText != null) {
                                Surface(
                                    color = if (yearInt == currentYearInt) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = tagText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (yearInt == currentYearInt) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            terms.forEach { term ->
                                val isCurrentTerm = year == defaultXnm && term == defaultXqm
                                val isSelected = year == selectedXnm && term == selectedXqm
                                val termName = when (term) {
                                    "3", "1" -> "第1学期"
                                    "12", "2" -> "第2学期"
                                    "16" -> "短学期"
                                    else -> "第${term}学期"
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectTerm(year, term) },
                                    label = {
                                        Text(if (isCurrentTerm) "$termName · 当前" else termName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamBlockDetailSheet(
    block: TimetableExamBlock,
    darkTheme: Boolean,
    onEditCustom: () -> Unit,
    onDeleteCustom: () -> Unit
) {
    val exam = block.exam
    val gradColors = ExamGradientPalette.colorsFor(block.gradientIndex, darkTheme)
    val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), block.date)
    val countdownText = when {
        daysDiff < 0L -> "已结束（${-daysDiff} 天前）"
        daysDiff == 0L -> "今天开考"
        else -> "距离开考还有 $daysDiff 天"
    }
    val weekDayCn = GdouPeriodSchedule.weekdayLabels[(block.dayOfWeek - 1).coerceIn(0, 6)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = gradColors,
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (exam.isCustom) "自定义考试" else "考试日程",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = listOf(exam.examName, exam.examMode)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow(
                icon = Icons.Outlined.Event,
                label = "考试日期",
                value = "${block.date}（$weekDayCn · 第 ${block.weekNumber} 周）"
            )
            DetailRow(
                icon = Icons.Outlined.AccessTime,
                label = "考试时间",
                value = exam.examTimeRaw.ifBlank { block.timeRangeText }
            )
            DetailRow(
                icon = Icons.Outlined.LocationOn,
                label = "考场地点",
                value = listOf(exam.campus, exam.classroom)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "考场待定" }
            )
            if (exam.seatNumber.isNotBlank()) {
                DetailRow(
                    icon = Icons.Outlined.Chair,
                    label = "座位号",
                    value = "第 ${exam.seatNumber} 号"
                )
            }
        }

        if (exam.isCustom) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onEditCustom,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑考试")
                }
                TextButton(
                    onClick = onDeleteCustom,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除考试", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private const val MAX_WEEKS = 25
private val TIME_COL_WIDTH = 44.dp
private val SECTION_HEIGHT = 64.dp
private val STICKY_PEEK_WIDTH = 14.dp

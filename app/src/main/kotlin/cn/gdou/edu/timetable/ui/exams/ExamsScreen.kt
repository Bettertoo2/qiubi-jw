package cn.gdou.edu.timetable.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.ui.theme.AccentDanger
import cn.gdou.edu.timetable.ui.theme.AccentInfo
import cn.gdou.edu.timetable.ui.theme.AccentWarning
import cn.gdou.edu.timetable.ui.theme.ExamGradientPalette
import cn.gdou.edu.timetable.ui.theme.LocalIsDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    exams: List<ExamEntity>,
    settings: AppSettings,
    isSyncing: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onRefresh: () -> Unit,
    onSaveCustomExam: (CustomExamDraft) -> Unit,
    onDeleteCustomExam: (Long) -> Unit
) {
    var filterTab by remember { mutableIntStateOf(0) } // 0=全部, 1=待考, 2=已结束
    var showAddEditor by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamEntity?>(null) }
    var deletingExam by remember { mutableStateOf<ExamEntity?>(null) }

    val nowMs = System.currentTimeMillis()
    val today = LocalDate.now()
    val zone = ZoneId.of("Asia/Shanghai")

    val upcomingExams = remember(exams, nowMs) {
        exams.filter { it.startTimestamp == null || it.startTimestamp >= nowMs }
    }
    val finishedExams = remember(exams, nowMs) {
        exams.filter { it.startTimestamp != null && it.startTimestamp < nowMs }
    }

    val displayed = when (filterTab) {
        1 -> upcomingExams
        2 -> finishedExams
        else -> exams
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp + bottomBarPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Header Summary Card with Custom Exam button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "考试安排",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = AppSettings.formatTermLabel(settings.selectedXnm, settings.selectedXqm),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val nextExam = upcomingExams.firstOrNull { it.startTimestamp != null }
                                    val statusText = if (nextExam?.startTimestamp != null) {
                                        val days = ChronoUnit.DAYS.between(
                                            today,
                                            Instant.ofEpochMilli(nextExam.startTimestamp).atZone(zone).toLocalDate()
                                        )
                                        when {
                                            days <= 0L -> "今日有考试：《${nextExam.courseName}》"
                                            else -> "最近一场：《${nextExam.courseName}》（还有 ${days} 天）"
                                        }
                                    } else if (exams.isEmpty()) {
                                        "教务系统暂未公布本学期考试安排，可手动添加自定义考试"
                                    } else {
                                        "本学期 ${exams.size} 场考试已全部结束"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(onClick = onRefresh, enabled = !isSyncing) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "刷新考试信息",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    editingExam = null
                                    showAddEditor = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("自定义考试时间安排（同步显示至日程）")
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterTab == 0,
                            onClick = { filterTab = 0 },
                            label = { Text("全部 (${exams.size})") }
                        )
                        FilterChip(
                            selected = filterTab == 1,
                            onClick = { filterTab = 1 },
                            label = { Text("待考 (${upcomingExams.size})") }
                        )
                        FilterChip(
                            selected = filterTab == 2,
                            onClick = { filterTab = 2 },
                            label = { Text("已结束 (${finishedExams.size})") }
                        )
                    }
                }

                if (displayed.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "暂无考试信息",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "教务发布考场后下拉即可同步，也可以手动添加期中、随堂测验或四六级等自定义考试",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = {
                                        editingExam = null
                                        showAddEditor = true
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("添加自定义考试")
                                }
                            }
                        }
                    }
                } else {
                    items(displayed, key = { "${it.id}_${it.isCustom}_${it.courseName}_${it.examTimeRaw}" }) { exam ->
                        ExamCard(
                            exam = exam,
                            nowMs = nowMs,
                            today = today,
                            zone = zone,
                            onEdit = {
                                editingExam = exam
                                showAddEditor = true
                            },
                            onDelete = { deletingExam = exam }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editingExam = null
                showAddEditor = true
            },
            icon = { Icon(Icons.Outlined.Add, contentDescription = "添加考试") },
            text = { Text("添加考试") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomBarPadding)
        )
    }

    if (showAddEditor) {
        CustomExamEditorBottomSheet(
            editingExam = editingExam,
            onSave = { draft ->
                onSaveCustomExam(draft)
                showAddEditor = false
                editingExam = null
            },
            onDismiss = {
                showAddEditor = false
                editingExam = null
            }
        )
    }

    deletingExam?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingExam = null },
            title = { Text("删除自定义考试") },
            text = { Text("确定要删除自定义考试「${target.courseName}」吗？删除后也会同步从课表中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCustomExam(target.id)
                    deletingExam = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExam = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ExamCard(
    exam: ExamEntity,
    nowMs: Long,
    today: LocalDate,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val badgeInfo: Pair<String, Color> = when (val ts = exam.startTimestamp) {
        null -> "时间待定" to MaterialTheme.colorScheme.outline
        else -> {
            if (ts < nowMs) {
                "已结束" to MaterialTheme.colorScheme.outline
            } else {
                val days = ChronoUnit.DAYS.between(
                    today,
                    Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                )
                when {
                    days <= 0L -> "今天开考" to AccentDanger
                    days <= 3L -> "还有 ${days} 天" to AccentDanger
                    days <= 7L -> "还有 ${days} 天" to AccentWarning
                    else -> "还有 ${days} 天" to AccentInfo
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = exam.courseName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (exam.isCustom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            ExamGradientPalette.colorsFor(exam.courseName.hashCode(), isDark)
                                        )
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "自定义",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = listOf(exam.examName, exam.examMode, exam.credits.takeIf { it.isNotBlank() }?.let { "${it}学分" })
                            .filterNotNull()
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = badgeInfo.second.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = badgeInfo.first,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeInfo.second,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = exam.examTimeRaw.ifBlank { "考试时间待定" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = listOf(exam.campus, exam.classroom)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "考场待定" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (exam.seatNumber.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Chair,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "座号 ${exam.seatNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (exam.isCustom) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("编辑")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

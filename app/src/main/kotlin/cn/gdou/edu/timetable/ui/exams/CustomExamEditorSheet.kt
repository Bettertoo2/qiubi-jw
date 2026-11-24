package cn.gdou.edu.timetable.ui.exams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.domain.TimetableExamMapper
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CustomExamDraft(
    val existingId: Long = 0L,
    val courseName: String,
    val examName: String,
    val dateIso: String,
    val startTime: String,
    val endTime: String,
    val classroom: String,
    val campus: String,
    val seatNumber: String,
    val examMode: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomExamEditorBottomSheet(
    editingExam: ExamEntity? = null,
    onSave: (CustomExamDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val parsedExisting = remember(editingExam) {
        editingExam?.let { TimetableExamMapper.parseExamDateTime(it) }
    }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    var courseName by remember(editingExam) { mutableStateOf(editingExam?.courseName.orEmpty()) }
    var examName by remember(editingExam) { mutableStateOf(editingExam?.examName?.ifBlank { "期末考试" } ?: "期末考试") }
    var selectedDate by remember(editingExam) { mutableStateOf(parsedExisting?.date ?: LocalDate.now()) }
    var startTime by remember(editingExam) { mutableStateOf(parsedExisting?.startTime ?: LocalTime.of(8, 30)) }
    var endTime by remember(editingExam) { mutableStateOf(parsedExisting?.endTime ?: LocalTime.of(10, 30)) }
    var classroom by remember(editingExam) { mutableStateOf(editingExam?.classroom.orEmpty()) }
    var campus by remember(editingExam) { mutableStateOf(editingExam?.campus.orEmpty()) }
    var seatNumber by remember(editingExam) { mutableStateOf(editingExam?.seatNumber.orEmpty()) }
    var examMode by remember(editingExam) { mutableStateOf(editingExam?.examMode?.ifBlank { "闭卷笔试" } ?: "闭卷笔试") }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingExam == null) "自定义考试时间安排" else "编辑自定义考试",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "添加后将永久保存在本地（教务刷新不会被覆盖），并自动同步以彩色渐变卡片显示在对应周的日程中。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = courseName,
                onValueChange = {
                    courseName = it
                    validationError = null
                },
                label = { Text("课程名称 *") },
                placeholder = { Text("例如：高等数学A / 大学英语四级") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Exam type presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "考试类型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("期末考试", "期中考试", "随堂测验", "实验考核", "等级考试", "补考/缓考").forEach { label ->
                        FilterChip(
                            selected = examName == label,
                            onClick = { examName = label },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Date selector
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "考试日期",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val weekDayCn = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[selectedDate.dayOfWeek.value - 1]
                            Text(
                                text = "$selectedDate（$weekDayCn）",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "选择日期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Time range quick presets + custom pickers
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "考试时间段",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val presets = listOf(
                    LocalTime.of(8, 30) to LocalTime.of(10, 30),
                    LocalTime.of(10, 45) to LocalTime.of(12, 45),
                    LocalTime.of(14, 30) to LocalTime.of(16, 30),
                    LocalTime.of(16, 45) to LocalTime.of(18, 45),
                    LocalTime.of(19, 30) to LocalTime.of(21, 30)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { (s, e) ->
                        val label = "${s.format(timeFmt)}-${e.format(timeFmt)}"
                        FilterChip(
                            selected = startTime == s && endTime == e,
                            onClick = {
                                startTime = s
                                endTime = e
                                validationError = null
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimeSelectorTile(
                        label = "开始时间",
                        timeText = startTime.format(timeFmt),
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    TimeSelectorTile(
                        label = "结束时间",
                        timeText = endTime.format(timeFmt),
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = classroom,
                    onValueChange = { classroom = it },
                    label = { Text("考场教室") },
                    placeholder = { Text("如 教学楼 202") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = seatNumber,
                    onValueChange = { seatNumber = it },
                    label = { Text("座号") },
                    placeholder = { Text("选填") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = campus,
                    onValueChange = { campus = it },
                    label = { Text("校区（选填）") },
                    placeholder = { Text("如 主校区") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = examMode,
                    onValueChange = { examMode = it },
                    label = { Text("考试形式") },
                    placeholder = { Text("闭卷笔试") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val trimmedName = courseName.trim()
                        if (trimmedName.isEmpty()) {
                            validationError = "请输入课程名称"
                            return@Button
                        }
                        if (!endTime.isAfter(startTime)) {
                            validationError = "结束时间必须晚于开始时间"
                            return@Button
                        }
                        onSave(
                            CustomExamDraft(
                                existingId = editingExam?.id ?: 0L,
                                courseName = trimmedName,
                                examName = examName.trim().ifBlank { "自定义考试" },
                                dateIso = selectedDate.toString(),
                                startTime = startTime.format(timeFmt),
                                endTime = endTime.format(timeFmt),
                                classroom = classroom.trim(),
                                campus = campus.trim(),
                                seatNumber = seatNumber.trim(),
                                examMode = examMode.trim()
                            )
                        )
                    },
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (editingExam == null) "保存并添加到日程" else "保存修改")
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
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

    if (showStartTimePicker) {
        val state = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("选择开始时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = LocalTime.of(state.hour, state.minute)
                    startTime = picked
                    if (!endTime.isAfter(picked)) {
                        endTime = picked.plusHours(2).let { if (it.isBefore(picked)) LocalTime.of(23, 59) else it }
                    }
                    validationError = null
                    showStartTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showEndTimePicker) {
        val state = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("选择结束时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime.of(state.hour, state.minute)
                    validationError = null
                    showEndTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TimeSelectorTile(
    label: String,
    timeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

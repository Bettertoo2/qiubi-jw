package cn.gdou.edu.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.gdou.edu.timetable.data.local.db.CustomScheduleEventEntity
import cn.gdou.edu.timetable.domain.ClassPeriodTime
import cn.gdou.edu.timetable.domain.GdouPeriodSchedule
import cn.gdou.edu.timetable.domain.TimetableExamMapper
import cn.gdou.edu.timetable.domain.TimetableStickyEventBlock
import cn.gdou.edu.timetable.ui.theme.StickyNotePalette
import cn.gdou.edu.timetable.ui.theme.StickyNoteStyle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CustomEventDraft(
    val existingId: Long = 0L,
    val title: String,
    val dateIso: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val note: String,
    val stickyColorIndex: Int,
    val repeatWeekly: Boolean
)

/**
 * Draws an authentic pastel sticky-note paper card with top adhesive tape bar
 * and a dog-eared folded bottom-right corner on the 日程 grid.
 */
@Composable
fun StickyNoteEventCard(
    block: TimetableStickyEventBlock,
    span: Int,
    darkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val event = block.event
    val style = StickyNotePalette.styleFor(event.stickyColorIndex, darkTheme)

    Box(
        modifier = modifier
            .padding(2.dp)
            .shadow(
                elevation = 2.5.dp,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 12.dp)
            )
            .drawStickyNotePaper(style = style, foldSize = 11.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, top = 7.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                Text(
                    text = if (event.repeatWeekly) "便签·周" else "便签",
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = style.ink.copy(alpha = 0.72f),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = event.title,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = style.ink,
                    maxLines = (span * 2).coerceIn(2, 5),
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = block.timeRangeText,
                    fontSize = 7.sp,
                    lineHeight = 8.5.sp,
                    letterSpacing = (-0.25).sp,
                    fontWeight = FontWeight.Medium,
                    color = style.ink.copy(alpha = 0.78f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                val sub = event.location.takeIf { it.isNotBlank() }?.let { "@$it" }
                    ?: event.note.takeIf { it.isNotBlank() }
                if (!sub.isNullOrBlank()) {
                    Text(
                        text = sub,
                        fontSize = 8.sp,
                        lineHeight = 9.5.sp,
                        color = style.ink.copy(alpha = 0.82f),
                        maxLines = if (span >= 2) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Distinct, tactile left-peeking sticky-note strip shown on the left edge of a course card
 * whenever a custom sticky note conflicts with a scheduled class. Tapping it switches the sticky note
 * into the foreground.
 */
@Composable
fun StickyNoteLeftPeekStrip(
    block: TimetableStickyEventBlock,
    extraCount: Int,
    darkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = StickyNotePalette.styleFor(block.event.stickyColorIndex, darkTheme)
    Box(
        modifier = modifier
            .padding(top = 2.dp, bottom = 2.dp, start = 2.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
            .drawBehind {
                val w = size.width
                val h = size.height
                val tapeH = 6.dp.toPx().coerceAtMost(h * 0.16f)
                val fold = 6.dp.toPx().coerceAtMost(w * 0.55f)

                // Sticky paper body with bottom-left notched ear for unmistakable sticky-note feel
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(fold, h)
                    lineTo(0f, h - fold)
                    close()
                }
                drawPath(path, color = style.paper)
                drawRect(color = style.topTape, topLeft = Offset.Zero, size = Size(w, tapeH))
                drawPath(
                    path = path,
                    color = style.border.copy(alpha = 0.9f),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Small folded triangle at bottom-left
                val foldPath = Path().apply {
                    moveTo(0f, h - fold)
                    lineTo(fold, h - fold)
                    lineTo(fold, h)
                    close()
                }
                drawPath(foldPath, color = style.foldCorner)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 8.dp, bottom = 8.dp, start = 1.dp, end = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "便\n签",
                fontSize = 8.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = style.ink,
                textAlign = TextAlign.Center
            )
            if (extraCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "+$extraCount",
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = style.ink.copy(alpha = 0.85f)
                )
            }
        }
    }
}

fun Modifier.drawStickyNotePaper(
    style: StickyNoteStyle,
    foldSize: Dp
): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    val fold = foldSize.toPx().coerceAtMost(minOf(w, h) * 0.35f)
    val tapeH = (5.dp.toPx()).coerceAtMost(h * 0.18f)

    val paperPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, h - fold)
        lineTo(w - fold, h)
        lineTo(0f, h)
        close()
    }
    drawPath(paperPath, color = style.paper)

    // Top sticky pad darker strip
    drawRect(
        color = style.topTape,
        topLeft = Offset.Zero,
        size = Size(w, tapeH)
    )

    // Subtle paper horizontal ruled lines
    val lineColor = style.border.copy(alpha = 0.22f)
    val step = 18.dp.toPx()
    var y = tapeH + step
    while (y < h - fold) {
        drawLine(
            color = lineColor,
            start = Offset(4.dp.toPx(), y),
            end = Offset(w - 4.dp.toPx(), y),
            strokeWidth = 0.6.dp.toPx()
        )
        y += step
    }

    drawPath(
        path = paperPath,
        color = style.border.copy(alpha = 0.75f),
        style = Stroke(width = 0.9.dp.toPx())
    )

    val foldPath = Path().apply {
        moveTo(w - fold, h)
        lineTo(w - fold, h - fold)
        lineTo(w, h - fold)
        close()
    }
    drawPath(foldPath, color = style.foldCorner)
    drawPath(
        path = foldPath,
        color = style.border.copy(alpha = 0.65f),
        style = Stroke(width = 0.7.dp.toPx())
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomEventEditorBottomSheet(
    periods: List<ClassPeriodTime>,
    editingEvent: CustomScheduleEventEntity? = null,
    defaultDate: LocalDate = LocalDate.now(),
    darkTheme: Boolean = false,
    onSave: (CustomEventDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val safePeriods = remember(periods) {
        if (periods.isNotEmpty()) periods else GdouPeriodSchedule.gdouStandardPeriods.take(10)
    }
    val initialRange = remember(editingEvent, safePeriods) {
        if (editingEvent != null) {
            val s = parseHhMm(editingEvent.startTime)
            val e = parseHhMm(editingEvent.endTime)
            if (s != null && e != null && e.isAfter(s)) {
                TimetableExamMapper.mapTimeRangeToSections(s, e, safePeriods)
            } else {
                1 to 2.coerceAtMost(safePeriods.size)
            }
        } else {
            val defStart = if (safePeriods.size >= 6) 5 else 1
            val defEnd = (defStart + 1).coerceAtMost(safePeriods.size)
            defStart to defEnd
        }
    }

    var title by remember(editingEvent) { mutableStateOf(editingEvent?.title.orEmpty()) }
    var selectedDate by remember(editingEvent, defaultDate) {
        mutableStateOf(
            editingEvent?.dateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: defaultDate
        )
    }
    var startSection by remember(editingEvent, initialRange) {
        mutableIntStateOf(initialRange.first.coerceIn(1, safePeriods.size))
    }
    var endSection by remember(editingEvent, initialRange) {
        mutableIntStateOf(initialRange.second.coerceIn(startSection, safePeriods.size))
    }
    var location by remember(editingEvent) { mutableStateOf(editingEvent?.location.orEmpty()) }
    var note by remember(editingEvent) { mutableStateOf(editingEvent?.note.orEmpty()) }
    var stickyColorIndex by remember(editingEvent) { mutableIntStateOf(editingEvent?.stickyColorIndex ?: 0) }
    var repeatWeekly by remember(editingEvent) { mutableStateOf(editingEvent?.repeatWeekly ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }

    val startPeriod = safePeriods.find { it.section == startSection } ?: safePeriods.first()
    val endPeriod = safePeriods.find { it.section == endSection } ?: safePeriods.last()
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
                text = if (editingEvent == null) "新建便签日程" else "编辑便签日程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "选择日程表节次与便签纸颜色；若与课程时间重叠，将优先显示课程并在左侧露出便签纸，点击即可切换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                label = { Text("日程标题 *") },
                placeholder = { Text("例如：社团例会 / 图书馆自习 / 跑步打卡") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Sticky-note color swatch picker (8 authentic folded paper swatches)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "便签纸颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StickyNotePalette.lightStyles.forEachIndexed { idx, _ ->
                        val style = StickyNotePalette.styleFor(idx, darkTheme)
                        val selected = stickyColorIndex == idx
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { stickyColorIndex = idx }
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(if (selected) 4.dp else 1.5.dp, RoundedCornerShape(4.dp))
                                    .drawStickyNotePaper(style = style, foldSize = 9.dp)
                                    .then(
                                        if (selected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(4.dp)
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "已选",
                                        tint = style.ink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = style.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
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
                                text = "日程日期",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val weekDayCn = GdouPeriodSchedule.weekdayLabels[selectedDate.dayOfWeek.value - 1]
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

            // Schedule-only period selector (strictly matches the timetable's exact period slots)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "日程表时间段",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val secText = if (startSection == endSection) {
                            "第${startSection}节"
                        } else {
                            "第${startSection}-${endSection}节"
                        }
                        Text(
                            text = "$secText（${startPeriod.startText}-${endPeriod.endText}）",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Quick 2-period blocks from the active timetable schedule
                val pairBlocks = remember(safePeriods) {
                    safePeriods.chunked(2).mapNotNull { chunk ->
                        val first = chunk.firstOrNull() ?: return@mapNotNull null
                        val last = chunk.last()
                        first to last
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pairBlocks.forEach { (pStart, pEnd) ->
                        val label = if (pStart.section == pEnd.section) {
                            "第${pStart.section}节 ${pStart.startText}-${pEnd.endText}"
                        } else {
                            "第${pStart.section}-${pEnd.section}节 ${pStart.startText}-${pEnd.endText}"
                        }
                        FilterChip(
                            selected = startSection == pStart.section && endSection == pEnd.section,
                            onClick = {
                                startSection = pStart.section
                                endSection = pEnd.section
                                validationError = null
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Fine-grained Start Section & End Section strictly from the timetable
                Text(
                    text = "开始节次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    safePeriods.forEach { p ->
                        FilterChip(
                            selected = startSection == p.section,
                            onClick = {
                                startSection = p.section
                                if (endSection < p.section) {
                                    endSection = p.section
                                }
                                validationError = null
                            },
                            label = { Text("第${p.section}节 ${p.startText}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(
                    text = "结束节次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    safePeriods.filter { it.section >= startSection }.forEach { p ->
                        FilterChip(
                            selected = endSection == p.section,
                            onClick = {
                                endSection = p.section
                                validationError = null
                            },
                            label = { Text("第${p.section}节 ${p.endText}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("地点（选填）") },
                placeholder = { Text("例如：图书馆 3F / 体育馆") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("便签备注（选填）") },
                placeholder = { Text("记录待办事项、需要带的物品等...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "每周重复此便签",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "开启后将从所选日期起每周同一星期显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = repeatWeekly, onCheckedChange = { repeatWeekly = it })
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

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
                        val trimmed = title.trim()
                        if (trimmed.isEmpty()) {
                            validationError = "请输入日程标题"
                            return@Button
                        }
                        onSave(
                            CustomEventDraft(
                                existingId = editingEvent?.id ?: 0L,
                                title = trimmed,
                                dateIso = selectedDate.toString(),
                                startTime = startPeriod.startText,
                                endTime = endPeriod.endText,
                                location = location.trim(),
                                note = note.trim(),
                                stickyColorIndex = stickyColorIndex,
                                repeatWeekly = repeatWeekly
                            )
                        )
                    },
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (editingEvent == null) "贴到日程表" else "保存修改")
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
}

@Composable
fun CustomEventDetailSheet(
    block: TimetableStickyEventBlock,
    darkTheme: Boolean,
    hasOverlappingCourse: Boolean = false,
    onSwitchBackToCourse: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val event = block.event
    val style = StickyNotePalette.styleFor(event.stickyColorIndex, darkTheme)
    val weekDayCn = GdouPeriodSchedule.weekdayLabels[(block.dayOfWeek - 1).coerceIn(0, 6)]
    val sectionLabel = if (block.startSection == block.endSection) {
        "第 ${block.startSection} 节"
    } else {
        "第 ${block.startSection}-${block.endSection} 节"
    }

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
                .shadow(4.dp, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 22.dp))
                .drawStickyNotePaper(style = style, foldSize = 22.dp)
                .padding(start = 18.dp, end = 22.dp, top = 18.dp, bottom = 22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = style.topTape,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "自定义便签 · ${style.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = style.ink,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (event.repeatWeekly) {
                        Surface(
                            color = style.topTape.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Outlined.Repeat, contentDescription = null, tint = style.ink, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "每周重复",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = style.ink
                                )
                            }
                        }
                    }
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = style.ink
                )
                if (event.note.isNotBlank()) {
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = style.ink.copy(alpha = 0.88f)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StickyDetailRow(
                icon = Icons.Outlined.Event,
                label = "日程日期",
                value = "${block.displayDate}（$weekDayCn · 第 ${block.weekNumber} 周）"
            )
            StickyDetailRow(
                icon = Icons.Outlined.AccessTime,
                label = "日程表时段",
                value = "$sectionLabel（${block.timeRangeText}）"
            )
            if (event.location.isNotBlank()) {
                StickyDetailRow(
                    icon = Icons.Outlined.LocationOn,
                    label = "地点",
                    value = event.location
                )
            }
            if (event.note.isNotBlank()) {
                StickyDetailRow(
                    icon = Icons.AutoMirrored.Outlined.Notes,
                    label = "备注",
                    value = event.note
                )
            }
        }

        if (hasOverlappingCourse && onSwitchBackToCourse != null) {
            Button(
                onClick = onSwitchBackToCourse,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("切回显示该时段课程")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("编辑便签")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("删除便签", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StickyDetailRow(
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
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun parseHhMm(raw: String): LocalTime? {
    val parts = raw.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return runCatching { LocalTime.of(h, m) }.getOrNull()
}

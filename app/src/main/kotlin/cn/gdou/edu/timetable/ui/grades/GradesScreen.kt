package cn.gdou.edu.timetable.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import cn.gdou.edu.timetable.data.remote.GradeScoreComponent
import cn.gdou.edu.timetable.domain.GpaCalculator
import cn.gdou.edu.timetable.domain.TermGpaPoint
import cn.gdou.edu.timetable.ui.GradeBreakdownUiState
import cn.gdou.edu.timetable.ui.theme.AccentDanger
import cn.gdou.edu.timetable.ui.theme.AccentInfo
import cn.gdou.edu.timetable.ui.theme.AccentSuccess
import cn.gdou.edu.timetable.ui.theme.AccentWarning
import cn.gdou.edu.timetable.ui.theme.LocalIsDarkTheme
import cn.gdou.edu.timetable.ui.theme.LocalThemeSeedColor
import cn.gdou.edu.timetable.ui.theme.themeHeroGradientColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    grades: List<GradeEntity>,
    profile: StudentProfileEntity?,
    isSyncing: Boolean,
    breakdownState: GradeBreakdownUiState,
    bottomBarPadding: Dp = 0.dp,
    onRefresh: () -> Unit,
    onGradeClick: (GradeEntity) -> Unit,
    onRefreshBreakdown: (GradeEntity) -> Unit,
    onDismissBreakdown: () -> Unit,
    onOpenWebReauth: () -> Unit
) {
    var selectedTermKey by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val allSummary = remember(grades) { GpaCalculator.summarize(grades) }
    val termOptions = remember(grades) {
        grades.map { "${it.xnm}:${it.xqm}" to "${it.yearLabel} 第${it.termLabel}学期" }
            .distinctBy { it.first }
    }

    val filteredGrades = remember(grades, selectedTermKey, searchQuery) {
        grades.filter { g ->
            val termMatch = selectedTermKey == "ALL" || "${g.xnm}:${g.xqm}" == selectedTermKey
            val queryMatch = searchQuery.isBlank() ||
                g.courseName.contains(searchQuery, ignoreCase = true) ||
                g.courseNature.contains(searchQuery, ignoreCase = true)
            termMatch && queryMatch
        }
    }
    val filteredSummary = remember(filteredGrades) { GpaCalculator.summarize(filteredGrades) }

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GpaHeroCard(
                    displayGpa = if (selectedTermKey == "ALL" && profile?.officialGpa != null) {
                        profile.officialGpa
                    } else {
                        filteredSummary.weightedGpa
                    },
                    calculatedGpa = filteredSummary.weightedGpa,
                    officialGpa = if (selectedTermKey == "ALL") profile?.officialGpa else null,
                    averageScore = filteredSummary.weightedAverageScore,
                    earnedCredits = filteredSummary.totalCreditsEarned,
                    passedCount = filteredSummary.passedCourseCount,
                    totalCount = filteredSummary.totalCourseCount,
                    scopeLabel = if (selectedTermKey == "ALL") "总平均学分绩点 (GPA)" else "所选学期绩点 (GPA)",
                    isSyncing = isSyncing,
                    onRefresh = onRefresh
                )
            }

            if (allSummary.termPoints.size >= 2) {
                item {
                    TermGpaTrendCard(termPoints = allSummary.termPoints)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTermKey == "ALL",
                        onClick = { selectedTermKey = "ALL" },
                        label = { Text("全部学期 (${grades.size})") }
                    )
                    termOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedTermKey == key,
                            onClick = { selectedTermKey = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索课程名称或课程性质...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            if (filteredGrades.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text("暂无匹配的成绩记录", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "成绩公布后下拉刷新即可实时同步并计算最新绩点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    filteredGrades,
                    key = { "${it.id}_${it.xnm}_${it.xqm}_${it.courseCode}_${it.courseName}" }
                ) { grade ->
                    GradeItemCard(
                        grade = grade,
                        onClick = { onGradeClick(grade) }
                    )
                }
            }
        }
    }

    val activeGrade = breakdownState.grade
    if (activeGrade != null) {
        GradeBreakdownBottomSheet(
            grade = activeGrade,
            state = breakdownState,
            onRefresh = { onRefreshBreakdown(activeGrade) },
            onDismiss = onDismissBreakdown,
            onOpenWebReauth = {
                onDismissBreakdown()
                onOpenWebReauth()
            }
        )
    }
}

@Composable
private fun GpaHeroCard(
    displayGpa: Double,
    calculatedGpa: Double,
    officialGpa: Double?,
    averageScore: Double,
    earnedCredits: Double,
    passedCount: Int,
    totalCount: Int,
    scopeLabel: String,
    isSyncing: Boolean,
    onRefresh: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = themeHeroGradientColors(LocalThemeSeedColor.current, LocalIsDarkTheme.current)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = scopeLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.2f", displayGpa),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (officialGpa != null) {
                            Text(
                                text = "教务官方绩点 · 加权估算 ${String.format(Locale.US, "%.2f", calculatedGpa)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    IconButton(onClick = onRefresh, enabled = !isSyncing) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新成绩与绩点",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroMetric(label = "已获学分", value = String.format(Locale.US, "%.1f", earnedCredits))
                    HeroMetric(label = "加权均分", value = String.format(Locale.US, "%.1f", averageScore))
                    HeroMetric(label = "通过课程", value = "$passedCount / $totalCount")
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f)
        )
    }
}

@Composable
private fun TermGpaTrendCard(termPoints: List<TermGpaPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "各学期绩点趋势",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            val maxGpa = (termPoints.maxOfOrNull { it.gpa } ?: 4.0).coerceAtLeast(4.0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                termPoints.takeLast(6).forEach { point ->
                    val ratio = (point.gpa / maxGpa).toFloat().coerceIn(0.12f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", point.gpa),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height((62 * ratio).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeItemCard(
    grade: GradeEntity,
    onClick: () -> Unit
) {
    val passed = GpaCalculator.isPassed(grade)
    val scoreColor = when {
        !passed -> AccentDanger
        (grade.numericScore ?: 0.0) >= 90.0 || grade.gradePoint >= 4.0 -> AccentSuccess
        (grade.numericScore ?: 0.0) >= 80.0 || grade.gradePoint >= 3.0 -> AccentInfo
        else -> AccentWarning
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = grade.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(
                        "${grade.yearLabel} 第${grade.termLabel}学期",
                        grade.courseNature.takeIf { it.isNotBlank() },
                        grade.examNature.takeIf { it.isNotBlank() && it != "正常考试" }
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "学分 ${String.format(Locale.US, "%.1f", grade.credits)}  ·  绩点 ${String.format(Locale.US, "%.2f", grade.gradePoint)}" +
                        if (grade.teacher.isNotBlank()) "  ·  ${grade.teacher}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (grade.breakdownJson.isNotBlank()) "点击查看分数构成（已缓存）" else "点击查看分数详细构成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = scoreColor.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = grade.scoreRaw,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "查看分数构成",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeBreakdownBottomSheet(
    grade: GradeEntity,
    state: GradeBreakdownUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onOpenWebReauth: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = grade.courseName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = listOfNotNull(
                            "${grade.yearLabel} 第${grade.termLabel}学期",
                            grade.courseNature.takeIf { it.isNotBlank() },
                            grade.examNature.takeIf { it.isNotBlank() },
                            grade.teacher.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (grade.courseCode.isNotBlank()) {
                        Text(
                            text = "课程代码：${grade.courseCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新分数构成"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BreakdownSummaryMetric(
                    label = "总评成绩",
                    value = grade.scoreRaw,
                    highlight = true,
                    modifier = Modifier.weight(1f)
                )
                BreakdownSummaryMetric(
                    label = "学分",
                    value = String.format(Locale.US, "%.1f", grade.credits),
                    highlight = false,
                    modifier = Modifier.weight(1f)
                )
                BreakdownSummaryMetric(
                    label = "绩点",
                    value = String.format(Locale.US, "%.2f", grade.gradePoint),
                    highlight = false,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            Text(
                text = "分数详细构成",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (state.isLoading && state.components.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                state.isLoading && state.components.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            text = "正在从教务系统获取分数构成...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.errorMessage != null -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onRefresh) {
                                    Text("重新获取")
                                }
                                if (state.requiresRelogin) {
                                    OutlinedButton(onClick = onOpenWebReauth) {
                                        Text("网页重新认证")
                                    }
                                }
                            }
                        }
                    }
                }

                state.components.isEmpty() -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "教务系统暂未返回该科目的分项构成（部分考查课、选修课或较早学期可能仅公布总评成绩）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = onRefresh) {
                                Text("重新拉取")
                            }
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.components.forEach { component ->
                            GradeComponentRow(component = component)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownSummaryMetric(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GradeComponentRow(component: GradeScoreComponent) {
    val isTotal = component.name.contains("总评") || component.name.contains("总成绩")
    val numeric = component.score.toDoubleOrNull()
    val scoreColor = when {
        numeric == null -> MaterialTheme.colorScheme.primary
        numeric < 60.0 -> AccentDanger
        numeric >= 90.0 -> AccentSuccess
        numeric >= 80.0 -> AccentInfo
        else -> AccentWarning
    }

    Surface(
        color = if (isTotal) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    Text(
                        text = component.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isTotal) FontWeight.Bold else FontWeight.SemiBold
                    )
                    if (component.weight.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "占比 ${component.weight}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = component.score,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            if (numeric != null && numeric in 0.0..100.0) {
                val fraction = (numeric / 100.0).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(scoreColor.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
}

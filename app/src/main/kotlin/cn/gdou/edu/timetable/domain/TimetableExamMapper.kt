package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.ExamEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class ParsedExamDateTime(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    val timeRangeText: String
        get() = "${startTime.format(TIME_FMT)}-${endTime.format(TIME_FMT)}"

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class TimetableExamBlock(
    val exam: ExamEntity,
    val weekNumber: Int,
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val startSection: Int,
    val endSection: Int,
    val date: LocalDate,
    val timeRangeText: String,
    val gradientIndex: Int
)

/**
 * Maps scheduled exams (both JW-synced and user-created custom exams) onto exact
 * week/day/period positions on the timetable grid.
 */
object TimetableExamMapper {
    private val SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai")
    private val DATE_REGEX = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val RANGE_REGEX = Regex("""(\d{1,2}):(\d{2})\s*[-~～至]\s*(\d{1,2}):(\d{2})""")
    private val SINGLE_TIME_REGEX = Regex("""(\d{1,2}):(\d{2})""")

    fun parseExamDateTime(exam: ExamEntity): ParsedExamDateTime? {
        val raw = exam.examTimeRaw.trim()
        val dateMatch = DATE_REGEX.find(raw)
        val dateFromRaw = if (dateMatch != null) {
            runCatching {
                LocalDate.of(
                    dateMatch.groupValues[1].toInt(),
                    dateMatch.groupValues[2].toInt(),
                    dateMatch.groupValues[3].toInt()
                )
            }.getOrNull()
        } else null

        val tsZoned = exam.startTimestamp?.let {
            Instant.ofEpochMilli(it).atZone(SHANGHAI_ZONE)
        }
        val date = dateFromRaw ?: tsZoned?.toLocalDate() ?: return null

        val rangeMatch = RANGE_REGEX.find(raw)
        val (startTime, endTime) = if (rangeMatch != null) {
            val s = runCatching {
                LocalTime.of(rangeMatch.groupValues[1].toInt(), rangeMatch.groupValues[2].toInt())
            }.getOrNull()
            val e = runCatching {
                LocalTime.of(rangeMatch.groupValues[3].toInt(), rangeMatch.groupValues[4].toInt())
            }.getOrNull()
            if (s != null && e != null && e.isAfter(s)) s to e
            else if (s != null) s to safePlusHours(s, 2)
            else return null
        } else {
            val single = SINGLE_TIME_REGEX.find(raw)
            val s = if (single != null) {
                runCatching {
                    LocalTime.of(single.groupValues[1].toInt(), single.groupValues[2].toInt())
                }.getOrNull()
            } else {
                tsZoned?.toLocalTime()
            } ?: LocalTime.of(8, 30)
            s to safePlusHours(s, 2)
        }

        return ParsedExamDateTime(date = date, startTime = startTime, endTime = endTime)
    }

    fun mapExamsForWeek(
        exams: List<ExamEntity>,
        semesterStart: LocalDate,
        weekNumber: Int,
        periods: List<ClassPeriodTime>
    ): List<TimetableExamBlock> {
        if (exams.isEmpty() || periods.isEmpty()) return emptyList()
        val weekStart = semesterStart.plusWeeks((weekNumber - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        return exams.mapIndexedNotNull { idx, exam ->
            val parsed = parseExamDateTime(exam) ?: return@mapIndexedNotNull null
            if (parsed.date.isBefore(weekStart) || parsed.date.isAfter(weekEnd)) {
                return@mapIndexedNotNull null
            }
            val (startSec, endSec) = mapTimeRangeToSections(parsed.startTime, parsed.endTime, periods)
            val gradientIdx = abs((exam.courseCode.ifBlank { exam.courseName }).hashCode() + idx)
            TimetableExamBlock(
                exam = exam,
                weekNumber = weekNumber,
                dayOfWeek = parsed.date.dayOfWeek.value,
                startSection = startSec,
                endSection = endSec,
                date = parsed.date,
                timeRangeText = parsed.timeRangeText,
                gradientIndex = gradientIdx
            )
        }.sortedWith(compareBy<TimetableExamBlock> { it.dayOfWeek }.thenBy { it.startSection })
    }

    fun mapTimeRangeToSections(
        startTime: LocalTime,
        endTime: LocalTime,
        periods: List<ClassPeriodTime>
    ): Pair<Int, Int> {
        if (periods.isEmpty()) return 1 to 2

        val overlapping = periods.filter { p ->
            p.start.isBefore(endTime) && p.end.isAfter(startTime)
        }
        if (overlapping.isNotEmpty()) {
            return overlapping.first().section to overlapping.last().section
        }

        // Exam falls in a break (e.g., noon 12:00-14:00): snap to nearest period & span ~2 sections
        val startMinutes = startTime.toSecondOfDay() / 60
        val nearest = periods.minByOrNull { p ->
            abs((p.start.toSecondOfDay() / 60) - startMinutes)
        } ?: periods.first()

        val durationMins = ChronoUnit.MINUTES.between(startTime, endTime).coerceAtLeast(45L)
        val span = ((durationMins + 44L) / 55L).toInt().coerceIn(1, 3)
        val maxSec = periods.last().section
        val endSec = (nearest.section + span - 1).coerceAtMost(maxSec)
        return nearest.section to endSec
    }

    private fun safePlusHours(time: LocalTime, hours: Long): LocalTime {
        val next = time.plusHours(hours)
        return if (next.isAfter(time)) next else LocalTime.of(23, 59)
    }
}

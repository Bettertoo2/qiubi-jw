package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.CustomScheduleEventEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TimetableStickyEventBlock(
    val event: CustomScheduleEventEntity,
    val displayDate: LocalDate,
    val weekNumber: Int,
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val startSection: Int,
    val endSection: Int,
    val timeRangeText: String
)

/**
 * Maps user-created custom sticky-note schedule events onto exact week/day/period slots.
 * Supports both single-date events and weekly recurring events starting from `event.dateIso`.
 */
object TimetableEventMapper {
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun mapEventsForWeek(
        events: List<CustomScheduleEventEntity>,
        semesterStart: LocalDate,
        weekNumber: Int,
        periods: List<ClassPeriodTime>
    ): List<TimetableStickyEventBlock> {
        if (events.isEmpty() || periods.isEmpty()) return emptyList()
        val weekStart = semesterStart.plusWeeks((weekNumber - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        return events.mapNotNull { event ->
            val anchorDate = runCatching { LocalDate.parse(event.dateIso.trim()) }.getOrNull()
                ?: return@mapNotNull null

            val displayDate: LocalDate = if (event.repeatWeekly) {
                val candidate = weekStart.plusDays((anchorDate.dayOfWeek.value - 1).toLong())
                if (candidate.isBefore(anchorDate)) return@mapNotNull null
                candidate
            } else {
                if (anchorDate.isBefore(weekStart) || anchorDate.isAfter(weekEnd)) return@mapNotNull null
                anchorDate
            }

            val startTime = parseTime(event.startTime) ?: LocalTime.of(8, 30)
            val endTime = parseTime(event.endTime)?.takeIf { it.isAfter(startTime) }
                ?: startTime.plusHours(1).let { if (it.isAfter(startTime)) it else LocalTime.of(23, 59) }

            val (startSec, endSec) = TimetableExamMapper.mapTimeRangeToSections(startTime, endTime, periods)
            TimetableStickyEventBlock(
                event = event,
                displayDate = displayDate,
                weekNumber = weekNumber,
                dayOfWeek = displayDate.dayOfWeek.value,
                startSection = startSec,
                endSection = endSec,
                timeRangeText = "${startTime.format(TIME_FMT)}-${endTime.format(TIME_FMT)}"
            )
        }.sortedWith(compareBy<TimetableStickyEventBlock> { it.dayOfWeek }.thenBy { it.startSection }.thenBy { it.event.id })
    }

    private fun parseTime(raw: String): LocalTime? {
        val parts = raw.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }
}

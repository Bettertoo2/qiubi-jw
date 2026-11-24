package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.CourseEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ClassPeriodTime(
    val section: Int,
    val start: LocalTime,
    val end: LocalTime
) {
    val startText: String get() = start.format(TIME_FMT)
    val endText: String get() = end.format(TIME_FMT)

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class SchedulePreset(
    val id: String,
    val name: String,
    val description: String,
    val periods: List<ClassPeriodTime>
)

data class LiveCourseStatus(
    val currentCourse: CourseEntity?,
    val nextCourse: CourseEntity?,
    val todayCourses: List<CourseEntity>
)

/**
 * Official Guangdong Ocean University (广东海洋大学) bell schedules.
 * Default matches GDOU's current standard schedule (第1节 08:10 起, 第3节 10:15, 第5节 14:30, 第7节 16:30, 第9节 19:30),
 * with presets for 广学楼/明德楼 and 08:00/19:10 schedules.
 */
object GdouPeriodSchedule {
    const val PRESET_GDOU_STANDARD = "GDOU_STANDARD"
    const val PRESET_GUANGXUE_MINGDE = "GUANGXUE_MINGDE"
    const val PRESET_EARLY_800 = "EARLY_800"

    val gdouStandardPeriods: List<ClassPeriodTime> = listOf(
        ClassPeriodTime(1, LocalTime.of(8, 10), LocalTime.of(8, 55)),
        ClassPeriodTime(2, LocalTime.of(9, 0), LocalTime.of(9, 45)),
        ClassPeriodTime(3, LocalTime.of(10, 15), LocalTime.of(11, 0)),
        ClassPeriodTime(4, LocalTime.of(11, 5), LocalTime.of(11, 50)),
        ClassPeriodTime(5, LocalTime.of(14, 30), LocalTime.of(15, 15)),
        ClassPeriodTime(6, LocalTime.of(15, 20), LocalTime.of(16, 5)),
        ClassPeriodTime(7, LocalTime.of(16, 30), LocalTime.of(17, 15)),
        ClassPeriodTime(8, LocalTime.of(17, 20), LocalTime.of(18, 5)),
        ClassPeriodTime(9, LocalTime.of(19, 30), LocalTime.of(20, 15)),
        ClassPeriodTime(10, LocalTime.of(20, 25), LocalTime.of(21, 10)),
        ClassPeriodTime(11, LocalTime.of(21, 15), LocalTime.of(22, 0)),
        ClassPeriodTime(12, LocalTime.of(22, 5), LocalTime.of(22, 50))
    )

    val guangxueMingdePeriods: List<ClassPeriodTime> = listOf(
        ClassPeriodTime(1, LocalTime.of(8, 10), LocalTime.of(8, 55)),
        ClassPeriodTime(2, LocalTime.of(9, 0), LocalTime.of(9, 45)),
        ClassPeriodTime(3, LocalTime.of(10, 5), LocalTime.of(10, 50)),
        ClassPeriodTime(4, LocalTime.of(10, 50), LocalTime.of(11, 35)),
        ClassPeriodTime(5, LocalTime.of(14, 30), LocalTime.of(15, 15)),
        ClassPeriodTime(6, LocalTime.of(15, 20), LocalTime.of(16, 5)),
        ClassPeriodTime(7, LocalTime.of(16, 25), LocalTime.of(17, 10)),
        ClassPeriodTime(8, LocalTime.of(17, 10), LocalTime.of(17, 55)),
        ClassPeriodTime(9, LocalTime.of(19, 30), LocalTime.of(20, 15)),
        ClassPeriodTime(10, LocalTime.of(20, 25), LocalTime.of(21, 10)),
        ClassPeriodTime(11, LocalTime.of(21, 15), LocalTime.of(22, 0)),
        ClassPeriodTime(12, LocalTime.of(22, 5), LocalTime.of(22, 50))
    )

    val early800Periods: List<ClassPeriodTime> = listOf(
        ClassPeriodTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        ClassPeriodTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
        ClassPeriodTime(3, LocalTime.of(10, 10), LocalTime.of(10, 55)),
        ClassPeriodTime(4, LocalTime.of(11, 0), LocalTime.of(11, 45)),
        ClassPeriodTime(5, LocalTime.of(14, 30), LocalTime.of(15, 15)),
        ClassPeriodTime(6, LocalTime.of(15, 20), LocalTime.of(16, 5)),
        ClassPeriodTime(7, LocalTime.of(16, 20), LocalTime.of(17, 5)),
        ClassPeriodTime(8, LocalTime.of(17, 10), LocalTime.of(17, 55)),
        ClassPeriodTime(9, LocalTime.of(19, 10), LocalTime.of(19, 55)),
        ClassPeriodTime(10, LocalTime.of(20, 0), LocalTime.of(20, 45)),
        ClassPeriodTime(11, LocalTime.of(20, 55), LocalTime.of(21, 40)),
        ClassPeriodTime(12, LocalTime.of(21, 45), LocalTime.of(22, 30))
    )

    val presets: List<SchedulePreset> = listOf(
        SchedulePreset(
            id = PRESET_GDOU_STANDARD,
            name = "标准作息",
            description = "08:10 / 10:15 / 14:30 / 16:30 / 19:30",
            periods = gdouStandardPeriods
        ),
        SchedulePreset(
            id = PRESET_GUANGXUE_MINGDE,
            name = "紧凑作息",
            description = "08:10 / 10:05 / 14:30 / 16:25 / 19:30",
            periods = guangxueMingdePeriods
        ),
        SchedulePreset(
            id = PRESET_EARLY_800,
            name = "早8:00 / 晚19:10",
            description = "08:00 / 10:10 / 14:30 / 16:20 / 19:10",
            periods = early800Periods
        )
    )

    val periods: List<ClassPeriodTime> get() = gdouStandardPeriods

    fun periodsFor(presetId: String): List<ClassPeriodTime> =
        presets.find { it.id == presetId }?.periods ?: gdouStandardPeriods

    fun periodFor(section: Int, presetId: String = PRESET_GDOU_STANDARD): ClassPeriodTime {
        val list = periodsFor(presetId)
        return list.find { it.section == section } ?: list.last()
    }

    fun timeRangeFor(
        startSection: Int,
        endSection: Int,
        presetId: String = PRESET_GDOU_STANDARD
    ): String {
        val s = periodFor(startSection, presetId)
        val e = periodFor(endSection, presetId)
        return "${s.startText}-${e.endText}"
    }

    fun timeRangeForCourse(
        course: CourseEntity,
        presetId: String = PRESET_GDOU_STANDARD
    ): String {
        val isGuangxueOrMingde = course.classroom.contains("广学") || course.classroom.contains("明德")
        val effectivePreset = if (presetId == PRESET_GDOU_STANDARD && isGuangxueOrMingde) {
            PRESET_GUANGXUE_MINGDE
        } else {
            presetId
        }
        return timeRangeFor(course.startSection, course.endSection, effectivePreset)
    }

    fun computeLiveStatus(
        courses: List<CourseEntity>,
        currentWeek: Int,
        todayDayOfWeek: Int,
        presetId: String = PRESET_GDOU_STANDARD,
        nowTime: LocalTime = LocalTime.now()
    ): LiveCourseStatus {
        val today = courses
            .filter { it.dayOfWeek == todayDayOfWeek && it.isActiveInWeek(currentWeek) }
            .sortedBy { it.startSection }

        val current = today.firstOrNull { course ->
            val start = periodFor(course.startSection, presetId).start
            val end = periodFor(course.endSection, presetId).end
            !nowTime.isBefore(start) && !nowTime.isAfter(end)
        }

        val next = today.firstOrNull { course ->
            val start = periodFor(course.startSection, presetId).start
            nowTime.isBefore(start)
        }

        return LiveCourseStatus(
            currentCourse = current,
            nextCourse = next,
            todayCourses = today
        )
    }

    val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
}

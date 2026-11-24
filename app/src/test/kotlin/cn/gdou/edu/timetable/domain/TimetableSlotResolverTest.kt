package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSlotResolverTest {

    private fun course(
        name: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: List<Int>
    ) = CourseEntity(
        xnm = "2026",
        xqm = "3",
        courseCode = name,
        courseName = name,
        teacher = "老师",
        classroom = "教学楼101",
        campus = "湖光校区",
        dayOfWeek = day,
        startSection = start,
        endSection = end,
        weeksRaw = "${weeks.firstOrNull()}-${weeks.lastOrNull()}周",
        weeksCsv = weeks.joinToString(","),
        credits = "2.0",
        courseNature = "必修",
        classGroup = "01",
        assessmentType = "考试",
        colorIndex = 0
    )

    @Test
    fun activeCourseHidesOverlappingNonCurrentWeekCoursesFromGrid() {
        val active = course("数值分析", day = 4, start = 3, end = 4, weeks = listOf(10, 11, 12, 13))
        val oldInactive = course("工程力学", day = 4, start = 3, end = 4, weeks = listOf(1, 2, 3, 4))

        val blocks = TimetableSlotResolver.resolve(
            courses = listOf(active, oldInactive),
            selectedWeek = 12,
            showNonCurrentWeek = true
        )

        assertEquals(1, blocks.size)
        assertTrue(blocks.first().isActiveInSelectedWeek)
        assertEquals("数值分析", blocks.first().primary.courseName)
        assertEquals(1, blocks.first().extraCourses.size)
        assertEquals("工程力学", blocks.first().extraCourses.first().courseName)
    }

    @Test
    fun multipleNonCurrentWeekCoursesInSameSlotCollapseToSingleCleanCard() {
        val earlier = course("船舶静力学", day = 2, start = 3, end = 4, weeks = listOf(1, 2, 3, 4))
        val upcoming = course("数值分析实验", day = 2, start = 3, end = 4, weeks = listOf(14, 15))

        val blocks = TimetableSlotResolver.resolve(
            courses = listOf(earlier, upcoming),
            selectedWeek = 12,
            showNonCurrentWeek = true
        )

        assertEquals(1, blocks.size)
        assertFalse(blocks.first().isActiveInSelectedWeek)
        // Prefers upcoming week 14 over already-past week 4
        assertEquals("数值分析实验", blocks.first().primary.courseName)
        assertEquals(1, blocks.first().extraCourses.size)
        assertEquals("船舶静力学", blocks.first().extraCourses.first().courseName)
    }

    @Test
    fun whenShowNonCurrentWeekDisabled_onlyActiveCoursesAreReturned() {
        val active = course("海洋工程基础", day = 1, start = 7, end = 8, weeks = listOf(12))
        val inactive = course("船舶材料", day = 1, start = 1, end = 2, weeks = listOf(1, 2))

        val blocks = TimetableSlotResolver.resolve(
            courses = listOf(active, inactive),
            selectedWeek = 12,
            showNonCurrentWeek = false
        )

        assertEquals(1, blocks.size)
        assertEquals("海洋工程基础", blocks.first().primary.courseName)
        assertTrue(blocks.first().extraCourses.isEmpty())
    }

    @Test
    fun gdouStandardSchedule_matchesOfficialPeriodTimes() {
        assertEquals("08:10-09:45", GdouPeriodSchedule.timeRangeFor(1, 2))
        assertEquals("10:15-11:50", GdouPeriodSchedule.timeRangeFor(3, 4))
        assertEquals("14:30-16:05", GdouPeriodSchedule.timeRangeFor(5, 6))
        assertEquals("16:30-18:05", GdouPeriodSchedule.timeRangeFor(7, 8))
        assertEquals("19:30-21:10", GdouPeriodSchedule.timeRangeFor(9, 10))
    }
}

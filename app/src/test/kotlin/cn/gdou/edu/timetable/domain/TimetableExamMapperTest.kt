package cn.gdou.edu.timetable.domain

import androidx.compose.ui.graphics.Color
import cn.gdou.edu.timetable.data.local.db.CustomScheduleEventEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.ui.theme.colorToHex
import cn.gdou.edu.timetable.ui.theme.hslToColor
import cn.gdou.edu.timetable.ui.theme.parseHexToArgb
import cn.gdou.edu.timetable.ui.theme.rgbToHsl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimetableExamMapperTest {

    @Test
    fun parseExamDateTime_handlesStandardAndCustomFormats() {
        val exam = sampleExam(raw = "2026-11-25(14:30-16:30)")
        val parsed = TimetableExamMapper.parseExamDateTime(exam)
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 11, 25), parsed!!.date)
        assertEquals(LocalTime.of(14, 30), parsed.startTime)
        assertEquals(LocalTime.of(16, 30), parsed.endTime)
        assertEquals("14:30-16:30", parsed.timeRangeText)
    }

    @Test
    fun mapTimeRangeToSections_mapsMorningAfternoonEveningAndNoonBreak() {
        val periods = GdouPeriodSchedule.gdouStandardPeriods.take(10)

        // 08:30-10:30 overlaps 1(08:10-08:55), 2(09:00-09:45), 3(10:15-11:00)
        assertEquals(1 to 3, TimetableExamMapper.mapTimeRangeToSections(LocalTime.of(8, 30), LocalTime.of(10, 30), periods))

        // 14:30-16:30 overlaps 5(14:30-15:15), 6(15:20-16:05); does not overlap 7(starts 16:30)
        assertEquals(5 to 6, TimetableExamMapper.mapTimeRangeToSections(LocalTime.of(14, 30), LocalTime.of(16, 30), periods))

        // 19:30-21:30 overlaps 9(19:30-20:15), 10(20:25-21:10)
        assertEquals(9 to 10, TimetableExamMapper.mapTimeRangeToSections(LocalTime.of(19, 30), LocalTime.of(21, 30), periods))

        // Lunch break 12:30-14:00 snaps safely inside 1..10
        val (s, e) = TimetableExamMapper.mapTimeRangeToSections(LocalTime.of(12, 30), LocalTime.of(14, 0), periods)
        assertTrue(s in 1..10 && e in s..10)
    }

    @Test
    fun mapExamsForWeek_filtersToMatchingWeekAndComputesDayOfWeek() {
        val semesterStart = LocalDate.of(2026, 8, 31) // Monday of Week 1
        val week13Wed = semesterStart.plusWeeks(12).plusDays(2) // Wednesday of Week 13
        val week14Fri = semesterStart.plusWeeks(13).plusDays(4) // Friday of Week 14

        val exams = listOf(
            sampleExam(name = "高等数学", raw = "$week13Wed(08:30-10:30)", isCustom = true),
            sampleExam(name = "数据结构", raw = "$week14Fri(14:30-16:30)", isCustom = false)
        )
        val periods = GdouPeriodSchedule.gdouStandardPeriods.take(10)

        val w13 = TimetableExamMapper.mapExamsForWeek(exams, semesterStart, 13, periods)
        assertEquals(1, w13.size)
        assertEquals("高等数学", w13[0].exam.courseName)
        assertEquals(3, w13[0].dayOfWeek)
        assertEquals(1, w13[0].startSection)
        assertEquals(3, w13[0].endSection)

        val w14 = TimetableExamMapper.mapExamsForWeek(exams, semesterStart, 14, periods)
        assertEquals(1, w14.size)
        assertEquals("数据结构", w14[0].exam.courseName)
        assertEquals(5, w14[0].dayOfWeek)
        assertEquals(5, w14[0].startSection)
        assertEquals(6, w14[0].endSection)

        val w12 = TimetableExamMapper.mapExamsForWeek(exams, semesterStart, 12, periods)
        assertTrue(w12.isEmpty())
    }

    @Test
    fun themeColorHelpers_roundTripHexAndHsl() {
        assertEquals("#7B52D3", colorToHex(0xFF7B52D3L))
        assertEquals(0xFF7B52D3L, parseHexToArgb("#7b52d3"))
        assertEquals(0xFF005A7DL, parseHexToArgb("005A7D"))
        assertNull(parseHexToArgb("12345"))
        assertNull(parseHexToArgb("#ZZZZZZ"))

        val original = Color(0xFF7B52D3)
        val (h, s, l) = rgbToHsl(original)
        val rebuilt = hslToColor(h, s, l)
        assertEquals(original.red, rebuilt.red, 0.01f)
        assertEquals(original.green, rebuilt.green, 0.01f)
        assertEquals(original.blue, rebuilt.blue, 0.01f)
    }

    @Test
    fun timetableEventMapper_handlesSingleAndWeeklyRecurringStickyNotes() {
        val semesterStart = LocalDate.of(2026, 8, 31) // Week 1 Monday
        val week13Tue = semesterStart.plusWeeks(12).plusDays(1) // Tuesday of Week 13
        val week13Thu = semesterStart.plusWeeks(12).plusDays(3) // Thursday of Week 13

        val events = listOf(
            CustomScheduleEventEntity(
                id = 1L,
                title = "图书馆自习",
                dateIso = week13Tue.toString(),
                startTime = "14:30",
                endTime = "16:05",
                location = "图书馆3F",
                stickyColorIndex = 0,
                repeatWeekly = false
            ),
            CustomScheduleEventEntity(
                id = 2L,
                title = "社团例会",
                dateIso = week13Thu.toString(),
                startTime = "19:30",
                endTime = "21:10",
                location = "活动中心",
                stickyColorIndex = 2,
                repeatWeekly = true
            )
        )
        val periods = GdouPeriodSchedule.gdouStandardPeriods.take(10)

        // Week 12 (before anchor date): neither event appears
        assertTrue(TimetableEventMapper.mapEventsForWeek(events, semesterStart, 12, periods).isEmpty())

        // Week 13: both appear on exact days & sections
        val w13 = TimetableEventMapper.mapEventsForWeek(events, semesterStart, 13, periods)
        assertEquals(2, w13.size)
        assertEquals("图书馆自习", w13[0].event.title)
        assertEquals(2, w13[0].dayOfWeek)
        assertEquals(5, w13[0].startSection)
        assertEquals(6, w13[0].endSection)
        assertEquals("社团例会", w13[1].event.title)
        assertEquals(4, w13[1].dayOfWeek)
        assertEquals(9, w13[1].startSection)
        assertEquals(10, w13[1].endSection)

        // Week 14: only weekly recurring sticky note repeats
        val w14 = TimetableEventMapper.mapEventsForWeek(events, semesterStart, 14, periods)
        assertEquals(1, w14.size)
        assertEquals("社团例会", w14[0].event.title)
        assertEquals(week13Thu.plusWeeks(1), w14[0].displayDate)
    }

    private fun sampleExam(
        name: String = "数据结构",
        raw: String,
        isCustom: Boolean = false
    ) = ExamEntity(
        xnm = "2026",
        xqm = "3",
        courseCode = "CS301",
        courseName = name,
        examName = "期末考试",
        examTimeRaw = raw,
        startTimestamp = null,
        classroom = "钟海楼02020",
        campus = "湖光校区",
        seatNumber = "12",
        credits = "4.0",
        examMode = "闭卷笔试",
        classGroup = "软件1242",
        isCustom = isCustom
    )
}

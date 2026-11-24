package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDiffEngineAndGpaTest {

    @Test
    fun diffEngine_detectsCourseExamGradeAndProfileChanges() {
        val oldProfile = StudentProfileEntity(
            studentId = "2024001",
            name = "张三",
            college = "数计学院",
            major = "软件工程",
            className = "软件1241",
            officialGpa = 3.60
        )
        val newProfile = oldProfile.copy(officialGpa = 3.72)

        val oldCourse = CourseEntity(
            xnm = "2026", xqm = "3", courseCode = "CS1", courseName = "数据结构",
            teacher = "王老师", classroom = "广学楼301", campus = "湖光校区",
            dayOfWeek = 1, startSection = 1, endSection = 2,
            weeksRaw = "1-16周", weeksCsv = "1,2,3", credits = "4.0",
            courseNature = "必修", classGroup = "01", assessmentType = "考试", colorIndex = 0
        )
        val movedCourse = oldCourse.copy(classroom = "钟海楼02010")

        val oldExams = emptyList<ExamEntity>()
        val newExams = listOf(
            ExamEntity(
                xnm = "2026", xqm = "3", courseCode = "CS1", courseName = "数据结构",
                examName = "期末考试", examTimeRaw = "2026-12-28(08:30-10:30)",
                startTimestamp = 1798417800000L, classroom = "钟海楼02010",
                campus = "湖光校区", seatNumber = "12", credits = "4.0",
                examMode = "闭卷", classGroup = "01"
            )
        )

        val oldGrades = listOf(
            GradeEntity(
                xnm = "2025", xqm = "3", yearLabel = "2025-2026", termLabel = "1",
                courseCode = "MA1", courseName = "高数", credits = 4.0,
                scoreRaw = "88", numericScore = 88.0, gradePoint = 3.8,
                courseNature = "必修", examNature = "正常考试", teacher = "林老师", invalidFlag = false
            )
        )
        val newGrades = oldGrades + GradeEntity(
            xnm = "2026", xqm = "3", yearLabel = "2026-2027", termLabel = "1",
            courseCode = "CS1", courseName = "数据结构", credits = 4.0,
            scoreRaw = "95", numericScore = 95.0, gradePoint = 4.5,
            courseNature = "必修", examNature = "正常考试", teacher = "王老师", invalidFlag = false
        )

        // Identical snapshot should report no changes
        val noDiff = DataDiffEngine.computeDiff(
            oldProfile, oldProfile,
            listOf(oldCourse), listOf(oldCourse),
            oldExams, oldExams,
            oldGrades, oldGrades
        )
        assertFalse(noDiff.hasChanges)

        // Updated snapshot should report all 4 categories of changes
        val diff = DataDiffEngine.computeDiff(
            oldProfile, newProfile,
            listOf(oldCourse), listOf(movedCourse),
            oldExams, newExams,
            oldGrades, newGrades
        )
        assertTrue(diff.hasChanges)
        assertTrue(diff.profileChanged)
        assertEquals(1, diff.courseChangeCount)
        assertEquals(1, diff.examChangeCount)
        assertEquals(1, diff.gradeChangeCount)
        assertTrue(diff.summary.contains("课表变动1处"))
        assertTrue(diff.summary.contains("考试变动1处"))
        assertTrue(diff.summary.contains("成绩变动1门"))
    }

    @Test
    fun gpaCalculator_computesCreditWeightedGpaAndIgnoresInvalidGrades() {
        val grades = listOf(
            GradeEntity(
                xnm = "2025", xqm = "3", yearLabel = "2025-2026", termLabel = "1",
                courseCode = "A", courseName = "高数", credits = 4.0,
                scoreRaw = "90", numericScore = 90.0, gradePoint = 4.0,
                courseNature = "必修", examNature = "正常考试", teacher = "A", invalidFlag = false
            ),
            GradeEntity(
                xnm = "2025", xqm = "12", yearLabel = "2025-2026", termLabel = "2",
                courseCode = "B", courseName = "线代", credits = 2.0,
                scoreRaw = "80", numericScore = 80.0, gradePoint = 3.0,
                courseNature = "必修", examNature = "正常考试", teacher = "B", invalidFlag = false
            ),
            GradeEntity(
                xnm = "2025", xqm = "12", yearLabel = "2025-2026", termLabel = "2",
                courseCode = "C", courseName = "作废课", credits = 3.0,
                scoreRaw = "40", numericScore = 40.0, gradePoint = 0.0,
                courseNature = "选修", examNature = "正常考试", teacher = "C", invalidFlag = true
            )
        )

        val summary = GpaCalculator.summarize(grades)
        // (4*4.0 + 2*3.0) / 6.0 = 22 / 6 = 3.6667
        assertEquals(3.6667, summary.weightedGpa, 0.001)
        assertEquals(6.0, summary.totalCreditsEarned, 0.001)
        assertEquals(2, summary.totalCourseCount)
        assertEquals(2, summary.termPoints.size)
        assertEquals(4.0, summary.termPoints[0].gpa, 0.001)
        assertEquals(3.0, summary.termPoints[1].gpa, 0.001)
    }
}

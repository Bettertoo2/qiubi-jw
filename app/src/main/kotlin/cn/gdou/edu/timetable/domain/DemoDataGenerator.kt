package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import cn.gdou.edu.timetable.data.remote.GdouParsers
import cn.gdou.edu.timetable.data.remote.GradeScoreComponent
import cn.gdou.edu.timetable.data.remote.ParsedTermMetadata
import cn.gdou.edu.timetable.data.remote.RemoteStudentBundle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Optional local preview dataset for testing the UI, local database replacement,
 * and change-detection notifications without exposing real credentials.
 */
object DemoDataGenerator {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val examDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun buildBundle(revision: Int): RemoteStudentBundle {
        val xnm = AppSettings.defaultXnm()
        val xqm = AppSettings.defaultXqm()
        val prevXnm = ((xnm.toIntOrNull() ?: 2026) - 1).toString()

        val profile = StudentProfileEntity(
            studentId = "202411701208",
            name = "林同学（演示）",
            gender = "男",
            college = "数学与计算机学院",
            major = "软件工程",
            className = "软件1242",
            grade = "2024",
            campus = "主校区",
            studentStatus = "在籍",
            officialGpa = if (revision % 2 == 0) 3.76 else 3.81,
            officialEarnedCredits = if (revision % 2 == 0) 64.5 else 67.5,
            currentXnm = xnm,
            currentXqm = xqm,
            updatedAt = System.currentTimeMillis()
        )

        val allWeeks = (1..16).joinToString(",")
        val oddWeeks = (1..15 step 2).joinToString(",")
        val evenWeeks = (2..16 step 2).joinToString(",")

        val roomVariant = if (revision % 2 == 0) "教学楼A304" else "教学楼B215（调课）"
        val courses = listOf(
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS301",
                courseName = "数据结构与算法", teacher = "陈教授",
                classroom = roomVariant, campus = "主校区",
                dayOfWeek = 1, startSection = 1, endSection = 2,
                weeksRaw = "1-16周", weeksCsv = allWeeks,
                credits = "4.0", courseNature = "专业必修", classGroup = "软件1242-01",
                assessmentType = "考试", colorIndex = 0
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS305",
                courseName = "计算机网络", teacher = "王老师",
                classroom = "教学楼B318", campus = "主校区",
                dayOfWeek = 1, startSection = 5, endSection = 6,
                weeksRaw = "1-16周", weeksCsv = allWeeks,
                credits = "3.5", courseNature = "专业必修", classGroup = "软件1242-01",
                assessmentType = "考试", colorIndex = 2
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "SE210",
                courseName = "移动应用开发(Android)", teacher = "李老师",
                classroom = "实验楼402机房", campus = "主校区",
                dayOfWeek = 2, startSection = 3, endSection = 4,
                weeksRaw = "1-16周", weeksCsv = allWeeks,
                credits = "3.0", courseNature = "专业核心", classGroup = "软件1242-01",
                assessmentType = "考查", colorIndex = 4
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS302",
                courseName = "操作系统原理", teacher = "张副教授",
                classroom = "教学楼A210", campus = "主校区",
                dayOfWeek = 3, startSection = 1, endSection = 2,
                weeksRaw = "1-16周", weeksCsv = allWeeks,
                credits = "3.5", courseNature = "专业必修", classGroup = "软件1242-01",
                assessmentType = "考试", colorIndex = 6
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "GE101",
                courseName = "自然科学导论", teacher = "周老师",
                classroom = "主楼多功能厅", campus = "主校区",
                dayOfWeek = 3, startSection = 9, endSection = 10,
                weeksRaw = "1-15周(单)", weeksCsv = oddWeeks,
                credits = "2.0", courseNature = "通识选修", classGroup = "通识-03",
                assessmentType = "考查", colorIndex = 8
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS301",
                courseName = "数据结构与算法", teacher = "陈教授",
                classroom = "教学楼A304", campus = "主校区",
                dayOfWeek = 4, startSection = 3, endSection = 4,
                weeksRaw = "1-16周", weeksCsv = allWeeks,
                credits = "4.0", courseNature = "专业必修", classGroup = "软件1242-01",
                assessmentType = "考试", colorIndex = 0
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "DB204",
                courseName = "数据库系统概论", teacher = "黄老师",
                classroom = "教学楼B412", campus = "主校区",
                dayOfWeek = 5, startSection = 1, endSection = 2,
                weeksRaw = "1-16周", weeksCsv = allWeeks,
                credits = "3.0", courseNature = "专业必修", classGroup = "软件1242-01",
                assessmentType = "考试", colorIndex = 10
            ),
            CourseEntity(
                xnm = xnm, xqm = xqm, courseCode = "PE104",
                courseName = "大学体育(游泳)", teacher = "刘教练",
                classroom = "体育中心游泳馆", campus = "主校区",
                dayOfWeek = 5, startSection = 7, endSection = 8,
                weeksRaw = "2-16周(双)", weeksCsv = evenWeeks,
                credits = "1.0", courseNature = "公共必修", classGroup = "体育-12",
                assessmentType = "考查", colorIndex = 3
            )
        )

        val today = LocalDate.now()
        fun examAt(daysFromNow: Long, hour: Int, minute: Int): Pair<String, Long> {
            val d = today.plusDays(daysFromNow)
            val dt = LocalDateTime.of(d.year, d.monthValue, d.dayOfMonth, hour, minute)
            val endHour = hour + 2
            val raw = "${d.format(examDateFmt)}(${String.format("%02d:%02d", hour, minute)}-${String.format("%02d:%02d", endHour, minute)})"
            return raw to dt.atZone(zone).toInstant().toEpochMilli()
        }

        val (e1Raw, e1Ts) = examAt(1, 14, 30)
        val (e2Raw, e2Ts) = examAt(3, 8, 30)
        val (e3Raw, e3Ts) = examAt(14, 9, 0)
        val exams = mutableListOf(
            ExamEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS301",
                courseName = "数据结构与算法", examName = "期末考试",
                examTimeRaw = e1Raw, startTimestamp = e1Ts,
                classroom = "教学楼B220", campus = "主校区",
                seatNumber = "18", credits = "4.0", examMode = "闭卷笔试", classGroup = "软件1242-01"
            ),
            ExamEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS302",
                courseName = "操作系统原理", examName = "期末考试",
                examTimeRaw = e2Raw, startTimestamp = e2Ts,
                classroom = "教学楼A308", campus = "主校区",
                seatNumber = "07", credits = "3.5", examMode = "闭卷笔试", classGroup = "软件1242-01"
            ),
            ExamEntity(
                xnm = xnm, xqm = xqm, courseCode = "CS305",
                courseName = "计算机网络", examName = "期末考试",
                examTimeRaw = e3Raw, startTimestamp = e3Ts,
                classroom = "教学楼B311", campus = "主校区",
                seatNumber = "32", credits = "3.5", examMode = "闭卷笔试", classGroup = "软件1242-01"
            )
        )
        if (revision % 2 == 1) {
            val (e4Raw, e4Ts) = examAt(24, 14, 30)
            exams.add(
                ExamEntity(
                    xnm = xnm, xqm = xqm, courseCode = "DB204",
                    courseName = "数据库系统概论", examName = "期末考试",
                    examTimeRaw = e4Raw, startTimestamp = e4Ts,
                    classroom = "教学楼A105", campus = "主校区",
                    seatNumber = "22", credits = "3.0", examMode = "闭卷笔试", classGroup = "软件1242-01"
                )
            )
        }

        val prevYearLabel = "$prevXnm-${prevXnm.toInt() + 1}"
        val currYearLabel = "$xnm-${xnm.toInt() + 1}"
        val grades = mutableListOf(
            GradeEntity(
                xnm = prevXnm, xqm = "3", yearLabel = prevYearLabel, termLabel = "1",
                courseCode = "MA101", courseName = "高等数学A(上)", credits = 5.0,
                scoreRaw = "91", numericScore = 91.0, gradePoint = 4.1,
                courseNature = "公共必修", examNature = "正常考试", teacher = "林老师", invalidFlag = false,
                jxbId = "DEMO_JXB_MA101",
                breakdownJson = GdouParsers.encodeBreakdown(
                    listOf(
                        GradeScoreComponent("平时成绩", "30%", "95"),
                        GradeScoreComponent("期末成绩", "70%", "89"),
                        GradeScoreComponent("总评成绩", "", "91")
                    )
                )
            ),
            GradeEntity(
                xnm = prevXnm, xqm = "3", yearLabel = prevYearLabel, termLabel = "1",
                courseCode = "CS101", courseName = "C语言程序设计", credits = 4.0,
                scoreRaw = "94", numericScore = 94.0, gradePoint = 4.4,
                courseNature = "专业必修", examNature = "正常考试", teacher = "陈老师", invalidFlag = false,
                jxbId = "DEMO_JXB_CS101",
                breakdownJson = GdouParsers.encodeBreakdown(
                    listOf(
                        GradeScoreComponent("平时成绩", "20%", "96"),
                        GradeScoreComponent("实验成绩", "20%", "95"),
                        GradeScoreComponent("期末成绩", "60%", "93"),
                        GradeScoreComponent("总评成绩", "", "94")
                    )
                )
            ),
            GradeEntity(
                xnm = prevXnm, xqm = "12", yearLabel = prevYearLabel, termLabel = "2",
                courseCode = "MA102", courseName = "线性代数", credits = 3.0,
                scoreRaw = "86", numericScore = 86.0, gradePoint = 3.6,
                courseNature = "公共必修", examNature = "正常考试", teacher = "赵老师", invalidFlag = false,
                jxbId = "DEMO_JXB_MA102",
                breakdownJson = GdouParsers.encodeBreakdown(
                    listOf(
                        GradeScoreComponent("平时成绩", "30%", "90"),
                        GradeScoreComponent("期末成绩", "70%", "84"),
                        GradeScoreComponent("总评成绩", "", "86")
                    )
                )
            ),
            GradeEntity(
                xnm = prevXnm, xqm = "12", yearLabel = prevYearLabel, termLabel = "2",
                courseCode = "CS202", courseName = "面向对象程序设计(Java)", credits = 3.5,
                scoreRaw = "90", numericScore = 90.0, gradePoint = 4.0,
                courseNature = "专业核心", examNature = "正常考试", teacher = "吴老师", invalidFlag = false,
                jxbId = "DEMO_JXB_CS202",
                breakdownJson = GdouParsers.encodeBreakdown(
                    listOf(
                        GradeScoreComponent("平时成绩", "20%", "92"),
                        GradeScoreComponent("实验成绩", "20%", "94"),
                        GradeScoreComponent("期末成绩", "60%", "88"),
                        GradeScoreComponent("总评成绩", "", "90")
                    )
                )
            ),
            GradeEntity(
                xnm = xnm, xqm = xqm, yearLabel = currYearLabel, termLabel = if (xqm == "3") "1" else "2",
                courseCode = "GE101", courseName = "自然科学导论", credits = 2.0,
                scoreRaw = "优秀", numericScore = 95.0, gradePoint = 4.5,
                courseNature = "通识选修", examNature = "正常考试", teacher = "周老师", invalidFlag = false,
                jxbId = "DEMO_JXB_OC101",
                breakdownJson = GdouParsers.encodeBreakdown(
                    listOf(
                        GradeScoreComponent("平时成绩", "40%", "96"),
                        GradeScoreComponent("期末考查", "60%", "94"),
                        GradeScoreComponent("总评成绩", "", "优秀")
                    )
                )
            )
        )
        if (revision % 2 == 1) {
            grades.add(
                GradeEntity(
                    xnm = xnm, xqm = xqm, yearLabel = currYearLabel, termLabel = if (xqm == "3") "1" else "2",
                    courseCode = "SE210", courseName = "移动应用开发(Android)", credits = 3.0,
                    scoreRaw = "93", numericScore = 93.0, gradePoint = 4.3,
                    courseNature = "专业核心", examNature = "正常考试", teacher = "李老师", invalidFlag = false,
                    jxbId = "DEMO_JXB_SE210",
                    breakdownJson = GdouParsers.encodeBreakdown(
                        listOf(
                            GradeScoreComponent("平时成绩", "20%", "95"),
                            GradeScoreComponent("实验项目", "30%", "96"),
                            GradeScoreComponent("期末成绩", "50%", "90"),
                            GradeScoreComponent("总评成绩", "", "93")
                        )
                    )
                )
            )
        }

        return RemoteStudentBundle(
            profile = profile,
            courses = courses,
            exams = exams,
            grades = grades,
            termMetadata = ParsedTermMetadata(
                defaultXnm = xnm,
                defaultXqm = xqm,
                availableTerms = listOf(xnm to "3", xnm to "12", prevXnm to "3", prevXnm to "12")
            ),
            effectiveXnm = xnm,
            effectiveXqm = xqm
        )
    }
}

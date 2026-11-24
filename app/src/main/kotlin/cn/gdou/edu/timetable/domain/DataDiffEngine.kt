package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity

data class SyncDiffResult(
    val hasChanges: Boolean,
    val summary: String,
    val details: List<String>,
    val courseChangeCount: Int = 0,
    val examChangeCount: Int = 0,
    val gradeChangeCount: Int = 0,
    val profileChanged: Boolean = false
)

/**
 * Compares previously persisted Room snapshots with newly fetched data from jw.gdou.edu.cn
 * to identify any student profile, timetable, exam, or grade/GPA updates prior to atomic replacement.
 */
object DataDiffEngine {
    fun computeDiff(
        oldProfile: StudentProfileEntity?,
        newProfile: StudentProfileEntity,
        oldCourses: List<CourseEntity>,
        newCourses: List<CourseEntity>,
        oldExams: List<ExamEntity>,
        newExams: List<ExamEntity>,
        oldGrades: List<GradeEntity>,
        newGrades: List<GradeEntity>
    ): SyncDiffResult {
        // First-time local sync
        if (oldProfile == null && oldCourses.isEmpty() && oldExams.isEmpty() && oldGrades.isEmpty()) {
            val initialDetails = listOf(
                "已初始化本地数据：${newCourses.size} 条课表、${newExams.size} 场考试、${newGrades.size} 门成绩"
            )
            return SyncDiffResult(
                hasChanges = true,
                summary = "首次同步完成，已保存到本地",
                details = initialDetails,
                courseChangeCount = newCourses.size,
                examChangeCount = newExams.size,
                gradeChangeCount = newGrades.size,
                profileChanged = true
            )
        }

        val details = mutableListOf<String>()
        val summaryParts = mutableListOf<String>()

        // 1. Profile diff
        var profileChanged = false
        if (oldProfile != null) {
            val changedFields = mutableListOf<String>()
            if (oldProfile.name != newProfile.name && newProfile.name.isNotBlank()) changedFields += "姓名"
            if (oldProfile.college != newProfile.college && newProfile.college.isNotBlank()) changedFields += "学院"
            if (oldProfile.major != newProfile.major && newProfile.major.isNotBlank()) changedFields += "专业"
            if (oldProfile.className != newProfile.className && newProfile.className.isNotBlank()) changedFields += "班级"
            if (oldProfile.studentStatus != newProfile.studentStatus && newProfile.studentStatus.isNotBlank()) changedFields += "学籍状态"
            if (oldProfile.officialGpa != newProfile.officialGpa && newProfile.officialGpa != null) {
                changedFields += "官方绩点(${String.format("%.2f", newProfile.officialGpa)})"
            }
            if (changedFields.isNotEmpty()) {
                profileChanged = true
                summaryParts += "个人信息变动"
                details += "学籍/个人信息更新：${changedFields.joinToString("、")}"
            }
        }

        // 2. Courses diff
        val oldCourseMap = oldCourses.associateBy { it.diffKey() }
        val newCourseMap = newCourses.associateBy { it.diffKey() }
        val addedCourses = newCourseMap.filterKeys { it !in oldCourseMap }.values
        val removedCourses = oldCourseMap.filterKeys { it !in newCourseMap }.values
        val modifiedCourses = newCourseMap.filter { (k, v) ->
            val prev = oldCourseMap[k]
            prev != null && prev.diffSignature() != v.diffSignature()
        }.values

        val courseChangeCount = addedCourses.size + removedCourses.size + modifiedCourses.size
        if (courseChangeCount > 0) {
            summaryParts += "课表变动${courseChangeCount}处"
            addedCourses.take(3).forEach {
                details += "新增课程：《${it.courseName}》(${it.classroom.ifBlank { "待定教室" }})"
            }
            modifiedCourses.take(3).forEach {
                details += "课程调整：《${it.courseName}》→ ${it.classroom.ifBlank { "教室更新" }} ${it.weeksRaw}"
            }
            removedCourses.take(2).forEach {
                details += "移除课程：《${it.courseName}》"
            }
        }

        // 3. Exams diff
        val oldExamMap = oldExams.associateBy { it.diffKey() }
        val newExamMap = newExams.associateBy { it.diffKey() }
        val addedExams = newExamMap.filterKeys { it !in oldExamMap }.values
        val removedExams = oldExamMap.filterKeys { it !in newExamMap }.values
        val modifiedExams = newExamMap.filter { (k, v) ->
            val prev = oldExamMap[k]
            prev != null && prev.diffSignature() != v.diffSignature()
        }.values

        val examChangeCount = addedExams.size + removedExams.size + modifiedExams.size
        if (examChangeCount > 0) {
            summaryParts += "考试变动${examChangeCount}处"
            addedExams.take(3).forEach {
                details += "新增考试：《${it.courseName}》${it.examTimeRaw} ${it.classroom}"
            }
            modifiedExams.take(3).forEach {
                details += "考试调整：《${it.courseName}》${it.examTimeRaw} ${it.classroom} 座号${it.seatNumber}"
            }
            removedExams.take(2).forEach {
                details += "取消考试：《${it.courseName}》"
            }
        }

        // 4. Grades & GPA diff
        val oldGradeMap = oldGrades.associateBy { it.diffKey() }
        val newGradeMap = newGrades.associateBy { it.diffKey() }
        val addedGrades = newGradeMap.filterKeys { it !in oldGradeMap }.values
        val removedGrades = oldGradeMap.filterKeys { it !in newGradeMap }.values
        val modifiedGrades = newGradeMap.filter { (k, v) ->
            val prev = oldGradeMap[k]
            prev != null && prev.diffSignature() != v.diffSignature()
        }.values

        val gradeChangeCount = addedGrades.size + removedGrades.size + modifiedGrades.size
        if (gradeChangeCount > 0) {
            summaryParts += "成绩变动${gradeChangeCount}门"
            addedGrades.take(4).forEach {
                details += "新出成绩：《${it.courseName}》${it.scoreRaw}（绩点 ${it.gradePoint}）"
            }
            modifiedGrades.take(3).forEach {
                details += "成绩更新：《${it.courseName}》→ ${it.scoreRaw}（绩点 ${it.gradePoint}）"
            }
            removedGrades.take(2).forEach {
                details += "成绩变更：《${it.courseName}》已移除"
            }
        }

        val hasChanges = profileChanged || courseChangeCount > 0 || examChangeCount > 0 || gradeChangeCount > 0
        val summary = if (hasChanges) {
            "数据已更新：${summaryParts.joinToString("，")}"
        } else {
            "数据已是最新，无变动"
        }

        return SyncDiffResult(
            hasChanges = hasChanges,
            summary = summary,
            details = details,
            courseChangeCount = courseChangeCount,
            examChangeCount = examChangeCount,
            gradeChangeCount = gradeChangeCount,
            profileChanged = profileChanged
        )
    }
}

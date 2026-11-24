package cn.gdou.edu.timetable.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "student_profile")
data class StudentProfileEntity(
    @PrimaryKey val studentId: String,
    val name: String,
    val gender: String = "",
    val college: String = "",
    val major: String = "",
    val className: String = "",
    val grade: String = "",
    val campus: String = "",
    val studentStatus: String = "在籍",
    val officialGpa: Double? = null,
    val officialEarnedCredits: Double? = null,
    val currentXnm: String = "",
    val currentXqm: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val xnm: String,
    val xqm: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val classroom: String,
    val campus: String,
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val startSection: Int,
    val endSection: Int,
    val weeksRaw: String,
    val weeksCsv: String,
    val credits: String,
    val courseNature: String,
    val classGroup: String,
    val assessmentType: String,
    val colorIndex: Int
) {
    fun weekSet(): Set<Int> {
        if (weeksCsv.isBlank()) return emptySet()
        return weeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun isActiveInWeek(week: Int): Boolean {
        val set = weekSet()
        return set.isEmpty() || week in set
    }

    /** Stable logical fingerprint for change detection across refreshes. */
    fun diffKey(): String =
        "$xnm|$xqm|$courseCode|$courseName|$dayOfWeek|$startSection-$endSection|$weeksCsv"

    fun diffSignature(): String =
        "$courseName|$teacher|$classroom|$campus|$dayOfWeek|$startSection-$endSection|$weeksRaw|$credits"
}

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val xnm: String,
    val xqm: String,
    val courseCode: String,
    val courseName: String,
    val examName: String,
    val examTimeRaw: String,
    val startTimestamp: Long?,
    val classroom: String,
    val campus: String,
    val seatNumber: String,
    val credits: String,
    val examMode: String,
    val classGroup: String,
    val isCustom: Boolean = false
) {
    fun diffKey(): String = "$xnm|$xqm|$courseCode|$courseName|$examName"

    fun diffSignature(): String =
        "$courseName|$examName|$examTimeRaw|$classroom|$campus|$seatNumber|$examMode"
}

@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val xnm: String,
    val xqm: String,
    val yearLabel: String,
    val termLabel: String,
    val courseCode: String,
    val courseName: String,
    val credits: Double,
    val scoreRaw: String,
    val numericScore: Double?,
    val gradePoint: Double,
    val courseNature: String,
    val examNature: String,
    val teacher: String,
    val invalidFlag: Boolean,
    val jxbId: String = "",
    val xhId: String = "",
    val breakdownJson: String = ""
) {
    fun diffKey(): String = "$xnm|$xqm|$courseCode|$courseName|$examNature"

    fun diffSignature(): String =
        "$courseName|$scoreRaw|$gradePoint|$credits|$examNature|$invalidFlag"
}

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val triggerType: String, // LOGIN, MANUAL, AUTO_START, AUTO_BG
    val success: Boolean,
    val hasChanges: Boolean,
    val summary: String,
    val details: String
)

@Entity(tableName = "custom_schedule_events")
data class CustomScheduleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val dateIso: String,           // YYYY-MM-DD
    val startTime: String,         // HH:mm
    val endTime: String,           // HH:mm
    val location: String = "",
    val note: String = "",
    val stickyColorIndex: Int = 0, // 0..7 sticky note paper colors
    val repeatWeekly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

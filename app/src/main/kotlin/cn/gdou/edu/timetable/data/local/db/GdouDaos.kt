package cn.gdou.edu.timetable.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentProfileDao {
    @Query("SELECT * FROM student_profile LIMIT 1")
    fun observeProfile(): Flow<StudentProfileEntity?>

    @Query("SELECT * FROM student_profile LIMIT 1")
    suspend fun getProfileOnce(): StudentProfileEntity?

    @Query("DELETE FROM student_profile")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: StudentProfileEntity)

    @Transaction
    suspend fun replaceProfile(profile: StudentProfileEntity) {
        deleteAll()
        insert(profile)
    }
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE xnm = :xnm AND xqm = :xqm ORDER BY dayOfWeek ASC, startSection ASC")
    fun observeCoursesForTerm(xnm: String, xqm: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY dayOfWeek ASC, startSection ASC")
    fun observeAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE xnm = :xnm AND xqm = :xqm ORDER BY dayOfWeek ASC, startSection ASC")
    suspend fun getCoursesForTermOnce(xnm: String, xqm: String): List<CourseEntity>

    @Query("DELETE FROM courses WHERE xnm = :xnm AND xqm = :xqm")
    suspend fun deleteForTerm(xnm: String, xqm: String)

    @Query("DELETE FROM courses")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Transaction
    suspend fun replaceCoursesForTerm(xnm: String, xqm: String, courses: List<CourseEntity>) {
        deleteForTerm(xnm, xqm)
        if (courses.isNotEmpty()) {
            insertAll(courses)
        }
    }
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE xnm = :xnm AND xqm = :xqm ORDER BY CASE WHEN startTimestamp IS NULL THEN 1 ELSE 0 END, startTimestamp ASC, courseName ASC")
    fun observeExamsForTerm(xnm: String, xqm: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams ORDER BY CASE WHEN startTimestamp IS NULL THEN 1 ELSE 0 END, startTimestamp ASC")
    fun observeAllExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE xnm = :xnm AND xqm = :xqm")
    suspend fun getExamsForTermOnce(xnm: String, xqm: String): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE xnm = :xnm AND xqm = :xqm AND isCustom = 0")
    suspend fun getSyncedExamsForTermOnce(xnm: String, xqm: String): List<ExamEntity>

    @Query("DELETE FROM exams WHERE xnm = :xnm AND xqm = :xqm AND isCustom = 0")
    suspend fun deleteSyncedForTerm(xnm: String, xqm: String)

    @Query("DELETE FROM exams WHERE isCustom = 0")
    suspend fun deleteAllSynced()

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM exams")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exams: List<ExamEntity>)

    @Transaction
    suspend fun replaceExamsForTerm(xnm: String, xqm: String, exams: List<ExamEntity>) {
        deleteSyncedForTerm(xnm, xqm)
        if (exams.isNotEmpty()) {
            insertAll(exams)
        }
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades ORDER BY xnm DESC, CAST(xqm AS INTEGER) DESC, courseName ASC")
    fun observeAllGrades(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades ORDER BY xnm DESC, CAST(xqm AS INTEGER) DESC, courseName ASC")
    suspend fun getAllGradesOnce(): List<GradeEntity>

    @Query("DELETE FROM grades")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(grades: List<GradeEntity>)

    @Query("UPDATE grades SET breakdownJson = :breakdownJson WHERE id = :gradeId")
    suspend fun updateBreakdown(gradeId: Long, breakdownJson: String)

    @Transaction
    suspend fun replaceAllGrades(grades: List<GradeEntity>) {
        deleteAll()
        if (grades.isNotEmpty()) {
            insertAll(grades)
        }
    }
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 30")
    fun observeRecentLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY timestamp DESC LIMIT 50)")
    suspend fun trimOldLogs()

    @Query("DELETE FROM sync_logs")
    suspend fun deleteAll()
}

@Dao
interface CustomScheduleEventDao {
    @Query("SELECT * FROM custom_schedule_events ORDER BY dateIso ASC, startTime ASC, id ASC")
    fun observeAll(): Flow<List<CustomScheduleEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CustomScheduleEventEntity): Long

    @Query("DELETE FROM custom_schedule_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_schedule_events")
    suspend fun deleteAll()
}

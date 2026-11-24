package cn.gdou.edu.timetable.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StudentProfileEntity::class,
        CourseEntity::class,
        ExamEntity::class,
        GradeEntity::class,
        SyncLogEntity::class,
        CustomScheduleEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class GdouDatabase : RoomDatabase() {
    abstract fun profileDao(): StudentProfileDao
    abstract fun courseDao(): CourseDao
    abstract fun examDao(): ExamDao
    abstract fun gradeDao(): GradeDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun customEventDao(): CustomScheduleEventDao

    companion object {
        private const val DB_NAME = "gdou_edu_local.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE grades ADD COLUMN jxbId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE grades ADD COLUMN xhId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE grades ADD COLUMN breakdownJson TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exams ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_schedule_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `startTime` TEXT NOT NULL,
                        `endTime` TEXT NOT NULL,
                        `location` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `stickyColorIndex` INTEGER NOT NULL,
                        `repeatWeekly` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): GdouDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                GdouDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

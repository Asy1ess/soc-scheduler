package com.soc.scheduler.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Database(
    entities = [
        ShiftType::class,
        ShiftPattern::class,
        PatternDay::class,
        ShiftOverride::class,
        TaskItem::class,
        HandoverNote::class,
        CheckTemplate::class,
        CheckRun::class,
        ShiftAlarm::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun taskDao(): TaskDao
    abstract fun handoverDao(): HandoverDao
    abstract fun checkDao(): CheckDao
    abstract fun shiftAlarmDao(): ShiftAlarmDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "soc-scheduler.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        /** 근무 시작일(수습 제외) 컬럼 추가. 기존 데이터는 그대로 둔다. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_pattern ADD COLUMN startEpochDay INTEGER")
            }
        }

        /** 근무 유형별 기상 알람 테이블 추가 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS shift_alarm (" +
                        "shiftTypeId INTEGER NOT NULL PRIMARY KEY, " +
                        "enabled INTEGER NOT NULL, " +
                        "hour INTEGER NOT NULL, " +
                        "minute INTEGER NOT NULL, " +
                        "soundUri TEXT NOT NULL, " +
                        "vibrate INTEGER NOT NULL, " +
                        "snoozeMinutes INTEGER NOT NULL)"
                )
            }
        }

        /**
         * 앱이 미리 넣어 두던 관제용 기본 점검 항목을 지운다.
         *
         * 제목이 그대로인 것만 지우므로, 이름을 바꿔 쓰고 있던 항목은 남는다.
         * 사용자가 직접 만든 항목도 건드리지 않는다.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val seeded = SEEDED_CHECK_TITLES.joinToString(", ") { "'" + it + "'" }
                db.execSQL(
                    "DELETE FROM check_run WHERE templateId IN " +
                        "(SELECT id FROM check_template WHERE title IN ($seeded))"
                )
                db.execSQL("DELETE FROM check_template WHERE title IN ($seeded)")
            }
        }

        /**
         * 근무 변경에 저장 시각과 삭제 표시를 더한다. 폰과 웹이 같은 기록을 나눠
         * 갖기 위해서다. 기존 행은 "지금 저장된 것"으로 본다.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL("ALTER TABLE shift_override ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT $now")
                db.execSQL("ALTER TABLE shift_override ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 예전 버전이 기본으로 넣어 주던 점검 항목 제목 */
        private val SEEDED_CHECK_TITLES = listOf(
            "보안장비 상태 점검",
            "탐지 이벤트 검토",
            "로그 수집 상태 확인",
            "백업 결과 확인",
            "취약점 스캔 결과 검토",
            "보안정책 검토",
        )
    }
}

/** 최초 설치 시 기본 근무 유형과 교대 패턴을 넣어 준다. 점검 항목은 사용자가 직접 만든다. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        fun shiftType(name: String, short: String, start: String, end: String, color: String, working: Int, order: Int) {
            db.execSQL(
                "INSERT INTO shift_type (name, shortLabel, startTime, endTime, colorArgb, isWorking, sortOrder) " +
                    "VALUES ('$name', '$short', '$start', '$end', ${color.toLong(16)}, $working, $order)"
            )
        }

        // id 1..7
        shiftType("주간", "주", "06:00", "14:00", "FF2E7D32", 1, 0)
        shiftType("오후", "오", "14:00", "22:00", "FFE65100", 1, 1)
        shiftType("야간", "야", "22:00", "06:00", "FF283593", 1, 2)
        shiftType("비번", "비", "", "", "FF616161", 0, 3)
        shiftType("휴무", "휴", "", "", "FF9E9E9E", 0, 4)
        shiftType("연차", "연", "", "", "FF00838F", 0, 5)
        shiftType("교육/출장", "교", "", "", "FF6A1B9A", 0, 6)

        // 기본 패턴: 4조 3교대 (주 주 오 오 야 야 비 휴)
        val anchor = LocalDate.now().toEpochDay()
        db.execSQL(
            "INSERT INTO shift_pattern (name, cycleDays, teamCount, anchorEpochDay, myOffset, isActive) " +
                "VALUES ('4조 3교대', 8, 4, $anchor, 0, 1)"
        )
        val defaultCycle = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L, 5L)
        defaultCycle.forEachIndexed { index, typeId ->
            db.execSQL("INSERT INTO pattern_day (patternId, dayIndex, shiftTypeId) VALUES (1, $index, $typeId)")
        }

    }
}

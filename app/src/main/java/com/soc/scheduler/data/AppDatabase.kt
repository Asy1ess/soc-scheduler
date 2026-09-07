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
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun taskDao(): TaskDao
    abstract fun handoverDao(): HandoverDao
    abstract fun checkDao(): CheckDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "soc-scheduler.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        /** 근무 시작일(수습 제외) 컬럼 추가. 기존 데이터는 그대로 둔다. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_pattern ADD COLUMN startEpochDay INTEGER")
            }
        }
    }
}

/** 최초 설치 시 기본 근무 유형 / 교대 패턴 / 점검 항목을 넣어 준다. */
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

        fun template(title: String, memo: String, recurrence: String, weekDays: String, monthDay: Int, time: String, order: Int) {
            db.execSQL(
                "INSERT INTO check_template (title, memo, recurrence, weekDays, monthDay, timeLabel, active, sortOrder) " +
                    "VALUES ('$title', '$memo', '$recurrence', '$weekDays', $monthDay, '$time', 1, $order)"
            )
        }

        template("보안장비 상태 점검", "IPS/IDS/WAF/방화벽 헬스체크", Recurrence.DAILY, "", 1, "09:00", 0)
        template("탐지 이벤트 검토", "전일 이벤트 오탐 여부 확인", Recurrence.DAILY, "", 1, "10:00", 1)
        template("로그 수집 상태 확인", "SIEM 수집 누락 여부 확인", Recurrence.DAILY, "", 1, "08:00", 2)
        template("백업 결과 확인", "일일 백업 성공 여부", Recurrence.DAILY, "", 1, "08:30", 3)
        template("취약점 스캔 결과 검토", "주간 스캔 리포트 확인", Recurrence.WEEKLY, "1", 1, "14:00", 4)
        template("보안정책 검토", "차단 정책 및 룰셋 점검", Recurrence.MONTHLY, "", 1, "15:00", 5)
    }
}

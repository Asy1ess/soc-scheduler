package com.soc.scheduler.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shift_type ORDER BY sortOrder, id")
    fun observeTypes(): Flow<List<ShiftType>>

    @Query("SELECT * FROM shift_type ORDER BY sortOrder, id")
    suspend fun types(): List<ShiftType>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertType(type: ShiftType): Long

    @Delete
    suspend fun deleteType(type: ShiftType)

    @Query("SELECT * FROM shift_pattern ORDER BY id")
    fun observePatterns(): Flow<List<ShiftPattern>>

    @Query("SELECT * FROM shift_pattern WHERE isActive = 1 LIMIT 1")
    fun observeActivePattern(): Flow<ShiftPattern?>

    @Query("SELECT * FROM shift_pattern WHERE isActive = 1 LIMIT 1")
    suspend fun activePattern(): ShiftPattern?

    @Query("SELECT * FROM shift_pattern WHERE id = :id")
    suspend fun patternById(id: Long): ShiftPattern?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: ShiftPattern): Long

    @Update
    suspend fun updatePattern(pattern: ShiftPattern)

    @Query("SELECT id FROM shift_pattern")
    suspend fun allPatternIds(): List<Long>

    @Query("DELETE FROM shift_pattern WHERE id = :id")
    suspend fun deletePattern(id: Long)

    @Query("UPDATE shift_pattern SET isActive = 0")
    suspend fun clearActivePatterns()

    @Query("UPDATE shift_pattern SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("SELECT * FROM pattern_day WHERE patternId = :patternId ORDER BY dayIndex")
    fun observePatternDays(patternId: Long): Flow<List<PatternDay>>

    @Query("SELECT * FROM pattern_day WHERE patternId = :patternId ORDER BY dayIndex")
    suspend fun patternDays(patternId: Long): List<PatternDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatternDays(days: List<PatternDay>)

    @Query("DELETE FROM pattern_day WHERE patternId = :patternId")
    suspend fun deletePatternDays(patternId: Long)

    @Query("SELECT * FROM shift_override WHERE epochDay BETWEEN :from AND :to")
    fun observeOverrides(from: Long, to: Long): Flow<List<ShiftOverride>>

    @Query("SELECT * FROM shift_override WHERE epochDay BETWEEN :from AND :to")
    suspend fun overridesBetween(from: Long, to: Long): List<ShiftOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: ShiftOverride)

    @Query("DELETE FROM shift_override WHERE epochDay = :epochDay")
    suspend fun deleteOverride(epochDay: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task ORDER BY done, dueAtMillis")
    fun observeAll(): Flow<List<TaskItem>>

    @Query("SELECT * FROM task WHERE dueAtMillis BETWEEN :from AND :to ORDER BY dueAtMillis")
    fun observeRange(from: Long, to: Long): Flow<List<TaskItem>>

    @Query("SELECT * FROM task WHERE dueAtMillis BETWEEN :from AND :to ORDER BY done, dueAtMillis")
    suspend fun tasksBetween(from: Long, to: Long): List<TaskItem>

    @Query("SELECT * FROM task WHERE done = 0 AND reminderMinutesBefore >= 0 AND dueAtMillis > :now")
    suspend fun pendingReminders(now: Long): List<TaskItem>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun byId(id: Long): TaskItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskItem): Long

    @Query("UPDATE task SET done = :done, doneAtMillis = :doneAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HandoverDao {
    @Query("SELECT * FROM handover ORDER BY epochDay DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<HandoverNote>>

    @Query(
        "SELECT * FROM handover WHERE title LIKE :like OR content LIKE :like OR category LIKE :like " +
            "ORDER BY epochDay DESC, createdAtMillis DESC"
    )
    fun search(like: String): Flow<List<HandoverNote>>

    @Query("SELECT * FROM handover WHERE epochDay = :epochDay ORDER BY createdAtMillis DESC")
    fun observeDay(epochDay: Long): Flow<List<HandoverNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: HandoverNote): Long

    @Query("DELETE FROM handover WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CheckDao {
    @Query("SELECT * FROM check_template ORDER BY sortOrder, id")
    fun observeTemplates(): Flow<List<CheckTemplate>>

    @Query("SELECT * FROM check_template ORDER BY sortOrder, id")
    suspend fun templatesOnce(): List<CheckTemplate>

    @Query("SELECT * FROM check_template WHERE active = 1")
    suspend fun activeTemplates(): List<CheckTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: CheckTemplate): Long

    @Query("DELETE FROM check_template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM check_run WHERE templateId = :id")
    suspend fun deleteRunsOfTemplate(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRuns(runs: List<CheckRun>)

    @Query(
        "SELECT r.id AS runId, r.templateId AS templateId, t.title AS title, t.memo AS memo, " +
            "t.timeLabel AS timeLabel, r.done AS done, r.note AS note " +
            "FROM check_run r JOIN check_template t ON t.id = r.templateId " +
            "WHERE r.epochDay = :epochDay ORDER BY t.timeLabel, t.sortOrder, t.id"
    )
    fun observeRuns(epochDay: Long): Flow<List<CheckRunView>>

    @Query(
        "SELECT r.id AS runId, r.templateId AS templateId, t.title AS title, t.memo AS memo, " +
            "t.timeLabel AS timeLabel, r.done AS done, r.note AS note " +
            "FROM check_run r JOIN check_template t ON t.id = r.templateId " +
            "WHERE r.epochDay = :epochDay ORDER BY t.timeLabel, t.sortOrder, t.id"
    )
    suspend fun runsOnce(epochDay: Long): List<CheckRunView>

    @Query("SELECT done FROM check_run WHERE id = :id")
    suspend fun isDone(id: Long): Boolean?

    @Query("UPDATE check_run SET done = :done, doneAtMillis = :doneAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?)

    @Query("UPDATE check_run SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String)
}

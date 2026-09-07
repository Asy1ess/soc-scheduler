package com.soc.scheduler.remote

import com.soc.scheduler.Graph
import com.soc.scheduler.data.ShiftType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Kakao
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

// ---------------------------------------------------------------- 전송 모델

@Serializable
data class ShiftTypeDto(
    val label: String,
    val name: String,
    val start: String = "",
    val end: String = "",
    val color: String = "",
)

@Serializable
data class MyProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
)

@Serializable
data class SharePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("pattern_name") val patternName: String,
    @SerialName("cycle_days") val cycleDays: Int,
    @SerialName("anchor_epoch_day") val anchorEpochDay: Long,
    @SerialName("my_offset") val myOffset: Int,
    @SerialName("start_epoch_day") val startEpochDay: Long? = null,
    val cycle: List<String>,
    val types: List<ShiftTypeDto>,
)

@Serializable
data class OverridePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("epoch_day") val epochDay: Long,
    val label: String,
)

/** list_friend_schedules() 결과 */
@Serializable
data class FriendScheduleDto(
    @SerialName("friend_id") val friendId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("pattern_name") val patternName: String = "",
    @SerialName("cycle_days") val cycleDays: Int = 0,
    @SerialName("anchor_epoch_day") val anchorEpochDay: Long = 0,
    @SerialName("my_offset") val myOffset: Int = 0,
    @SerialName("start_epoch_day") val startEpochDay: Long? = null,
    val cycle: List<String> = emptyList(),
    val types: List<ShiftTypeDto> = emptyList(),
) {
    /** 친구의 특정 날짜 근무 라벨. 패턴이 없으면 null */
    fun labelAt(date: LocalDate, overrides: Map<Long, String> = emptyMap()): String? {
        overrides[date.toEpochDay()]?.let { return it }

        // 교대 근무 시작 전 구간은 앱과 동일하게 기본 주간 일정으로 채운다.
        val start = startEpochDay
        if (start != null && date.toEpochDay() < start) return preStartLabel(date)

        if (cycleDays <= 0 || cycle.isEmpty()) return null
        val diff = date.toEpochDay() - anchorEpochDay
        val raw = (diff + myOffset) % cycleDays
        val index = ((raw + cycleDays) % cycleDays).toInt()
        return cycle.getOrNull(index)
    }

    private fun preStartLabel(date: LocalDate): String? {
        val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
            date.dayOfWeek == java.time.DayOfWeek.SUNDAY
        return if (weekend) {
            types.firstOrNull { it.name.contains("휴무") || it.label == "휴" }?.label
        } else {
            types.firstOrNull { it.name.contains("주간") || it.label == "주" }?.label
        }
    }

    fun typeOf(label: String?): ShiftTypeDto? =
        if (label == null) null else types.firstOrNull { it.label == label }
}

@Serializable
data class FriendOverrideDto(
    @SerialName("user_id") val userId: String,
    @SerialName("epoch_day") val epochDay: Long,
    val label: String,
)

// ---------------------------------------------------------------- 저장소

/**
 * 친구 공유 기능의 네트워크 계층.
 *
 * 올라가는 것은 근무표(패턴 + 근무 유형 + 날짜별 변경)뿐이다.
 * 일정 · 인수인계 · 점검 기록은 이 파일 어디에서도 다루지 않는다.
 */
object FriendRepository {

    val isAvailable: Boolean get() = Supa.isConfigured

    val sessionStatus: Flow<SessionStatus>
        get() = if (isAvailable) Supa.client.auth.sessionStatus else flowOf(SessionStatus.NotAuthenticated(false))

    fun currentUserId(): String? =
        if (isAvailable) Supa.client.auth.currentUserOrNull()?.id else null

    suspend fun signInWithGoogle() {
        Supa.client.auth.signInWith(Google)
    }

    suspend fun signInWithKakao() {
        Supa.client.auth.signInWith(Kakao)
    }

    suspend fun signOut() {
        Supa.client.auth.signOut()
    }

    suspend fun myProfile(): MyProfileDto? {
        val uid = currentUserId() ?: return null
        return Supa.client.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<MyProfileDto>()
    }

    suspend fun setDisplayName(name: String) {
        val uid = currentUserId() ?: return
        Supa.client.from("profiles").update(
            buildJsonObject { put("display_name", JsonPrimitive(name)) }
        ) { filter { eq("id", uid) } }
    }

    /** 내 근무표를 서버에 올린다. 로그인 상태가 아니면 아무것도 하지 않는다. */
    suspend fun publishMySchedule() {
        val uid = currentUserId() ?: return
        val repo = Graph.repo
        val pattern = repo.shiftDao.activePattern() ?: return
        val days = repo.shiftDao.patternDays(pattern.id)
        if (days.isEmpty()) return

        val types: List<ShiftType> = repo.shiftDao.types()
        val typeMap = types.associateBy { it.id }
        val cycle = days.sortedBy { it.dayIndex }
            .mapNotNull { typeMap[it.shiftTypeId]?.shortLabel }
        if (cycle.size != days.size) return

        Supa.client.from("shift_shares").upsert(
            SharePayload(
                userId = uid,
                patternName = pattern.name,
                cycleDays = pattern.cycleDays,
                anchorEpochDay = pattern.anchorEpochDay,
                myOffset = pattern.myOffset,
                startEpochDay = pattern.startEpochDay,
                cycle = cycle,
                types = types.map {
                    ShiftTypeDto(
                        label = it.shortLabel,
                        name = it.name,
                        start = it.startTime,
                        end = it.endTime,
                        color = java.lang.Long.toHexString(it.colorArgb).uppercase(),
                    )
                },
            )
        )

        // 앞뒤 두 달치 근무 변경만 올린다. 과거 이력을 통째로 보낼 이유는 없다.
        val today = LocalDate.now()
        val from = today.minusMonths(1).toEpochDay()
        val to = today.plusMonths(2).toEpochDay()
        val overrides = repo.shiftDao.overridesBetween(from, to)

        Supa.client.from("shift_overrides").delete { filter { eq("user_id", uid) } }
        if (overrides.isNotEmpty()) {
            Supa.client.from("shift_overrides").insert(
                overrides.mapNotNull { ov ->
                    typeMap[ov.shiftTypeId]?.let {
                        OverridePayload(uid, ov.epochDay, it.shortLabel)
                    }
                }
            )
        }
    }

    suspend fun friends(): List<FriendScheduleDto> =
        Supa.client.postgrest.rpc("list_friend_schedules").decodeList()

    suspend fun friendOverrides(): Map<String, Map<Long, String>> {
        val rows = Supa.client.from("shift_overrides")
            .select()
            .decodeList<FriendOverrideDto>()
        return rows.groupBy { it.userId }
            .mapValues { entry -> entry.value.associate { it.epochDay to it.label } }
    }

    /** @return 친구가 된 상대의 id */
    suspend fun addFriendByCode(code: String): String =
        Supa.client.postgrest.rpc(
            "add_friend_by_code",
            buildJsonObject { put("code", JsonPrimitive(code)) },
        ).decodeAs()

    suspend fun removeFriend(friendId: String) {
        Supa.client.postgrest.rpc(
            "remove_friend",
            buildJsonObject { put("target", JsonPrimitive(friendId)) },
        )
    }
}
